package com.example.piecontrol;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;

import com.example.piecontrol.data.PieItem;

public class AppLauncher {
    // Fraction of each screen dimension a floating window covers
    private static final float FLOAT_SCREEN_FRACTION = 0.6f;

    public static void launch(Context context, PieItem item) {
        start(context, item, null);
    }

    /** Launches in a freeform (pop-up) window centered on screen. */
    public static void launchFloating(Context context, PieItem item) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int marginX = (int) (screen.widthPixels * (1 - FLOAT_SCREEN_FRACTION) / 2);
        int marginY = (int) (screen.heightPixels * (1 - FLOAT_SCREEN_FRACTION) / 2);
        Rect bounds = new Rect(marginX, marginY,
                screen.widthPixels - marginX, screen.heightPixels - marginY);
        start(context, item, ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle());
    }

    private static void start(Context context, PieItem item, Bundle options) {
        Intent intent = null;
        if (item.activityName != null && !item.activityName.isEmpty()) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(item.packageName, item.activityName));
        }
        if (intent == null) {
            PackageManager pm = context.getPackageManager();
            intent = pm.getLaunchIntentForPackage(item.packageName);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent, options);
            } catch (Exception ignored) {}
        }
    }
}
