package com.khatibstudio.cyvia.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.db.dao.CycleEntryDao;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.model.FlowIntensity;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for cycle entry data.
 * All write operations are dispatched to {@link CyviaDatabase#databaseWriteExecutor}.
 */
public class CycleRepository {

    private final CycleEntryDao dao;

    public CycleRepository(Application application) {
        dao = CyviaDatabase.getDatabase(application).cycleEntryDao();
    }

    // ─── LiveData queries (observed by ViewModels) ────────────────────────

    public LiveData<List<CycleEntry>> getAllCycles() {
        return dao.getAllCycles();
    }

    public LiveData<CycleEntry> getOngoingCycle() {
        return dao.getOngoingCycle();
    }

    // ─── Synchronous queries (called from background thread) ─────────────

    public List<CycleEntry> getAllCyclesSync() {
        return dao.getAllCyclesSync();
    }

    public CycleEntry getOngoingCycleSync() {
        return dao.getOngoingCycleSync();
    }

    public List<CycleEntry> getRecentNonExcludedCycles(int limit) {
        return dao.getRecentNonExcludedCycles(limit);
    }

    public List<CycleEntry> getCyclesInRange(LocalDate from, LocalDate to) {
        return dao.getCyclesInRange(from.toEpochDay(), to.toEpochDay());
    }

    // ─── Write operations ────────────────────────────────────────────────

    /**
     * Starts a new period: creates a CycleEntry with today as startDate.
     * If a cycle is already ongoing, it ends it first.
     */
    public void startPeriod(LocalDate date, FlowIntensity intensity) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            // End any currently ongoing period first
            CycleEntry ongoing = dao.getOngoingCycleSync();
            if (ongoing != null) {
                ongoing.endDate = date.toEpochDay() - 1;
                dao.updateCycleEntry(ongoing);
            }
            // Start the new cycle
            CycleEntry newCycle = new CycleEntry(date.toEpochDay(), intensity);
            dao.insertCycleEntry(newCycle);
        });
    }

    /** Ends the currently ongoing period with today as the end date. */
    public void endPeriod(LocalDate endDate) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            CycleEntry ongoing = dao.getOngoingCycleSync();
            if (ongoing != null) {
                ongoing.endDate = endDate.toEpochDay();
                dao.updateCycleEntry(ongoing);
            }
        });
    }

    public void insertCycle(CycleEntry entry) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> dao.insertCycleEntry(entry));
    }

    public void updateCycle(CycleEntry entry) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> dao.updateCycleEntry(entry));
    }

    public void deleteCycle(CycleEntry entry) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> dao.deleteCycleEntry(entry));
    }

    public void deleteAll() {
        CyviaDatabase.databaseWriteExecutor.execute(dao::deleteAll);
    }
}
