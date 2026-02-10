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
import com.example.piecontrol.data.PieItemDao;

import java.util.ArrayList;
import java.util.List;

public class PieView extends View {
    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint folderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private boolean highlightCenter = false;

    private final Vibrator vibrator;
    private OnItemSelectedListener listener;

    // Folder navigation state
    private int currentParentId = 0;
    private final List<Integer> parentStack = new ArrayList<>();

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
        folderPaint.setColor(0xFFFFCC00);
        folderPaint.setStyle(Paint.Style.FILL);
        backPaint.setColor(Color.WHITE);
        backPaint.setStyle(Paint.Style.FILL);
        backPaint.setStrokeWidth(3 * density);
        backPaint.setStrokeCap(Paint.Cap.ROUND);
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
        PieItemDao dao = AppDatabase.getInstance(getContext()).pieItemDao();
        List<PieItem> all = dao.getAllItemsByParent(currentParentId);

        int maxLevel = -1;
        for (PieItem item : all) {
            if (item.level > maxLevel) maxLevel = item.level;
        }
        ringCount = Math.max(0, maxLevel + 1);

        itemsByLevel = new ArrayList<>();
        slotsPerRing = new int[ringCount];
        for (int i = 0; i < ringCount; i++) {
            List<PieItem> levelItems = dao.getItemsByLevelAndParent(i, currentParentId);
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

        // Draw back button in center area when in a sub-pie
        if (!parentStack.isEmpty()) {
            drawBackButton(canvas, innerRadius);
        }

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
                    if (item.isFolder) {
                        drawFolderIcon(canvas, ix, iy);
                        // Draw folder name below icon
                        if (item.name != null) {
                            textPaint.setTextSize(8 * density);
                            canvas.drawText(item.name, ix, iy + iconSizeDp * density * 0.45f, textPaint);
                            textPaint.setTextSize(10 * density);
                        }
                    } else {
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
        }

        canvas.restore();
    }

    private void drawFolderIcon(Canvas canvas, float cx, float cy) {
        float s = iconSizeDp * density * 0.35f;
        // Folder body
        RectF body = new RectF(cx - s, cy - s * 0.5f, cx + s, cy + s * 0.7f);
        folderPaint.setColor(0xFFFFCC00);
        canvas.drawRoundRect(body, 3 * density, 3 * density, folderPaint);
        // Folder tab
        RectF tab = new RectF(cx - s, cy - s * 0.8f, cx - s * 0.2f, cy - s * 0.4f);
        canvas.drawRoundRect(tab, 2 * density, 2 * density, folderPaint);
    }

    private void drawBackButton(Canvas canvas, float innerRadius) {
        // Draw a semi-circle background in the center
        float r = innerRadius * 0.8f;
        slicePaint.setColor(highlightCenter ? colorHighlight : 0xAA444444);
        RectF oval = new RectF(centerX - r, centerY - r, centerX + r, centerY + r);
        canvas.drawArc(oval, -totalAngle / 2f, totalAngle, true, slicePaint);
        canvas.drawArc(oval, -totalAngle / 2f, totalAngle, true, strokePaint);

        // Draw back arrow
        float arrowSize = 12 * density;
        float ax = centerX + r * 0.35f;
        float ay = centerY;
        backPaint.setStyle(Paint.Style.STROKE);
        Path arrow = new Path();
        arrow.moveTo(ax, ay);
        arrow.lineTo(ax - arrowSize, ay);
        arrow.moveTo(ax - arrowSize, ay);
        arrow.lineTo(ax - arrowSize * 0.5f, ay - arrowSize * 0.5f);
        arrow.moveTo(ax - arrowSize, ay);
        arrow.lineTo(ax - arrowSize * 0.5f, ay + arrowSize * 0.5f);
        canvas.drawPath(arrow, backPaint);
        backPaint.setStyle(Paint.Style.FILL);
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
                boolean newCenter = isInCenter(tx, ty);

                if (newRing != highlightRing || newSlot != highlightSlot || newCenter != highlightCenter) {
                    boolean changed = (newRing >= 0 && (newRing != highlightRing || newSlot != highlightSlot))
                                   || (newCenter && !highlightCenter);
                    highlightRing = newRing;
                    highlightSlot = newSlot;
                    highlightCenter = newCenter && newRing < 0;
                    if (changed && vibeTickAmplitude > 0) {
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
                        if (selected.isFolder) {
                            // Navigate into folder
                            if (vibeSelectAmplitude > 0)
                                vibrator.vibrate(VibrationEffect.createOneShot(vibeSelectDuration, vibeSelectAmplitude));
                            parentStack.add(currentParentId);
                            currentParentId = selected.id;
                            loadItems();
                            highlightRing = -1;
                            highlightSlot = -1;
                            highlightCenter = false;
                            invalidate();
                        } else {
                            if (vibeSelectAmplitude > 0)
                                vibrator.vibrate(VibrationEffect.createOneShot(vibeSelectDuration, vibeSelectAmplitude));
                            if (listener != null) listener.onItemSelected(selected);
                        }
                    } else {
                        if (listener != null) listener.onDismiss();
                    }
                } else if (highlightCenter && !parentStack.isEmpty()) {
                    // Go back to parent
                    if (vibeSelectAmplitude > 0)
                        vibrator.vibrate(VibrationEffect.createOneShot(vibeSelectDuration, vibeSelectAmplitude));
                    currentParentId = parentStack.remove(parentStack.size() - 1);
                    loadItems();
                    highlightRing = -1;
                    highlightSlot = -1;
                    highlightCenter = false;
                    invalidate();
                } else {
                    if (listener != null) listener.onDismiss();
                }
                highlightRing = -1;
                highlightSlot = -1;
                highlightCenter = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                highlightRing = -1;
                highlightSlot = -1;
                highlightCenter = false;
                invalidate();
                if (listener != null) listener.onDismiss();
                return true;
        }
        return false;
    }

    private boolean isInCenter(float tx, float ty) {
        if (parentStack.isEmpty()) return false;
        float dx = tx - centerX;
        float dy = ty - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float innerRadius = innerRadiusDp * density;
        if (dist >= innerRadius) return false;
        // Check angle is within arc span
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        return angle >= -totalAngle / 2f && angle <= totalAngle / 2f;
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
