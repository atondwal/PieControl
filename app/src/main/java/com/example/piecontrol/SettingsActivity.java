package com.example.piecontrol;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences("pie_config", MODE_PRIVATE);

        // Pie geometry
        setupSlider(R.id.ring_width_seek, R.id.ring_width_label,
                "ring_width", 51, 20, "Ring width: ", "dp");
        setupSlider(R.id.inner_radius_seek, R.id.inner_radius_label,
                "inner_radius", 60, 0, "Inner radius: ", "dp");
        setupSlider(R.id.gap_seek, R.id.gap_label,
                "gap_degrees", 0, 0, "Slice gap: ", "\u00B0");

        // Trigger zone
        setupSlider(R.id.trigger_width_seek, R.id.trigger_width_label,
                "trigger_width", 20, 5, "Width: ", "dp");
        setupSlider(R.id.trigger_height_seek, R.id.trigger_height_label,
                "trigger_height", 43, 10, "Height: ", "%");
        setupSlider(R.id.trigger_pos_seek, R.id.trigger_pos_label,
                "trigger_pos", 44, 0, "Position: ", "%");

        // Vibration
        setupSlider(R.id.vibe_tick_seek, R.id.vibe_tick_label,
                "vibe_tick", 60, 0, "Tick: ", "");
        setupSlider(R.id.vibe_select_seek, R.id.vibe_select_label,
                "vibe_select", 120, 0, "Select: ", "");
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

    private void notifyServiceReload() {
        Intent intent = new Intent(this, PieOverlayService.class);
        intent.setAction(PieOverlayService.ACTION_RELOAD);
        startForegroundService(intent);
    }
}
