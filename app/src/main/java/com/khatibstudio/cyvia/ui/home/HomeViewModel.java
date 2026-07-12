package com.khatibstudio.cyvia.ui.home;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.model.CyclePrediction;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.LogRepository;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.domain.PredictionEngine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * ViewModel for the Home screen.
 *
 * Exposes:
 *   - allCycles: LiveData<List<CycleEntry>> for observers
 *   - prediction: derived from allCycles via PredictionEngine
 *   - cycleDay: current day within the cycle
 *   - homeStatus: the headline status string type
 */
public class HomeViewModel extends AndroidViewModel {

    private final CycleRepository cycleRepository;
    private final LogRepository logRepository;
    private final SettingsRepository settings;
    private final PredictionEngine predictionEngine;

    private final LiveData<List<CycleEntry>> allCycles;
    private final LiveData<DailyLog> todayLog;
    private final LiveData<List<DailyLog>> allLogs;
    private final androidx.lifecycle.MutableLiveData<LocalDate> currentDate = new androidx.lifecycle.MutableLiveData<>(LocalDate.now());
    private final MediatorLiveData<CyclePrediction> prediction = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> cycleDay = new MediatorLiveData<>();

    public HomeViewModel(Application application) {
        super(application);
        CyviaApplication app = CyviaApplication.from(application);
        cycleRepository = app.getCycleRepository();
        logRepository = app.getLogRepository();
        settings = app.getSettingsRepository();
        predictionEngine = new PredictionEngine(settings);

        allCycles = cycleRepository.getAllCycles();
        todayLog = Transformations.switchMap(currentDate, date -> logRepository.getLogForDate(date));
        allLogs = logRepository.getAllLogs();

        // Compute prediction whenever cycle data changes
        prediction.addSource(allCycles, cycles -> {
            if (cycles != null) {
                prediction.setValue(predictionEngine.predict(cycles));
            }
        });

        // Compute current cycle day
        cycleDay.addSource(allCycles, cycles -> updateCycleDayValue());
        cycleDay.addSource(currentDate, date -> updateCycleDayValue());
    }

    private void updateCycleDayValue() {
        List<CycleEntry> cycles = allCycles.getValue();
        if (cycles == null || cycles.isEmpty()) {
            cycleDay.setValue(null);
            return;
        }
        CycleEntry mostRecent = cycles.get(0);
        LocalDate startDate = LocalDate.ofEpochDay(mostRecent.startDate);
        int day = (int) ChronoUnit.DAYS.between(startDate, currentDate.getValue()) + 1;

        // Clamp: if day exceeds the average cycle length, wrap it so the ring
        // never gets stuck showing a stale phase after the cycle boundary.
        int avgCycleLen = settings.getAvgCycleLength();
        CyclePrediction pred = prediction.getValue();
        if (pred != null && pred.averageCycleLength > 0) {
            avgCycleLen = pred.averageCycleLength;
        }
        if (avgCycleLen <= 0) avgCycleLen = 28;

        if (day > avgCycleLen) {
            // Wrap using modulo so it cycles back to day 1
            day = ((day - 1) % avgCycleLen) + 1;
        }
        if (day < 1) day = 1;

        cycleDay.setValue(day);
    }

    public void refresh() {
        currentDate.setValue(LocalDate.now());
    }

    public LiveData<List<CycleEntry>> getAllCycles() {
        return allCycles;
    }

    public LiveData<CyclePrediction> getPrediction() {
        return prediction;
    }

    public LiveData<Integer> getCycleDay() {
        return cycleDay;
    }

    public LiveData<DailyLog> getTodayLog() {
        return todayLog;
    }

    public LiveData<List<DailyLog>> getAllLogs() {
        return allLogs;
    }

    public String getUserName() {
        return settings.getUserName();
    }

    public boolean shouldShowFertileWindow() {
        return settings.shouldShowFertileWindow();
    }

    /** Returns the current phase name based on cycle day and average cycle length. */
    public String getCyclePhase(int cycleDayNum, int avgCycleLength) {
        List<CycleEntry> cycles = allCycles.getValue();
        if (cycles != null && !cycles.isEmpty()) {
            CycleEntry mostRecent = cycles.get(0);
            if (!mostRecent.isOngoing()) {
                long startEpoch = mostRecent.startDate;
                long targetEpoch = startEpoch + cycleDayNum - 1;
                if (targetEpoch <= mostRecent.endDate) {
                    return "MENSTRUAL";
                }
            }
        }

        int periodLen = settings.getAvgPeriodLength();
        if (periodLen <= 0 || periodLen >= avgCycleLength) periodLen = 5;

        int ovDay = Math.max(periodLen + 6, avgCycleLength - 14);
        int fertileStart = Math.max(periodLen + 1, ovDay - 2);
        int fertileEnd = Math.min(avgCycleLength, ovDay + 2);

        if (cycleDayNum <= periodLen) return "MENSTRUAL";
        if (cycleDayNum < fertileStart) return "FOLLICULAR";
        if (cycleDayNum <= fertileEnd) return "OVULATORY";
        return "LUTEAL";
    }
}
