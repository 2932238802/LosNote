package com.example.losnote.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.losnote.R;
import com.example.losnote.data.DBHelper;

import java.util.List;

/**
 * 第四个模块：学习统计。
 * - 连续打卡、今日已复习、待复习、总掌握率
 * - 各栏目掌握进度条
 */
public class StatsFragment extends Fragment {

    private DBHelper db;
    private TextView tvStreak, tvToday, tvDue, tvRate;
    private LinearLayout containerProgress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        db = DBHelper.get(requireContext());
        tvStreak = v.findViewById(R.id.tv_streak);
        tvToday = v.findViewById(R.id.tv_today);
        tvDue = v.findViewById(R.id.tv_due);
        tvRate = v.findViewById(R.id.tv_mastered_rate);
        containerProgress = v.findViewById(R.id.container_progress);
        refreshOnShow();
    }

    public void refreshOnShow() {
        if (db == null) {
            return;
        }
        int total = db.count(null);
        int mastered = db.countMastered();

        tvStreak.setText(String.valueOf(db.streakDays()));
        tvToday.setText(String.valueOf(db.countReviewedToday()));
        tvDue.setText(String.valueOf(db.countDueToday()));
        int rate = total == 0 ? 0 : Math.round(mastered * 100f / total);
        tvRate.setText(rate + "%");

        // 各栏目进度
        containerProgress.removeAllViews();
        List<String> cats = db.queryCategories();
        if (cats.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("还没有题目，先去录入吧～");
            empty.setTextColor(getResources().getColor(R.color.hint_text));
            empty.setPadding(0, 24, 0, 0);
            containerProgress.addView(empty);
            return;
        }
        List<int[]> counts = db.categoryProgressCounts(cats);
        for (int i = 0; i < cats.size(); i++) {
            int m = counts.get(i)[0];
            int t = counts.get(i)[1];
            containerProgress.addView(buildProgressRow(cats.get(i), m, t));
        }
    }

    /** 构建一行栏目进度：标题 + 进度条 + "m/t" */
    private View buildProgressRow(String cat, int mastered, int total) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        row.setPadding(0, pad, 0, pad);

        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(requireContext());
        name.setText(cat);
        name.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(lp);

        TextView ratio = new TextView(requireContext());
        ratio.setText(mastered + " / " + total);
        ratio.setTextSize(13);
        ratio.setTextColor(getResources().getColor(R.color.hint_text));

        titleRow.addView(name);
        titleRow.addView(ratio);

        ProgressBar bar = new ProgressBar(requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, total));
        bar.setProgress(mastered);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(4);
        bar.setLayoutParams(barLp);

        row.addView(titleRow);
        row.addView(bar);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
