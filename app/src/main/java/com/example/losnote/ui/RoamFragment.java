package com.example.losnote.ui;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.losnote.R;
import com.example.losnote.ai.AiClient;
import com.example.losnote.ai.AiConfig;
import com.example.losnote.data.DBHelper;
import com.example.losnote.data.Note;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.noties.markwon.Markwon;

/**
 * 简约漫游模式：
 * - 根据本地题库画像 + 用户可选偏好，生成一个客观准确的新知识点
 * - 不与已有题库/本次漫游历史重复
 * - 可保存，或直接下一个
 */
public class RoamFragment extends Fragment {

    private DBHelper db;
    private Markwon markwon;
    private EditText etPreference, etCategory;
    private TextView tvTitle, tvDef, tvHint;
    private Note current;
    private final Set<String> roamHistory = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_roam, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        db = DBHelper.get(requireContext());
        markwon = MarkdownRenderer.create(requireContext());

        etPreference = v.findViewById(R.id.et_roam_preference);
        etCategory = v.findViewById(R.id.et_roam_category);
        tvTitle = v.findViewById(R.id.tv_roam_title);
        tvDef = v.findViewById(R.id.tv_roam_def);
        tvHint = v.findViewById(R.id.tv_roam_hint);

        MaterialButton btnNext = v.findViewById(R.id.btn_roam_next);
        MaterialButton btnSkip = v.findViewById(R.id.btn_roam_skip);
        MaterialButton btnSave = v.findViewById(R.id.btn_roam_save);

        btnNext.setOnClickListener(view -> generateOne());
        btnSkip.setOnClickListener(view -> generateOne());
        btnSave.setOnClickListener(view -> saveCurrent());
    }

    public void refreshOnShow() { }

    private void generateOne() {
        ProgressDialog pd = new ProgressDialog(requireContext());
        pd.setMessage("AI 正在根据你的题库漫游…");
        pd.setCancelable(false);
        pd.show();
        String pref = etPreference.getText().toString().trim();
        String category = etCategory.getText().toString().trim();
        if (category.isEmpty()) category = "AI漫游";
        String finalCategory = category;
        new Thread(() -> {
            try {
                AiConfig cfg = AiConfig.load(requireContext());
                if (cfg.apiKey == null || cfg.apiKey.isEmpty()) {
                    throw new RuntimeException("请先到 题库 → 设置 → AI 设置 中配置 API Key");
                }
                Note n = callAiRoam(cfg, pref, finalCategory);
                requireActivity().runOnUiThread(() -> {
                    pd.dismiss();
                    current = n;
                    roamHistory.add(normalize(n.name));
                    tvTitle.setText(n.name);
                    markwon.setMarkdown(tvDef, n.definition);
                    tvHint.setText("已根据题库画像生成，可保存或继续下一个");
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(getContext(), "漫游失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private Note callAiRoam(AiConfig cfg, String pref, String category) throws Exception {
        List<Note> all = db.queryAll();
        JSONArray known = new JSONArray();
        int max = Math.min(120, all.size());
        // 这里使用最近/当前查询顺序的一部分题库名称与栏目作为“题库画像”，不要只看编程
        for (int i = 0; i < max; i++) {
            Note n = all.get(i);
            JSONObject o = new JSONObject();
            o.put("name", n.name);
            o.put("category", n.category);
            known.put(o);
        }
        JSONArray history = new JSONArray();
        for (String h : roamHistory) history.put(h);

        String prompt = "你是一个严谨的知识漫游助手。请根据用户的本地题库画像，推荐 1 个新的、客观准确、值得学习的知识点。"
                + "必须动态分析题库中体现出的兴趣分布，不要默认偏向编程；如果题库或用户偏好偏书法/历史/文学等，就推荐对应领域。"
                + "推荐的知识点不能与已有题库重复，也不要与本次漫游历史重复。"
                + "如果 AI 设置中启用了联网搜索提示，且模型支持联网，请结合可靠公开信息；否则基于通用可靠知识，不要编造。"
                + "输出 JSON 对象：name、definition、category。definition 用 Markdown，包含：简明定义、关键要点、例子/应用/练习建议、注意事项。"
                + "除非主题是编程/计算机，否则不要输出代码。只输出 JSON 对象，不要解释。\n\n"
                + "用户补充偏好：" + (pref.isEmpty() ? "无，主要根据题库画像判断" : pref) + "\n"
                + "保存栏目：" + category + "\n"
                + "本次漫游历史（避免重复）：" + history.toString() + "\n"
                + "本地题库画像样例：" + known.toString();

        String content = AiClient.chat(cfg, "你只输出合法 JSON 对象。内容必须客观准确，宁可保守，不要编造。", prompt, 0.6);
        content = AiClient.stripFence(content);
        JSONObject obj = new JSONObject(content);
        String name = obj.optString("name", "AI知识点").trim();
        String def = obj.optString("definition", "").trim();
        String cat = obj.optString("category", category).trim();
        if (cat.isEmpty()) cat = category;
        if (name.isEmpty()) name = "AI知识点";
        return new Note(name, def, cat);
    }

    private void saveCurrent() {
        if (current == null || TextUtils.isEmpty(current.name)) {
            Toast.makeText(getContext(), "还没有可保存的知识点", Toast.LENGTH_SHORT).show();
            return;
        }
        String cat = etCategory.getText().toString().trim();
        if (!cat.isEmpty()) current.category = cat;
        db.insert(current);
        Toast.makeText(getContext(), "已保存到题库：" + current.name, Toast.LENGTH_SHORT).show();
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）【】《》]+", "").trim();
    }
}
