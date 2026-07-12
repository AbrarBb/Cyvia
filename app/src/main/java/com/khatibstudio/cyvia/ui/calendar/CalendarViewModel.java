package com.khatibstudio.cyvia.ui.calendar;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.model.CyclePrediction;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.LogRepository;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.domain.PredictionEngine;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ViewModel for the Calendar screen.
 * Manages the displayed month and exposes day-category data for the grid.
 */
public class CalendarViewModel extends AndroidViewModel {

    private final CycleRepository cycleRepository;
    private final LogRepository logRepository;
    private final SettingsRepository settings;
    private final PredictionEngine predictionEngine;

    private final LiveData<List<CycleEntry>> allCycles;

    // Currently displayed month
    private YearMonth displayedMonth = YearMonth.now();

    // Emits CalendarData whenever month or cycle data changes
    private final MediatorLiveData<CalendarPageData> calendarData = new MediatorLiveData<>();

    public CalendarViewModel(Application application) {
        super(application);
        CyviaApplication app = CyviaApplication.from(application);
        cycleRepository = app.getCycleRepository();
        logRepository = app.getLogRepository();
        settings = app.getSettingsRepository();
        predictionEngine = new PredictionEngine(settings);

        allCycles = cycleRepository.getAllCycles();
        LiveData<List<DailyLog>> allLogs = logRepository.getAllLogs();
        calendarData.addSource(allCycles, cycles -> rebuildCalendarData());
        calendarData.addSource(allLogs, logs -> rebuildCalendarData());
    }

    public YearMonth getDisplayedMonth() { return displayedMonth; }

    public LiveData<CalendarPageData> getCalendarData() { return calendarData; }

    public void goToPreviousMonth() {
        displayedMonth = displayedMonth.minusMonths(1);
        rebuildCalendarData();
    }

    public void goToNextMonth() {
        displayedMonth = displayedMonth.plusMonths(1);
        rebuildCalendarData();
    }

    private void rebuildCalendarData() {
        final YearMonth targetMonth = displayedMonth;

        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            com.khatibstudio.cyvia.data.db.dao.CycleEntryDao dao = CyviaDatabase.getDatabase(getApplication()).cycleEntryDao();
            sanitizeAndMergeCycles(dao);

            List<CycleEntry> cycleList = dao.getAllCyclesSync();
            if (cycleList == null) {
                cycleList = new ArrayList<>();
            }

            CyclePrediction prediction = predictionEngine.predict(cycleList);
            LocalDate monthStart = targetMonth.atDay(1);
            LocalDate monthEnd = targetMonth.atEndOfMonth();

            PredictionEngine.CalendarData calData =
                    predictionEngine.buildCalendarData(prediction, cycleList, monthStart, monthEnd);

            List<Long> loggedEpochDays = logRepository.getLoggedDates();
            Set<LocalDate> loggedDates = new HashSet<>();
            for (Long epochDay : loggedEpochDays) {
                loggedDates.add(LocalDate.ofEpochDay(epochDay));
            }

            calendarData.postValue(new CalendarPageData(
                    targetMonth, calData, loggedDates, prediction));
        });
    }

    private void sanitizeAndMergeCycles(com.khatibstudio.cyvia.data.db.dao.CycleEntryDao dao) {
        List<CycleEntry> allCycles = dao.getAllCyclesSync();
        if (allCycles == null || allCycles.size() < 2) return;

        // Sort cycles chronologically by startDate
        allCycles.sort((c1, c2) -> Long.compare(c1.startDate, c2.startDate));

        List<CycleEntry> toDelete = new ArrayList<>();
        List<CycleEntry> toUpdate = new ArrayList<>();

        CycleEntry current = allCycles.get(0);

        for (int i = 1; i < allCycles.size(); i++) {
            CycleEntry next = allCycles.get(i);

            long currentStart = current.startDate;
            long currentEnd = current.isOngoing() ? LocalDate.now().toEpochDay() : current.endDate;

            long nextStart = next.startDate;
            long nextEnd = next.isOngoing() ? LocalDate.now().toEpochDay() : next.endDate;

            // Check if they overlap or are adjacent (distance <= 1 day)
            if (nextStart <= currentEnd + 1) {
                // Merge next into current
                current.startDate = Math.min(currentStart, nextStart);
                if (current.isOngoing() || next.isOngoing()) {
                    current.endDate = -1L; // remains ongoing
                } else {
                    current.endDate = Math.max(currentEnd, nextEnd);
                }

                if (next.flowIntensity != null && (current.flowIntensity == null || next.flowIntensity.ordinal() > current.flowIntensity.ordinal())) {
                    current.flowIntensity = next.flowIntensity;
                }

                toDelete.add(next);
                if (!toUpdate.contains(current)) {
                    toUpdate.add(current);
                }
            } else {
                current = next;
            }
        }

        if (toUpdate.isEmpty() && toDelete.isEmpty()) return;

        // Apply changes to database
        for (CycleEntry cycle : toUpdate) {
            dao.updateCycleEntry(cycle);
        }
        for (CycleEntry cycle : toDelete) {
            dao.deleteCycleEntry(cycle);
        }
    }

    // ─── Calendar page data ───────────────────────────────────────────────

    public static class CalendarPageData {
        public final YearMonth month;
        public final PredictionEngine.CalendarData calData;
        public final Set<LocalDate> loggedDates;
        public final CyclePrediction prediction;

        public CalendarPageData(YearMonth month, PredictionEngine.CalendarData calData,
                                Set<LocalDate> loggedDates, CyclePrediction prediction) {
            this.month = month;
            this.calData = calData;
            this.loggedDates = loggedDates;
            this.prediction = prediction;
        }
    }
}
