package com.example.piecontrol.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pie_items")
public class PieItem {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int level;
    public int position;
    public String name;
    public String packageName;
    public String activityName;
    // When set, launching opens this link instead of the app
    public String uri;

    @ColumnInfo(defaultValue = "0")
    public int parentId;

    @ColumnInfo(defaultValue = "0")
    public boolean isFolder;
}
