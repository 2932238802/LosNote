package com.example.losnote.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.losnote.R;
import com.example.losnote.data.DBHelper;
import com.example.losnote.data.DailyLog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 今日足迹：记录今天做过的、让自己变得更好的事。
 * - 记录 / 删除当天足迹
 * - 今日记录数、连续记录天数、累计足迹
 * - 查看全部历史足迹
 */
public class DailyFragment extends Fragment {

    private DBHelper db;
    private EditText etInput;
    private TextView tvDate, tvCount, tvStreak, tvTotal, tvEmpty;
    private RecyclerView recycler;
    private DailyLogAdapter adapter;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);
    private final SimpleDateFormat dayGroupFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private final SimpleDateFormat dayTitleFormat =
            new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.CHINA);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_daily, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        db = DBHelper.get(requireContext());

        etInput = v.findViewById(R.id.et_daily_input);
        tvDate = v.findViewById(R.id.tv_daily_date);
        tvCount = v.findViewById(R.id.tv_daily_count);
        tvStreak = v.findViewById(R.id.tv_daily_streak);
        tvTotal = v.findViewById(R.id.tv_daily_total);
        tvEmpty = v.findViewById(R.id.tv_daily_empty);
        recycler = v.findViewById(R.id.recycler_daily);

        MaterialButton btnAdd = v.findViewById(R.id.btn_daily_add);
        MaterialButton btnHistory = v.findViewById(R.id.btn_daily_history);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DailyLogAdapter(this::confirmDelete);
        recycler.setAdapter(adapter);

        btnAdd.setOnClickListener(view -> addLog());
        btnHistory.setOnClickListener(view -> showHistory());

        refreshOnShow();
    }

    public void refreshOnShow() {
        if (db == null) {
            return;
        }
        tvDate.setText(dateFormat.format(new Date()));
        tvCount.setText(String.valueOf(db.countDailyLogsToday()));
        tvStreak.setText(String.valueOf(db.dailyStreakDays()));
        tvTotal.setText(String.valueOf(db.countDailyLogsTotal()));

        List<DailyLog> logs = db.queryDailyLogsToday();
        adapter.setItems(logs);
        tvEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addLog() {
        String content = etInput.getText() == null ? "" : etInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(getContext(), "先写下一件今天做过的事吧", Toast.LENGTH_SHORT).show();
            return;
        }
        db.insertDailyLog(content);
        etInput.setText("");
        hideKeyboard();
        refreshOnShow();
        Toast.makeText(getContext(), "已记下，今天又前进了一步 ✓", Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(DailyLog log) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除这条足迹？")
                .setMessage(log.content)
                .setPositiveButton("删除", (d, w) -> {
                    db.deleteDailyLog(log.id);
                    refreshOnShow();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHistory() {
        List<DailyLog> all = db.queryAllDailyLogs(200);
        if (all.isEmpty()) {
            Toast.makeText(getContext(), "还没有任何足迹，从今天开始吧", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(db.countDailyLogsTotal()).append(" 条 · 连续 ")
                .append(db.dailyStreakDays()).append(" 天\n");
        String lastDay = null;
        for (DailyLog log : all) {
            String day = dayGroupFormat.format(new Date(log.createdAt));
            if (!day.equals(lastDay)) {
                sb.append("\n【").append(dayTitleFormat.format(new Date(log.createdAt))).append("】\n");
                lastDay = day;
            }
            sb.append("· ").append(timeFormat.format(new Date(log.createdAt)))
                    .append("  ").append(log.content).append("\n");
        }
        if (db.countDailyLogsTotal() > all.size()) {
            sb.append("\n仅显示最近 ").append(all.size()).append(" 条");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("全部足迹")
                .setMessage(sb.toString().trim())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
        }
    }
}
