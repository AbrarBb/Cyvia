package com.khatibstudio.cyvia.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.khatibstudio.cyvia.data.db.entity.DailyLog;

import java.util.List;

/**
 * DAO for daily log CRUD operations.
 */
@Dao
public interface DailyLogDao {

    /** Insert or replace a log for a given date. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplaceDailyLog(DailyLog log);

    @Update
    void updateDailyLog(DailyLog log);

    @Delete
    void deleteDailyLog(DailyLog log);

    /** Get log for a specific date (epoch-day). Returns null if no log exists. */
    @Query("SELECT * FROM daily_logs WHERE date = :epochDay LIMIT 1")
    LiveData<DailyLog> getLogForDate(long epochDay);

    /** Synchronous version for background use. */
    @Query("SELECT * FROM daily_logs WHERE date = :epochDay LIMIT 1")
    DailyLog getLogForDateSync(long epochDay);

    /** Returns all logs ordered by date descending. Used for Insights charts. */
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    LiveData<List<DailyLog>> getAllLogs();

    /** Synchronous version — used for backup/restore and stats. */
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    List<DailyLog> getAllLogsSync();

    /** Returns logs within a date range (epoch-days). Used by CalendarFragment. */
    @Query("SELECT * FROM daily_logs WHERE date >= :fromEpochDay AND date <= :toEpochDay ORDER BY date ASC")
    List<DailyLog> getLogsInRange(long fromEpochDay, long toEpochDay);

    /**
     * Returns all dates that have a non-null mood or any logged symptoms.
     * Used for showing the dot indicator on calendar days.
     */
    @Query("SELECT date FROM daily_logs WHERE mood IS NOT NULL OR (symptom_ids IS NOT NULL AND symptom_ids != '') OR (notes IS NOT NULL AND notes != '') OR temperature IS NOT NULL OR intimacy = 1")
    List<Long> getLoggedDates();

    /** Distinct mood values for insights (mood frequency chart). */
    @Query("SELECT mood, COUNT(*) as count FROM daily_logs WHERE mood IS NOT NULL GROUP BY mood ORDER BY count DESC")
    List<MoodCount> getMoodFrequency();

    /** Count of logs mentioning a specific symptom ID in symptom_ids. */
    @Query("SELECT COUNT(*) FROM daily_logs WHERE symptom_ids LIKE '%' || :symptomId || '%'")
    int getSymptomCount(int symptomId);

    /** All weight entries ordered by date — continuous chart, not per-cycle. */
    @Query("SELECT * FROM daily_logs WHERE weight IS NOT NULL ORDER BY date ASC")
    LiveData<List<DailyLog>> getWeightEntries();

    /** Delete everything. */
    @Query("DELETE FROM daily_logs")
    void deleteAll();

    // ─── Sub-result class for mood frequency query ───────────────────────
    class MoodCount {
        public String mood;
        public int count;
    }
}
