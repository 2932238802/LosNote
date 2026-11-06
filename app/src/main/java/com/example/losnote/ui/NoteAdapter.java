package com.example.losnote.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.losnote.data.Note;
import com.example.losnote.data.Srs;
import com.example.losnote.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.noties.markwon.Markwon;

/**
 * 题目列表适配器。
 * 普通模式：单击触发 onClick（查看/编辑/删除菜单），长按触发 onLongClick。
 * 多选模式：每项前显示复选框，单击切换勾选状态，用于批量删除。
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.VH> {

    public interface OnItemLongClick {
        void onLongClick(Note note);
    }

    public interface OnItemClick {
        void onClick(Note note);
    }

    public interface OnSelectionChanged {
        void onChanged(int selectedCount);
    }

    private List<Note> data;
    private OnItemLongClick longClick;
    private OnItemClick click;
    private OnSelectionChanged selectionChanged;

    private boolean selectionMode = false;
    private final Set<Long> selectedIds = new HashSet<>();
    @Nullable private Markwon markwon;

    public NoteAdapter(List<Note> data) {
        this.data = data;
    }

    public void setMarkwon(Markwon markwon) {
        this.markwon = markwon;
    }

    public void setData(List<Note> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    public void setOnItemLongClick(OnItemLongClick l) {
        this.longClick = l;
    }

    public void setOnItemClick(OnItemClick l) {
        this.click = l;
    }

    public void setOnSelectionChanged(OnSelectionChanged l) {
        this.selectionChanged = l;
    }

    /* ---------------- 多选模式 ---------------- */

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean on) {
        this.selectionMode = on;
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelection();
    }

    /** 全选当前列表 */
    public void selectAll() {
        selectedIds.clear();
        if (data != null) {
            for (Note n : data) {
                selectedIds.add(n.id);
            }
        }
        notifyDataSetChanged();
        notifySelection();
    }

    /** 清空选择 */
    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelection();
    }

    /** 在长按进入多选模式时预先选中当前项。 */
    public void select(Note note) {
        if (note == null) return;
        selectedIds.add(note.id);
        notifyDataSetChanged();
        notifySelection();
    }

    public Set<Long> getSelectedIds() {
        return selectedIds;
    }

    private void notifySelection() {
        if (selectionChanged != null) {
            selectionChanged.onChanged(selectedIds.size());
        }
    }

    private void toggle(Note n) {
        if (selectedIds.contains(n.id)) {
            selectedIds.remove(n.id);
        } else {
            selectedIds.add(n.id);
        }
        notifyDataSetChanged();
        notifySelection();
    }

    /* ---------------- 渲染 ---------------- */

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Note n = data.get(position);
        if (markwon != null) {
            markwon.setMarkdown(h.tvName, n.name);
        } else {
            h.tvName.setText(n.name);
        }
        if (n.category == null || n.category.trim().isEmpty()) {
            h.tvCategory.setVisibility(View.GONE);
        } else {
            h.tvCategory.setVisibility(View.VISIBLE);
            h.tvCategory.setText(n.category);
        }
        h.tvDue.setText(Srs.nextReviewText(n));
        h.tvMastered.setVisibility(n.mastery == 1 ? View.VISIBLE : View.GONE);
        h.tvDefinition.setText(n.definition == null || n.definition.isEmpty()
                ? "（无定义）" : n.definition);

        if (selectionMode) {
            h.checkBox.setVisibility(View.VISIBLE);
            h.checkBox.setChecked(selectedIds.contains(n.id));
        } else {
            h.checkBox.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggle(n);
            } else if (click != null) {
                click.onClick(n);
            }
        });
        h.itemView.setOnLongClickListener(v -> {
            if (longClick != null) {
                longClick.onLongClick(n);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvCategory, tvDefinition, tvMastered, tvDue;
        CheckBox checkBox;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_item_name);
            tvCategory = v.findViewById(R.id.tv_item_category);
            tvDefinition = v.findViewById(R.id.tv_item_definition);
            tvMastered = v.findViewById(R.id.tv_item_mastered);
            tvDue = v.findViewById(R.id.tv_item_due);
            checkBox = v.findViewById(R.id.cb_item_select);
        }
    }
}
