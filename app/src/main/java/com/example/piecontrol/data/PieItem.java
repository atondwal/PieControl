package com.example.piecontrol.data;

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
}
