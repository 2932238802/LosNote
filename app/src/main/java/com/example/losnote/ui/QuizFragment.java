package com.example.losnote.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.losnote.R;
import com.example.losnote.data.DBHelper;
import com.example.losnote.data.Note;
import com.example.losnote.data.Srs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.noties.markwon.Markwon;

/**
 * 抽查模块：加权随机抽题 + 左右滑动切题 + 星级评分。
 * 所有题目都有概率被抽到，星数越多概率越低，星数越少概率越高。
 */
public class QuizFragment extends Fragment {

    private DBHelper db;
    private Markwon markwon;
    private final Random random = new Random();

    private MaterialButton btnCategorySelect;
    private final Set<String> selectedCategories = new HashSet<>();
    private TextView tvScope, tvCardName, tvCardDefinition, tvCardHint, tvCardCategory, tvCardNext;
    private View card, barGrade;

    private List<Note> pool = new ArrayList<>();
    private final List<Note> history = new ArrayList<>();
    private int historyIndex = -1;
    private Note currentNote;
    private boolean revealed = false;
    private boolean horizontalScrolling = false;

    private GestureDetector gesture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        db = DBHelper.get(requireContext());
        markwon = MarkdownRenderer.create(requireContext());

        btnCategorySelect = v.findViewById(R.id.btn_category_select);
        tvScope = v.findViewById(R.id.tv_scope);
        card = v.findViewById(R.id.card_quiz);
        tvCardName = v.findViewById(R.id.tv_card_name);
        tvCardDefinition = v.findViewById(R.id.tv_card_definition);
        tvCardHint = v.findViewById(R.id.tv_card_hint);
        tvCardCategory = v.findViewById(R.id.tv_card_category);
        tvCardNext = v.findViewById(R.id.tv_card_next);
        barGrade = v.findViewById(R.id.bar_grade);

        MaterialButton btnDraw = v.findViewById(R.id.btn_draw);
        btnDraw.setOnClickListener(view -> drawOne());

        v.findViewById(R.id.btn_again).setOnClickListener(view -> grade(Srs.AGAIN));
        v.findViewById(R.id.btn_good).setOnClickListener(view -> grade(Srs.GOOD));
        v.findViewById(R.id.btn_easy).setOnClickListener(view -> grade(Srs.EASY));
        v.findViewById(R.id.btn_quiz_edit).setOnClickListener(view -> editCurrent());
        v.findViewById(R.id.btn_quiz_delete).setOnClickListener(view -> deleteCurrent());

        btnCategorySelect.setOnClickListener(view -> showCategoryMultiSelect());

        // 点击翻面 + 左右滑动切题
        gesture = new GestureDetector(requireContext(), new SwipeListener());
        card.setOnTouchListener((view, event) -> {
            gesture.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                boolean wasHorizontal = horizontalScrolling;
                horizontalScrolling = false;
                return wasHorizontal;
            }
            return horizontalScrolling;
        });
        card.setClickable(true);
        card.setOnClickListener(ignored -> flipCard());

        View cardInner = v.findViewById(R.id.card_inner);
        cardInner.setOnClickListener(ignored -> flipCard());

        v.findViewById(R.id.scroll_card).setOnClickListener(ignored -> flipCard());

        refreshOnShow();
    }

    public void refreshOnShow() {
        if (db == null) {
            return;
        }
        // 过滤掉已不存在的栏目
        Set<String> existing = new HashSet<>(db.queryCategories());
        selectedCategories.retainAll(existing);
        updateCategoryButtonText();
        reloadPool();
    }

    private Set<String> currentCategories() {
        return selectedCategories;
    }

    private void reloadPool() {
        history.clear();
        historyIndex = -1;
        pool = db.queryByCategory(currentCategories());
        updateScope();
    }

    private void updateScope() {
        String catName = selectedCategories.isEmpty() ? "全部栏目" :
                String.join(", ", selectedCategories);
        tvScope.setText(catName + " · 共 " + pool.size() + " 题");
    }

    /** 打开多栏目选择对话框 */
    private void showCategoryMultiSelect() {
        List<String> allCats = db.queryCategories();
        if (allCats.isEmpty()) {
            Toast.makeText(getContext(), "还没有栏目", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = allCats.toArray(new String[0]);
        boolean[] checked = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            checked[i] = selectedCategories.contains(items[i]);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择栏目")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedCategories.add(items[which]);
                    } else {
                        selectedCategories.remove(items[which]);
                    }
                })
                .setNeutralButton("全选/取消", (dialog, which) -> {
                    // 切换全选状态
                    boolean allSelected = selectedCategories.size() == allCats.size();
                    if (allSelected) {
                        selectedCategories.clear();
                    } else {
                        selectedCategories.addAll(allCats);
                    }
                    // 刷新对话框
                    showCategoryMultiSelect();
                })
                .setPositiveButton("确定", (dialog, which) -> {
                    updateCategoryButtonText();
                    reloadPool();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateCategoryButtonText() {
        if (selectedCategories.isEmpty()) {
            btnCategorySelect.setText("全部栏目");
        } else if (selectedCategories.size() <= 3) {
            btnCategorySelect.setText(String.join(", ", selectedCategories));
        } else {
            btnCategorySelect.setText(selectedCategories.size() + " 个栏目");
        }
    }

    /** 加权随机抽题：星数越少概率越高，星数越多概率越低 */
    private void drawOne() {
        if (pool == null || pool.isEmpty()) {
            Toast.makeText(getContext(), "该范围内没有题目", Toast.LENGTH_SHORT).show();
            return;
        }

        // 计算权重：weight = 1 / (stars + 1)，星星越多权重越低
        double totalWeight = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = 1.0 / (pool.get(i).stars + 1);
            totalWeight += weights[i];
        }

        double r = random.nextDouble() * totalWeight;
        int index = 0;
        double accum = weights[0];
        while (r > accum && index < pool.size() - 1) {
            index++;
            accum += weights[index];
        }
        currentNote = pool.get(index);

        // 如果从历史中间又抽新题，丢弃后面的分支历史
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(currentNote);
        historyIndex = history.size() - 1;
        revealed = false;
        showCard(false);
    }

    /** 显示指定的当前题目 */
    public void showCurrentNote(Note note) {
        if (note == null) {
            return;
        }
        history.clear();
        history.add(note);
        historyIndex = 0;
        currentNote = note;
        revealed = false;
        showCard(false);
    }
    /** 显示历史中的上一题 */
    private void showPrevious() {
        if (historyIndex <= 0) {
            Toast.makeText(getContext(), "已经是第一题了", Toast.LENGTH_SHORT).show();
            return;
        }
        historyIndex--;
        currentNote = history.get(historyIndex);
        revealed = false;
        showCard(false);
    }

    /** 显示历史中的下一题；如果没有历史下一题，则抽新题 */
    private void showNext() {
        if (historyIndex >= 0 && historyIndex < history.size() - 1) {
            historyIndex++;
            currentNote = history.get(historyIndex);
            revealed = false;
            showCard(false);
        } else {
            drawOne();
        }
    }

    /** 翻面：点击直接显示/隐藏答案，无动画 */
    private void flipCard() {
        if (currentNote == null) {
            return;
        }
        revealed = !revealed;
        renderFace();
    }

    private void showCard(boolean animateIn) {
        card.setVisibility(View.VISIBLE);
        renderFace();
        if (animateIn) {
            card.setAlpha(0f);
            card.animate().alpha(1f).setDuration(200).start();
        }
    }

    private void renderFace() {
        markwon.setMarkdown(tvCardName, currentNote.name);
        tvCardCategory.setText(currentNote.category);
        tvCardNext.setText(starText(currentNote));

        if (revealed) {
            String def = (currentNote.definition == null || currentNote.definition.isEmpty())
                    ? "（这条还没有填写定义）" : currentNote.definition;
            // 抽查页取消横向滚动：全部按屏幕宽度自动换行
            tvCardDefinition.setHorizontallyScrolling(false);
            tvCardDefinition.setMinWidth(0);
            markwon.setMarkdown(tvCardDefinition, def);
            tvCardDefinition.setVisibility(View.VISIBLE);
            tvCardHint.setVisibility(View.GONE);
            barGrade.setVisibility(View.VISIBLE);
        } else {
            tvCardDefinition.setVisibility(View.GONE);
            tvCardHint.setVisibility(View.VISIBLE);
            tvCardHint.setText("点击卡片查看答案 · 左右滑动切题");
            barGrade.setVisibility(View.GONE);
        }
    }

    /** 累计获得星数 */
    private static String starText(Note n) {
        if (n.stars <= 0) return "星级 0";
        return "星级 " + n.stars;
    }

    /** SRS 评分 */
    private void grade(int g) {
        if (currentNote == null) {
            return;
        }
        // 累计星数：AGAIN=1星, GOOD=2星, EASY=3星
        int starsEarned = g + 1;
        currentNote.stars += starsEarned;
        Srs.apply(currentNote, g);
        db.saveReview(currentNote, g);
        // 同步内存池
        for (Note n : pool) {
            if (n.id == currentNote.id) {
                n.mastery = currentNote.mastery;
                n.intervalDays = currentNote.intervalDays;
                n.easeFactor = currentNote.easeFactor;
                n.reviewCount = currentNote.reviewCount;
                n.nextReview = currentNote.nextReview;
                n.lastReview = currentNote.lastReview;
                n.stars = currentNote.stars;
                break;
            }
        }
        String tip;
        switch (g) {
            case Srs.AGAIN: tip = "已记录：忘记"; break;
            case Srs.EASY: tip = "已记录：简单"; break;
            default: tip = "已记录：记得"; break;
        }
        Toast.makeText(getContext(), tip, Toast.LENGTH_SHORT).show();
        drawOne();
    }

    /** 抽查页直接编辑当前题目 */
    private void editCurrent() {
        if (currentNote == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit, null);
        EditText etName = dialogView.findViewById(R.id.et_edit_name);
        EditText etDef = dialogView.findViewById(R.id.et_edit_definition);
        EditText etCat = dialogView.findViewById(R.id.et_edit_category);
        etName.setText(currentNote.name);
        etDef.setText(currentNote.definition);
        etCat.setText(currentNote.category);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑当前题目")
                .setView(dialogView)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentNote.name = name;
                    currentNote.definition = etDef.getText().toString().trim();
                    String cat = etCat.getText().toString().trim();
                    currentNote.category = cat.isEmpty() ? "未分类" : cat;
                    db.update(currentNote);
                    renderFace();
                    Toast.makeText(getContext(), "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 抽查页直接删除当前题目 */
    private void deleteCurrent() {
        if (currentNote == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除当前题目")
                .setMessage("确定删除「" + currentNote.name + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    long id = currentNote.id;
                    db.delete(id);
                    pool.removeIf(n -> n.id == id);
                    history.removeIf(n -> n.id == id);
                    currentNote = null;
                    card.setVisibility(View.GONE);
                    updateScope();
                    Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                    drawOne();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 卡片手势：左右快速滑动切题 */
    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;   // 返回 true 才能收到后续 move/fling
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // 横向移动明显大于纵向时，标记为横向滑动（用于拦截 ScrollView）
            if (e1 != null && e2 != null) {
                float dx = Math.abs(e2.getX() - e1.getX());
                float dy = Math.abs(e2.getY() - e1.getY());
                if (dx > dy && dx > 30) {
                    horizontalScrolling = true;
                }
            }
            return horizontalScrolling;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) {
                return false;
            }
            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();
            // 横向滑动且幅度足够 -> 切题
            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 80
                    && Math.abs(velocityX) > 150) {
                final boolean toNext = dx < 0; // 左滑下一题，右滑上一题
                final float target = toNext ? -card.getWidth() : card.getWidth();
                card.animate().translationX(target).alpha(0f).setDuration(150)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator a) {
                                card.setTranslationX(0);
                                card.setAlpha(1f);
                                card.animate().setListener(null);
                                if (toNext) {
                                    showNext();
                                } else {
                                    showPrevious();
                                }
                            }
                        }).start();
                return true;
            }
            return false;
        }
    }
}
