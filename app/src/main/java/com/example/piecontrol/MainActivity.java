package com.example.piecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.piecontrol.data.AppDatabase;
import com.example.piecontrol.data.PieItem;
import com.example.piecontrol.data.PieItemDao;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_APP_PICK = 1;
    private static final int REQ_OVERLAY = 2;
    private static final int REQ_APP_REPLACE = 3;

    private PieItemDao dao;
    private SharedPreferences prefs;
    private RecyclerView recyclerView;
    private SectionedAdapter adapter;

    private EditText ringCountEdit;
    private EditText slotsEdit;

    // For per-level add: which level the user tapped "+" on
    private int pendingAddLevel = -1;
    // For replace: which PieItem id is being replaced
    private int pendingReplaceItemId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dao = AppDatabase.getInstance(this).pieItemDao();
        prefs = getSharedPreferences("pie_config", MODE_PRIVATE);

        ringCountEdit = findViewById(R.id.ring_count);
        slotsEdit = findViewById(R.id.slots_config);
        Button applyBtn = findViewById(R.id.btn_apply_config);
        recyclerView = findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load config
        int ringCount = prefs.getInt("ring_count", 2);
        ringCountEdit.setText(String.valueOf(ringCount));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ringCount; i++) {
            if (i > 0) sb.append(",");
            sb.append(prefs.getInt("slots_ring_" + i, i == 0 ? 3 : 5));
        }
        slotsEdit.setText(sb.toString());

        // Vibration sliders
        SeekBar vibeTickSeek = findViewById(R.id.vibe_tick_seek);
        SeekBar vibeSelectSeek = findViewById(R.id.vibe_select_seek);
        TextView vibeTickLabel = findViewById(R.id.vibe_tick_label);
        TextView vibeSelectLabel = findViewById(R.id.vibe_select_label);

        int tickVal = prefs.getInt("vibe_tick", 60);
        int selectVal = prefs.getInt("vibe_select", 120);
        vibeTickSeek.setProgress(tickVal);
        vibeSelectSeek.setProgress(selectVal);
        vibeTickLabel.setText("Tick vibration: " + tickVal);
        vibeSelectLabel.setText("Select vibration: " + selectVal);

        vibeTickSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                vibeTickLabel.setText("Tick vibration: " + progress);
                if (user) {
                    prefs.edit().putInt("vibe_tick", progress).apply();
                    notifyServiceReload();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        vibeSelectSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                vibeSelectLabel.setText("Select vibration: " + progress);
                if (user) {
                    prefs.edit().putInt("vibe_select", progress).apply();
                    notifyServiceReload();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        applyBtn.setOnClickListener(v -> applyConfig());

        loadItems();
        checkOverlayPermission();
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            startOverlayService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_APP_PICK && resultCode == RESULT_OK && data != null) {
            addAppToLevel(data, pendingAddLevel);
        } else if (requestCode == REQ_APP_REPLACE && resultCode == RESULT_OK && data != null) {
            replaceApp(data);
        }
    }

    private void addAppToLevel(Intent data, int level) {
        if (level < 0) return;
        String name = data.getStringExtra(AppPickerActivity.EXTRA_APP_NAME);
        String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME);
        String act = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_NAME);

        List<PieItem> existing = dao.getItemsByLevel(level);
        PieItem item = new PieItem();
        item.level = level;
        item.position = existing.size();
        item.name = name;
        item.packageName = pkg;
        item.activityName = act;
        dao.insert(item);
        loadItems();
        notifyServiceReload();
    }

    private void replaceApp(Intent data) {
        if (pendingReplaceItemId < 0) return;
        String name = data.getStringExtra(AppPickerActivity.EXTRA_APP_NAME);
        String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME);
        String act = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_NAME);

        // Find the item and update it
        List<PieItem> all = dao.getAllItems();
        for (PieItem item : all) {
            if (item.id == pendingReplaceItemId) {
                item.name = name;
                item.packageName = pkg;
                item.activityName = act;
                dao.update(item);
                break;
            }
        }
        pendingReplaceItemId = -1;
        loadItems();
        notifyServiceReload();
    }

    private void applyConfig() {
        try {
            int ringCount = Integer.parseInt(ringCountEdit.getText().toString().trim());
            String[] parts = slotsEdit.getText().toString().trim().split(",");
            if (ringCount < 1 || ringCount > 5) {
                Toast.makeText(this, "Ring count must be 1-5", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("ring_count", ringCount);
            for (int i = 0; i < ringCount; i++) {
                int slots = (i < parts.length) ? Integer.parseInt(parts[i].trim()) : 3;
                editor.putInt("slots_ring_" + i, Math.max(1, Math.min(12, slots)));
            }
            editor.apply();
            Toast.makeText(this, "Config saved", Toast.LENGTH_SHORT).show();
            loadItems(); // rebuild sections in case ring count changed
            notifyServiceReload();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadItems() {
        int ringCount = prefs.getInt("ring_count", 2);
        List<Object> rows = new ArrayList<>();
        for (int level = 0; level < ringCount; level++) {
            List<PieItem> levelItems = dao.getItemsByLevel(level);
            int maxSlots = prefs.getInt("slots_ring_" + level, level == 0 ? 3 : 5);
            rows.add(new LevelHeader(level, levelItems.size(), maxSlots));
            rows.addAll(levelItems);
        }
        if (adapter == null) {
            adapter = new SectionedAdapter(rows);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateRows(rows);
        }
    }

    private void swapPositions(PieItem a, PieItem b) {
        int tmpPos = a.position;
        a.position = b.position;
        b.position = tmpPos;
        dao.update(a);
        dao.update(b);
        loadItems();
        notifyServiceReload();
    }

    private void moveUp(PieItem item) {
        if (item.position <= 0) return;
        List<PieItem> siblings = dao.getItemsByLevel(item.level);
        for (PieItem sib : siblings) {
            if (sib.position == item.position - 1) {
                swapPositions(item, sib);
                return;
            }
        }
    }

    private void moveDown(PieItem item) {
        List<PieItem> siblings = dao.getItemsByLevel(item.level);
        for (PieItem sib : siblings) {
            if (sib.position == item.position + 1) {
                swapPositions(item, sib);
                return;
            }
        }
    }

    private void showEditDialog(PieItem item) {
        List<PieItem> siblings = dao.getItemsByLevel(item.level);
        boolean canUp = item.position > 0;
        boolean canDown = item.position < siblings.size() - 1;

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (canUp) {
            options.add("Move Up");
            actions.add(() -> moveUp(item));
        }
        if (canDown) {
            options.add("Move Down");
            actions.add(() -> moveDown(item));
        }

        int ringCount = prefs.getInt("ring_count", 2);
        if (ringCount > 1) {
            options.add("Move to Level...");
            actions.add(() -> showMoveLevelDialog(item));
        }

        options.add("Replace App");
        actions.add(() -> {
            pendingReplaceItemId = item.id;
            Intent i = new Intent(this, AppPickerActivity.class);
            startActivityForResult(i, REQ_APP_REPLACE);
        });

        options.add("Delete");
        actions.add(() -> showDeleteConfirmation(item));

        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(options.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void showMoveLevelDialog(PieItem item) {
        int ringCount = prefs.getInt("ring_count", 2);
        List<String> levels = new ArrayList<>();
        List<Integer> levelIndices = new ArrayList<>();
        for (int i = 0; i < ringCount; i++) {
            if (i != item.level) {
                int count = dao.getItemsByLevel(i).size();
                int max = prefs.getInt("slots_ring_" + i, i == 0 ? 3 : 5);
                levels.add("Level " + (i + 1) + " (" + count + "/" + max + " slots)");
                levelIndices.add(i);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Move to which level?")
                .setItems(levels.toArray(new String[0]), (d, which) -> {
                    int oldLevel = item.level;
                    int oldPos = item.position;
                    int newLevel = levelIndices.get(which);

                    // Remove from old level and compact
                    dao.deleteById(item.id);
                    dao.compactPositions(oldLevel, oldPos);

                    // Add to new level at end
                    List<PieItem> newLevelItems = dao.getItemsByLevel(newLevel);
                    PieItem moved = new PieItem();
                    moved.level = newLevel;
                    moved.position = newLevelItems.size();
                    moved.name = item.name;
                    moved.packageName = item.packageName;
                    moved.activityName = item.activityName;
                    dao.insert(moved);

                    loadItems();
                    notifyServiceReload();
                })
                .show();
    }

    private void showDeleteConfirmation(PieItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + item.name + "?")
                .setPositiveButton("Remove", (d, w) -> {
                    int level = item.level;
                    int pos = item.position;
                    dao.deleteById(item.id);
                    dao.compactPositions(level, pos);
                    loadItems();
                    notifyServiceReload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, PieOverlayService.class);
        startForegroundService(intent);
    }

    private void notifyServiceReload() {
        Intent intent = new Intent(this, PieOverlayService.class);
        intent.setAction(PieOverlayService.ACTION_RELOAD);
        startForegroundService(intent);
    }

    // --- Data class for section headers ---
    static class LevelHeader {
        final int level;
        final int count;
        final int maxSlots;
        LevelHeader(int level, int count, int maxSlots) {
            this.level = level;
            this.count = count;
            this.maxSlots = maxSlots;
        }
    }

    // --- Sectioned adapter ---
    private class SectionedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;
        private final List<Object> rows;

        SectionedAdapter(List<Object> rows) {
            this.rows = new ArrayList<>(rows);
        }

        void updateRows(List<Object> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
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
                        + " (" + header.count + "/" + header.maxSlots + " slots)");
                hv.addBtn.setOnClickListener(v -> {
                    pendingAddLevel = header.level;
                    Intent i = new Intent(MainActivity.this, AppPickerActivity.class);
                    startActivityForResult(i, REQ_APP_PICK);
                });
            } else {
                PieItem item = (PieItem) rows.get(position);
                ItemVH iv = (ItemVH) holder;
                iv.name.setText(item.name);
                iv.info.setText("Slot " + (item.position + 1));
                try {
                    iv.icon.setImageDrawable(
                            getPackageManager().getApplicationIcon(item.packageName));
                } catch (Exception e) {
                    iv.icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }

                // Determine if this is first/last in its level
                List<PieItem> siblings = dao.getItemsByLevel(item.level);
                boolean isFirst = item.position <= 0;
                boolean isLast = item.position >= siblings.size() - 1;
                iv.btnUp.setVisibility(isFirst ? View.INVISIBLE : View.VISIBLE);
                iv.btnDown.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

                iv.btnUp.setOnClickListener(v -> moveUp(item));
                iv.btnDown.setOnClickListener(v -> moveDown(item));
                iv.itemView.setOnClickListener(v -> showEditDialog(item));
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class HeaderVH extends RecyclerView.ViewHolder {
            TextView label;
            Button addBtn;
            HeaderVH(View v) {
                super(v);
                label = v.findViewById(R.id.header_label);
                addBtn = v.findViewById(R.id.header_add_btn);
            }
        }

        class ItemVH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView info;
            ImageButton btnUp;
            ImageButton btnDown;
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
}
