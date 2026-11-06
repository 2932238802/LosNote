package com.example.losnote.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.losnote.R;
import com.example.losnote.data.Note;

import java.util.List;

/**
 * AI 整理后的导入预览列表。
 * - 勾选框：决定是否导入
 * - 点击卡片：编辑该条题目（名称/定义/栏目）
 */
public class PreviewNoteAdapter extends RecyclerView.Adapter<PreviewNoteAdapter.VH> {

    public interface OnChanged { void onChanged(); }
    public interface OnEdit { void onEdit(Note note, int position); }

    private final List<Note> data;
    private final boolean[] checked;
    private OnChanged changed;
    private OnEdit edit;

    public PreviewNoteAdapter(List<Note> data) {
        this.data = data;
        this.checked = new boolean[data.size()];
        for (int i = 0; i < checked.length; i++) checked[i] = true;
    }

    public void setOnChanged(OnChanged changed) { this.changed = changed; }
    public void setOnEdit(OnEdit edit) { this.edit = edit; }

    public int selectedCount() {
        int c = 0;
        for (boolean b : checked) if (b) c++;
        return c;
    }

    public void toggleAll() {
        boolean anyUnchecked = false;
        for (boolean b : checked) if (!b) { anyUnchecked = true; break; }
        for (int i = 0; i < checked.length; i++) checked[i] = anyUnchecked;
        notifyDataSetChanged();
        if (changed != null) changed.onChanged();
    }

    public java.util.List<Note> selectedNotes() {
        java.util.List<Note> res = new java.util.ArrayList<>();
        for (int i = 0; i < data.size(); i++) if (checked[i]) res.add(data.get(i));
        return res;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_preview_note, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Note n = data.get(position);
        h.cb.setOnCheckedChangeListener(null);
        h.cb.setChecked(checked[position]);
        h.name.setText(n.name);
        h.cat.setText(n.category);
        h.def.setText(n.definition == null ? "" : n.definition);

        // 点击整张卡片 = 编辑该条，不再用来切换勾选，避免误操作
        h.itemView.setOnClickListener(v -> {
            if (edit != null) edit.onEdit(n, position);
        });

        // 勾选框专门负责选择/取消选择
        h.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checked[position] = isChecked;
            if (changed != null) changed.onChanged();
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CheckBox cb;
        TextView name, cat, def;
        VH(@NonNull View v) {
            super(v);
            cb = v.findViewById(R.id.cb_preview);
            name = v.findViewById(R.id.tv_preview_name);
            cat = v.findViewById(R.id.tv_preview_category);
            def = v.findViewById(R.id.tv_preview_def);
        }
    }
}
