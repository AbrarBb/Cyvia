package com.khatibstudio.cyvia.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.db.dao.DailyLogDao;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for daily log data.
 */
public class LogRepository {

    private final DailyLogDao dao;

    public LogRepository(Application application) {
        dao = CyviaDatabase.getDatabase(application).dailyLogDao();
    }

    // ─── LiveData queries ────────────────────────────────────────────────

    public LiveData<DailyLog> getLogForDate(LocalDate date) {
        return dao.getLogForDate(date.toEpochDay());
    }

    public LiveData<List<DailyLog>> getAllLogs() {
        return dao.getAllLogs();
    }

    public LiveData<List<DailyLog>> getWeightEntries() {
        return dao.getWeightEntries();
    }

    // ─── Synchronous queries ─────────────────────────────────────────────

    public DailyLog getLogForDateSync(LocalDate date) {
        return dao.getLogForDateSync(date.toEpochDay());
    }

    public List<DailyLog> getAllLogsSync() {
        return dao.getAllLogsSync();
    }

    public List<DailyLog> getLogsInRange(LocalDate from, LocalDate to) {
        return dao.getLogsInRange(from.toEpochDay(), to.toEpochDay());
    }

    public List<Long> getLoggedDates() {
        return dao.getLoggedDates();
    }

    public List<DailyLogDao.MoodCount> getMoodFrequency() {
        return dao.getMoodFrequency();
    }

    public int getSymptomCount(int symptomId) {
        return dao.getSymptomCount(symptomId);
    }

    // ─── Write operations ────────────────────────────────────────────────

    public void saveLog(DailyLog log) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> dao.insertOrReplaceDailyLog(log));
    }

    public void deleteLog(DailyLog log) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> dao.deleteDailyLog(log));
    }

    public void deleteAll() {
        CyviaDatabase.databaseWriteExecutor.execute(dao::deleteAll);
    }
}
