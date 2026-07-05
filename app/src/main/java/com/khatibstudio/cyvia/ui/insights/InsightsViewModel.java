package com.khatibstudio.cyvia.ui.insights;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.LogRepository;
import com.khatibstudio.cyvia.data.db.entity.SymptomTag;
import com.khatibstudio.cyvia.data.repository.SymptomRepository;
import com.khatibstudio.cyvia.domain.CycleStatsCalculator;

import java.util.List;

/**
 * ViewModel for the Insights screen.
 */
public class InsightsViewModel extends AndroidViewModel {

    private final CycleRepository cycleRepository;
    private final LogRepository logRepository;
    private final SymptomRepository symptomRepository;

    private final LiveData<List<CycleEntry>> allCycles;
    private final LiveData<List<DailyLog>> allLogs;
    private final LiveData<List<SymptomTag>> allSymptomTags;

    private final MediatorLiveData<CycleStatsCalculator.CycleStats> stats = new MediatorLiveData<>();

    public InsightsViewModel(Application application) {
        super(application);
        CyviaApplication app = CyviaApplication.from(application);
        cycleRepository = app.getCycleRepository();
        logRepository = app.getLogRepository();
        symptomRepository = app.getSymptomRepository();

        allCycles = cycleRepository.getAllCycles();
        allLogs = logRepository.getAllLogs();
        allSymptomTags = symptomRepository.getAllSymptomTags();

        stats.addSource(allCycles, cycles -> {
            if (cycles != null) {
                com.khatibstudio.cyvia.data.repository.SettingsRepository settings = app.getSettingsRepository();
                stats.setValue(CycleStatsCalculator.compute(cycles, false, settings.getAvgCycleLength(), settings.getAvgPeriodLength()));
            }
        });
    }

    public LiveData<CycleStatsCalculator.CycleStats> getStats() { return stats; }
    public LiveData<List<DailyLog>> getAllLogs() { return allLogs; }
    public LiveData<List<CycleEntry>> getAllCycles() { return allCycles; }
    public LiveData<List<SymptomTag>> getAllSymptomTags() { return allSymptomTags; }
}
