package com.example.piecontrol;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

public class TriggerZoneView extends View {
    private OnTriggerListener listener;

    public interface OnTriggerListener {
        void onTrigger(float y);
    }

    public TriggerZoneView(Context context) {
        super(context);
    }

    public void setOnTriggerListener(OnTriggerListener l) {
        this.listener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (listener != null) {
                listener.onTrigger(event.getRawY());
            }
            return true;
        }
        return false;
    }
}
