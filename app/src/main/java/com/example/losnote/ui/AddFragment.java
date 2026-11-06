package com.example.losnote.ui;

import android.os.Bundle;
import android.net.Uri;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.losnote.R;
import com.example.losnote.ai.AiConfig;
import com.example.losnote.ai.AiClient;
import com.example.losnote.data.DBHelper;
import com.example.losnote.data.Note;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块一：上传 / 录入。
 * - 单条录入：名称 + 定义 + 栏目（栏目支持下拉选择已有的，也可手动输入新栏目）
 * - 批量导入：弹窗粘贴多行文本，每行格式  名称 | 定义 | 栏目
 * 录入后的查看/编辑/删除请到"题库"页。
 */
public class AddFragment extends Fragment {

    private DBHelper db;

    private EditText etName;
    private EditText etDefinition;
    private AutoCompleteTextView etCategory;
    private ActivityResultLauncher<String[]> fileImportLauncher;
    private ActivityResultLauncher<String[]> aiImportLauncher;
    private String pendingAiCategory = "CPP";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) importFromFile(uri); });
        aiImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) startAiOrganize(uri, pendingAiCategory); });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        db = DBHelper.get(requireContext());

        etName = v.findViewById(R.id.et_name);
        etDefinition = v.findViewById(R.id.et_definition);
        etCategory = v.findViewById(R.id.et_category);

        Button btnSave = v.findViewById(R.id.btn_save);
        Button btnFileImport = v.findViewById(R.id.btn_file_import);
        Button btnBatchImport = v.findViewById(R.id.btn_batch_import);
        Button btnAiImport = v.findViewById(R.id.btn_ai_import);

        btnSave.setOnClickListener(view -> saveSingle());
        btnFileImport.setOnClickListener(view -> fileImportLauncher.launch(new String[]{"text/*", "application/json", "application/octet-stream"}));
        btnBatchImport.setOnClickListener(view -> showBatchDialog());
        btnAiImport.setOnClickListener(view -> showAiImportDialog());

        refreshCategoryDropdown();
    }

    /* ---------------- 单条保存 ---------------- */

    private void saveSingle() {
        String name = etName.getText().toString().trim();
        String def = etDefinition.getText().toString().trim();
        String cat = etCategory.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(getContext(), "请填写名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(cat)) {
            cat = "未分类";
        }

        db.insert(new Note(name, def, cat));
        Toast.makeText(getContext(), "已保存", Toast.LENGTH_SHORT).show();

        // 清空名称和定义，保留栏目，方便连续录入同一栏目
        etName.setText("");
        etDefinition.setText("");
        etName.requestFocus();

        refreshCategoryDropdown();
    }

    /* ---------------- 批量导入 ---------------- */

    private void showBatchDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_batch, null);
        EditText etBatch = dialogView.findViewById(R.id.et_batch);
        EditText etBatchCat = dialogView.findViewById(R.id.et_batch_category);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("批量导入")
                .setView(dialogView)
                .setPositiveButton("导入", (d, which) ->
                        doBatchImport(etBatch.getText().toString(),
                                etBatchCat.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 支持两种格式：
     * 1) 多行模式（推荐用于大段答案，含代码/换行）：
     *    用 @@@ 单独一行分隔每条题目；每条第一行为名称，其余行为定义（保留换行）。
     * 2) 单行模式（兼容旧格式）：每行一条  名称 | 定义 | 栏目
     *
     * 栏目优先级：单行模式内自带的栏目 > 弹窗里填的统一栏目 > "未分类"
     */
    private void doBatchImport(String raw, String defaultCat) {
        if (TextUtils.isEmpty(raw) || raw.trim().isEmpty()) {
            Toast.makeText(getContext(), "内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String fallbackCat = (defaultCat == null || defaultCat.trim().isEmpty())
                ? "未分类" : defaultCat.trim();

        List<Note> notes;
        if (raw.contains("@@@")) {
            notes = parseMultiline(raw, fallbackCat);
        } else {
            notes = parseSingleLine(raw, fallbackCat);
        }

        if (notes.isEmpty()) {
            Toast.makeText(getContext(), "没有可导入的有效内容", Toast.LENGTH_SHORT).show();
            return;
        }

        int ok = db.insertBatch(notes);
        Toast.makeText(getContext(), "成功导入 " + ok + " 条", Toast.LENGTH_LONG).show();
        refreshCategoryDropdown();
    }

    /** AI 整理导入：先选文件，再调用 DeepSeek，最后预览勾选导入 */
    private void showAiImportDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_import, null);
        EditText etCat = view.findViewById(R.id.et_ai_category);
        String current = etCategory.getText().toString().trim();
        if (!current.isEmpty()) etCat.setText(current);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("AI整理文件导入")
                .setView(view)
                .setPositiveButton("选择文件", (d, w) -> {
                    pendingAiCategory = etCat.getText().toString().trim();
                    if (pendingAiCategory.isEmpty()) pendingAiCategory = "CPP";
                    ensureApiKeyThenOpenFile();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void ensureApiKeyThenOpenFile() {
        AiConfig cfg = AiConfig.load(requireContext());
        if (cfg.apiKey != null && !cfg.apiKey.isEmpty()) {
            aiImportLauncher.launch(new String[]{"text/*", "application/octet-stream"});
            return;
        }
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_settings, null);
        AutoCompleteTextView etProvider = view.findViewById(R.id.et_ai_provider);
        EditText etKey = view.findViewById(R.id.et_ai_api_key);
        EditText etBase = view.findViewById(R.id.et_ai_base_url);
        EditText etModel = view.findViewById(R.id.et_ai_model);
        com.google.android.material.materialswitch.MaterialSwitch sw = view.findViewById(R.id.switch_ai_web_search);
        String[] providers = {AiConfig.PROVIDER_DEEPSEEK, AiConfig.PROVIDER_QWEN, AiConfig.PROVIDER_CUSTOM};
        etProvider.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, providers));
        etProvider.setText(cfg.provider, false);
        etKey.setText(cfg.apiKey);
        etBase.setText(cfg.baseUrl);
        etModel.setText(cfg.model);
        sw.setChecked(cfg.webSearch);
        etProvider.setOnItemClickListener((p, v, pos, id) -> {
            String provider = providers[pos];
            etBase.setText(AiConfig.defaultBaseUrl(provider));
            etModel.setText(AiConfig.defaultModel(provider));
        });
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("配置 AI API Key")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    AiConfig c = new AiConfig();
                    c.provider = etProvider.getText().toString().trim();
                    c.apiKey = etKey.getText().toString().trim();
                    c.baseUrl = etBase.getText().toString().trim();
                    c.model = etModel.getText().toString().trim();
                    c.webSearch = sw.isChecked();
                    if (c.apiKey.isEmpty()) {
                        Toast.makeText(getContext(), "API Key 不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    c.save(requireContext());
                    aiImportLauncher.launch(new String[]{"text/*", "application/octet-stream"});
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startAiOrganize(Uri uri, String category) {
        try {
            String raw = readTextFromUri(uri);
            if (raw.trim().isEmpty()) {
                Toast.makeText(getContext(), "文件内容为空", Toast.LENGTH_SHORT).show();
                return;
            }
            ProgressDialog pd = new ProgressDialog(requireContext());
            pd.setMessage("DeepSeek 正在整理题目…");
            pd.setCancelable(false);
            pd.show();
            new Thread(() -> {
                try {
                    AiConfig cfg = AiConfig.load(requireContext());
                    List<Note> notes = callDeepSeekOrganize(raw, category, cfg);
                    requireActivity().runOnUiThread(() -> {
                        pd.dismiss();
                        showAiPreview(notes);
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        pd.dismiss();
                        Toast.makeText(getContext(), "AI整理失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(getContext(), "读取文件失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readTextFromUri(Uri uri) throws Exception {
        InputStream is = requireContext().getContentResolver().openInputStream(uri);
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private List<Note> callDeepSeekOrganize(String raw, String category, AiConfig aiConfig) throws Exception {
        String sample = raw.length() > 45000 ? raw.substring(0, 45000) : raw;
        String prompt = "你是题库整理助手。请把用户提供的笔记整理为 JSON 数组。"
                + "每个元素包含 name、definition、category 三个字段。"
                + "name 是题目，definition 是答案/定义，尽量保留 Markdown、代码块、列表和表格。"
                + "category 统一填：" + category + "。"
                + "只输出 JSON 数组，不要解释，不要 Markdown 代码围栏。\n\n笔记内容：\n" + sample;

        String content = AiClient.chat(aiConfig, "你只输出合法 JSON。", prompt, 0.1);
        content = AiClient.stripFence(content);
        JSONArray arr = new JSONArray(content);
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("name", "").trim();
            if (name.isEmpty()) continue;
            String def = o.optString("definition", "").trim();
            String cat = o.optString("category", category).trim();
            if (cat.isEmpty()) cat = category;
            notes.add(new Note(name, def, cat));
        }
        return notes;
    }

    private void showAiPreview(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            Toast.makeText(getContext(), "AI 没有整理出有效题目", Toast.LENGTH_LONG).show();
            return;
        }
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_preview, null);
        TextView tvCount = view.findViewById(R.id.tv_preview_count);
        RecyclerView rv = view.findViewById(R.id.recycler_preview);
        Button btnAll = view.findViewById(R.id.btn_preview_select_all);
        Button btnImport = view.findViewById(R.id.btn_preview_import);
        PreviewNoteAdapter adapter = new PreviewNoteAdapter(notes);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        Runnable update = () -> tvCount.setText("选中 " + adapter.selectedCount() + " / 共 " + notes.size() + " 题");
        adapter.setOnChanged(update::run);
        adapter.setOnEdit((note, position) -> showPreviewEditDialog(note, position, adapter));
        update.run();
        btnAll.setOnClickListener(v -> adapter.toggleAll());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("AI整理预览")
                .setView(view)
                .create();
        btnImport.setOnClickListener(v -> {
            List<Note> selected = adapter.selectedNotes();
            if (selected.isEmpty()) {
                Toast.makeText(getContext(), "没有选中题目", Toast.LENGTH_SHORT).show();
                return;
            }
            int ok = db.insertBatch(selected);
            Toast.makeText(getContext(), "成功导入 " + ok + " 条", Toast.LENGTH_LONG).show();
            refreshCategoryDropdown();
            dialog.dismiss();
        });
        dialog.show();
    }

    /** 编辑 AI 预览中的某一条，修改后再批量导入 */
    private void showPreviewEditDialog(Note note, int position, PreviewNoteAdapter adapter) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit, null);
        EditText etName = dialogView.findViewById(R.id.et_edit_name);
        EditText etDef = dialogView.findViewById(R.id.et_edit_definition);
        EditText etCat = dialogView.findViewById(R.id.et_edit_category);

        etName.setText(note.name);
        etDef.setText(note.definition);
        etCat.setText(note.category);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑预览题目")
                .setView(dialogView)
                .setPositiveButton("保存", (d, which) -> {
                    String name = etName.getText().toString().trim();
                    String def = etDef.getText().toString().trim();
                    String cat = etCat.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (cat.isEmpty()) cat = "未分类";
                    note.name = name;
                    note.definition = def;
                    note.category = cat;
                    adapter.notifyItemChanged(position);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 从本地 txt/md 文件导入：读取文件后复用同一套解析逻辑，并弹窗预览数量 */
    private void importFromFile(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            String raw = sb.toString();
            String cat = etCategory.getText().toString().trim();
            if (cat.isEmpty()) cat = "CPP";
            List<Note> notes = raw.contains("@@@") ? parseMultiline(raw, cat) : parseSingleLine(raw, cat);
            if (notes.isEmpty()) {
                Toast.makeText(getContext(), "文件中没有可导入的有效题目", Toast.LENGTH_LONG).show();
                return;
            }
            StringBuilder preview = new StringBuilder();
            int max = Math.min(8, notes.size());
            for (int i = 0; i < max; i++) {
                preview.append(i + 1).append(". ").append(notes.get(i).name).append('\n');
            }
            if (notes.size() > max) preview.append("……等共 ").append(notes.size()).append(" 条");
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("文件导入预览")
                    .setMessage(preview.toString())
                    .setPositiveButton("导入全部", (d, w) -> {
                        int ok = db.insertBatch(notes);
                        Toast.makeText(getContext(), "成功导入 " + ok + " 条", Toast.LENGTH_LONG).show();
                        refreshCategoryDropdown();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "读取文件失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 多行模式：单独一行的 @@@ 分隔，每块第一行=名称，其余=定义 */
    private List<Note> parseMultiline(String raw, String fallbackCat) {
        List<Note> notes = new ArrayList<>();
        String[] blocks = raw.split("(?m)^\\s*@@@\\s*$");
        for (String block : blocks) {
            String b = block.trim();
            if (b.isEmpty()) {
                continue;
            }
            int nl = b.indexOf('\n');
            String name;
            String def;
            if (nl < 0) {
                name = b.trim();
                def = "";
            } else {
                name = b.substring(0, nl).trim();
                def = b.substring(nl + 1).trim();
            }
            if (name.isEmpty()) {
                continue;
            }
            notes.add(new Note(name, def, fallbackCat));
        }
        return notes;
    }

    /** 单行模式：每行  名称 | 定义 | 栏目 */
    private List<Note> parseSingleLine(String raw, String fallbackCat) {
        List<Note> notes = new ArrayList<>();
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            // 首段为名称，末段为栏目，中间各段合并为定义，保留命令和 Markdown 中的管道符。
            String[] parts = line.split("[|｜]", -1);
            String name = parts.length > 0 ? parts[0].trim() : "";
            String def = "";
            String cat = "";
            if (parts.length == 2) {
                def = parts[1].trim();
            } else if (parts.length > 2) {
                StringBuilder definition = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) {
                    if (definition.length() > 0) definition.append(" | ");
                    definition.append(parts[i].trim());
                }
                def = definition.toString();
                cat = parts[parts.length - 1].trim();
            }
            if (name.isEmpty()) {
                continue;
            }
            if (cat.isEmpty()) {
                cat = fallbackCat;
            }
            notes.add(new Note(name, def, cat));
        }
        return notes;
    }

    /* ---------------- 栏目下拉 ---------------- */

    private void refreshCategoryDropdown() {
        List<String> cats = db.queryCategories();
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, cats);
        etCategory.setAdapter(a);
    }
}
