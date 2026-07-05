package com.khatibstudio.cyvia.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.khatibstudio.cyvia.data.db.entity.SymptomTag;

import java.util.List;

/**
 * DAO for symptom tag management.
 * Includes both built-in and user-created custom symptoms.
 */
@Dao
public interface SymptomTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertSymptomTag(SymptomTag tag);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAllSymptomTags(List<SymptomTag> tags);

    @Update
    void updateSymptomTag(SymptomTag tag);

    @Delete
    void deleteSymptomTag(SymptomTag tag);

    /** All symptoms — built-ins first, then custom. */
    @Query("SELECT * FROM symptom_tags ORDER BY is_custom ASC, label ASC")
    LiveData<List<SymptomTag>> getAllSymptomTags();

    /** Synchronous version for background use (backup, log pre-population). */
    @Query("SELECT * FROM symptom_tags ORDER BY is_custom ASC, label ASC")
    List<SymptomTag> getAllSymptomTagsSync();

    /** Only user-created custom tags. */
    @Query("SELECT * FROM symptom_tags WHERE is_custom = 1 ORDER BY label ASC")
    LiveData<List<SymptomTag>> getCustomSymptomTags();

    /** Lookup by ID (for loading symptom chips from stored CSV). */
    @Query("SELECT * FROM symptom_tags WHERE id IN (:ids)")
    List<SymptomTag> getSymptomTagsByIds(List<Integer> ids);

    /** Delete only custom symptoms. Built-in symptoms are never deleted. */
    @Query("DELETE FROM symptom_tags WHERE is_custom = 1")
    void deleteAllCustomSymptoms();

    /** Delete all (called by "Delete all data"). */
    @Query("DELETE FROM symptom_tags")
    void deleteAll();

    /** Check if default symptoms have already been seeded. */
    @Query("SELECT COUNT(*) FROM symptom_tags WHERE is_custom = 0")
    int getDefaultSymptomCount();
}
