package com.example.piecontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;

import com.example.piecontrol.data.PieItem;

public class PieOverlayService extends Service {
    private static final String CHANNEL_ID = "pie_overlay";
    public static final String ACTION_RELOAD = "com.example.piecontrol.RELOAD";

    private WindowManager windowManager;
    private TriggerZoneView triggerView;
    private PieView pieView;
    private boolean pieShowing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(1, buildNotification());
        addTriggerZone();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RELOAD.equals(intent.getAction())) {
            if (pieView != null) {
                pieView.reload();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removePie();
        if (triggerView != null) {
            try { windowManager.removeView(triggerView); } catch (Exception ignored) {}
            triggerView = null;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Pie Control Overlay", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Pie Control")
                .setContentText("Swipe from left edge to open")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .build();
    }

    private void addTriggerZone() {
        triggerView = new TriggerZoneView(this);
        triggerView.setOnTriggerListener(this::showPie);

        int triggerWidth = (int) (20 * getResources().getDisplayMetrics().density);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                triggerWidth,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.x = 0;
        params.y = 0;

        windowManager.addView(triggerView, params);
    }

    private void showPie(float touchY) {
        if (pieShowing) return;

        pieView = new PieView(this);
        pieView.setCenterY(touchY);
        pieView.setOnItemSelectedListener(new PieView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(PieItem item) {
                AppLauncher.launch(PieOverlayService.this, item);
                removePie();
            }

            @Override
            public void onDismiss() {
                removePie();
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;

        windowManager.addView(pieView, params);
        pieShowing = true;
    }

    private void removePie() {
        if (pieView != null && pieShowing) {
            try { windowManager.removeView(pieView); } catch (Exception ignored) {}
            pieView = null;
            pieShowing = false;
        }
    }
}
