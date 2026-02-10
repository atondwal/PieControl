package com.example.piecontrol.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PieItemDao {
    @Query("SELECT * FROM pie_items ORDER BY level, position")
    List<PieItem> getAllItems();

    @Query("SELECT * FROM pie_items WHERE level = :level ORDER BY position")
    List<PieItem> getItemsByLevel(int level);

    @Insert
    long insert(PieItem item);

    @Update
    void update(PieItem item);

    @Delete
    void delete(PieItem item);

    @Query("DELETE FROM pie_items WHERE id = :id")
    void deleteById(int id);

    @Query("UPDATE pie_items SET position = position - 1 WHERE level = :level AND position > :pos")
    void compactPositions(int level, int pos);

    @Query("UPDATE pie_items SET level = level - 1 WHERE level > :removedLevel")
    void compactLevels(int removedLevel);
}
