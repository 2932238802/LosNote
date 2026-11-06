package com.example.losnote.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.losnote.R;
import com.example.losnote.data.DailyLog;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 今日足迹列表适配器。 */
public class DailyLogAdapter extends RecyclerView.Adapter<DailyLogAdapter.VH> {

    public interface OnDeleteListener {
        void onDelete(DailyLog log);
    }

    private final List<DailyLog> items = new ArrayList<>();
    private final OnDeleteListener deleteListener;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public DailyLogAdapter(@NonNull OnDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setItems(@NonNull List<DailyLog> logs) {
        items.clear();
        items.addAll(logs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_log, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DailyLog log = items.get(position);
        holder.tvTime.setText(timeFormat.format(new Date(log.createdAt)));
        holder.tvContent.setText(log.content);
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(log));
        holder.itemView.setOnLongClickListener(v -> {
            deleteListener.onDelete(log);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTime;
        final TextView tvContent;
        final MaterialButton btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_daily_time);
            tvContent = itemView.findViewById(R.id.tv_daily_content);
            btnDelete = itemView.findViewById(R.id.btn_daily_delete);
        }
    }
}
