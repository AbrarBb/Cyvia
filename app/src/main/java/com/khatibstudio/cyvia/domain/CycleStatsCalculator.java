package com.khatibstudio.cyvia.domain;

import com.khatibstudio.cyvia.data.db.entity.CycleEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates aggregate statistics across all logged cycles.
 * Used by InsightsFragment and InsightsViewModel.
 */
public class CycleStatsCalculator {

    // ─── Result object ────────────────────────────────────────────────────

    public static class CycleStats {
        public int totalCycles;
        public double averageCycleLength;
        public int shortestCycle;
        public int longestCycle;
        public double averagePeriodLength;
        /** 0–100 score: 100 = perfectly regular, lower = more irregular */
        public int regularityScore;
        /** Human-readable regularity label */
        public String regularityLabel;
        /** Cycle lengths in chronological order for charting. */
        public List<Integer> cycleLengths = new ArrayList<>();

        public boolean hasData() {
            return totalCycles > 0;
        }
    }

    /**
     * Compute stats from the given list of cycles (ordered newest-first).
     *
     * @param allCycles All cycles including excluded ones.
     *                  Excluded cycles are still shown in charts but
     *                  marked differently in the UI — the caller handles this.
     * @param includeExcluded If false, excluded cycles are omitted from averages.
     */
    public static CycleStats compute(List<CycleEntry> allCycles, boolean includeExcluded) {
        return compute(allCycles, includeExcluded, 28, 5);
    }

    public static CycleStats compute(List<CycleEntry> allCycles, boolean includeExcluded, int defaultCycleLen, int defaultPeriodLen) {
        CycleStats stats = new CycleStats();

        if (allCycles == null || allCycles.isEmpty()) {
            stats.totalCycles = 0;
            return stats;
        }

        // Compute period duration across all eligible cycles
        long totalPeriodDays = 0;
        int periodCount = 0;
        for (CycleEntry cycle : allCycles) {
            if (!includeExcluded && cycle.excluded) continue;
            int duration;
            if (!cycle.isOngoing()) {
                duration = cycle.getDurationDays();
            } else {
                long diff = java.time.LocalDate.now().toEpochDay() - cycle.startDate + 1;
                duration = (int) Math.max(1, Math.min(diff, 14));
            }
            if (duration > 0 && duration <= 14) {
                totalPeriodDays += duration;
                periodCount++;
            }
        }
        stats.averagePeriodLength = periodCount > 0 ? (double) totalPeriodDays / periodCount : (defaultPeriodLen > 0 ? defaultPeriodLen : 5);

        // Compute inter-cycle lengths from consecutive start dates
        List<Integer> lengths = new ArrayList<>();
        for (int i = 0; i < allCycles.size() - 1; i++) {
            CycleEntry current = allCycles.get(i);
            CycleEntry previous = allCycles.get(i + 1);

            if (!includeExcluded && current.excluded) continue;

            long length = current.startDate - previous.startDate;
            if (length >= 15 && length <= 90) {
                lengths.add((int) length);
                stats.cycleLengths.add(0, (int) length); // chronological order
            }
        }

        if (lengths.isEmpty()) {
            stats.totalCycles = allCycles.size();
            int defCycle = defaultCycleLen > 0 ? defaultCycleLen : 28;
            stats.averageCycleLength = defCycle;
            stats.shortestCycle = defCycle;
            stats.longestCycle = defCycle;
            stats.regularityScore = 100;
            stats.regularityLabel = allCycles.size() == 1 ? "Learning rhythm" : "Somewhat regular";
            if (stats.cycleLengths.isEmpty()) {
                stats.cycleLengths.add(defCycle);
            }
            return stats;
        }

        stats.totalCycles = lengths.size();

        // Average cycle length
        long sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int len : lengths) {
            sum += len;
            if (len < min) min = len;
            if (len > max) max = len;
        }
        stats.averageCycleLength = (double) sum / lengths.size();
        stats.shortestCycle = min;
        stats.longestCycle = max;

        // Regularity score: based on standard deviation of cycle lengths
        double variance = 0;
        for (int len : lengths) {
            double diff = len - stats.averageCycleLength;
            variance += diff * diff;
        }
        variance /= lengths.size();
        double stdDev = Math.sqrt(variance);

        int score = (int) Math.max(0, Math.min(100, 100 - (stdDev / 7.0) * 100));
        stats.regularityScore = score;

        if (score >= 75) {
            stats.regularityLabel = "Very regular";
        } else if (score >= 45) {
            stats.regularityLabel = "Somewhat regular";
        } else {
            stats.regularityLabel = "Irregular";
        }

        return stats;
    }
}
