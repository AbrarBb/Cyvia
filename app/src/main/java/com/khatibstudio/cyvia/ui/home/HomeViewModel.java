package com.khatibstudio.cyvia.ui.home;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
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
    private final MutableLiveData<LocalDate> currentDate = new MutableLiveData<>(LocalDate.now());
    private final MediatorLiveData<CyclePrediction> prediction = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> cycleDay = new MediatorLiveData<>();
    /** True when the displayed cycleDay was produced by modulo-wrapping past the
     *  end of the last logged cycle — meaning we crossed into a new predicted cycle. */
    private boolean cycleDayIsWrapped = false;

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
            cycleDayIsWrapped = false;
            cycleDay.setValue(null);
            return;
        }
        CycleEntry mostRecent = cycles.get(0);
        LocalDate startDate = LocalDate.ofEpochDay(mostRecent.startDate);
        int day = (int) ChronoUnit.DAYS.between(startDate, currentDate.getValue()) + 1;
        if (day < 1) day = 1;

        cycleDayIsWrapped = false;
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

    /**
     * Returns the current phase name based on cycle day and average cycle length.
     *
     * Key distinction:
     * - "MENSTRUAL" = user is within a CONFIRMED period (a CycleEntry covers today)
     * - "PREDICTED_MENSTRUAL" = cycle math says period should be happening, but no
     *   CycleEntry covers today — it's just a prediction
     */
    public String getCyclePhase(int cycleDayNum, int avgCycleLength) {
        // Step 1: Check if today is within a CONFIRMED period (actual CycleEntry)
        if (isTodayConfirmedPeriod()) {
            return "MENSTRUAL";
        }

        // Step 2: Math-based phase calculation
        int periodLen = settings.getAvgPeriodLength();
        if (periodLen <= 0 || periodLen >= avgCycleLength) periodLen = 5;

        int ovDay = Math.max(periodLen + 6, avgCycleLength - 14);
        int fertileStart = Math.max(periodLen + 1, ovDay - 2);
        int fertileEnd = Math.min(avgCycleLength, ovDay + 2);

        // If the math says we're in period days, but no CycleEntry confirms it,
        // it's a PREDICTION (user hasn't logged "Yes, period started")
        if (cycleDayNum <= periodLen) {
            return "PREDICTED_MENSTRUAL";
        }
        if (cycleDayNum < fertileStart) return "FOLLICULAR";
        if (cycleDayNum <= fertileEnd) return "OVULATORY";
        return "LUTEAL";
    }

    /**
     * Returns true ONLY if a CycleEntry in the database actually covers
     * today as a PERIOD day — i.e. the user has confirmed the period started
     * AND today is within the period duration (not just the overall cycle).
     *
     * For ongoing cycles: period lasts at most avgPeriodLength days from start.
     * For ended cycles: period lasts from startDate to endDate.
     */
    public boolean isTodayConfirmedPeriod() {
        List<CycleEntry> cycles = allCycles.getValue();
        if (cycles == null) return false;
        long todayEpoch = LocalDate.now().toEpochDay();
        int avgPeriodLen = settings.getAvgPeriodLength();
        if (avgPeriodLen <= 0) avgPeriodLen = 5;

        for (CycleEntry c : cycles) {
            if (c.isOngoing()) {
                // Ongoing cycle = user started period but hasn't started a new one yet.
                // The actual PERIOD only lasts avgPeriodLen days from the start.
                // After that, user is in follicular/ovulatory/luteal of this cycle.
                long periodEnd = c.startDate + avgPeriodLen - 1;
                if (todayEpoch >= c.startDate && todayEpoch <= periodEnd) {
                    return true;
                }
            } else {
                // Ended cycle: period ran from startDate to endDate
                if (todayEpoch >= c.startDate && todayEpoch <= c.endDate) {
                    return true;
                }
            }
        }
        return false;
    }
}
