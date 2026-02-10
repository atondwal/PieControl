package com.example.piecontrol;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

public class TriggerZoneView extends View {
    private OnTriggerListener listener;
    private View forwardTarget;

    public interface OnTriggerListener {
        void onTrigger(float y);
    }

    public TriggerZoneView(Context context) {
        super(context);
    }

    public void setOnTriggerListener(OnTriggerListener l) {
        this.listener = l;
    }

    public void setForwardTarget(View target) {
        this.forwardTarget = target;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (listener != null) {
                    listener.onTrigger(event.getRawY());
                }
                // Forward as ACTION_DOWN to pie view using raw coords
                forwardToPie(event);
                return true;

            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                forwardToPie(event);
                return true;
        }
        return false;
    }

    private void forwardToPie(MotionEvent event) {
        if (forwardTarget != null) {
            MotionEvent forwarded = MotionEvent.obtain(event);
            forwarded.setLocation(event.getRawX(), event.getRawY());
            forwardTarget.onTouchEvent(forwarded);
            forwarded.recycle();
        }
    }
}
