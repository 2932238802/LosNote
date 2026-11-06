package com.example.losnote.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.losnote.MainActivity;
import com.example.losnote.R;
import com.example.losnote.ai.AiConfig;
import com.example.losnote.ai.AiClient;
import com.example.losnote.data.DBHelper;
import com.example.losnote.data.DailyLog;
import com.example.losnote.data.Note;
import com.example.losnote.sync.SyncClient;
import com.example.losnote.sync.SyncConfig;
import com.example.losnote.sync.SyncResult;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 第三个模块：本地题库管理。
 * - 搜索（名称/内容）
 * - 按栏目筛选
 * - 单条 查看/编辑/删除；批量删除；按栏目删除
 * - 导出/导入（JSON 文件，用于备份）
 */
public class BankFragment extends Fragment {

    private DBHelper db;
    private io.noties.markwon.Markwon markwon;
    private MaterialButton btnBankCategory;
    private final Set<String> selectedBankCategories = new HashSet<>();
    private EditText etSearch;
    private LinearLayout chipsBankCategories;
    private TextView tvCount;
    private TextView tvHint;
    private RecyclerView recycler;
    private NoteAdapter adapter;
    private final List<Note> visibleNotes = new ArrayList<>();

    private MaterialButton btnMultiSelect;
    private View barSelection;
    private MaterialButton btnDeleteSelected;

    // 文件选择器（SAF）
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bank, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        db = DBHelper.get(requireContext());
        markwon = MarkdownRenderer.create(requireContext());

        btnBankCategory = v.findViewById(R.id.btn_bank_category);
        etSearch = v.findViewById(R.id.et_search);
        chipsBankCategories = v.findViewById(R.id.chips_bank_categories);
        tvCount = v.findViewById(R.id.tv_bank_count);
        tvHint = v.findViewById(R.id.tv_bank_hint);
        recycler = v.findViewById(R.id.recycler_bank);

        btnMultiSelect = v.findViewById(R.id.btn_multi_select);
        barSelection = v.findViewById(R.id.bar_selection);
        btnDeleteSelected = v.findViewById(R.id.btn_delete_selected);
        MaterialButton btnSelectAll = v.findViewById(R.id.btn_select_all);
        MaterialButton btnCancel = v.findViewById(R.id.btn_cancel_select);
        MaterialButton btnSettings = v.findViewById(R.id.btn_settings);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NoteAdapter(new ArrayList<>());
        adapter.setMarkwon(markwon);
        adapter.setOnItemClick(this::showView);
        adapter.setOnItemLongClick(note -> {
            if (!adapter.isSelectionMode()) {
                enterSelectionMode();
            }
            adapter.select(note);
        });
        adapter.setOnSelectionChanged(count ->
                btnDeleteSelected.setText("删除选中(" + count + ")"));
        recycler.setAdapter(adapter);

        btnBankCategory.setOnClickListener(view -> showBankCategoryMultiSelect());

        // 搜索：输入即时过滤
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { refreshList(); }
            public void afterTextChanged(Editable s) { }
        });

        btnMultiSelect.setOnClickListener(view -> enterSelectionMode());
        btnCancel.setOnClickListener(view -> exitSelectionMode());
        btnSelectAll.setOnClickListener(view -> adapter.selectAll());
        btnDeleteSelected.setOnClickListener(view -> confirmDeleteSelected());
        btnSettings.setOnClickListener(view -> showSettingsMenu());

        registerFilePickers();
        refreshOnShow();
    }

    /* ---------------- 设置菜单 / DeepSeek 设置 / AI 去重 ---------------- */

    private void showSettingsMenu() {
        String version = "1.1";
        try {
            android.content.pm.PackageInfo pi = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception ignored) {
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("设置 · v" + version)
                .setItems(new String[]{"学习统计", "云同步", "DeepSeek API", "AI去重", "栏目管理", "导入/导出"}, (d, which) -> {
                    switch (which) {
                        case 0: showStatsDialog(); break;
                        case 1: showSyncSettings(); break;
                        case 2: showDeepSeekSettings(); break;
                        case 3: showDedupeDialog(); break;
                        case 4: showCategoryManage(); break;
                        case 5: showIoMenu(); break;
                    }
                })
                .show();
    }

    /** 云同步设置：服务器地址 + 令牌，保存后可以立刻同步一次 */
    private void showSyncSettings() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sync_settings, null);
        EditText etUrl = view.findViewById(R.id.et_sync_url);
        EditText etToken = view.findViewById(R.id.et_sync_token);
        TextView hint = view.findViewById(R.id.tv_sync_hint);

        SyncConfig cfg = SyncConfig.load(requireContext());
        etUrl.setText(cfg.serverUrl);
        etToken.setText(cfg.token);
        hint.setText(lastSyncText(cfg));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("云同步")
                .setView(view)
                .setPositiveButton("保存并同步", (d, w) -> {
                    SyncConfig next = new SyncConfig();
                    next.serverUrl = etUrl.getText().toString();
                    next.token = etToken.getText().toString();
                    next.lastSyncAt = cfg.lastSyncAt;
                    next.save(requireContext());
                    if (!next.isConfigured()) {
                        Toast.makeText(requireContext(), "地址和令牌都要填", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    runSyncWithToast();
                })
                .setNeutralButton("仅同步", (d, w) -> runSyncWithToast())
                .setNegativeButton("取消", null)
                .show();
    }

    private String lastSyncText(SyncConfig cfg) {
        if (!cfg.isConfigured()) {
            return "还没有配置过服务器，同步不会运行。";
        }
        if (cfg.lastSyncAt <= 0) {
            return "已配置，还没同步过。";
        }
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
        return "上次同步：" + f.format(new java.util.Date(cfg.lastSyncAt));
    }

    /** 手动同步：转圈 → 提示结果 → 刷新列表 */
    private void runSyncWithToast() {
        ProgressDialog pd = new ProgressDialog(requireContext());
        pd.setMessage("同步中…");
        pd.setCancelable(false);
        pd.show();
        SyncClient.syncAsync(requireContext(), result -> {
            if (!isAdded()) {
                return;
            }
            pd.dismiss();
            Toast.makeText(requireContext(), result.summary(), Toast.LENGTH_LONG).show();
            refreshOnShow();
        });
    }

    private void showStatsDialog() {
        StringBuilder sb = new StringBuilder();
        int total = db.count(null);
        int mastered = db.countMastered();
        int rate = total == 0 ? 0 : Math.round(mastered * 100f / total);
        sb.append("连续天数：").append(db.streakDays()).append(" 天\n");
        sb.append("今日已复习：").append(db.countReviewedToday()).append(" 题\n");
        sb.append("待复习：").append(db.countDueToday()).append(" 题\n");
        sb.append("总掌握率：").append(rate).append("%（").append(mastered).append("/").append(total).append("）\n\n");
        sb.append("各栏目：\n");
        java.util.List<String> cats = db.queryCategories();
        java.util.List<int[]> counts = db.categoryProgressCounts(cats);
        for (int i = 0; i < cats.size(); i++) {
            int m = counts.get(i)[0], t = counts.get(i)[1];
            int r = t == 0 ? 0 : Math.round(m * 100f / t);
            sb.append("- ").append(cats.get(i)).append("：").append(r).append("%（").append(m).append("/").append(t).append("）\n");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("学习统计")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showDeepSeekSettings() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_settings, null);
        AutoCompleteTextView etProvider = view.findViewById(R.id.et_ai_provider);
        EditText etKey = view.findViewById(R.id.et_ai_api_key);
        EditText etBase = view.findViewById(R.id.et_ai_base_url);
        EditText etModel = view.findViewById(R.id.et_ai_model);
        com.google.android.material.materialswitch.MaterialSwitch sw = view.findViewById(R.id.switch_ai_web_search);
        TextView hint = view.findViewById(R.id.tv_ai_hint);

        String[] providers = {AiConfig.PROVIDER_DEEPSEEK, AiConfig.PROVIDER_QWEN, AiConfig.PROVIDER_CUSTOM};
        etProvider.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, providers));
        AiConfig cfg = AiConfig.load(requireContext());
        etProvider.setText(cfg.provider, false);
        etKey.setText(cfg.apiKey);
        etBase.setText(cfg.baseUrl);
        etModel.setText(cfg.model);
        sw.setChecked(cfg.webSearch);
        hint.setText("DeepSeek 默认模型：deepseek-chat\n通义千问默认模型：qwen-plus\n自定义可填任意 OpenAI-compatible chat/completions 地址。GitHub Copilot 后续如接入，也可走自定义预留位。");

        etProvider.setOnItemClickListener((p, v, pos, id) -> {
            String provider = providers[pos];
            etBase.setText(AiConfig.defaultBaseUrl(provider));
            etModel.setText(AiConfig.defaultModel(provider));
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("AI 设置")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    AiConfig c = new AiConfig();
                    c.provider = etProvider.getText().toString().trim();
                    c.apiKey = etKey.getText().toString().trim();
                    c.baseUrl = etBase.getText().toString().trim();
                    c.model = etModel.getText().toString().trim();
                    c.webSearch = sw.isChecked();
                    c.save(requireContext());
                    Toast.makeText(getContext(), "已保存 AI 设置", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("清除Key", (d, w) -> {
                    AiConfig c = AiConfig.load(requireContext());
                    c.apiKey = "";
                    c.save(requireContext());
                    Toast.makeText(getContext(), "已清除 API Key", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDedupeDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("AI 查找重复知识点")
                .setMessage("将抽取题库中的题目名称发送给 DeepSeek 分析重复/高度相似项。确认后会进入预览，可勾选要删除的题目。")
                .setPositiveButton("开始分析", (d, w) -> startAiDedupe())
                .setNegativeButton("取消", null)
                .show();
    }

    private void startAiDedupe() {
        AiConfig cfg = AiConfig.load(requireContext());
        if (cfg.apiKey == null || cfg.apiKey.isEmpty()) {
            showDeepSeekSettings();
            return;
        }
        List<Note> all = db.queryAll();
        if (all.size() < 2) {
            Toast.makeText(getContext(), "题库题目太少，无需去重", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog pd = new ProgressDialog(requireContext());
        pd.setMessage("DeepSeek 正在分析重复题目…");
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                List<Long> duplicateIds = callDeepSeekDedupe(all, cfg);
                requireActivity().runOnUiThread(() -> {
                    pd.dismiss();
                    showDedupePreview(all, duplicateIds);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(getContext(), "AI去重失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private List<Long> callDeepSeekDedupe(List<Note> notes, AiConfig aiConfig) throws Exception {
        // 先做本地保守预筛选：只把“题目名称非常相似”的小组发给 AI。
        // 不能把整个 C++ 题库都发给 AI 判“高度相似”，否则模型容易把同一领域的题都当重复。
        JSONArray groups = new JSONArray();
        java.util.Set<Long> candidateIds = new java.util.HashSet<>();
        int max = Math.min(notes.size(), 1200);
        boolean[] usedAsLater = new boolean[max];
        for (int i = 0; i < max; i++) {
            if (usedAsLater[i]) continue;
            Note a = notes.get(i);
            String na = normalizeTitle(a.name);
            if (na.length() < 3) continue;
            JSONArray group = new JSONArray();
            group.put(new JSONObject().put("id", a.id).put("name", a.name).put("category", a.category));
            for (int j = i + 1; j < max; j++) {
                Note b = notes.get(j);
                String nb = normalizeTitle(b.name);
                if (isVerySimilarTitle(na, nb)) {
                    group.put(new JSONObject().put("id", b.id).put("name", b.name).put("category", b.category));
                    candidateIds.add(a.id);
                    candidateIds.add(b.id);
                    usedAsLater[j] = true;
                }
            }
            if (group.length() > 1) {
                groups.put(group);
            }
            // 控制 token，避免一次过大
            if (groups.length() >= 120) break;
        }

        if (groups.length() == 0) {
            return new ArrayList<>();
        }

        String prompt = "你是题库去重助手。下面是若干候选重复组，每组内的题目名称已经由本地算法判定为非常相似。"
                + "请只在每个候选组内部判断真正重复的知识点；不是同一个知识点、只是同领域相关的，绝对不要删除。"
                + "每组保留最清晰/最完整/名称最规范的一条，只输出需要删除的 id 数组。"
                + "如果不确定，请保留，不要输出该 id。只输出 JSON 数组，例如 [12,35,99]，不要解释。\n\n候选组：\n" + groups.toString();

        String content = AiClient.chat(
                aiConfig,
                "你是严格保守的题库去重助手。只输出合法 JSON 数组。宁可少删，不要误删。",
                prompt,
                0.0
        );
        content = AiClient.stripFence(content);
        JSONArray ids = new JSONArray(content);
        List<Long> res = new ArrayList<>();
        for (int i = 0; i < ids.length(); i++) {
            long id = ids.getLong(i);
            // 二次保险：DeepSeek 返回的 id 必须来自本地候选重复组，否则忽略
            if (candidateIds.contains(id)) res.add(id);
        }
        // 保险阈值：如果模型仍然返回过多，判定为异常，避免一键全删
        if (res.size() > Math.max(20, notes.size() / 5)) {
            throw new RuntimeException("AI 返回的重复项过多，已拦截。建议按栏目分批去重。候选数=" + res.size());
        }
        return res;
    }

    /** 标题归一化：去掉常见问法、标点、空白，只保留核心字串 */
    private String normalizeTitle(String s) {
        if (s == null) return "";
        String r = s.toLowerCase();
        r = r.replaceAll("^[0-9]+[.、)）\\s]*", "");
        r = r.replaceAll("什么是|讲一讲|说一说|说说|请解释|简述|简要说明|介绍一下", "");
        r = r.replaceAll("的定义是什么|的定义|是什么|有哪些|如何|怎么|为什么", "");
        r = r.replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）【】《》]+", "");
        return r.trim();
    }

    /** 极保守相似判断：完全相同或编辑距离相似度极高才进入 AI 候选 */
    private boolean isVerySimilarTitle(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        int max = Math.max(a.length(), b.length());
        if (max < 5) return false;
        int dist = levenshtein(a, b);
        double sim = 1.0 - (dist * 1.0 / max);
        return sim >= 0.92;
    }

    private int levenshtein(String a, String b) {
        int[] dp = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) dp[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int temp = dp[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[j] = Math.min(Math.min(dp[j] + 1, dp[j - 1] + 1), prev + cost);
                prev = temp;
            }
        }
        return dp[b.length()];
    }

    private void showDedupePreview(List<Note> all, List<Long> duplicateIds) {
        if (duplicateIds == null || duplicateIds.isEmpty()) {
            Toast.makeText(getContext(), "AI 未发现明显重复项", Toast.LENGTH_LONG).show();
            return;
        }
        List<Note> dupNotes = new ArrayList<>();
        for (Note n : all) if (duplicateIds.contains(n.id)) dupNotes.add(n);
        showDeletePreview(dupNotes, "AI去重预览", "删除选中重复项");
    }

    private void showDeletePreview(List<Note> notes, String title, String buttonText) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_preview, null);
        TextView tvCount = view.findViewById(R.id.tv_preview_count);
        RecyclerView rv = view.findViewById(R.id.recycler_preview);
        Button btnAll = view.findViewById(R.id.btn_preview_select_all);
        Button btnImport = view.findViewById(R.id.btn_preview_import);
        btnImport.setText(buttonText);
        PreviewNoteAdapter adapter = new PreviewNoteAdapter(notes);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        Runnable update = () -> tvCount.setText("选中 " + adapter.selectedCount() + " / 共 " + notes.size() + " 项");
        adapter.setOnChanged(update::run);
        update.run();
        btnAll.setOnClickListener(v -> adapter.toggleAll());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(view)
                .create();
        btnImport.setOnClickListener(v -> {
            List<Long> ids = new ArrayList<>();
            for (Note n : adapter.selectedNotes()) ids.add(n.id);
            int del = db.deleteByIds(ids);
            Toast.makeText(getContext(), "已删除 " + del + " 条", Toast.LENGTH_LONG).show();
            refreshOnShow();
            dialog.dismiss();
        });
        dialog.show();
    }

    /* ---------------- 栏目管理 ---------------- */

    private void showCategoryManage() {
        List<String> cats = db.queryCategories();
        if (cats.isEmpty()) {
            Toast.makeText(getContext(), "还没有栏目", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) {
            String c = cats.get(i);
            items[i] = c + "（" + db.count(c) + "题）";
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("栏目管理")
                .setItems(items, (d, which) -> showCategoryActions(cats.get(which)))
                .show();
    }

    private void showCategoryActions(String cat) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(cat)
                .setItems(new String[]{"重命名/合并", "删除该栏目全部题目"}, (d, which) -> {
                    if (which == 0) showRenameCategory(cat);
                    else confirmDeleteCategory(cat);
                })
                .show();
    }

    private void showRenameCategory(String oldCat) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_category, null);
        EditText et = view.findViewById(R.id.et_category_name);
        et.setText(oldCat);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("重命名/合并栏目")
                .setMessage("如果输入已有栏目名，将自动合并到该栏目。")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    String newCat = et.getText().toString().trim();
                    if (newCat.isEmpty()) {
                        Toast.makeText(getContext(), "栏目不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int n = db.updateCategory(oldCat, newCat);
                    Toast.makeText(getContext(), "已移动 " + n + " 题到「" + newCat + "」", Toast.LENGTH_SHORT).show();
                    refreshOnShow();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteCategory(String cat) {
        int total = db.count(cat);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除栏目")
                .setMessage("将删除「" + cat + "」栏目下全部 " + total + " 道题！此操作不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    int n = db.deleteByCategory(cat);
                    Toast.makeText(getContext(), "已删除 " + n + " 题", Toast.LENGTH_SHORT).show();
                    refreshOnShow();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /* ---------------- 文件导出/导入 ---------------- */

    private void registerFilePickers() {
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> { if (uri != null) writeExport(uri); });
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) readImport(uri); });
    }

    private void showIoMenu() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("备份与恢复")
                .setItems(new String[]{"导出题库（备份到文件）", "从文件导入"}, (d, which) -> {
                    if (which == 0) {
                        exportLauncher.launch("losnote_backup.json");
                    } else {
                        importLauncher.launch(new String[]{"application/json", "text/*"});
                    }
                })
                .show();
    }

    /** 生成 v2 备份 JSON（字段与网页端 /api/export 一致），抽出来便于单测。 */
    @NonNull
    static String buildBackupJson(@NonNull DBHelper db) throws Exception {
        List<Note> all = db.queryAll();
        JSONArray arr = new JSONArray();
        for (Note n : all) {
            JSONObject o = new JSONObject();
            o.put("uuid", n.uuid == null ? "" : n.uuid);
            o.put("name", n.name);
            o.put("definition", n.definition == null ? "" : n.definition);
            o.put("category", n.category == null ? "" : n.category);
            o.put("createdAt", n.createdAt);
            o.put("mastery", n.mastery);
            o.put("intervalDays", n.intervalDays);
            o.put("easeFactor", n.easeFactor);
            o.put("reviewCount", n.reviewCount);
            o.put("nextReview", n.nextReview);
            o.put("lastReview", n.lastReview);
            o.put("stars", n.stars);
            arr.put(o);
        }
        JSONArray logs = new JSONArray();
        for (DailyLog d : db.queryAllDailyLogs(10000)) {
            JSONObject o = new JSONObject();
            o.put("uuid", d.uuid == null ? "" : d.uuid);
            o.put("content", d.content);
            o.put("createdAt", d.createdAt);
            logs.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("version", 2);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("reviewHistoryIncluded", false);
        root.put("notes", arr);
        root.put("dailyLogs", logs);
        return root.toString(2);
    }

    /**
     * 导入备份 JSON，返回 {题目数, 足迹数}。
     * v2 保留 uuid 与完整 SRS 进度（网页端导出的文件走这条），v1 只有基础字段。
     */
    @NonNull
    static int[] importBackupJson(@NonNull DBHelper db, @NonNull String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", 1);
        JSONArray arr = root.getJSONArray("notes");
        int noteCount = 0;
        if (version >= 2) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                Note n = new Note(name, o.optString("definition", ""),
                        emptyTo(o.optString("category", ""), "未分类"));
                n.uuid = emptyTo(o.optString("uuid", ""), null);
                n.createdAt = o.optLong("createdAt", System.currentTimeMillis());
                n.mastery = o.optInt("mastery", 0);
                n.intervalDays = o.optInt("intervalDays", 0);
                n.easeFactor = o.optInt("easeFactor", 250);
                n.reviewCount = o.optInt("reviewCount", 0);
                n.nextReview = o.optLong("nextReview", 0);
                n.lastReview = o.optLong("lastReview", 0);
                n.stars = o.optInt("stars", 0);
                if (db.importNote(n) > 0) {
                    noteCount++;
                }
            }
        } else {
            List<Note> notes = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                Note n = new Note(name, o.optString("definition", ""),
                        emptyTo(o.optString("category", ""), "未分类"));
                n.mastery = o.optInt("mastery", 0);
                notes.add(n);
            }
            noteCount = notes.isEmpty() ? 0 : db.insertBatch(notes);
        }
        int logCount = 0;
        JSONArray logs = root.optJSONArray("dailyLogs");
        if (logs != null) {
            for (int i = 0; i < logs.length(); i++) {
                JSONObject o = logs.getJSONObject(i);
                String content = o.optString("content", "").trim();
                if (content.isEmpty()) {
                    continue;
                }
                DailyLog d = new DailyLog(0L, content, o.optLong("createdAt", System.currentTimeMillis()));
                d.uuid = emptyTo(o.optString("uuid", ""), null);
                if (db.importDailyLog(d) > 0) {
                    logCount++;
                }
            }
        }
        return new int[]{noteCount, logCount};
    }

    private void writeExport(Uri uri) {
        try {
            String json = buildBackupJson(db);
            int noteCount = db.queryAll().size();
            int logCount = db.queryAllDailyLogs(10000).size();

            OutputStream os = requireContext().getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
            Toast.makeText(getContext(), "已导出 " + noteCount + " 题（含复习进度）+ "
                    + logCount + " 条足迹", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void readImport(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();

            int[] counts = importBackupJson(db, sb.toString());
            int noteCount = counts[0];
            int logCount = counts[1];
            if (noteCount == 0 && logCount == 0) {
                Toast.makeText(getContext(), "文件中没有有效数据（或已全部导入过）", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(getContext(),
                    "已导入 " + noteCount + " 题 + " + logCount + " 条足迹，联网后自动上传",
                    Toast.LENGTH_LONG).show();
            refreshOnShow();
        } catch (Exception e) {
            Toast.makeText(getContext(), "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String emptyTo(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }

    public void refreshOnShow() {
        if (db == null) {
            return;
        }
        Set<String> existing = new HashSet<>(db.queryCategories());
        selectedBankCategories.retainAll(existing);
        updateBankCategoryButtonText();
        refreshList();
    }

    private void refreshList() {
        Set<String> cats = selectedBankCategories;
        String kw = etSearch.getText().toString().trim();

        List<Note> list;
        if (!kw.isEmpty()) {
            list = db.search(kw, cats);
        } else if (!cats.isEmpty()) {
            list = db.queryByCategory(cats);
        } else {
            list = db.queryAll();
        }
        visibleNotes.clear();
        visibleNotes.addAll(list);
        adapter.setData(visibleNotes);
        tvCount.setText("共 " + list.size() + " 题");
        if (list.isEmpty()) {
            tvHint.setText(kw.isEmpty() && cats.isEmpty() ? "还没有知识点" : "没有找到匹配内容");
        } else {
            tvHint.setText("点击查看 · 长按批量选择");
        }
    }

    /** 打开多栏目选择对话框（题库页） */
    private void showBankCategoryMultiSelect() {
        List<String> allCats = db.queryCategories();
        if (allCats.isEmpty()) {
            Toast.makeText(getContext(), "还没有栏目", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = allCats.toArray(new String[0]);
        boolean[] checked = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            checked[i] = selectedBankCategories.contains(items[i]);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("按栏目筛选")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedBankCategories.add(items[which]);
                    } else {
                        selectedBankCategories.remove(items[which]);
                    }
                })
                .setNeutralButton("全选/取消", (dialog, which) -> {
                    boolean allSelected = selectedBankCategories.size() == allCats.size();
                    if (allSelected) {
                        selectedBankCategories.clear();
                    } else {
                        selectedBankCategories.addAll(allCats);
                    }
                    showBankCategoryMultiSelect();
                })
                .setPositiveButton("确定", (dialog, which) -> {
                    updateBankCategoryButtonText();
                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateBankCategoryButtonText() {
        renderBankChips();
    }

    /** 栏目筛选 chips：单选点击切换，多选对话框可叠加 */
    private void renderBankChips() {
        if (chipsBankCategories == null || db == null) {
            return;
        }
        chipsBankCategories.removeAllViews();
        List<String> cats = db.queryCategories();

        Chip all = makeBankChip("全部", selectedBankCategories.isEmpty());
        all.setOnClickListener(view -> {
            selectedBankCategories.clear();
            renderBankChips();
            refreshList();
        });
        chipsBankCategories.addView(all);

        for (String c : cats) {
            boolean on = selectedBankCategories.contains(c);
            Chip chip = makeBankChip(c, on);
            chip.setOnClickListener(view -> {
                if (selectedBankCategories.size() == 1 && selectedBankCategories.contains(c)) {
                    selectedBankCategories.clear();
                } else {
                    selectedBankCategories.clear();
                    selectedBankCategories.add(c);
                }
                renderBankChips();
                refreshList();
            });
            chipsBankCategories.addView(chip);
        }
    }

    private Chip makeBankChip(String text, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setTextSize(13);
        chip.setChipMinHeight(getResources().getDisplayMetrics().density * 32);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (getResources().getDisplayMetrics().density * 8));
        chip.setLayoutParams(lp);
        return chip;
    }

    /* ---------------- 多选 / 批量删除 ---------------- */

    private void enterSelectionMode() {
        adapter.setSelectionMode(true);
        barSelection.setVisibility(View.VISIBLE);
        btnMultiSelect.setText("按栏目删除");
        btnMultiSelect.setOnClickListener(view -> confirmDeleteByCategory());
        tvHint.setText("勾选要删除的题目");
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        barSelection.setVisibility(View.GONE);
        btnMultiSelect.setText("批量删除");
        btnMultiSelect.setOnClickListener(view -> enterSelectionMode());
        tvHint.setText("点击题目可查看/编辑/删除");
    }

    private void confirmDeleteSelected() {
        List<Long> ids = new ArrayList<>(adapter.getSelectedIds());
        if (ids.isEmpty()) {
            Toast.makeText(getContext(), "还没有勾选任何题目", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("批量删除")
                .setMessage("确定删除选中的 " + ids.size() + " 道题？此操作不可恢复。")
                .setPositiveButton("删除", (d, which) -> {
                    int n = db.deleteByIds(ids);
                    Toast.makeText(getContext(), "已删除 " + n + " 题", Toast.LENGTH_SHORT).show();
                    exitSelectionMode();
                    refreshOnShow();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteByCategory() {
        if (selectedBankCategories.isEmpty()) {
            int total = db.count(null);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("清空题库")
                    .setMessage("当前为「全部栏目」，将删除全部 " + total + " 道题！此操作不可恢复。")
                    .setPositiveButton("全部删除", (d, which) -> {
                        db.deleteAll();
                        Toast.makeText(getContext(), "已清空题库", Toast.LENGTH_SHORT).show();
                        exitSelectionMode();
                        refreshOnShow();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else if (selectedBankCategories.size() == 1) {
            String sel = selectedBankCategories.iterator().next();
            int total = db.count(sel);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除栏目")
                    .setMessage("将删除「" + sel + "」栏目下全部 " + total + " 道题！此操作不可恢复。")
                    .setPositiveButton("删除", (d, which) -> {
                        int n = db.deleteByCategory(sel);
                        Toast.makeText(getContext(), "已删除 " + n + " 题", Toast.LENGTH_SHORT).show();
                        exitSelectionMode();
                        refreshOnShow();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            Toast.makeText(getContext(), "「按栏目删除」仅支持单选栏目时使用，当前选定了 " +
                    selectedBankCategories.size() + " 个栏目。请先在栏目筛选中只保留一个栏目。",
                    Toast.LENGTH_LONG).show();
        }
    }

    /* ---------------- 单条操作 ---------------- */

    private void showItemMenu(Note note) {
        String[] actions = {"查看", "进入抽查", "编辑", "删除"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(note.name)
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0: showView(note); break;
                        case 1: ((MainActivity) requireActivity()).openQuizWithNote(note); break;
                        case 2: showEdit(note); break;
                        case 3: confirmDelete(note); break;
                    }
                })
                .show();
    }

    private void showView(Note note) {
        showView(note, visibleNotes.indexOf(note));
    }

    /** 查看当前筛选结果中的知识点，可直接前后切换或进入抽查。 */
    private void showView(Note note, int position) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_view, null);
        TextView tvCat = dialogView.findViewById(R.id.tv_view_category);
        TextView tvDef = dialogView.findViewById(R.id.tv_view_definition);
        com.google.android.material.button.MaterialButton btnPrevious = dialogView.findViewById(R.id.btn_view_previous);
        com.google.android.material.button.MaterialButton btnNext = dialogView.findViewById(R.id.btn_view_next);
        com.google.android.material.button.MaterialButton btnEdit = dialogView.findViewById(R.id.btn_view_edit);
        com.google.android.material.button.MaterialButton btnDelete = dialogView.findViewById(R.id.btn_view_delete);

        tvCat.setText(note.category);
        String def = (note.definition == null || note.definition.isEmpty())
                ? "（无定义）" : note.definition;
        markwon.setMarkdown(tvDef, def);
        btnPrevious.setEnabled(position > 0);
        btnNext.setEnabled(position >= 0 && position < visibleNotes.size() - 1);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(note.name)
                .setView(dialogView)
                .setPositiveButton("进入抽查", (d, w) -> ((MainActivity) requireActivity()).openQuizWithNote(note))
                .setNeutralButton("编辑", (d, w) -> showEdit(note))
                .setNegativeButton("关闭", null)
                .create();
        btnPrevious.setOnClickListener(v -> {
            int nextPosition = position - 1;
            if (nextPosition >= 0 && nextPosition < visibleNotes.size()) {
                dialog.dismiss();
                showView(visibleNotes.get(nextPosition), nextPosition);
            }
        });
        btnNext.setOnClickListener(v -> {
            int nextPosition = position + 1;
            if (nextPosition >= 0 && nextPosition < visibleNotes.size()) {
                dialog.dismiss();
                showView(visibleNotes.get(nextPosition), nextPosition);
            }
        });
        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            showEdit(note);
        });
        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDelete(note);
        });
        dialog.show();
    }

    private void showEdit(Note note) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit, null);
        EditText etName = dialogView.findViewById(R.id.et_edit_name);
        EditText etDef = dialogView.findViewById(R.id.et_edit_definition);
        EditText etCat = dialogView.findViewById(R.id.et_edit_category);

        etName.setText(note.name);
        etDef.setText(note.definition);
        etCat.setText(note.category);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑题目")
                .setView(dialogView)
                .setPositiveButton("保存", (d, which) -> {
                    String name = etName.getText().toString().trim();
                    String def = etDef.getText().toString().trim();
                    String cat = etCat.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(cat)) {
                        cat = "未分类";
                    }
                    note.name = name;
                    note.definition = def;
                    note.category = cat;
                    db.update(note);
                    Toast.makeText(getContext(), "已保存", Toast.LENGTH_SHORT).show();
                    refreshOnShow();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(Note note) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除")
                .setMessage("确定删除「" + note.name + "」？此操作不可恢复。")
                .setPositiveButton("删除", (d, which) -> {
                    db.delete(note.id);
                    Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                    refreshOnShow();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
