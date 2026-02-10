package com.example.piecontrol;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppPickerActivity extends Activity {
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_ACTIVITY_NAME = "activity_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        RecyclerView rv = findViewById(R.id.app_list);
        rv.setLayoutManager(new LinearLayoutManager(this));

        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        List<ResolveInfo> sorted = new ArrayList<>(apps);
        Collections.sort(sorted, (a, b) -> {
            String na = a.loadLabel(pm).toString();
            String nb = b.loadLabel(pm).toString();
            return na.compareToIgnoreCase(nb);
        });

        rv.setAdapter(new AppAdapter(sorted, pm));
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        private final List<ResolveInfo> apps;
        private final PackageManager pm;

        AppAdapter(List<ResolveInfo> apps, PackageManager pm) {
            this.apps = apps;
            this.pm = pm;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ResolveInfo info = apps.get(position);
            holder.name.setText(info.loadLabel(pm));
            holder.icon.setImageDrawable(info.loadIcon(pm));
            holder.itemView.setOnClickListener(v -> {
                ActivityInfo ai = info.activityInfo;
                Intent result = new Intent();
                result.putExtra(EXTRA_APP_NAME, info.loadLabel(pm).toString());
                result.putExtra(EXTRA_PACKAGE_NAME, ai.packageName);
                result.putExtra(EXTRA_ACTIVITY_NAME, ai.name);
                setResult(RESULT_OK, result);
                finish();
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.app_icon);
                name = v.findViewById(R.id.app_name);
            }
        }
    }
}
