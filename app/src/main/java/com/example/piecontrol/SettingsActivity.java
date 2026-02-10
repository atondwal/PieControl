package com.example.piecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences("pie_config", MODE_PRIVATE);

        // Appearance
        setupColorPicker(R.id.color_bg_preview, R.id.color_bg_label,
                "color_bg", 0xDD333333, "Slice color");
        setupColorPicker(R.id.color_highlight_preview, R.id.color_highlight_label,
                "color_highlight", 0xDD5588CC, "Highlight color");
        setupColorPicker(R.id.color_stroke_preview, R.id.color_stroke_label,
                "color_stroke", 0xDD888888, "Stroke color");
        setupSlider(R.id.icon_size_seek, R.id.icon_size_label,
                "icon_size", 36, 16, "Icon size: ", "dp");
        setupStrokeWidthSlider();

        // Pie geometry
        setupSlider(R.id.ring_width_seek, R.id.ring_width_label,
                "ring_width", 51, 20, "Ring width: ", "dp");
        setupSlider(R.id.inner_radius_seek, R.id.inner_radius_label,
                "inner_radius", 60, 0, "Inner radius: ", "dp");
        setupSlider(R.id.gap_seek, R.id.gap_label,
                "gap_degrees", 0, 0, "Slice gap: ", "\u00B0");
        setupSlider(R.id.arc_span_seek, R.id.arc_span_label,
                "arc_span", 180, 90, "Arc span: ", "\u00B0");

        // Trigger zone
        setupSlider(R.id.trigger_width_seek, R.id.trigger_width_label,
                "trigger_width", 20, 5, "Width: ", "dp");
        setupSlider(R.id.trigger_height_seek, R.id.trigger_height_label,
                "trigger_height", 43, 10, "Height: ", "%");
        setupSlider(R.id.trigger_pos_seek, R.id.trigger_pos_label,
                "trigger_pos", 44, 0, "Position: ", "%");

        // Vibration
        setupSlider(R.id.vibe_tick_seek, R.id.vibe_tick_label,
                "vibe_tick", 60, 0, "Tick amplitude: ", "");
        setupSlider(R.id.vibe_select_seek, R.id.vibe_select_label,
                "vibe_select", 120, 0, "Select amplitude: ", "");
        setupSlider(R.id.vibe_tick_ms_seek, R.id.vibe_tick_ms_label,
                "vibe_tick_ms", 10, 1, "Tick duration: ", "ms");
        setupSlider(R.id.vibe_select_ms_seek, R.id.vibe_select_ms_label,
                "vibe_select_ms", 20, 1, "Select duration: ", "ms");
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
        int tenths = prefs.getInt("stroke_width_tenths", 15);
        seek.setProgress(tenths);
        label.setText("Stroke width: " + (tenths / 10) + "." + (tenths % 10) + "dp");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                label.setText("Stroke width: " + (progress / 10) + "." + (progress % 10) + "dp");
                if (user) {
                    prefs.edit().putInt("stroke_width_tenths", progress).apply();
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

    private void notifyServiceReload() {
        Intent intent = new Intent(this, PieOverlayService.class);
        intent.setAction(PieOverlayService.ACTION_RELOAD);
        startForegroundService(intent);
    }
}
