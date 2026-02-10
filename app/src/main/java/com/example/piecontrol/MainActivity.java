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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
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

    private PieItemDao dao;
    private SharedPreferences prefs;
    private RecyclerView recyclerView;
    private ItemAdapter adapter;
    private List<PieItem> items = new ArrayList<>();

    private EditText ringCountEdit;
    private EditText slotsEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dao = AppDatabase.getInstance(this).pieItemDao();
        prefs = getSharedPreferences("pie_config", MODE_PRIVATE);

        ringCountEdit = findViewById(R.id.ring_count);
        slotsEdit = findViewById(R.id.slots_config);
        Button applyBtn = findViewById(R.id.btn_apply_config);
        Button addBtn = findViewById(R.id.btn_add);
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
        addBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, AppPickerActivity.class);
            startActivityForResult(i, REQ_APP_PICK);
        });

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
            showLevelDialog(data);
        }
    }

    private void showLevelDialog(Intent data) {
        String name = data.getStringExtra(AppPickerActivity.EXTRA_APP_NAME);
        String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME);
        String act = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_NAME);

        int ringCount = prefs.getInt("ring_count", 2);
        String[] levels = new String[ringCount];
        for (int i = 0; i < ringCount; i++) levels[i] = "Level " + (i + 1);

        new AlertDialog.Builder(this)
                .setTitle("Add " + name + " to which level?")
                .setItems(levels, (d, which) -> {
                    List<PieItem> existing = dao.getItemsByLevel(which);
                    PieItem item = new PieItem();
                    item.level = which;
                    item.position = existing.size();
                    item.name = name;
                    item.packageName = pkg;
                    item.activityName = act;
                    dao.insert(item);
                    loadItems();
                    notifyServiceReload();
                })
                .show();
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
            notifyServiceReload();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadItems() {
        items = dao.getAllItems();
        adapter = new ItemAdapter();
        recyclerView.setAdapter(adapter);
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

    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pie_slot, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            PieItem item = items.get(position);
            holder.name.setText(item.name);
            holder.info.setText("Level " + (item.level + 1) + ", Slot " + (item.position + 1));
            try {
                holder.icon.setImageDrawable(
                        getPackageManager().getApplicationIcon(item.packageName));
            } catch (Exception e) {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Remove " + item.name + "?")
                        .setPositiveButton("Remove", (d, w) -> {
                            dao.deleteById(item.id);
                            loadItems();
                            notifyServiceReload();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView info;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.slot_icon);
                name = v.findViewById(R.id.slot_name);
                info = v.findViewById(R.id.slot_info);
            }
        }
    }
}
