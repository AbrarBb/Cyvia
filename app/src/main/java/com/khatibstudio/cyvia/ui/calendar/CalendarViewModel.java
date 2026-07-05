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
        List<CycleEntry> cycles = allCycles.getValue();
        final List<CycleEntry> cycleList = (cycles == null) ? new ArrayList<>() : cycles;
        final YearMonth targetMonth = displayedMonth;

        CyviaDatabase.databaseWriteExecutor.execute(() -> {
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
