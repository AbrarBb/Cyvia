package com.khatibstudio.cyvia.data.model;

import java.time.LocalDate;

/**
 * Immutable value object returned by PredictionEngine.
 * Contains all forward-looking cycle predictions for a given user state.
 */
public class CyclePrediction {

    /** Predicted start date of the next period. May be null if not enough data. */
    public final LocalDate nextPeriodStart;

    /** Predicted start of the fertile window (ovulationDay - 5). Null if suppressed by TrackingMode. */
    public final LocalDate fertileWindowStart;

    /** Predicted end of the fertile window (ovulationDay + 1). Null if suppressed. */
    public final LocalDate fertileWindowEnd;

    /** Predicted ovulation day (cycleStart + avgLength - 14). Null if suppressed. */
    public final LocalDate ovulationDay;

    /**
     * Number of cycles used to compute this prediction.
     * < 3 = low confidence; shown with a caveat in the UI.
     */
    public final int cyclesUsed;

    /**
     * True if the prediction is based on fewer than 3 cycles —
     * the UI should show a gentle "accuracy improves over time" message.
     */
    public final boolean isLowConfidence;

    /**
     * True if the TrackingMode requires wider confidence intervals
     * (PERIMENOPAUSE or POSTPARTUM). UI should show a reliability caveat.
     */
    public final boolean showReliabilityCaveat;

    /** Average cycle length used for this prediction, in days. */
    public final int averageCycleLength;

    public CyclePrediction(
            LocalDate nextPeriodStart,
            LocalDate fertileWindowStart,
            LocalDate fertileWindowEnd,
            LocalDate ovulationDay,
            int cyclesUsed,
            boolean isLowConfidence,
            boolean showReliabilityCaveat,
            int averageCycleLength) {
        this.nextPeriodStart = nextPeriodStart;
        this.fertileWindowStart = fertileWindowStart;
        this.fertileWindowEnd = fertileWindowEnd;
        this.ovulationDay = ovulationDay;
        this.cyclesUsed = cyclesUsed;
        this.isLowConfidence = isLowConfidence;
        this.showReliabilityCaveat = showReliabilityCaveat;
        this.averageCycleLength = averageCycleLength;
    }

    /**
     * Returns a prediction with no forward-looking data (used when there are zero logged cycles).
     */
    public static CyclePrediction empty() {
        return new CyclePrediction(null, null, null, null, 0, true, false, 28);
    }

    public boolean hasData() {
        return nextPeriodStart != null;
    }
}
