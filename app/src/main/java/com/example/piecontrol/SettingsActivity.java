package com.example.piecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.piecontrol.data.AppDatabase;
import com.example.piecontrol.data.PieItem;
import com.example.piecontrol.data.PieItemDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SettingsActivity extends Activity {
    private static final int REQ_EXPORT = 100;
    private static final int REQ_IMPORT = 101;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = Prefs.get(this);

        // Appearance
        setupColorPicker(R.id.color_bg_preview, R.id.color_bg_label,
                Prefs.KEY_COLOR_BG, Prefs.DEFAULT_COLOR_BG, "Slice color");
        setupColorPicker(R.id.color_highlight_preview, R.id.color_highlight_label,
                Prefs.KEY_COLOR_HIGHLIGHT, Prefs.DEFAULT_COLOR_HIGHLIGHT, "Highlight color");
        setupColorPicker(R.id.color_stroke_preview, R.id.color_stroke_label,
                Prefs.KEY_COLOR_STROKE, Prefs.DEFAULT_COLOR_STROKE, "Stroke color");
        setupSlider(R.id.icon_size_seek, R.id.icon_size_label,
                Prefs.KEY_ICON_SIZE, Prefs.DEFAULT_ICON_SIZE, 16, "Icon size: ", "dp");
        setupStrokeWidthSlider();

        // Pie geometry
        setupSlider(R.id.ring_width_seek, R.id.ring_width_label,
                Prefs.KEY_RING_WIDTH, Prefs.DEFAULT_RING_WIDTH, 20, "Ring width: ", "dp");
        setupSlider(R.id.inner_radius_seek, R.id.inner_radius_label,
                Prefs.KEY_INNER_RADIUS, Prefs.DEFAULT_INNER_RADIUS, 0, "Inner radius: ", "dp");
        setupSlider(R.id.gap_seek, R.id.gap_label,
                Prefs.KEY_GAP_DEGREES, Prefs.DEFAULT_GAP_DEGREES, 0, "Slice gap: ", "\u00B0");
        setupSlider(R.id.arc_span_seek, R.id.arc_span_label,
                Prefs.KEY_ARC_SPAN, Prefs.DEFAULT_ARC_SPAN, 90, "Arc span: ", "\u00B0");

        // Trigger zone
        setupSlider(R.id.trigger_width_seek, R.id.trigger_width_label,
                Prefs.KEY_TRIGGER_WIDTH, Prefs.DEFAULT_TRIGGER_WIDTH, 5, "Width: ", "dp");
        setupSlider(R.id.trigger_height_seek, R.id.trigger_height_label,
                Prefs.KEY_TRIGGER_HEIGHT, Prefs.DEFAULT_TRIGGER_HEIGHT, 10, "Height: ", "%");
        setupSlider(R.id.trigger_pos_seek, R.id.trigger_pos_label,
                Prefs.KEY_TRIGGER_POS, Prefs.DEFAULT_TRIGGER_POS, 0, "Position: ", "%");

        // Vibration
        setupSlider(R.id.vibe_tick_seek, R.id.vibe_tick_label,
                Prefs.KEY_VIBE_TICK, Prefs.DEFAULT_VIBE_TICK, 0, "Tick amplitude: ", "");
        setupSlider(R.id.vibe_select_seek, R.id.vibe_select_label,
                Prefs.KEY_VIBE_SELECT, Prefs.DEFAULT_VIBE_SELECT, 0, "Select amplitude: ", "");
        setupSlider(R.id.vibe_tick_ms_seek, R.id.vibe_tick_ms_label,
                Prefs.KEY_VIBE_TICK_MS, Prefs.DEFAULT_VIBE_TICK_MS, 1, "Tick duration: ", "ms");
        setupSlider(R.id.vibe_select_ms_seek, R.id.vibe_select_ms_label,
                Prefs.KEY_VIBE_SELECT_MS, Prefs.DEFAULT_VIBE_SELECT_MS, 1, "Select duration: ", "ms");

        // Backup
        findViewById(R.id.btn_export).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "pie_control_backup.json");
            startActivityForResult(intent, REQ_EXPORT);
        });
        findViewById(R.id.btn_import).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/json");
            startActivityForResult(intent, REQ_IMPORT);
        });
    }

    private void setupSlider(int seekId, int labelId, String prefKey,
                              int defaultVal, int minVal, String prefix, String suffix) {
        SeekBar seek = findViewById(seekId);
        TextView label = findViewById(labelId);
        int val = prefs.getInt(prefKey, defaultVal);
        seek.setProgress(val - minVal);
        label.setText(prefix + val + suffix);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                int v = progress + minVal;
                label.setText(prefix + v + suffix);
                if (user) {
                    prefs.edit().putInt(prefKey, v).apply();
                    notifyServiceReload();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void setupStrokeWidthSlider() {
        SeekBar seek = findViewById(R.id.stroke_width_seek);
        TextView label = findViewById(R.id.stroke_width_label);
        int tenths = prefs.getInt(Prefs.KEY_STROKE_WIDTH_TENTHS, Prefs.DEFAULT_STROKE_WIDTH_TENTHS);
        seek.setProgress(tenths);
        label.setText("Stroke width: " + (tenths / 10) + "." + (tenths % 10) + "dp");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                label.setText("Stroke width: " + (progress / 10) + "." + (progress % 10) + "dp");
                if (user) {
                    prefs.edit().putInt(Prefs.KEY_STROKE_WIDTH_TENTHS, progress).apply();
                    notifyServiceReload();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void setupColorPicker(int previewId, int labelId, String prefKey,
                                   int defaultColor, String labelText) {
        View preview = findViewById(previewId);
        TextView label = findViewById(labelId);
        int color = prefs.getInt(prefKey, defaultColor);
        preview.setBackgroundColor(color);
        label.setText(labelText + ": #" + String.format("%08X", color));

        View.OnClickListener clickListener = v -> showColorDialog(prefKey, labelText, preview, label);
        preview.setOnClickListener(clickListener);
        label.setOnClickListener(clickListener);
    }

    private void showColorDialog(String prefKey, String labelText, View preview, TextView label) {
        int currentColor = prefs.getInt(prefKey, 0);
        int[] argb = {
            Color.alpha(currentColor), Color.red(currentColor),
            Color.green(currentColor), Color.blue(currentColor)
        };
        String[] names = {"Alpha", "Red", "Green", "Blue"};

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        View colorPreview = new View(this);
        colorPreview.setBackgroundColor(currentColor);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
        previewParams.bottomMargin = (int) (8 * density);
        layout.addView(colorPreview, previewParams);

        TextView[] labels = new TextView[4];
        for (int i = 0; i < 4; i++) {
            labels[i] = new TextView(this);
            labels[i].setText(names[i] + ": " + argb[i]);
            layout.addView(labels[i]);

            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(argb[i]);
            final int idx = i;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    argb[idx] = progress;
                    labels[idx].setText(names[idx] + ": " + progress);
                    colorPreview.setBackgroundColor(Color.argb(argb[0], argb[1], argb[2], argb[3]));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            layout.addView(sb);
        }

        new AlertDialog.Builder(this)
                .setTitle(labelText)
                .setView(layout)
                .setPositiveButton("OK", (d, w) -> {
                    int newColor = Color.argb(argb[0], argb[1], argb[2], argb[3]);
                    prefs.edit().putInt(prefKey, newColor).apply();
                    preview.setBackgroundColor(newColor);
                    label.setText(labelText + ": #" + String.format("%08X", newColor));
                    notifyServiceReload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQ_EXPORT) {
            doExport(uri);
        } else if (requestCode == REQ_IMPORT) {
            doImport(uri);
        }
    }

    private void doExport(Uri uri) {
        try {
            JSONObject root = new JSONObject();

            // Export prefs
            JSONObject prefsJson = new JSONObject();
            Map<String, ?> all = prefs.getAll();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                prefsJson.put(entry.getKey(), entry.getValue());
            }
            root.put("prefs", prefsJson);

            // Export pie items
            PieItemDao dao = AppDatabase.getInstance(this).pieItemDao();
            List<PieItem> items = dao.getAllItems();
            JSONArray itemsArr = new JSONArray();
            for (PieItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("level", item.level);
                obj.put("position", item.position);
                obj.put("name", item.name);
                obj.put("packageName", item.packageName);
                obj.put("activityName", item.activityName);
                obj.put("parentId", item.parentId);
                obj.put("isFolder", item.isFolder);
                itemsArr.put(obj);
            }
            root.put("items", itemsArr);

            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            os.close();
            Toast.makeText(this, "Exported " + items.size() + " items", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void doImport(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            is.close();

            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));

            // Restore prefs
            JSONObject prefsJson = root.getJSONObject("prefs");
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            for (java.util.Iterator<String> it = prefsJson.keys(); it.hasNext(); ) {
                String key = it.next();
                Object val = prefsJson.get(key);
                if (val instanceof Integer) editor.putInt(key, (Integer) val);
                else if (val instanceof Long) editor.putInt(key, ((Long) val).intValue());
                else if (val instanceof String) editor.putString(key, (String) val);
                else if (val instanceof Boolean) editor.putBoolean(key, (Boolean) val);
                else if (val instanceof Number) editor.putInt(key, ((Number) val).intValue());
            }
            editor.apply();

            // Restore pie items
            PieItemDao dao = AppDatabase.getInstance(this).pieItemDao();
            List<PieItem> existing = dao.getAllItems();
            for (PieItem item : existing) dao.delete(item);

            JSONArray itemsArr = root.getJSONArray("items");
            int count = itemsArr.length();
            for (int i = 0; i < count; i++) {
                JSONObject obj = itemsArr.getJSONObject(i);
                PieItem item = new PieItem();
                item.level = obj.getInt("level");
                item.position = obj.getInt("position");
                item.name = obj.optString("name", null);
                item.packageName = obj.optString("packageName", null);
                item.activityName = obj.optString("activityName", null);
                item.parentId = obj.optInt("parentId", 0);
                item.isFolder = obj.optBoolean("isFolder", false);
                dao.insert(item);
            }

            Toast.makeText(this, "Imported " + count + " items", Toast.LENGTH_SHORT).show();
            notifyServiceReload();
            recreate();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void notifyServiceReload() {
        Intent intent = new Intent(this, PieOverlayService.class);
        intent.setAction(PieOverlayService.ACTION_RELOAD);
        startForegroundService(intent);
    }
}
