package com.example.piecontrol;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}

    public static final String NAME = "pie_config";

    // Appearance
    public static final String KEY_COLOR_BG = "color_bg";
    public static final String KEY_COLOR_HIGHLIGHT = "color_highlight";
    public static final String KEY_COLOR_STROKE = "color_stroke";
    public static final String KEY_ICON_SIZE = "icon_size";
    public static final String KEY_STROKE_WIDTH_TENTHS = "stroke_width_tenths";

    // Geometry
    public static final String KEY_RING_WIDTH = "ring_width";
    public static final String KEY_INNER_RADIUS = "inner_radius";
    public static final String KEY_GAP_DEGREES = "gap_degrees";
    public static final String KEY_ARC_SPAN = "arc_span";

    // Trigger zone
    public static final String KEY_TRIGGER_WIDTH = "trigger_width";
    public static final String KEY_TRIGGER_HEIGHT = "trigger_height";
    public static final String KEY_TRIGGER_POS = "trigger_pos";

    // Vibration
    public static final String KEY_VIBE_TICK = "vibe_tick";
    public static final String KEY_VIBE_SELECT = "vibe_select";
    public static final String KEY_VIBE_TICK_MS = "vibe_tick_ms";
    public static final String KEY_VIBE_SELECT_MS = "vibe_select_ms";

    // Defaults
    public static final int DEFAULT_COLOR_BG = 0xDD333333;
    public static final int DEFAULT_COLOR_HIGHLIGHT = 0xDD5588CC;
    public static final int DEFAULT_COLOR_STROKE = 0xDD888888;
    public static final int DEFAULT_ICON_SIZE = 36;
    public static final int DEFAULT_STROKE_WIDTH_TENTHS = 15;
    public static final int DEFAULT_RING_WIDTH = 51;
    public static final int DEFAULT_INNER_RADIUS = 60;
    public static final int DEFAULT_GAP_DEGREES = 0;
    public static final int DEFAULT_ARC_SPAN = 180;
    public static final int DEFAULT_TRIGGER_WIDTH = 20;
    public static final int DEFAULT_TRIGGER_HEIGHT = 43;
    public static final int DEFAULT_TRIGGER_POS = 44;
    public static final int DEFAULT_VIBE_TICK = 60;
    public static final int DEFAULT_VIBE_SELECT = 120;
    public static final int DEFAULT_VIBE_TICK_MS = 10;
    public static final int DEFAULT_VIBE_SELECT_MS = 20;

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
