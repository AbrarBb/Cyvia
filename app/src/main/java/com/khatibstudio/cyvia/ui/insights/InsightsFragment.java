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
import com.khatibstudio.cyvia.ads.AdManager;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.db.entity.SymptomTag;
import com.khatibstudio.cyvia.data.model.Mood;
import com.khatibstudio.cyvia.data.model.SymptomCategory;
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

import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * Insights screen — shows cycle stats, MPAndroidChart charts.
 */
public class InsightsFragment extends Fragment {

    private FragmentInsightsBinding binding;
    private InsightsViewModel viewModel;
    private AdManager adManager;

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
        adManager = new AdManager(com.khatibstudio.cyvia.CyviaApplication.from(requireContext()).getSettingsRepository());
        adManager.preloadRewarded(requireContext(), AdManager.REPORT_REWARDED_AD_UNIT_ID);

        styleCharts();
        setupObservers();
        updateAnalyticalForecast(null, null, null);
        updateIntimacyGuide();

        binding.cardDoctorReport.setOnClickListener(v -> {
            adManager.showRewardedAd(requireActivity(), AdManager.REPORT_REWARDED_AD_UNIT_ID, () -> {
                generateAndShareDoctorReport();
            });
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
        if (!settings.shouldShowFertileWindow() || settings.isMinimalistMode() || !settings.isTrackIntimacyEnabled()) {
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
        styleHorizontalBarChart(binding.chartSymptoms);
        stylePieChart(binding.chartMood);
    }

    private void styleBarChart(BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setGranularityEnabled(true);
        chart.getXAxis().setTextSize(11f);
        chart.getXAxis().setTextColor(requireContext().getColor(R.color.cyvia_on_surface));
        chart.getXAxis().setAxisMinimum(0.5f);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setGranularity(1f);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
        chart.setNoDataTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
        chart.setDrawGridBackground(false);
        chart.setExtraBottomOffset(10f);
        chart.setNoDataText("Log some data to see your chart");
    }

    private void styleHorizontalBarChart(com.github.mikephil.charting.charts.HorizontalBarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setGranularityEnabled(true);
        chart.getXAxis().setTextSize(11f);
        chart.getXAxis().setTextColor(requireContext().getColor(R.color.cyvia_on_surface));
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGranularity(1f);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
        chart.setNoDataTextColor(requireContext().getColor(R.color.cyvia_on_surface_variant));
        chart.setDrawGridBackground(false);
        chart.setNoDataText("Log some symptoms to see your chart");
        chart.setExtraLeftOffset(8f);
        chart.setExtraRightOffset(15f);
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
        final List<String> cDates = stats.cycleStartDates;
        for (int i = 0; i < cLengths.size(); i++) {
            cycleEntries.add(new BarEntry(i, cLengths.get(i)));
        }
        if (cycleEntries.isEmpty()) {
            binding.chartCycleLength.setNoDataText("Log a few cycles to see your chart");
            binding.chartCycleLength.clear();
        } else {
            BarDataSet dataSet = new BarDataSet(cycleEntries, "Cycle length (days)");
            dataSet.setColor(requireContext().getColor(R.color.cyvia_primary));
            dataSet.setValueTextColor(requireContext().getColor(R.color.cyvia_on_surface));
            dataSet.setValueTextSize(11f);
            dataSet.setDrawValues(true);

            // X-axis: show actual start date (yyyy/MM/dd) for each bar
            binding.chartCycleLength.getXAxis().setAxisMinimum(-0.5f);
            binding.chartCycleLength.getXAxis().setAxisMaximum(cLengths.size() - 0.5f);
            binding.chartCycleLength.getXAxis().setGranularity(1f);
            binding.chartCycleLength.getXAxis().setGranularityEnabled(true);
            binding.chartCycleLength.getXAxis().setLabelCount(cLengths.size(), true);
            binding.chartCycleLength.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int idx = (int) Math.floor(value + 0.5f);
                    if (idx >= 0 && idx < cDates.size()) return cDates.get(idx);
                    return "";
                }
            });
            binding.chartCycleLength.getXAxis().setLabelRotationAngle(-45f);
            binding.chartCycleLength.getXAxis().setTextSize(9f);
            binding.chartCycleLength.getXAxis().setTextColor(requireContext().getColor(R.color.cyvia_on_surface));
            binding.chartCycleLength.setExtraBottomOffset(20f);
            binding.chartCycleLength.setData(new BarData(dataSet));
            binding.chartCycleLength.animateY(500);
            binding.chartCycleLength.invalidate();
        }

        // Period duration bar chart
        List<BarEntry> periodEntries = new ArrayList<>();
        List<Integer> pLengths = stats.periodLengths;
        final List<String> pDates = stats.cycleStartDates; // same cycle dates for period chart
        for (int i = 0; i < pLengths.size(); i++) {
            periodEntries.add(new BarEntry(i, pLengths.get(i)));
        }
        if (periodEntries.isEmpty()) {
            binding.chartPeriodLength.setNoDataText("Log periods to see duration chart");
            binding.chartPeriodLength.clear();
        } else {
            BarDataSet pDataSet = new BarDataSet(periodEntries, "Period duration (days)");
            pDataSet.setColor(requireContext().getColor(R.color.cyvia_secondary));
            pDataSet.setValueTextColor(requireContext().getColor(R.color.cyvia_on_surface));
            pDataSet.setValueTextSize(11f);
            pDataSet.setDrawValues(true);

            // X-axis: show actual start date for each bar
            binding.chartPeriodLength.getXAxis().setAxisMinimum(-0.5f);
            binding.chartPeriodLength.getXAxis().setAxisMaximum(pLengths.size() - 0.5f);
            binding.chartPeriodLength.getXAxis().setGranularity(1f);
            binding.chartPeriodLength.getXAxis().setGranularityEnabled(true);
            binding.chartPeriodLength.getXAxis().setLabelCount(pLengths.size(), true);
            binding.chartPeriodLength.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int idx = (int) Math.floor(value + 0.5f);
                    if (idx >= 0 && idx < pDates.size()) return pDates.get(idx);
                    return "";
                }
            });
            binding.chartPeriodLength.getXAxis().setLabelRotationAngle(-45f);
            binding.chartPeriodLength.getXAxis().setTextSize(9f);
            binding.chartPeriodLength.getXAxis().setTextColor(requireContext().getColor(R.color.cyvia_on_surface));
            binding.chartPeriodLength.setExtraBottomOffset(20f);
            binding.chartPeriodLength.setData(new BarData(pDataSet));
            binding.chartPeriodLength.animateY(500);
            binding.chartPeriodLength.invalidate();
        }
    }

    // ─── Symptom chart ────────────────────────────────────────────────────

    private void updateSymptomChart(List<DailyLog> logs, List<SymptomTag> tags) {
        Map<Integer, String> tagNames = new HashMap<>();
        for (SymptomTag tag : tags) {
            if (tag.category == SymptomCategory.PHYSICAL) {
                tagNames.put(tag.id, tag.label);
            }
        }

        Map<Integer, Integer> counts = new HashMap<>();
        for (DailyLog log : logs) {
            if (log.symptomIds != null && !log.symptomIds.isEmpty()) {
                String[] ids = log.symptomIds.split(",");
                for (String sId : ids) {
                    try {
                        int id = Integer.parseInt(sId.trim());
                        if (tagNames.containsKey(id)) {
                            counts.put(id, counts.getOrDefault(id, 0) + 1);
                        }
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
            String label = tagNames.getOrDefault(tagId, "Tag #" + tagId);
            if (label.length() > 16) {
                label = label.substring(0, 14) + "…";
            }
            labels.add(label);
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
        Map<String, Integer> moodCounts = new HashMap<>();

        // 1. Count built-in moods
        for (DailyLog log : logs) {
            if (log.mood != null) {
                String moodName = log.mood.name();
                String friendlyName = moodName.substring(0, 1) + moodName.substring(1).toLowerCase().replace('_', ' ');
                if (log.mood == Mood.MOOD_SWING) friendlyName = "Mood Swing";
                if (log.mood == Mood.FOOD_CRAVING) friendlyName = "Food Craving";
                moodCounts.put(friendlyName, moodCounts.getOrDefault(friendlyName, 0) + 1);
            }
        }

        // 2. Count custom moods (stored in symptomIds with category = MOOD)
        List<SymptomTag> tags = viewModel.getAllSymptomTags().getValue();
        Map<Integer, SymptomTag> moodTagMap = new HashMap<>();
        if (tags != null) {
            for (SymptomTag tag : tags) {
                if (tag.category == SymptomCategory.MOOD) {
                    moodTagMap.put(tag.id, tag);
                }
            }
        }

        for (DailyLog log : logs) {
            if (log.symptomIds != null && !log.symptomIds.isEmpty()) {
                String[] ids = log.symptomIds.split(",");
                for (String sId : ids) {
                    try {
                        int id = Integer.parseInt(sId.trim());
                        SymptomTag tag = moodTagMap.get(id);
                        if (tag != null) {
                            moodCounts.put(tag.label, moodCounts.getOrDefault(tag.label, 0) + 1);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (moodCounts.isEmpty()) {
            binding.chartMood.setNoDataText("Log your mood to see your chart");
            binding.chartMood.clear();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> usedColors = new ArrayList<>();

        int[] moodColors = {
                requireContext().getColor(R.color.mood_calm),
                requireContext().getColor(R.color.mood_happy),
                requireContext().getColor(R.color.mood_sad),
                requireContext().getColor(R.color.mood_anxious),
                requireContext().getColor(R.color.mood_energetic),
                requireContext().getColor(R.color.mood_irritable),
                requireContext().getColor(R.color.cyvia_primary),
                requireContext().getColor(R.color.cyvia_secondary),
                requireContext().getColor(R.color.cyvia_tertiary)
        };

        int colorIdx = 0;
        for (Map.Entry<String, Integer> entry : moodCounts.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            usedColors.add(moodColors[colorIdx % moodColors.length]);
            colorIdx++;
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
                if (l.mood == Mood.ENERGETIC) personalTired += 6; // track high-energy as counter to fatigue
                if (l.mood == Mood.SAD || l.mood == Mood.ANXIOUS || l.mood == Mood.MOOD_SWING || l.mood == Mood.LONELY) personalMood += 5;
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
        android.widget.Toast.makeText(getContext(), "Generating Cycle & Wellness Summary PDF...", android.widget.Toast.LENGTH_SHORT).show();

        com.khatibstudio.cyvia.CyviaApplication app = com.khatibstudio.cyvia.CyviaApplication.from(requireContext());
        com.khatibstudio.cyvia.data.db.CyviaDatabase.databaseWriteExecutor.execute(() -> {
            List<CycleEntry> cycles = app.getCycleRepository().getAllCyclesSync();
            List<DailyLog> logs = app.getLogRepository().getAllLogsSync();
            List<SymptomTag> tags = app.getSymptomRepository().getAllSymptomTagsSync();
            boolean minorSafe = app.getSettingsRepository().isMinorSafeMode();

            Map<Integer, String> tagMap = new HashMap<>();
            if (tags != null) {
                for (SymptomTag tag : tags) tagMap.put(tag.id, tag.label);
            }

            CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Cyvia Cycle & Wellness Summary</title>");
            html.append("<style>");
            html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #2E1E40; line-height: 1.5; padding: 25px; max-width: 820px; margin: 0 auto; background: #fff; } ");
            html.append("h1 { color: #D81B60; border-bottom: 2px solid #F48FB1; padding-bottom: 8px; margin-bottom: 4px; font-size: 22px; } ");
            html.append(".subtitle { color: #666; font-size: 13px; margin-bottom: 20px; } ");
            html.append("h2 { color: #7B1FA2; margin-top: 24px; border-bottom: 1px solid #E1BEE7; padding-bottom: 4px; font-size: 16px; } ");
            html.append(".grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; margin-bottom: 20px; } ");
            html.append(".card { background: #FCF8FA; padding: 10px 14px; border-radius: 8px; border-left: 4px solid #EC407A; } ");
            html.append(".label { font-size: 10.5px; text-transform: uppercase; color: #888; font-weight: bold; } ");
            html.append(".val { font-size: 16px; font-weight: bold; color: #2E1E40; margin-top: 2px; } ");
            html.append("table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12.5px; } ");
            html.append("th, td { padding: 7px 9px; text-align: left; border-bottom: 1px solid #eee; } ");
            html.append("th { background-color: #F8F0F5; color: #4A3E56; font-weight: 600; font-size: 12px; } ");
            html.append("tr:nth-child(even) { background-color: #FDFAFC; } ");
            html.append(".badge { display: inline-block; padding: 2px 7px; border-radius: 10px; font-size: 11px; font-weight: bold; } ");
            html.append(".HEAVY { background: #ffe3e3; color: #c92a2a; } .MEDIUM { background: #fff0f6; color: #a61e4d; } ");
            html.append(".LIGHT { background: #e3fafc; color: #0b7285; } .SPOTTING { background: #f1f3f5; color: #495057; } ");
            html.append(".PILL { background: #f3e8ff; color: #6b21a8; } .ACTIVITY { background: #e0f2fe; color: #0369a1; } ");
            html.append(".DISCHARGE { background: #fef3c7; color: #92400e; } .INTIMACY { background: #ffe4e6; color: #be123c; } ");
            html.append(".footer { margin-top: 35px; font-size: 11px; color: #888; text-align: center; border-top: 1px solid #eee; padding-top: 12px; } ");
            html.append("</style></head><body>");

            DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
            String todayStr = LocalDate.now().format(dFmt);
            String startStr = "N/A";
            if (cycles != null && !cycles.isEmpty()) {
                startStr = LocalDate.ofEpochDay(cycles.get(cycles.size() - 1).startDate).format(dFmt);
            } else if (logs != null && !logs.isEmpty()) {
                startStr = LocalDate.ofEpochDay(logs.get(logs.size() - 1).date).format(dFmt);
            }

            html.append("<h1>Cyvia Cycle & Wellness Summary</h1>");
            html.append("<div class='subtitle'>Generated on ").append(todayStr).append(" • Timeline: ").append(startStr).append(" to ").append(todayStr).append("</div>");

            // 1. Cycle Overview
            html.append("<h2>1. Cycle & Rhythm Overview</h2>");
            html.append("<div class='grid'>");
            html.append("<div class='card'><div class='label'>Total Cycles Logged</div><div class='val'>").append(stats.totalCycles).append("</div></div>");
            html.append("<div class='card'><div class='label'>Average Cycle Length</div><div class='val'>").append(stats.totalCycles > 0 ? String.format("%.1f days", stats.averageCycleLength) : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Cycle Regularity</div><div class='val'>").append(stats.totalCycles > 0 ? stats.regularityLabel + " (" + stats.regularityScore + "/100)" : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Shortest Cycle</div><div class='val'>").append(stats.shortestCycle != Integer.MAX_VALUE ? stats.shortestCycle + " days" : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Longest Cycle</div><div class='val'>").append(stats.longestCycle != Integer.MIN_VALUE ? stats.longestCycle + " days" : "N/A").append("</div></div>");
            html.append("<div class='card'><div class='label'>Avg Bleed Duration</div><div class='val'>").append(stats.averagePeriodLength > 0 ? String.format("%.1f days", stats.averagePeriodLength) : "N/A").append("</div></div>");
            html.append("</div>");

            // 2. Cycle History & Flow
            html.append("<h2>2. Cycle History & Flow Intensity</h2>");
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

            // Aggregations
            Map<String, Integer> symCounts = new HashMap<>();
            Map<String, Integer> moodCounts = new HashMap<>();
            Map<String, Integer> dischargeCounts = new HashMap<>();
            Map<String, Integer> activityCounts = new HashMap<>();
            Map<String, Integer> intimacyCounts = new HashMap<>();
            int pillDays = 0;

            if (logs != null) {
                for (DailyLog l : logs) {
                    if (l.mood != null) {
                        moodCounts.put(l.mood.name(), moodCounts.getOrDefault(l.mood.name(), 0) + 1);
                    }
                    if (Boolean.TRUE.equals(l.pillsTaken)) {
                        pillDays++;
                    }
                    if (l.dischargeType != null && !l.dischargeType.trim().isEmpty()) {
                        String clean = l.dischargeType.replace("_", " ");
                        dischargeCounts.put(clean, dischargeCounts.getOrDefault(clean, 0) + 1);
                    }
                    if (l.exerciseType != null && !l.exerciseType.trim().isEmpty()) {
                        String clean = l.exerciseType.replace("_", " ");
                        activityCounts.put(clean, activityCounts.getOrDefault(clean, 0) + 1);
                    }
                    if (!minorSafe) {
                        if (l.sexType != null && !l.sexType.trim().isEmpty()) {
                            String clean = l.sexType.replace("_", " ");
                            intimacyCounts.put(clean, intimacyCounts.getOrDefault(clean, 0) + 1);
                        } else if (Boolean.TRUE.equals(l.intimacy)) {
                            intimacyCounts.put("Intimacy Logged", intimacyCounts.getOrDefault("Intimacy Logged", 0) + 1);
                        }
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
                }
            }

            // 3. Symptoms & Mood Distribution
            html.append("<h2>3. Symptoms & Mood Distribution</h2>");
            html.append("<table><thead><tr><th>Category</th><th>Description</th><th>Occurrences</th></tr></thead><tbody>");
            boolean hasSyms = false;
            for (Map.Entry<String, Integer> e : symCounts.entrySet()) {
                html.append("<tr><td>Physical Symptom</td><td><b>").append(e.getKey()).append("</b></td><td>").append(e.getValue()).append(" days</td></tr>");
                hasSyms = true;
            }
            for (Map.Entry<String, Integer> e : moodCounts.entrySet()) {
                html.append("<tr><td>Emotional Mood</td><td><b>").append(e.getKey()).append("</b></td><td>").append(e.getValue()).append(" days</td></tr>");
                hasSyms = true;
            }
            if (!hasSyms) html.append("<tr><td colspan='3'>No symptoms or moods logged yet.</td></tr>");
            html.append("</tbody></table>");

            // 4. Lifestyle, Medicine & Wellness Summary
            html.append("<h2>4. Lifestyle, Medicine & Wellness Tracking</h2>");
            html.append("<table><thead><tr><th>Area</th><th>Logged Details</th><th>Frequency</th></tr></thead><tbody>");
            boolean hasLifestyle = false;
            if (pillDays > 0) {
                html.append("<tr><td>Medicine</td><td><span class='badge PILL'>Take Pill</span></td><td>").append(pillDays).append(" days recorded</td></tr>");
                hasLifestyle = true;
            }
            for (Map.Entry<String, Integer> e : dischargeCounts.entrySet()) {
                html.append("<tr><td>Vaginal Discharge</td><td><span class='badge DISCHARGE'>").append(e.getKey()).append("</span></td><td>").append(e.getValue()).append(" days</td></tr>");
                hasLifestyle = true;
            }
            for (Map.Entry<String, Integer> e : activityCounts.entrySet()) {
                html.append("<tr><td>Physical Activity</td><td><span class='badge ACTIVITY'>").append(e.getKey()).append("</span></td><td>").append(e.getValue()).append(" days</td></tr>");
                hasLifestyle = true;
            }
            if (!minorSafe) {
                for (Map.Entry<String, Integer> e : intimacyCounts.entrySet()) {
                    html.append("<tr><td>Intimacy & Sex</td><td><span class='badge INTIMACY'>").append(e.getKey()).append("</span></td><td>").append(e.getValue()).append(" days</td></tr>");
                    hasLifestyle = true;
                }
            }
            if (!hasLifestyle) {
                html.append("<tr><td colspan='3'>No lifestyle, medicine, or intimacy entries recorded yet.</td></tr>");
            }
            html.append("</tbody></table>");

            // 5. Comprehensive Daily Logs
            if (logs != null && !logs.isEmpty()) {
                html.append("<h2>5. Daily Health & Symptom Log</h2>");
                html.append("<table><thead><tr><th>Date</th><th>Mood</th><th>Physical Symptoms</th><th>Medicine</th><th>Discharge</th><th>Activity</th>");
                if (!minorSafe) html.append("<th>Intimacy</th>");
                html.append("<th>Notes / BBT</th></tr></thead><tbody>");

                List<DailyLog> sortedLogs = new ArrayList<>(logs);
                sortedLogs.sort((l1, l2) -> Long.compare(l2.date, l1.date));

                for (DailyLog l : sortedLogs) {
                    String dt = LocalDate.ofEpochDay(l.date).format(dFmt);
                    String md = l.mood != null ? l.mood.name() : "—";
                    
                    StringBuilder symStr = new StringBuilder();
                    if (l.symptomIds != null && !l.symptomIds.isEmpty()) {
                        for (String sId : l.symptomIds.split(",")) {
                            try {
                                int id = Integer.parseInt(sId.trim());
                                String name = tagMap.get(id);
                                if (name != null) {
                                    if (symStr.length() > 0) symStr.append(", ");
                                    symStr.append(name);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    if (symStr.length() == 0) symStr.append("—");

                    String pill = Boolean.TRUE.equals(l.pillsTaken) ? "Pill taken" : "—";
                    String disch = l.dischargeType != null ? l.dischargeType.replace("_", " ") : "—";
                    String act = l.exerciseType != null ? l.exerciseType.replace("_", " ") : "—";
                    String intim = l.sexType != null ? l.sexType.replace("_", " ") : (Boolean.TRUE.equals(l.intimacy) ? "Yes" : "—");

                    StringBuilder notesBbt = new StringBuilder();
                    if (l.temperature != null) notesBbt.append(String.format("%.2f °C", l.temperature));
                    if (l.weight != null) {
                        if (notesBbt.length() > 0) notesBbt.append(" • ");
                        notesBbt.append(l.weight).append(" ").append(l.weightUnit != null ? l.weightUnit : "kg");
                    }
                    if (l.notes != null && !l.notes.trim().isEmpty()) {
                        if (notesBbt.length() > 0) notesBbt.append(" • ");
                        notesBbt.append(l.notes.trim());
                    }
                    if (notesBbt.length() == 0) notesBbt.append("—");

                    html.append("<tr>");
                    html.append("<td>").append(dt).append("</td>");
                    html.append("<td>").append(md).append("</td>");
                    html.append("<td>").append(symStr).append("</td>");
                    html.append("<td>").append(pill).append("</td>");
                    html.append("<td>").append(disch).append("</td>");
                    html.append("<td>").append(act).append("</td>");
                    if (!minorSafe) html.append("<td>").append(intim).append("</td>");
                    html.append("<td>").append(notesBbt).append("</td>");
                    html.append("</tr>");
                }
                html.append("</tbody></table>");
            }

            html.append("<div class='footer'>Personal Wellness Summary • Generated privately & offline with Cyvia</div>");
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
                                try {
                                    android.print.PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter("Cyvia_Cycle_Summary");
                                    if (printManager != null) {
                                        printManager.print("Cyvia Cycle & Wellness Summary", printAdapter, new android.print.PrintAttributes.Builder().build());
                                    }
                                } catch (Exception e) {
                                    android.widget.Toast.makeText(requireContext(), "Error exporting summary: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        android.widget.Toast.makeText(requireContext(), "Summary ready! Select 'Save as PDF' or Print.", android.widget.Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(requireContext(), "Error generating PDF: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
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
