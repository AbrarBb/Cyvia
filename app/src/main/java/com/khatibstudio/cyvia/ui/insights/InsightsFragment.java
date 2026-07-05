package com.khatibstudio.cyvia.ui.insights;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.db.entity.SymptomTag;
import com.khatibstudio.cyvia.data.model.Mood;
import com.khatibstudio.cyvia.databinding.FragmentInsightsBinding;
import com.khatibstudio.cyvia.domain.CycleStatsCalculator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.khatibstudio.cyvia.util.KawaiiIconUtil;

/**
 * Insights screen — shows cycle stats, MPAndroidChart charts.
 */
public class InsightsFragment extends Fragment {

    private FragmentInsightsBinding binding;
    private InsightsViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInsightsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(InsightsViewModel.class);

        styleCharts();
        setupObservers();
        updateAnalyticalForecast(null, null, null);
        updateIntimacyGuide();

        com.khatibstudio.cyvia.ads.AdManager adManager = new com.khatibstudio.cyvia.ads.AdManager(
                com.khatibstudio.cyvia.CyviaApplication.from(requireContext()).getSettingsRepository());
        adManager.preloadRewarded(requireContext());

        binding.cardDoctorReport.setOnClickListener(v -> {
            adManager.showRewardedAd(requireActivity(), this::generateAndShareDoctorReport);
        });

        binding.cardInsightsFaq.setOnClickListener(v ->
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_faq));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateIntimacyGuide();
    }

    private void updateIntimacyGuide() {
        if (binding == null) return;
        com.khatibstudio.cyvia.data.repository.SettingsRepository settings =
                com.khatibstudio.cyvia.CyviaApplication.from(requireContext()).getSettingsRepository();
        if (!settings.shouldShowFertileWindow()) {
            binding.cardIntimacyGuide.setVisibility(View.GONE);
        } else {
            binding.cardIntimacyGuide.setVisibility(View.VISIBLE);
            if (settings.shouldShowReliabilityCaveat()) {
                binding.tvIntimacyCaveat.setVisibility(View.VISIBLE);
            } else {
                binding.tvIntimacyCaveat.setVisibility(View.GONE);
            }
        }
    }

    // ─── Chart styling ────────────────────────────────────────────────────

    private void styleCharts() {
        styleBarChart(binding.chartCycleLength);
        styleBarChart(binding.chartPeriodLength);
        styleBarChart(binding.chartSymptoms);
        stylePieChart(binding.chartMood);
    }

    private void styleBarChart(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setNoDataText("Log some symptoms to see your chart");
    }

    private void stylePieChart(PieChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setUsePercentValues(true);
        chart.setHoleRadius(48f);
        chart.setTransparentCircleRadius(55f);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setNoDataText("Log your mood to see your chart");
    }

    // ─── Observers ────────────────────────────────────────────────────────

    private void setupObservers() {
        viewModel.getStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats == null || !stats.hasData()) {
                binding.layoutEmptyInsights.setVisibility(View.VISIBLE);
                binding.tvAvgCycle.setText("—");
                binding.tvShortestCycle.setText("—");
                binding.tvLongestCycle.setText("—");
                binding.tvRegularityLabel.setText("—");
                binding.tvRegularityScore.setText("—");
                binding.chartCycleLength.clear();
                binding.chartPeriodLength.clear();
                return;
            }
            binding.layoutEmptyInsights.setVisibility(View.GONE);
            updateStatCards(stats);
            updateCycleChart(stats);
        });

        viewModel.getAllLogs().observe(getViewLifecycleOwner(), logs -> {
            updateAnalyticalForecast(viewModel.getAllCycles().getValue(), logs, viewModel.getAllSymptomTags().getValue());
            if (logs == null || logs.isEmpty()) {
                binding.chartMood.clear();
                binding.chartSymptoms.clear();
                return;
            }
            updateMoodChart(logs);
            List<SymptomTag> tags = viewModel.getAllSymptomTags().getValue();
            if (tags != null) {
                updateSymptomChart(logs, tags);
            }
        });

        viewModel.getAllCycles().observe(getViewLifecycleOwner(), cycles -> {
            updateAnalyticalForecast(cycles, viewModel.getAllLogs().getValue(), viewModel.getAllSymptomTags().getValue());
        });

        viewModel.getAllSymptomTags().observe(getViewLifecycleOwner(), tags -> {
            updateAnalyticalForecast(viewModel.getAllCycles().getValue(), viewModel.getAllLogs().getValue(), tags);
            List<DailyLog> logs = viewModel.getAllLogs().getValue();
            if (logs != null && tags != null && !logs.isEmpty()) {
                updateSymptomChart(logs, tags);
            }
        });
    }

    // ─── Stat cards ───────────────────────────────────────────────────────

    private void updateStatCards(CycleStatsCalculator.CycleStats stats) {
        String suffix = " d";
        binding.tvAvgCycle.setText(String.format("%.0f%s", stats.averageCycleLength, suffix));
        binding.tvShortestCycle.setText(stats.shortestCycle + suffix);
        binding.tvLongestCycle.setText(stats.longestCycle + suffix);
        binding.tvRegularityLabel.setText(stats.regularityLabel);
        binding.tvRegularityScore.setText(stats.regularityScore + "%");
    }

    // ─── Cycle chart ──────────────────────────────────────────────────────

    // ─── Cycle and Period BarCharts ───────────────────────────────────────

    private void updateCycleChart(CycleStatsCalculator.CycleStats stats) {
        // Cycle lengths bar chart
        List<BarEntry> cycleEntries = new ArrayList<>();
        List<Integer> cLengths = stats.cycleLengths;
        for (int i = 0; i < cLengths.size(); i++) {
            cycleEntries.add(new BarEntry(i + 1, cLengths.get(i)));
        }
        if (cycleEntries.isEmpty()) {
            binding.chartCycleLength.setNoDataText("Log a few cycles to see your chart");
            binding.chartCycleLength.clear();
        } else {
            BarDataSet dataSet = new BarDataSet(cycleEntries, "Cycle length (days)");
            dataSet.setColor(requireContext().getColor(R.color.cyvia_primary));
            dataSet.setValueTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
            dataSet.setValueTextSize(10f);
            binding.chartCycleLength.setData(new BarData(dataSet));
            binding.chartCycleLength.animateY(500);
            binding.chartCycleLength.invalidate();
        }

        // Period duration bar chart
        List<BarEntry> periodEntries = new ArrayList<>();
        List<Integer> pLengths = stats.periodLengths;
        for (int i = 0; i < pLengths.size(); i++) {
            periodEntries.add(new BarEntry(i + 1, pLengths.get(i)));
        }
        if (periodEntries.isEmpty()) {
            binding.chartPeriodLength.setNoDataText("Log periods to see duration chart");
            binding.chartPeriodLength.clear();
        } else {
            BarDataSet pDataSet = new BarDataSet(periodEntries, "Period duration (days)");
            pDataSet.setColor(requireContext().getColor(R.color.cyvia_secondary));
            pDataSet.setValueTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
            pDataSet.setValueTextSize(10f);
            binding.chartPeriodLength.setData(new BarData(pDataSet));
            binding.chartPeriodLength.animateY(500);
            binding.chartPeriodLength.invalidate();
        }
    }

    // ─── Symptom chart ────────────────────────────────────────────────────

    private void updateSymptomChart(List<DailyLog> logs, List<SymptomTag> tags) {
        Map<Integer, String> tagNames = new HashMap<>();
        for (SymptomTag tag : tags) {
            tagNames.put(tag.id, tag.label);
        }

        Map<Integer, Integer> counts = new HashMap<>();
        for (DailyLog log : logs) {
            if (log.symptomIds != null && !log.symptomIds.isEmpty()) {
                String[] ids = log.symptomIds.split(",");
                for (String sId : ids) {
                    try {
                        int id = Integer.parseInt(sId.trim());
                        counts.put(id, counts.getOrDefault(id, 0) + 1);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (counts.isEmpty()) {
            binding.chartSymptoms.setNoDataText("Log your daily mood & symptoms to see your frequency chart");
            binding.chartSymptoms.clear();
            return;
        }

        List<Integer> sortedIds = new ArrayList<>(counts.keySet());
        Collections.sort(sortedIds, (a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        if (sortedIds.size() > 6) sortedIds = sortedIds.subList(0, 6);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < sortedIds.size(); i++) {
            int tagId = sortedIds.get(i);
            entries.add(new BarEntry(i, counts.get(tagId)));
            labels.add(tagNames.getOrDefault(tagId, "Tag #" + tagId));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Symptoms");
        dataSet.setColor(requireContext().getColor(R.color.cyvia_secondary));
        dataSet.setValueTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
        dataSet.setValueTextSize(10f);

        binding.chartSymptoms.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.chartSymptoms.getXAxis().setLabelCount(labels.size(), false);
        binding.chartSymptoms.getXAxis().setTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
        binding.chartSymptoms.setData(new BarData(dataSet));
        binding.chartSymptoms.animateY(600);
        binding.chartSymptoms.invalidate();
    }

    // ─── Mood chart ───────────────────────────────────────────────────────

    private void updateMoodChart(List<DailyLog> logs) {
        Map<Mood, Integer> moodCounts = new HashMap<>();
        for (DailyLog log : logs) {
            if (log.mood != null) {
                moodCounts.put(log.mood, moodCounts.getOrDefault(log.mood, 0) + 1);
            }
        }
        if (moodCounts.isEmpty()) {
            binding.chartMood.setNoDataText("Log your mood to see your chart");
            binding.chartMood.clear();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        String[] moodNames = {"Happy", "Calm", "Sad", "Anxious", "Irritable", "Energetic", "Tired"};
        Mood[] moods = {Mood.HAPPY, Mood.CALM, Mood.SAD, Mood.ANXIOUS,
                Mood.IRRITABLE, Mood.ENERGETIC, Mood.TIRED};
        int[] moodColors = {
                requireContext().getColor(R.color.mood_happy),
                requireContext().getColor(R.color.mood_calm),
                requireContext().getColor(R.color.mood_sad),
                requireContext().getColor(R.color.mood_anxious),
                requireContext().getColor(R.color.mood_irritable),
                requireContext().getColor(R.color.mood_energetic),
                requireContext().getColor(R.color.mood_tired)
        };

        List<Integer> usedColors = new ArrayList<>();
        for (int i = 0; i < moods.length; i++) {
            if (moodCounts.containsKey(moods[i])) {
                entries.add(new PieEntry(moodCounts.get(moods[i]), moodNames[i]));
                usedColors.add(moodColors[i]);
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "Mood");
        dataSet.setColors(usedColors);
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(requireContext().getColor(R.color.cyvia_on_background));

        binding.chartMood.setData(new PieData(dataSet));
        binding.chartMood.animateY(800);
        binding.chartMood.invalidate();
    }

    // ─── Analytical Dynamic Forecast ─────────────────────────────────────

    private static class ForecastItem {
        String name;
        int pct;
        String iconKey;
        int fallbackIcon;
        ForecastItem(String n, int p, String ik, int fi) {
            name = n; pct = p; iconKey = ik; fallbackIcon = fi;
        }
    }

    private void updateAnalyticalForecast(List<CycleEntry> cycles, List<DailyLog> logs, List<SymptomTag> tags) {
        if (binding == null || binding.tvForecastDate1 == null) return;

        // 1. Dynamic live dates
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        binding.tvForecastDate1.setText("Today, " + today.format(fmt));
        binding.tvForecastDate2.setText("Tomorrow, " + today.plusDays(1).format(fmt));
        binding.tvForecastDate3.setText(today.plusDays(2).format(DateTimeFormatter.ofPattern("EEE, MMM d")));

        // 2. Estimate current cycle day dynamically from real history
        int cycleDay = 19; // Default luteal phase if no cycles logged yet
        if (cycles != null && !cycles.isEmpty()) {
            LocalDate latestStart = null;
            for (CycleEntry c : cycles) {
                try {
                    LocalDate d = LocalDate.ofEpochDay(c.startDate);
                    if (latestStart == null || d.isAfter(latestStart)) {
                        latestStart = d;
                    }
                } catch (Exception ignored) {}
            }
            if (latestStart != null) {
                long diff = ChronoUnit.DAYS.between(latestStart, today);
                if (diff >= 0 && diff <= 60) {
                    cycleDay = (int) diff + 1;
                }
            }
        }

        // 3. Weight probabilities and count personal logged symptoms
        int personalAcne = 0, personalTired = 0, personalAches = 0, personalMood = 0, personalCramps = 0;
        Map<Integer, Integer> symptomLogCounts = new HashMap<>();
        Map<Integer, SymptomTag> tagLookup = new HashMap<>();
        if (tags != null) {
            for (SymptomTag t : tags) tagLookup.put(t.id, t);
        }

        if (logs != null) {
            for (DailyLog l : logs) {
                if (l.symptomIds != null && !l.symptomIds.isEmpty()) {
                    for (String idStr : l.symptomIds.split(",")) {
                        try {
                            int id = Integer.parseInt(idStr.trim());
                            symptomLogCounts.put(id, symptomLogCounts.getOrDefault(id, 0) + 1);
                            SymptomTag t = tagLookup.get(id);
                            if (t != null && t.label != null) {
                                String lower = t.label.toLowerCase();
                                if (lower.contains("cramp")) personalCramps += 5;
                                if (lower.contains("acne") || lower.contains("breakout") || lower.contains("spotting")) personalAcne += 5;
                                if (lower.contains("ache") || lower.contains("headache") || lower.contains("backache") || lower.contains("pain")) personalAches += 5;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                if (l.mood == Mood.TIRED) personalTired += 6;
                if (l.mood == Mood.SAD || l.mood == Mood.ANXIOUS || l.mood == Mood.IRRITABLE) personalMood += 5;
            }
        }

        // 4. Build phase-based likelihoods with Kawaii icons
        List<ForecastItem> items = new ArrayList<>();
        if (cycleDay <= 5) {
            items.add(new ForecastItem("Cramps & Bloating", Math.min(96, 74 + personalCramps), "ic_forecast_cramps", R.drawable.ic_forecast_cramps));
            items.add(new ForecastItem("Tired & Sleepy", Math.min(94, 68 + personalTired), "ic_mood_tired", R.drawable.ic_mood_tired));
            items.add(new ForecastItem("Lower Back Aches", Math.min(88, 52 + personalAches), "ic_forecast_aches", R.drawable.ic_forecast_aches));
            items.add(new ForecastItem("Sensitive Mood", Math.min(84, 48 + personalMood), "ic_mood_sensitive", R.drawable.ic_mood_sensitive));
            items.add(new ForecastItem("Skin Breakouts", Math.max(15, 30 - personalAcne/2), "ic_forecast_acne", R.drawable.ic_forecast_acne));
        } else if (cycleDay <= 12) {
            items.add(new ForecastItem("High Energy", Math.min(95, 82 - personalTired/2), "ic_mood_energetic", R.drawable.ic_mood_energetic));
            items.add(new ForecastItem("Clear Glowing Skin", Math.min(92, 75 - personalAcne/2), "ic_forecast_acne", R.drawable.ic_forecast_acne));
            items.add(new ForecastItem("Shoulder Tension", Math.min(60, 22 + personalAches), "ic_forecast_aches", R.drawable.ic_forecast_aches));
            items.add(new ForecastItem("Calm & Positive", Math.min(90, 78 - personalMood/2), "ic_mood_calm", R.drawable.ic_mood_calm));
            items.add(new ForecastItem("Playful Mood", 78, "ic_mood_frisky", R.drawable.ic_mood_frisky));
        } else if (cycleDay <= 16) {
            items.add(new ForecastItem("Ovulation Twinges", Math.min(88, 54 + personalCramps), "ic_forecast_cramps", R.drawable.ic_forecast_cramps));
            items.add(new ForecastItem("Peak Energy", Math.min(95, 81 - personalTired/3), "ic_mood_energetic", R.drawable.ic_mood_energetic));
            items.add(new ForecastItem("Happy & Social", 88, "ic_mood_happy", R.drawable.ic_mood_happy));
            items.add(new ForecastItem("Increased Empathy", Math.min(76, 41 + personalMood), "ic_mood_sensitive", R.drawable.ic_mood_sensitive));
            items.add(new ForecastItem("Breast Sensitivity", Math.min(85, 49 + personalCramps), "ic_kawaii_melody", R.drawable.ic_forecast_cramps));
        } else {
            items.add(new ForecastItem("Acne Breakouts", Math.min(94, 71 + personalAcne), "ic_forecast_acne", R.drawable.ic_forecast_acne));
            items.add(new ForecastItem("Tired / Fatigue", Math.min(92, 58 + personalTired), "ic_mood_tired", R.drawable.ic_mood_tired));
            items.add(new ForecastItem("Sugar Cravings", Math.min(88, 55 + personalMood), "ic_kawaii_cinna", R.drawable.ic_mood_irritable));
            items.add(new ForecastItem("Lonely / Sensitive", Math.min(85, 38 + personalMood), "ic_mood_sensitive", R.drawable.ic_mood_sensitive));
            items.add(new ForecastItem("Breast Tenderness", Math.min(86, 33 + personalCramps), "ic_kawaii_melody", R.drawable.ic_forecast_cramps));
        }

        // 5. Inject user's top most frequent logged symptoms/custom items
        if (tags != null && !symptomLogCounts.isEmpty()) {
            Map<Integer, SymptomTag> tagMap = new HashMap<>();
            for (SymptomTag t : tags) tagMap.put(t.id, t);

            List<Integer> sortedIds = new ArrayList<>(symptomLogCounts.keySet());
            Collections.sort(sortedIds, (id1, id2) -> Integer.compare(
                    symptomLogCounts.getOrDefault(id2, 0),
                    symptomLogCounts.getOrDefault(id1, 0)
            ));

            int injected = 0;
            for (int id : sortedIds) {
                if (injected >= 2) break;
                SymptomTag t = tagMap.get(id);
                if (t != null) {
                    int count = symptomLogCounts.get(id);
                    int customPct = Math.min(98, 62 + (count * 9));
                    int fallback = R.drawable.ic_forecast_cramps;
                    items.set(injected, new ForecastItem(t.label, customPct, t.iconKey, fallback));
                    injected++;
                }
            }
        }

        // Sort items by percentage descending so highest risk is always at top
        Collections.sort(items, (o1, o2) -> Integer.compare(o2.pct, o1.pct));

        while (items.size() < 5) {
            items.add(new ForecastItem("Stable Cycle", 25, "ic_mood_calm", R.drawable.ic_mood_calm));
        }

        updateForecastRow(binding.tvForecastName1, binding.tvForecastLevel1, binding.tvForecastPct1, binding.iconForecast1, items.get(0).name, items.get(0).pct, items.get(0).iconKey, items.get(0).fallbackIcon);
        updateForecastRow(binding.tvForecastName2, binding.tvForecastLevel2, binding.tvForecastPct2, binding.iconForecast2, items.get(1).name, items.get(1).pct, items.get(1).iconKey, items.get(1).fallbackIcon);
        updateForecastRow(binding.tvForecastName3, binding.tvForecastLevel3, binding.tvForecastPct3, binding.iconForecast3, items.get(2).name, items.get(2).pct, items.get(2).iconKey, items.get(2).fallbackIcon);
        updateForecastRow(binding.tvForecastName4, binding.tvForecastLevel4, binding.tvForecastPct4, binding.iconForecast4, items.get(3).name, items.get(3).pct, items.get(3).iconKey, items.get(3).fallbackIcon);
        updateForecastRow(binding.tvForecastName5, binding.tvForecastLevel5, binding.tvForecastPct5, binding.iconForecast5, items.get(4).name, items.get(4).pct, items.get(4).iconKey, items.get(4).fallbackIcon);
    }

    private void updateForecastRow(android.widget.TextView nameView, android.widget.TextView levelView,
                                   android.widget.TextView pctView, android.widget.ImageView iconView,
                                   String name, int pct, String iconKey, int fallbackResId) {
        if (nameView == null) return;
        nameView.setText(name);
        pctView.setText(pct + "%");
        KawaiiIconUtil.loadIcon(requireContext(), iconView, iconKey, name, fallbackResId);
        if (pct >= 60) {
            levelView.setText("HIGH");
            levelView.setTextColor(Color.parseColor("#E53935"));
        } else if (pct >= 38) {
            levelView.setText("MED");
            levelView.setTextColor(Color.parseColor("#FB8C00"));
        } else {
            levelView.setText("LOW");
            levelView.setTextColor(Color.parseColor("#43A047"));
        }
    }

    private void generateAndShareDoctorReport() {
        if (getContext() == null) return;
        android.widget.Toast.makeText(getContext(), "Generating Doctor Report...", android.widget.Toast.LENGTH_SHORT).show();

        com.khatibstudio.cyvia.CyviaApplication app = com.khatibstudio.cyvia.CyviaApplication.from(requireContext());
        com.khatibstudio.cyvia.data.db.CyviaDatabase.databaseWriteExecutor.execute(() -> {
            List<CycleEntry> cycles = app.getCycleRepository().getAllCyclesSync();
            List<DailyLog> logs = app.getLogRepository().getAllLogsSync();
            List<SymptomTag> tags = app.getSymptomRepository().getAllSymptomTagsSync();

            Map<Integer, String> tagMap = new HashMap<>();
            if (tags != null) {
                for (SymptomTag tag : tags) tagMap.put(tag.id, tag.label);
            }

            CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Cyvia Medical Report</title>");
            html.append("<style>");
            html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #333; line-height: 1.5; padding: 30px; max-width: 800px; margin: 0 auto; } ");
            html.append("h1 { color: #EF5350; border-bottom: 2px solid #EF5350; padding-bottom: 10px; margin-bottom: 5px; } ");
            html.append(".subtitle { color: #666; font-size: 14px; margin-bottom: 25px; } ");
            html.append("h2 { color: #1E88E5; margin-top: 30px; border-bottom: 1px solid #ddd; padding-bottom: 5px; font-size: 18px; } ");
            html.append(".grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px; margin-bottom: 25px; } ");
            html.append(".card { background: #f8f9fa; padding: 12px 15px; border-radius: 6px; border-left: 4px solid #EF5350; } ");
            html.append(".label { font-size: 11px; text-transform: uppercase; color: #777; font-weight: bold; } ");
            html.append(".val { font-size: 18px; font-weight: bold; color: #222; margin-top: 3px; } ");
            html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13.5px; } ");
            html.append("th, td { padding: 8px 10px; text-align: left; border-bottom: 1px solid #eee; } ");
            html.append("th { background-color: #f1f3f5; color: #495057; font-weight: 600; } ");
            html.append("tr:nth-child(even) { background-color: #fafafa; } ");
            html.append(".badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11.5px; font-weight: bold; } ");
            html.append(".HEAVY { background: #ffe3e3; color: #c92a2a; } .MEDIUM { background: #fff0f6; color: #a61e4d; } ");
            html.append(".LIGHT { background: #e3fafc; color: #0b7285; } .SPOTTING { background: #f1f3f5; color: #495057; } ");
            html.append(".footer { margin-top: 40px; font-size: 11.5px; color: #888; text-align: center; border-top: 1px solid #eee; padding-top: 15px; } ");
            html.append("</style></head><body>");

            DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
            String todayStr = LocalDate.now().format(dFmt);
            String startStr = "N/A";
            if (cycles != null && !cycles.isEmpty()) {
                startStr = LocalDate.ofEpochDay(cycles.get(cycles.size() - 1).startDate).format(dFmt);
            } else if (logs != null && !logs.isEmpty()) {
                startStr = LocalDate.ofEpochDay(logs.get(logs.size() - 1).date).format(dFmt);
            }

            html.append("<h1>Cyvia Patient Menstrual Health Report</h1>");
            html.append("<div class='subtitle'>Generated on ").append(todayStr).append(" • Horizon: ").append(startStr).append(" to ").append(todayStr).append("</div>");

            html.append("<h2>1. Clinical Cycle Overview</h2>");
            html.append("<div class='grid'>");
            html.append("<div class='card'><div class='label'>Total Cycles Logged</div><div class='val'>").append(stats.totalCycles).append("</div></div>");
            html.append("<div class='card'><div class='label'>Average Cycle Length</div><div class='val'>").append(stats.totalCycles > 0 ? String.format("%.1f days", stats.averageCycleLength) : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Cycle Regularity</div><div class='val'>").append(stats.totalCycles > 0 ? stats.regularityLabel + " (" + stats.regularityScore + "/100)" : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Shortest Cycle</div><div class='val'>").append(stats.shortestCycle != Integer.MAX_VALUE ? stats.shortestCycle + " days" : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Longest Cycle</div><div class='val'>").append(stats.longestCycle != Integer.MIN_VALUE ? stats.longestCycle + " days" : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Avg Bleed Duration</div><div class='val'>").append(stats.averagePeriodLength > 0 ? String.format("%.1f days", stats.averagePeriodLength) : "N/A").append("</div></div>");
            html.append("</div>");

            html.append("<h2>2. Menstrual Cycle History (Beginning to Ending)</h2>");
            html.append("<table><thead><tr><th>#</th><th>Start Date</th><th>End Date</th><th>Duration</th><th>Flow Intensity</th></tr></thead><tbody>");
            if (cycles != null && !cycles.isEmpty()) {
                for (int i = 0; i < cycles.size(); i++) {
                    CycleEntry c = cycles.get(i);
                    String sDate = LocalDate.ofEpochDay(c.startDate).format(dFmt);
                    String eDate = c.isOngoing() ? "Ongoing" : LocalDate.ofEpochDay(c.endDate).format(dFmt);
                    String dur = c.isOngoing() ? "Ongoing" : c.getDurationDays() + " days";
                    String flowName = c.flowIntensity != null ? c.flowIntensity.name() : "MEDIUM";
                    html.append("<tr><td>").append(cycles.size() - i).append("</td>");
                    html.append("<td>").append(sDate).append("</td>");
                    html.append("<td>").append(eDate).append("</td>");
                    html.append("<td>").append(dur).append("</td>");
                    html.append("<td><span class='badge ").append(flowName).append("'>").append(flowName).append("</span></td></tr>");
                }
            } else {
                html.append("<tr><td colspan='5'>No recorded cycles found.</td></tr>");
            }
            html.append("</tbody></table>");

            // Symptom frequencies
            Map<String, Integer> symCounts = new HashMap<>();
            Map<String, Integer> moodCounts = new HashMap<>();
            List<DailyLog> clinicalLogs = new ArrayList<>();

            if (logs != null) {
                for (DailyLog l : logs) {
                    if (l.mood != null) {
                        moodCounts.put(l.mood.name(), moodCounts.getOrDefault(l.mood.name(), 0) + 1);
                    }
                    if (l.symptomIds != null && !l.symptomIds.isEmpty()) {
                        for (String sId : l.symptomIds.split(",")) {
                            try {
                                int id = Integer.parseInt(sId.trim());
                                String name = tagMap.get(id);
                                if (name != null) symCounts.put(name, symCounts.getOrDefault(name, 0) + 1);
                            } catch (Exception ignored) {}
                        }
                    }
                    if ((l.notes != null && !l.notes.trim().isEmpty()) || l.temperature != null) {
                        clinicalLogs.add(l);
                    }
                }
            }

            html.append("<h2>3. Symptom & Mood Distribution</h2>");
            html.append("<table><thead><tr><th>Symptom / Mood</th><th>Reported Occurrences</th></tr></thead><tbody>");
            boolean hasSyms = false;
            for (Map.Entry<String, Integer> e : symCounts.entrySet()) {
                html.append("<tr><td><b>").append(e.getKey()).append("</b> (Symptom)</td><td>").append(e.getValue()).append(" times</td></tr>");
                hasSyms = true;
            }
            for (Map.Entry<String, Integer> e : moodCounts.entrySet()) {
                html.append("<tr><td><b>").append(e.getKey()).append("</b> (Mood)</td><td>").append(e.getValue()).append(" times</td></tr>");
                hasSyms = true;
            }
            if (!hasSyms) html.append("<tr><td colspan='2'>No symptoms or moods logged yet.</td></tr>");
            html.append("</tbody></table>");

            if (!clinicalLogs.isEmpty()) {
                html.append("<h2>4. Clinical Temperature & Notes Log</h2>");
                html.append("<table><thead><tr><th>Date</th><th>BBT (°C)</th><th>Patient Notes</th></tr></thead><tbody>");
                for (DailyLog cl : clinicalLogs) {
                    String dt = LocalDate.ofEpochDay(cl.date).format(dFmt);
                    String temp = cl.temperature != null ? String.format("%.2f °C", cl.temperature) : "—";
                    String nt = cl.notes != null ? cl.notes : "—";
                    html.append("<tr><td>").append(dt).append("</td><td>").append(temp).append("</td><td>").append(nt).append("</td></tr>");
                }
                html.append("</tbody></table>");
            }

            html.append("<div class='footer'>Confidential Medical Report • Generated by Cyvia Private Period Tracker</div>");
            html.append("</body></html>");

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        android.print.PrintManager printManager = (android.print.PrintManager) requireContext().getSystemService(android.content.Context.PRINT_SERVICE);
                        android.webkit.WebView webView = new android.webkit.WebView(requireContext());
                        webView.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);
                        webView.setWebViewClient(new android.webkit.WebViewClient() {
                            @Override
                            public void onPageFinished(android.webkit.WebView view, String url) {
                                android.print.PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter("Cyvia_Medical_Report");
                                if (printManager != null) {
                                    printManager.print("Cyvia Medical Doctor Report", printAdapter, new android.print.PrintAttributes.Builder().build());
                                }
                            }
                        });
                        android.widget.Toast.makeText(requireContext(), "Report ready! Select 'Save as PDF' or Print.", android.widget.Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(requireContext(), "Error opening PDF generator: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
