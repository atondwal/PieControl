package com.example.piecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.piecontrol.data.AppDatabase;
import com.example.piecontrol.data.PieItem;
import com.example.piecontrol.data.PieItemDao;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_APP_PICK = 1;
    private static final int REQ_OVERLAY = 2;
    private static final int REQ_APP_REPLACE = 3;

    private PieItemDao dao;
    private RecyclerView recyclerView;
    private SectionedAdapter adapter;
    private TextView breadcrumb;

    // For per-level add: which level the user tapped "+" on
    private int pendingAddLevel = -1;
    // For replace: which PieItem id is being replaced
    private int pendingReplaceItemId = -1;

    // Folder navigation
    private int currentParentId = 0;
    private final List<Integer> parentStack = new ArrayList<>();
    private final List<String> parentNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dao = AppDatabase.getInstance(this).pieItemDao();

        recyclerView = findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        breadcrumb = findViewById(R.id.breadcrumb);
        breadcrumb.setOnClickListener(v -> navigateBack());

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Add Level button
        Button addLevelBtn = findViewById(R.id.btn_add_level);
        addLevelBtn.setOnClickListener(v -> {
            int newLevel = getLevelCount();
            showAddChoiceDialog(newLevel);
        });

        loadItems();
        checkOverlayPermission();
    }

    /** Returns the number of levels within currentParentId (0 if no items). */
    private int getLevelCount() {
        List<PieItem> all = dao.getAllItemsByParent(currentParentId);
        int maxLevel = -1;
        for (PieItem item : all) {
            if (item.level > maxLevel) maxLevel = item.level;
        }
        return maxLevel + 1;
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            startOverlayService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_APP_PICK && resultCode == RESULT_OK && data != null) {
            addAppToLevel(data, pendingAddLevel);
        } else if (requestCode == REQ_APP_REPLACE && resultCode == RESULT_OK && data != null) {
            replaceApp(data);
        }
    }

    private void showAddChoiceDialog(int level) {
        new AlertDialog.Builder(this)
                .setTitle("Add to Level " + (level + 1))
                .setItems(new String[]{getString(R.string.add_app), getString(R.string.add_folder)}, (d, which) -> {
                    if (which == 0) {
                        pendingAddLevel = level;
                        Intent i = new Intent(this, AppPickerActivity.class);
                        startActivityForResult(i, REQ_APP_PICK);
                    } else {
                        showAddFolderDialog(level);
                    }
                })
                .show();
    }

    private void showAddFolderDialog(int level) {
        EditText input = new EditText(this);
        input.setHint(R.string.folder_name_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_folder)
                .setView(input)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<PieItem> existing = dao.getItemsByLevelAndParent(level, currentParentId);
                    PieItem item = new PieItem();
                    item.level = level;
                    item.position = existing.size();
                    item.name = name;
                    item.isFolder = true;
                    item.parentId = currentParentId;
                    dao.insert(item);
                    loadItems();
                    notifyServiceReload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addAppToLevel(Intent data, int level) {
        if (level < 0) return;
        String name = data.getStringExtra(AppPickerActivity.EXTRA_APP_NAME);
        String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME);
        String act = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_NAME);

        List<PieItem> existing = dao.getItemsByLevelAndParent(level, currentParentId);
        PieItem item = new PieItem();
        item.level = level;
        item.position = existing.size();
        item.name = name;
        item.packageName = pkg;
        item.activityName = act;
        item.parentId = currentParentId;
        dao.insert(item);
        loadItems();
        notifyServiceReload();
    }

    private void replaceApp(Intent data) {
        if (pendingReplaceItemId < 0) return;
        String name = data.getStringExtra(AppPickerActivity.EXTRA_APP_NAME);
        String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME);
        String act = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_NAME);

        List<PieItem> all = dao.getAllItems();
        for (PieItem item : all) {
            if (item.id == pendingReplaceItemId) {
                item.name = name;
                item.packageName = pkg;
                item.activityName = act;
                dao.update(item);
                break;
            }
        }
        pendingReplaceItemId = -1;
        loadItems();
        notifyServiceReload();
    }

    private void loadItems() {
        int levelCount = getLevelCount();
        List<Object> rows = new ArrayList<>();
        for (int level = 0; level < levelCount; level++) {
            List<PieItem> levelItems = dao.getItemsByLevelAndParent(level, currentParentId);
            rows.add(new LevelHeader(level, levelItems.size()));
            rows.addAll(levelItems);
        }
        if (adapter == null) {
            adapter = new SectionedAdapter(rows, getPackageManager(), adapterListener);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateRows(rows);
        }
        updateBreadcrumb();
    }

    private final SectionedAdapter.Listener adapterListener = new SectionedAdapter.Listener() {
        @Override public void onAddRequested(int level) { showAddChoiceDialog(level); }
        @Override public void onMoveUp(PieItem item) { MainActivity.this.moveUp(item); }
        @Override public void onMoveDown(PieItem item) { MainActivity.this.moveDown(item); }
        @Override public void onItemClicked(PieItem item) {
            if (item.isFolder) navigateIntoFolder(item);
            else showEditDialog(item);
        }
        @Override public void onItemLongClicked(PieItem item) { showEditDialog(item); }
    };

    private void updateBreadcrumb() {
        if (currentParentId == 0) {
            breadcrumb.setVisibility(View.GONE);
        } else {
            breadcrumb.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder("< Root");
            for (String name : parentNames) {
                sb.append(" > ").append(name);
            }
            breadcrumb.setText(sb.toString());
        }
    }

    private void navigateIntoFolder(PieItem folder) {
        parentStack.add(currentParentId);
        parentNames.add(folder.name);
        currentParentId = folder.id;
        adapter = null;
        loadItems();
    }

    private void navigateBack() {
        if (parentStack.isEmpty()) return;
        currentParentId = parentStack.remove(parentStack.size() - 1);
        parentNames.remove(parentNames.size() - 1);
        adapter = null;
        loadItems();
    }

    @Override
    public void onBackPressed() {
        if (!parentStack.isEmpty()) {
            navigateBack();
        } else {
            super.onBackPressed();
        }
    }

    private void deleteItemAndCompact(PieItem item) {
        int level = item.level;
        int pos = item.position;
        int parent = item.parentId;

        // If it's a folder, delete all children first
        if (item.isFolder) {
            deleteFolderContentsRecursive(item.id);
        }

        dao.deleteById(item.id);
        dao.compactPositions(level, pos, parent);

        // If level is now empty, compact levels above it down
        if (dao.getItemsByLevelAndParent(level, parent).isEmpty()) {
            dao.compactLevels(level, parent);
        }
    }

    private void deleteFolderContentsRecursive(int folderId) {
        List<PieItem> children = dao.getAllItemsByParent(folderId);
        for (PieItem child : children) {
            if (child.isFolder) {
                deleteFolderContentsRecursive(child.id);
            }
        }
        dao.deleteByParentId(folderId);
    }

    private void swapPositions(PieItem a, PieItem b) {
        int tmpPos = a.position;
        a.position = b.position;
        b.position = tmpPos;
        dao.update(a);
        dao.update(b);
        loadItems();
        notifyServiceReload();
    }

    private void moveUp(PieItem item) {
        if (item.position <= 0) return;
        List<PieItem> siblings = dao.getItemsByLevelAndParent(item.level, item.parentId);
        for (PieItem sib : siblings) {
            if (sib.position == item.position - 1) {
                swapPositions(item, sib);
                return;
            }
        }
    }

    private void moveDown(PieItem item) {
        List<PieItem> siblings = dao.getItemsByLevelAndParent(item.level, item.parentId);
        for (PieItem sib : siblings) {
            if (sib.position == item.position + 1) {
                swapPositions(item, sib);
                return;
            }
        }
    }

    private void showEditDialog(PieItem item) {
        List<PieItem> siblings = dao.getItemsByLevelAndParent(item.level, item.parentId);
        boolean canUp = item.position > 0;
        boolean canDown = item.position < siblings.size() - 1;

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (canUp) {
            options.add(getString(R.string.move_up));
            actions.add(() -> moveUp(item));
        }
        if (canDown) {
            options.add(getString(R.string.move_down));
            actions.add(() -> moveDown(item));
        }

        options.add(getString(R.string.move_to_level));
        actions.add(() -> showMoveLevelDialog(item));

        if (item.isFolder) {
            options.add(getString(R.string.rename));
            actions.add(() -> showRenameFolderDialog(item));
        } else {
            options.add(getString(R.string.replace_app));
            actions.add(() -> {
                pendingReplaceItemId = item.id;
                Intent i = new Intent(this, AppPickerActivity.class);
                startActivityForResult(i, REQ_APP_REPLACE);
            });
        }

        options.add(getString(R.string.delete));
        actions.add(() -> showDeleteConfirmation(item));

        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(options.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void showRenameFolderDialog(PieItem item) {
        EditText input = new EditText(this);
        input.setText(item.name);
        input.selectAll();
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_folder)
                .setView(input)
                .setPositiveButton(R.string.rename, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.name = name;
                    dao.update(item);
                    loadItems();
                    notifyServiceReload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMoveLevelDialog(PieItem item) {
        int levelCount = getLevelCount();
        List<String> labels = new ArrayList<>();
        List<Integer> levelIndices = new ArrayList<>();

        for (int i = 0; i < levelCount; i++) {
            if (i != item.level) {
                int count = dao.getItemsByLevelAndParent(i, currentParentId).size();
                labels.add("Level " + (i + 1) + " (" + count + " items)");
                levelIndices.add(i);
            }
        }
        // Offer creating a new level
        labels.add("New Level " + (levelCount + 1));
        levelIndices.add(levelCount);

        new AlertDialog.Builder(this)
                .setTitle("Move to which level?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    int oldLevel = item.level;
                    int oldPos = item.position;
                    int newLevel = levelIndices.get(which);
                    int parentId = item.parentId;

                    // Remove from old level and compact positions
                    dao.deleteById(item.id);
                    dao.compactPositions(oldLevel, oldPos, parentId);

                    // If old level is now empty, compact levels
                    boolean oldLevelEmpty = dao.getItemsByLevelAndParent(oldLevel, parentId).isEmpty();
                    if (oldLevelEmpty) {
                        dao.compactLevels(oldLevel, parentId);
                        if (newLevel > oldLevel) newLevel--;
                    }

                    // Add to new level at end
                    List<PieItem> newLevelItems = dao.getItemsByLevelAndParent(newLevel, parentId);
                    PieItem moved = new PieItem();
                    moved.level = newLevel;
                    moved.position = newLevelItems.size();
                    moved.name = item.name;
                    moved.packageName = item.packageName;
                    moved.activityName = item.activityName;
                    moved.parentId = item.parentId;
                    moved.isFolder = item.isFolder;
                    dao.insert(moved);

                    // If it was a folder, update children's parentId to the new id
                    if (item.isFolder) {
                        // Children reference the old id; we need to update them to the new item's id
                        // Since we just inserted, get the new id
                        List<PieItem> allItems = dao.getAllItems();
                        int newId = -1;
                        for (PieItem pi : allItems) {
                            if (pi.id > newId) newId = pi.id;
                        }
                        List<PieItem> children = dao.getAllItemsByParent(item.id);
                        for (PieItem child : children) {
                            child.parentId = newId;
                            dao.update(child);
                        }
                    }

                    loadItems();
                    notifyServiceReload();
                })
                .show();
    }

    private void showDeleteConfirmation(PieItem item) {
        String message = item.isFolder
                ? "Delete folder \"" + item.name + "\" and all its contents?"
                : "Remove " + item.name + "?";
        String title = item.isFolder ? getString(R.string.delete_folder) : "Remove " + item.name + "?";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(item.isFolder ? message : null)
                .setPositiveButton(R.string.remove, (d, w) -> {
                    deleteItemAndCompact(item);
                    loadItems();
                    notifyServiceReload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, PieOverlayService.class);
        startForegroundService(intent);
    }

    private void notifyServiceReload() {
        Intent intent = new Intent(this, PieOverlayService.class);
        intent.setAction(PieOverlayService.ACTION_RELOAD);
        startForegroundService(intent);
    }

}
