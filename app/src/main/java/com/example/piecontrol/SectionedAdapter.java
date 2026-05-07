package com.example.piecontrol;

import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.piecontrol.data.PieItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SectionedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    public interface Listener {
        void onAddRequested(int level);
        void onMoveUp(PieItem item);
        void onMoveDown(PieItem item);
        void onItemClicked(PieItem item);
        void onItemLongClicked(PieItem item);
    }

    private final List<Object> rows;
    private final Map<Integer, Integer> levelCounts = new HashMap<>();
    private final PackageManager pm;
    private final Listener listener;

    public SectionedAdapter(List<Object> rows, PackageManager pm, Listener listener) {
        this.rows = new ArrayList<>(rows);
        this.pm = pm;
        this.listener = listener;
        rebuildLevelCounts();
    }

    public void updateRows(List<Object> newRows) {
        rows.clear();
        rows.addAll(newRows);
        rebuildLevelCounts();
        notifyDataSetChanged();
    }

    private void rebuildLevelCounts() {
        levelCounts.clear();
        for (Object row : rows) {
            if (row instanceof LevelHeader) {
                LevelHeader h = (LevelHeader) row;
                levelCounts.put(h.level, h.count);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof LevelHeader ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_level_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = inf.inflate(R.layout.item_pie_slot, parent, false);
            return new ItemVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderVH) {
            LevelHeader header = (LevelHeader) rows.get(position);
            HeaderVH hv = (HeaderVH) holder;
            hv.label.setText("Level " + (header.level + 1)
                    + " (" + header.count + " items)");
            hv.addBtn.setOnClickListener(v -> listener.onAddRequested(header.level));
        } else {
            PieItem item = (PieItem) rows.get(position);
            ItemVH iv = (ItemVH) holder;
            iv.name.setText(item.name);

            if (item.isFolder) {
                iv.info.setText("Folder");
                iv.icon.setImageResource(android.R.drawable.ic_menu_agenda);
            } else {
                iv.info.setText("Slot " + (item.position + 1));
                try {
                    iv.icon.setImageDrawable(pm.getApplicationIcon(item.packageName));
                } catch (PackageManager.NameNotFoundException e) {
                    iv.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            }

            int siblingCount = levelCounts.getOrDefault(item.level, 0);
            boolean isFirst = item.position <= 0;
            boolean isLast = item.position >= siblingCount - 1;
            iv.btnUp.setVisibility(isFirst ? View.INVISIBLE : View.VISIBLE);
            iv.btnDown.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

            iv.btnUp.setOnClickListener(v -> listener.onMoveUp(item));
            iv.btnDown.setOnClickListener(v -> listener.onMoveDown(item));
            iv.itemView.setOnClickListener(v -> listener.onItemClicked(item));
            iv.itemView.setOnLongClickListener(v -> {
                listener.onItemLongClicked(item);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView label;
        final Button addBtn;
        HeaderVH(View v) {
            super(v);
            label = v.findViewById(R.id.header_label);
            addBtn = v.findViewById(R.id.header_add_btn);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView info;
        final ImageButton btnUp;
        final ImageButton btnDown;
        ItemVH(View v) {
            super(v);
            icon = v.findViewById(R.id.slot_icon);
            name = v.findViewById(R.id.slot_name);
            info = v.findViewById(R.id.slot_info);
            btnUp = v.findViewById(R.id.btn_move_up);
            btnDown = v.findViewById(R.id.btn_move_down);
        }
    }
}
