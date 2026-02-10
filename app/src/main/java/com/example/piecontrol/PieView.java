package com.example.piecontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import com.example.piecontrol.data.AppDatabase;
import com.example.piecontrol.data.PieItem;

import java.util.ArrayList;
import java.util.List;

public class PieView extends View {
    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path slicePath = new Path();
    private final RectF outerRect = new RectF();
    private final RectF innerRect = new RectF();

    private float density;
    private float centerX, centerY;
    private float gapDegrees;
    private float ringWidthDp;
    private float innerRadiusDp;
    private int ringCount;
    private int[] slotsPerRing;
    private List<List<PieItem>> itemsByLevel;
    private int vibeTickAmplitude;
    private int vibeSelectAmplitude;
    private int colorBg;
    private int colorHighlight;
    private int colorStroke;
    private int iconSizeDp;
    private float strokeWidthDp;
    private int vibeTickDuration;
    private int vibeSelectDuration;
    private float totalAngle;

    private int highlightRing = -1;
    private int highlightSlot = -1;

    private final Vibrator vibrator;
    private OnItemSelectedListener listener;

    public interface OnItemSelectedListener {
        void onItemSelected(PieItem item);
        void onDismiss();
    }

    public PieView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(10 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        strokePaint.setStyle(Paint.Style.STROKE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        loadConfig();
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
        this.listener = l;
    }

    public void setCenterY(float y) {
        this.centerY = y;
    }

    private void loadConfig() {
        SharedPreferences prefs = getContext().getSharedPreferences("pie_config", Context.MODE_PRIVATE);
        vibeTickAmplitude = prefs.getInt("vibe_tick", 60);
        vibeSelectAmplitude = prefs.getInt("vibe_select", 120);
        ringWidthDp = prefs.getInt("ring_width", 51);
        innerRadiusDp = prefs.getInt("inner_radius", 60);
        gapDegrees = prefs.getInt("gap_degrees", 0);
        colorBg = prefs.getInt("color_bg", 0xDD333333);
        colorHighlight = prefs.getInt("color_highlight", 0xDD5588CC);
        colorStroke = prefs.getInt("color_stroke", 0xDD888888);
        iconSizeDp = prefs.getInt("icon_size", 36);
        strokeWidthDp = prefs.getInt("stroke_width_tenths", 15) / 10f;
        vibeTickDuration = prefs.getInt("vibe_tick_ms", 10);
        vibeSelectDuration = prefs.getInt("vibe_select_ms", 20);
        totalAngle = prefs.getInt("arc_span", 180);

        strokePaint.setStrokeWidth(strokeWidthDp * density);
        strokePaint.setColor(colorStroke);
        loadItems();
    }

    public void reload() {
        loadConfig();
        invalidate();
    }

    private void loadItems() {
        AppDatabase db = AppDatabase.getInstance(getContext());
        List<PieItem> all = db.pieItemDao().getAllItems();

        int maxLevel = -1;
        for (PieItem item : all) {
            if (item.level > maxLevel) maxLevel = item.level;
        }
        ringCount = Math.max(0, maxLevel + 1);

        itemsByLevel = new ArrayList<>();
        slotsPerRing = new int[ringCount];
        for (int i = 0; i < ringCount; i++) {
            List<PieItem> levelItems = db.pieItemDao().getItemsByLevel(i);
            itemsByLevel.add(levelItems);
            slotsPerRing[i] = levelItems.size();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = 0; // left edge
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Translate canvas to screen coordinates so drawing aligns with touch
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        canvas.save();
        canvas.translate(-loc[0], -loc[1]);

        float ringWidth = ringWidthDp * density;
        float innerRadius = innerRadiusDp * density;

        for (int ring = 0; ring < ringCount; ring++) {
            int slots = slotsPerRing[ring];
            if (slots == 0) continue;
            float rInner = innerRadius + ring * ringWidth;
            float rOuter = rInner + ringWidth;
            float gapTotal = gapDegrees * slots;
            float sweep = (totalAngle - gapTotal) / slots;
            float startBase = -totalAngle / 2f;

            List<PieItem> items = ring < itemsByLevel.size() ? itemsByLevel.get(ring) : null;

            for (int slot = 0; slot < slots; slot++) {
                float startAngle = startBase + slot * (sweep + gapDegrees);

                boolean highlighted = (ring == highlightRing && slot == highlightSlot);
                slicePaint.setColor(highlighted ? colorHighlight : colorBg);

                // Draw arc slice
                slicePath.reset();
                outerRect.set(centerX - rOuter, centerY - rOuter, centerX + rOuter, centerY + rOuter);
                innerRect.set(centerX - rInner, centerY - rInner, centerX + rInner, centerY + rInner);
                slicePath.arcTo(outerRect, startAngle, sweep);
                slicePath.arcTo(innerRect, startAngle + sweep, -sweep);
                slicePath.close();
                canvas.drawPath(slicePath, slicePaint);
                canvas.drawPath(slicePath, strokePaint);

                // Draw icon or text at midpoint
                float midAngle = (float) Math.toRadians(startAngle + sweep / 2f);
                float midRadius = (rInner + rOuter) / 2f;
                float ix = centerX + (float) Math.cos(midAngle) * midRadius;
                float iy = centerY + (float) Math.sin(midAngle) * midRadius;

                PieItem item = (items != null && slot < items.size()) ? items.get(slot) : null;
                if (item != null) {
                    Drawable icon = loadIcon(item);
                    if (icon != null) {
                        int iconSize = (int) (iconSizeDp * density);
                        int half = iconSize / 2;
                        icon.setBounds((int) ix - half, (int) iy - half,
                                       (int) ix + half, (int) iy + half);
                        icon.draw(canvas);
                    } else {
                        canvas.drawText(item.name != null ? item.name : "?", ix, iy + 4 * density, textPaint);
                    }
                }
            }
        }

        canvas.restore();
    }

    private Drawable loadIcon(PieItem item) {
        try {
            PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationIcon(item.packageName);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getRawX();
        float ty = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                int[] hit = hitTest(tx, ty);
                int newRing = hit != null ? hit[0] : -1;
                int newSlot = hit != null ? hit[1] : -1;
                if (newRing != highlightRing || newSlot != highlightSlot) {
                    highlightRing = newRing;
                    highlightSlot = newSlot;
                    if (newRing >= 0) {
                        if (vibeTickAmplitude > 0)
                            vibrator.vibrate(VibrationEffect.createOneShot(vibeTickDuration, vibeTickAmplitude));
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (highlightRing >= 0 && highlightSlot >= 0) {
                    List<PieItem> items = highlightRing < itemsByLevel.size()
                            ? itemsByLevel.get(highlightRing) : null;
                    if (items != null && highlightSlot < items.size()) {
                        PieItem selected = items.get(highlightSlot);
                        if (vibeSelectAmplitude > 0)
                            vibrator.vibrate(VibrationEffect.createOneShot(vibeSelectDuration, vibeSelectAmplitude));
                        if (listener != null) listener.onItemSelected(selected);
                    } else {
                        if (listener != null) listener.onDismiss();
                    }
                } else {
                    if (listener != null) listener.onDismiss();
                }
                highlightRing = -1;
                highlightSlot = -1;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                highlightRing = -1;
                highlightSlot = -1;
                invalidate();
                if (listener != null) listener.onDismiss();
                return true;
        }
        return false;
    }

    private int[] hitTest(float tx, float ty) {
        float dx = tx - centerX;
        float dy = ty - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180

        float ringWidth = ringWidthDp * density;
        float innerRadius = innerRadiusDp * density;

        // Check which ring
        for (int ring = 0; ring < ringCount; ring++) {
            float rInner = innerRadius + ring * ringWidth;
            float rOuter = rInner + ringWidth;
            if (dist >= rInner && dist < rOuter) {
                int slots = slotsPerRing[ring];
                if (slots == 0) break;
                float gapTotal = gapDegrees * slots;
                float sweep = (totalAngle - gapTotal) / slots;
                float startBase = -totalAngle / 2f;

                for (int slot = 0; slot < slots; slot++) {
                    float startAngle = startBase + slot * (sweep + gapDegrees);
                    float endAngle = startAngle + sweep;
                    if (angle >= startAngle && angle < endAngle) {
                        return new int[]{ring, slot};
                    }
                }
                break;
            }
        }
        return null;
    }
}
