package com.example.piecontrol;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.example.piecontrol.data.PieItem;

public class AppLauncher {
    public static void launch(Context context, PieItem item) {
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
                context.startActivity(intent);
            } catch (Exception ignored) {}
        }
    }
}
