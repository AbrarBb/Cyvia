package com.khatibstudio.cyvia.domain;

import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.model.CyclePrediction;
import com.khatibstudio.cyvia.data.model.TrackingMode;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fully local, transparent cycle prediction engine.
 *
 * Algorithm:
 *   1. Take up to 6 most recent non-excluded completed cycles.
 *   2. Compute their lengths (start of next cycle − start of current cycle).
 *   3. Average those lengths (simple mean; could be upgraded to weighted later).
 *   4. Next period = most recent cycle start + average length.
 *   5. Ovulation = next period start − 14 days (luteal phase constant).
 *   6. Fertile window = ovulation − 5 to ovulation + 1.
 *
 * Gap exclusion:
 *   Cycles with `excluded = true` are filtered out before computing the average.
 *   This prevents a 2-year gap (contraception, pregnancy) from skewing the result
 *   to absurd values like 138-day cycles (real competitor complaint).
 *
 * TrackingMode gates:
 *   - NO_PERIODS_CONTRACEPTION  → return empty fertile/ovulation fields
 *   - PERIMENOPAUSE / POSTPARTUM → set showReliabilityCaveat = true
 */
public class PredictionEngine {

    /** Maximum number of past cycles to include in the rolling average. */
    private static final int MAX_CYCLES_FOR_AVERAGE = 6;

    /** Minimum cycles needed before we consider the prediction "confident". */
    private static final int MIN_CYCLES_FOR_CONFIDENCE = 3;

    /** Luteal phase length (days from ovulation to next period). */
    private static final int LUTEAL_PHASE_DAYS = 14;

    /** Maximum fertile window span before ovulation. */
    private static final int FERTILE_WINDOW_BEFORE = 5;

    /** Fertile window days after ovulation. */
    private static final int FERTILE_WINDOW_AFTER = 1;

    private final SettingsRepository settings;

    public PredictionEngine(SettingsRepository settings) {
        this.settings = settings;
    }

    /**
     * Computes the next cycle prediction.
     *
     * @param allCycles All cycle entries, ordered newest-first.
     *                  May include excluded cycles — this method filters them.
     * @return A {@link CyclePrediction} object. Never null. Returns an empty
     *         prediction (with sensible defaults) if no cycles are available.
     */
    public CyclePrediction predict(List<CycleEntry> allCycles) {
        TrackingMode mode = settings.getTrackingMode();

        // Filter out excluded cycles
        List<CycleEntry> eligible = filterEligible(allCycles);

        if (eligible.isEmpty()) {
            return CyclePrediction.empty();
        }

        // Compute average cycle length from the most recent N cycles
        int avgLength = computeAverageCycleLength(eligible);

        // Most recent cycle start date
        long mostRecentStartEpoch = eligible.get(0).startDate;
        LocalDate mostRecentStart = LocalDate.ofEpochDay(mostRecentStartEpoch);

        // Predicted next period start
        LocalDate nextPeriodStart = mostRecentStart.plusDays(avgLength);

        // Fertile window / ovulation (suppressed for some tracking modes)
        LocalDate fertileStart = null;
        LocalDate fertileEnd = null;
        LocalDate ovulationDay = null;

        if (shouldShowFertile(mode)) {
            ovulationDay = nextPeriodStart.minusDays(LUTEAL_PHASE_DAYS);
            fertileStart = ovulationDay.minusDays(FERTILE_WINDOW_BEFORE);
            fertileEnd = ovulationDay.plusDays(FERTILE_WINDOW_AFTER);
        }

        int cyclesUsed = Math.min(eligible.size(), MAX_CYCLES_FOR_AVERAGE);
        boolean lowConfidence = cyclesUsed < MIN_CYCLES_FOR_CONFIDENCE;
        boolean caveat = mode == TrackingMode.PERIMENOPAUSE || mode == TrackingMode.POSTPARTUM;

        return new CyclePrediction(
                nextPeriodStart,
                fertileStart,
                fertileEnd,
                ovulationDay,
                cyclesUsed,
                lowConfidence,
                caveat,
                avgLength
        );
    }

    /**
     * Determines which calendar days in a given month fall into each prediction category.
     * Useful for the CalendarFragment colour-coding.
     *
     * @param prediction  A previously computed {@link CyclePrediction}.
     * @param allCycles   All cycle entries (for confirmed period days).
     * @param monthStart  First day of the month being rendered.
     * @param monthEnd    Last day of the month being rendered.
     * @return A {@link CalendarData} object with Sets of dates for each category.
     */
    public CalendarData buildCalendarData(
            CyclePrediction prediction,
            List<CycleEntry> allCycles,
            LocalDate monthStart,
            LocalDate monthEnd) {

        CalendarData data = new CalendarData();
        LocalDate gridStart = monthStart.minusMonths(1);
        LocalDate gridEnd = monthEnd.plusMonths(2);

        // Confirmed period days from logged cycles
        for (CycleEntry cycle : allCycles) {
            LocalDate start = LocalDate.ofEpochDay(cycle.startDate);
            LocalDate end = cycle.isOngoing()
                    ? LocalDate.now()
                    : LocalDate.ofEpochDay(cycle.endDate);

            LocalDate day = start;
            while (!day.isAfter(end)) {
                if (!day.isBefore(gridStart) && !day.isAfter(gridEnd)) {
                    data.periodDays.add(day);
                }
                day = day.plusDays(1);
            }
        }

        if (prediction.hasData() && prediction.nextPeriodStart != null) {
            int avgPeriodLength = settings.getAvgPeriodLength();
            if (avgPeriodLength <= 0) avgPeriodLength = 5;
            int avgCycleLen = prediction.averageCycleLength;
            if (avgCycleLen <= 0) avgCycleLen = settings.getAvgCycleLength();
            if (avgCycleLen <= 0) avgCycleLen = 28;

            boolean showFertile = shouldShowFertile(settings.getTrackingMode());
            LocalDate curPeriodStart = prediction.nextPeriodStart;

            // Project forward up to 12 cycles (approx 1 year) so users can plan dates/vacations ahead
            for (int cycleIdx = 0; cycleIdx < 12; cycleIdx++) {
                if (curPeriodStart.isAfter(gridEnd)) break;

                // Predicted period days
                for (int i = 0; i < avgPeriodLength; i++) {
                    LocalDate d = curPeriodStart.plusDays(i);
                    if (!d.isBefore(gridStart) && !d.isAfter(gridEnd)) {
                        data.predictedDays.add(d);
                    }
                }

                // Projected ovulation and fertile window
                if (showFertile) {
                    LocalDate ovDay = curPeriodStart.minusDays(LUTEAL_PHASE_DAYS);
                    LocalDate fertStart = ovDay.minusDays(FERTILE_WINDOW_BEFORE);
                    LocalDate fertEnd = ovDay.plusDays(FERTILE_WINDOW_AFTER);

                    LocalDate d = fertStart;
                    while (!d.isAfter(fertEnd)) {
                        if (!d.isBefore(gridStart) && !d.isAfter(gridEnd)) {
                            data.fertileDays.add(d);
                        }
                        d = d.plusDays(1);
                    }

                    if (!ovDay.isBefore(gridStart) && !ovDay.isAfter(gridEnd)) {
                        data.ovulationDays.add(ovDay);
                        if (data.ovulationDay == null && !ovDay.isBefore(monthStart) && !ovDay.isAfter(monthEnd)) {
                            data.ovulationDay = ovDay;
                        }
                    }
                }

                curPeriodStart = curPeriodStart.plusDays(avgCycleLen);
            }
        }

        return data;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * Returns cycles that are eligible for the rolling average:
     * - Not excluded
     * - Completed (have an endDate)
     * - Limited to MAX_CYCLES_FOR_AVERAGE most recent
     */
    private List<CycleEntry> filterEligible(List<CycleEntry> allCycles) {
        List<CycleEntry> result = new ArrayList<>();
        for (CycleEntry cycle : allCycles) {
            if (!cycle.excluded) {
                result.add(cycle);
                if (result.size() >= MAX_CYCLES_FOR_AVERAGE + 1) {
                    // We need N+1 cycles to compute N inter-cycle lengths
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Computes the average cycle length from completed consecutive cycle pairs.
     * Cycle length = startDate of next cycle − startDate of current cycle.
     *
     * Falls back to the user-overridden average from settings if no completed cycles exist.
     */
    private int computeAverageCycleLength(List<CycleEntry> eligible) {
        // We need at least 2 entries to compute 1 inter-cycle length
        if (eligible.size() < 2) {
            // Only 1 cycle logged — use settings override (default: 28)
            return settings.getAvgCycleLength();
        }

        long totalDays = 0;
        int count = 0;
        int limit = Math.min(eligible.size() - 1, MAX_CYCLES_FOR_AVERAGE);

        for (int i = 0; i < limit; i++) {
            // eligible[0] = most recent, eligible[1] = one before, etc.
            long newerStart = eligible.get(i).startDate;
            long olderStart = eligible.get(i + 1).startDate;
            long length = newerStart - olderStart;

            // Sanity check: ignore lengths < 15 or > 90 (extreme outliers)
            if (length >= 15 && length <= 90) {
                totalDays += length;
                count++;
            }
        }

        if (count == 0) {
            return settings.getAvgCycleLength();
        }

        return (int) Math.round((double) totalDays / count);
    }

    private boolean shouldShowFertile(TrackingMode mode) {
        return mode != TrackingMode.NO_PERIODS_CONTRACEPTION;
    }

    // ─── CalendarData holder ─────────────────────────────────────────────

    /** Simple data container for calendar day categorisation. */
    public static class CalendarData {
        public final Set<LocalDate> periodDays = new HashSet<>();
        public final Set<LocalDate> predictedDays = new HashSet<>();
        public final Set<LocalDate> fertileDays = new HashSet<>();
        public final Set<LocalDate> ovulationDays = new HashSet<>();
        public LocalDate ovulationDay = null;
    }
}
