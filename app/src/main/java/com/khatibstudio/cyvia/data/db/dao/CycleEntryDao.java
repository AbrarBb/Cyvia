package com.khatibstudio.cyvia.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.khatibstudio.cyvia.data.db.entity.CycleEntry;

import java.util.List;

/**
 * DAO for cycle entry CRUD operations.
 * All LiveData queries automatically re-deliver new data when the table changes.
 */
@Dao
public interface CycleEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertCycleEntry(CycleEntry entry);

    @Update
    void updateCycleEntry(CycleEntry entry);

    @Delete
    void deleteCycleEntry(CycleEntry entry);

    /** Returns all cycles ordered newest-first. Used for predictions (take first N). */
    @Query("SELECT * FROM cycle_entries ORDER BY start_date DESC")
    LiveData<List<CycleEntry>> getAllCycles();

    /** Synchronous version for use inside background threads (backup, prediction). */
    @Query("SELECT * FROM cycle_entries ORDER BY start_date DESC")
    List<CycleEntry> getAllCyclesSync();

    /**
     * Returns the N most recent non-excluded cycles, ordered newest-first.
     * Used by PredictionEngine for the rolling average.
     */
    @Query("SELECT * FROM cycle_entries WHERE excluded = 0 ORDER BY start_date DESC LIMIT :limit")
    List<CycleEntry> getRecentNonExcludedCycles(int limit);

    /** Returns the currently ongoing cycle (endDate = -1), if any. */
    @Query("SELECT * FROM cycle_entries WHERE end_date = -1 ORDER BY start_date DESC LIMIT 1")
    LiveData<CycleEntry> getOngoingCycle();

    /** Synchronous version of getOngoingCycle. */
    @Query("SELECT * FROM cycle_entries WHERE end_date = -1 ORDER BY start_date DESC LIMIT 1")
    CycleEntry getOngoingCycleSync();

    /** Returns cycles whose start_date falls within a given range (for calendar display). */
    @Query("SELECT * FROM cycle_entries WHERE start_date >= :fromEpochDay AND start_date <= :toEpochDay ORDER BY start_date ASC")
    List<CycleEntry> getCyclesInRange(long fromEpochDay, long toEpochDay);

    /** Count of all cycles (used to determine onboarding state). */
    @Query("SELECT COUNT(*) FROM cycle_entries")
    int getCycleCount();

    /** Delete everything — used by "Delete all data" in Settings. */
    @Query("DELETE FROM cycle_entries")
    void deleteAll();
}
