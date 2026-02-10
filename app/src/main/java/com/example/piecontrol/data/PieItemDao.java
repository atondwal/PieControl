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

    @Query("SELECT * FROM pie_items WHERE level = :level AND parentId = :parentId ORDER BY position")
    List<PieItem> getItemsByLevelAndParent(int level, int parentId);

    @Query("SELECT * FROM pie_items WHERE parentId = :parentId ORDER BY level, position")
    List<PieItem> getAllItemsByParent(int parentId);

    @Query("DELETE FROM pie_items WHERE parentId = :parentId")
    void deleteByParentId(int parentId);

    @Insert
    long insert(PieItem item);

    @Update
    void update(PieItem item);

    @Delete
    void delete(PieItem item);

    @Query("DELETE FROM pie_items WHERE id = :id")
    void deleteById(int id);

    @Query("UPDATE pie_items SET position = position - 1 WHERE level = :level AND position > :pos AND parentId = :parentId")
    void compactPositions(int level, int pos, int parentId);

    @Query("UPDATE pie_items SET level = level - 1 WHERE level > :removedLevel AND parentId = :parentId")
    void compactLevels(int removedLevel, int parentId);
}
