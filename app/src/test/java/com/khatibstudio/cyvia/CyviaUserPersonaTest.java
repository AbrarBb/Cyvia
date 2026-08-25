package com.khatibstudio.cyvia;

import static org.junit.Assert.*;

import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.model.CyclePrediction;
import com.khatibstudio.cyvia.data.model.Mood;
import com.khatibstudio.cyvia.data.model.TrackingMode;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.domain.CycleStatsCalculator;
import com.khatibstudio.cyvia.domain.MochiCareEngine;
import com.khatibstudio.cyvia.domain.PredictionEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Comprehensive user persona-based test suite for Cyvia's core domain logic.
 *
 * Simulates 10 real-world user personas across prediction, statistics,
 * and care messaging engines — covering edge cases, tracking modes,
 * and boundary conditions.
 */
@RunWith(JUnit4.class)
public class CyviaUserPersonaTest {

    private FakeSettingsRepository fakeSettings;

    // ─── Fake Settings for Isolation ──────────────────────────────────────

    private static class FakeSettingsRepository extends SettingsRepository {
        int avgCycleLength = 28;
        int avgPeriodLength = 5;
        TrackingMode trackingMode = TrackingMode.REGULAR;

        @Override
        public int getAvgCycleLength() {
            return avgCycleLength;
        }

        @Override
        public int getAvgPeriodLength() {
            return avgPeriodLength;
        }

        @Override
        public TrackingMode getTrackingMode() {
            return trackingMode;
        }
    }

    // ─── Test Helpers ─────────────────────────────────────────────────────

    @Before
    public void setUp() {
        fakeSettings = new FakeSettingsRepository();
    }

    private CycleEntry makeCycle(long startEpoch, long endEpoch) {
        CycleEntry c = new CycleEntry();
        c.startDate = startEpoch;
        c.endDate = endEpoch;
        c.excluded = false;
        return c;
    }

    private List<CycleEntry> makeCyclesFromStarts(long... startEpochs) {
        List<CycleEntry> cycles = new ArrayList<>();
        for (long s : startEpochs) {
            cycles.add(makeCycle(s, s + 4)); // 5-day period
        }
        return cycles;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 1: Regular Cycle User (Sarah, 25)
    //  28-day cycles, consistent, 5-day periods
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona1_regularUser_predictionAccuracy() {
        LocalDate base = LocalDate.of(2026, 1, 1);
        List<CycleEntry> cycles = new ArrayList<>();
        // Newest first: i=0 (Jan 1), i=1 (Jan 1 - 28), etc.
        for (int i = 0; i <= 5; i++) {
            long start = base.minusDays(28L * i).toEpochDay();
            cycles.add(makeCycle(start, start + 4));
        }

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Should have data", prediction.hasData());
        assertEquals("Average cycle length should be 28", 28, prediction.averageCycleLength);
        assertFalse("Should be confident (6 cycles)", prediction.isLowConfidence);
        assertNotNull("Should have fertile window", prediction.fertileWindowStart);
        assertNotNull("Should have ovulation day", prediction.ovulationDay);
    }

    @Test
    public void persona1_regularUser_regularityScore() {
        LocalDate base = LocalDate.of(2026, 1, 1);
        List<CycleEntry> cycles = new ArrayList<>();
        // Newest first: i=0 (Jan 1), i=1 (Jan 1 - 28), etc.
        for (int i = 0; i <= 5; i++) {
            long start = base.minusDays(28L * i).toEpochDay();
            cycles.add(makeCycle(start, start + 4));
        }

        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);
        assertEquals("Perfect regularity: score should be 100", 100, stats.regularityScore);
        assertEquals("Label should be 'Very regular'", "Very regular", stats.regularityLabel);
    }

    @Test
    public void persona1_regularUser_ovulationPlacement() {
        LocalDate base = LocalDate.of(2026, 7, 1);
        long start = base.toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(start, start - 28, start - 56);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        LocalDate expectedNextPeriod = base.plusDays(28);
        LocalDate expectedOvulation = expectedNextPeriod.minusDays(14);

        assertEquals("Next period should be base + 28", expectedNextPeriod, prediction.nextPeriodStart);
        assertEquals("Ovulation should be 14 days before next period", expectedOvulation, prediction.ovulationDay);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 2: Irregular Cycle User (Maya, 32)
    //  Cycle lengths: 24, 33, 27, 35, 29 days — high variance
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona2_irregularUser_averageHandlesVariance() {
        fakeSettings.trackingMode = TrackingMode.IRREGULAR;

        long base = LocalDate.of(2026, 6, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base,
                base - 29,
                base - 29 - 35,
                base - 29 - 35 - 27,
                base - 29 - 35 - 27 - 33,
                base - 29 - 35 - 27 - 33 - 24
        );

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Should have data", prediction.hasData());
        int avg = prediction.averageCycleLength;
        assertTrue("Average should be around 29-30, got " + avg, avg >= 29 && avg <= 30);
        assertFalse("Should be confident (5 cycles)", prediction.isLowConfidence);
    }

    @Test
    public void persona2_irregularUser_regularityScoreLow() {
        long base = LocalDate.of(2026, 6, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base, base - 29, base - 64, base - 91, base - 124, base - 148
        );

        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);
        assertTrue("Should have data", stats.hasData());
        assertTrue("Score should be below 80 for irregular cycles, got " + stats.regularityScore,
                stats.regularityScore < 80);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 3: Brand New User (Priya, 19)
    //  Only 1 cycle logged — low confidence
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona3_newUser_singleCycleLowConfidence() {
        long start = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(start);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Single cycle should produce data using defaults", prediction.hasData());
        assertTrue("Should be low confidence", prediction.isLowConfidence);
        assertEquals("Cycles used should be 1", 1, prediction.cyclesUsed);
    }

    @Test
    public void persona3_newUser_twoCyclesStillLow() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 28);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Two cycles should still be low confidence", prediction.isLowConfidence);
    }

    @Test
    public void persona3_newUser_threeCyclesConfident() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 28, base - 56);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertFalse("Three cycles should be confident", prediction.isLowConfidence);
        assertEquals("Cycles used should be 3", 3, prediction.cyclesUsed);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 4: Trying to Conceive (Amina, 30)
    //  TTC mode — fertile window must be visible
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona4_ttcUser_fertileWindowShown() {
        fakeSettings.trackingMode = TrackingMode.TRYING_TO_CONCEIVE;

        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 28, base - 56, base - 84);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertNotNull("TTC: fertile window start must be shown", prediction.fertileWindowStart);
        assertNotNull("TTC: fertile window end must be shown", prediction.fertileWindowEnd);
        assertNotNull("TTC: ovulation day must be shown", prediction.ovulationDay);
    }

    @Test
    public void persona4_ttcUser_ovulationBeforePeriod() {
        fakeSettings.trackingMode = TrackingMode.TRYING_TO_CONCEIVE;

        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 30, base - 60);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Ovulation should be before next period",
                prediction.ovulationDay.isBefore(prediction.nextPeriodStart));
        long daysBetween = prediction.nextPeriodStart.toEpochDay() - prediction.ovulationDay.toEpochDay();
        assertEquals("Ovulation should be exactly 14 days before next period", 14, daysBetween);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 5: Contraception User (Lena, 22)
    //  NO_PERIODS mode — fertile window must be suppressed
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona5_contraceptionUser_fertileWindowSuppressed() {
        fakeSettings.trackingMode = TrackingMode.NO_PERIODS_CONTRACEPTION;

        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 28, base - 56);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertNull("Contraception mode: fertile window start must be null", prediction.fertileWindowStart);
        assertNull("Contraception mode: fertile window end must be null", prediction.fertileWindowEnd);
        assertNull("Contraception mode: ovulation day must be null", prediction.ovulationDay);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 6: Postpartum User (Fatima, 34)
    //  Postpartum mode — predictions shown with reliability caveat
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona6_postpartumUser_reliabilityCaveat() {
        fakeSettings.trackingMode = TrackingMode.POSTPARTUM;

        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 35, base - 70);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Postpartum should show reliability caveat", prediction.showReliabilityCaveat);
        assertTrue("Postpartum should still produce predictions", prediction.hasData());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 7: Perimenopause User (Diana, 48)
    //  Perimenopause mode — high variance expected, caveat shown
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona7_perimenopauseUser_reliabilityCaveat() {
        fakeSettings.trackingMode = TrackingMode.PERIMENOPAUSE;

        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base, base - 45, base - 67, base - 105, base - 160
        );

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Perimenopause should show reliability caveat", prediction.showReliabilityCaveat);
    }

    @Test
    public void persona7_perimenopauseUser_regularityVeryLow() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base, base - 45, base - 67, base - 105, base - 160
        );

        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);
        assertTrue("Perimenopause regularity score should be very low (<50), got " + stats.regularityScore,
                stats.regularityScore < 50);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 8: Pregnancy Gap User (Noor, 29)
    //  Has a 150-day gap from pregnancy, then resumes normal cycles
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona8_pregnancyGapUser_outlierExcluded() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base,           // most recent cycle
                base - 28,      // 28-day gap
                base - 178,     // 150-day gap (pregnancy!) — should be filtered
                base - 206      // another 28-day gap
        );

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertEquals("Average should be 28, not inflated by pregnancy gap",
                28, prediction.averageCycleLength);
    }

    @Test
    public void persona8_pregnancyGapUser_explicitExclusion() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        CycleEntry recent = makeCycle(base, base + 4);
        CycleEntry before = makeCycle(base - 28, base - 24);
        CycleEntry excluded = makeCycle(base - 178, base - 174);
        excluded.excluded = true;
        CycleEntry old = makeCycle(base - 206, base - 202);

        List<CycleEntry> cycles = Arrays.asList(recent, before, excluded, old);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertTrue("Prediction should have data", prediction.hasData());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 9: Symptom-Heavy User (Jin, 26)
    //  Logs severe cramps, headaches — Mochi Care Engine tested
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona9_symptomUser_crampsGenerateCareMessage() {
        DailyLog log = new DailyLog();
        log.symptomIds = "1,4";
        log.mood = Mood.SAD;

        String message = MochiCareEngine.generateCareMessage(log, 3, "MENSTRUAL");

        assertNotNull("Care message should not be null", message);
        assertFalse("Care message should not be empty", message.isEmpty());
        assertTrue("Message length should be reasonable (<300 chars), got " + message.length(),
                message.length() < 300);
    }

    @Test
    public void persona9_symptomUser_headacheCareMessage() {
        DailyLog log = new DailyLog();
        log.symptomIds = "2";
        log.mood = Mood.ANXIOUS;

        String message = MochiCareEngine.generateCareMessage(log, 5, "MENSTRUAL");

        assertNotNull("Headache care message should not be null", message);
        assertFalse("Headache care message should not be empty", message.isEmpty());
    }

    @Test
    public void persona9_symptomUser_fatigueCareMessage() {
        DailyLog log = new DailyLog();
        log.symptomIds = "6,10";
        log.mood = null;

        String message = MochiCareEngine.generateCareMessage(log, 20, "LUTEAL");

        assertNotNull("Fatigue care message should not be null", message);
    }

    @Test
    public void persona9_symptomUser_bloatingCareMessage() {
        DailyLog log = new DailyLog();
        log.symptomIds = "3";
        log.mood = Mood.SENSITIVE;

        String message = MochiCareEngine.generateCareMessage(log, 22, "LUTEAL");

        assertNotNull("Bloating care message should not be null", message);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PERSONA 10: Happy/Calm User (Mei, 28)
    //  Logs positive moods — care engine generates uplifting messages
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void persona10_happyUser_positiveMessages() {
        DailyLog log = new DailyLog();
        log.symptomIds = "";
        log.mood = Mood.HAPPY;

        String message = MochiCareEngine.generateCareMessage(log, 10, "FOLLICULAR");

        assertNotNull("Happy mood message should not be null", message);
        assertFalse("Happy mood message should not be empty", message.isEmpty());
    }

    @Test
    public void persona10_happyUser_calmMessages() {
        DailyLog log = new DailyLog();
        log.symptomIds = "";
        log.mood = Mood.CALM;

        String message = MochiCareEngine.generateCareMessage(log, 14, "OVULATORY");

        assertNotNull("Calm mood message should not be null", message);
    }

    @Test
    public void persona10_happyUser_energeticMessages() {
        DailyLog log = new DailyLog();
        log.symptomIds = "";
        log.mood = Mood.ENERGETIC;

        String message = MochiCareEngine.generateCareMessage(log, 8, "FOLLICULAR");

        assertNotNull("Energetic mood message should not be null", message);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EDGE CASE TESTS — Boundary conditions
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void edgeCase_zeroCycles_returnsEmpty() {
        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(new ArrayList<>());

        assertFalse("Zero cycles should return empty prediction", prediction.hasData());
        assertTrue("Zero cycles should be low confidence", prediction.isLowConfidence);
    }

    @Test
    public void edgeCase_allCyclesExcluded_returnsEmpty() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        CycleEntry c1 = makeCycle(base, base + 4);
        c1.excluded = true;
        CycleEntry c2 = makeCycle(base - 28, base - 24);
        c2.excluded = true;

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(Arrays.asList(c1, c2));

        assertFalse("All excluded cycles should produce empty prediction", prediction.hasData());
    }

    @Test
    public void edgeCase_statsWithSingleCycle_noNullPointer() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base);

        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);
        assertNotNull("Stats should not be null for single cycle", stats);
        assertTrue("Should have data", stats.hasData());
    }

    @Test
    public void edgeCase_statsWithNullInput_noNullPointer() {
        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(null, false);
        assertNotNull("Stats should not be null for null input", stats);
        assertFalse("Null input should not have data", stats.hasData());
    }

    @Test
    public void edgeCase_statsWithEmptyInput_noNullPointer() {
        CycleStatsCalculator.CycleStats stats =
                CycleStatsCalculator.compute(Collections.emptyList(), false);
        assertNotNull("Stats should not be null for empty input", stats);
        assertFalse("Empty input should not have data", stats.hasData());
    }

    @Test
    public void edgeCase_careEngineWithNullLog_noNullPointer() {
        String message = MochiCareEngine.generateCareMessage(null, null, null);
        assertNotNull("Care message should not be null even with null inputs", message);
    }

    @Test
    public void edgeCase_careEngineWithNullMood_noNullPointer() {
        DailyLog log = new DailyLog();
        log.symptomIds = "";
        log.mood = null;

        String message = MochiCareEngine.generateCareMessage(log, 1, "MENSTRUAL");
        assertNotNull("Care message should not be null with null mood", message);
    }

    @Test
    public void edgeCase_veryShortCycle_excludedFromAverage() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base, base - 10, base - 38
        );

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        assertEquals("Should compute from valid gaps", 28, prediction.averageCycleLength);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CALENDAR PROJECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void calendar_12MonthProjectionPopulates() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 28, base - 56, base - 84);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        LocalDate monthStart = LocalDate.of(2026, 7, 1);
        LocalDate monthEnd = LocalDate.of(2027, 6, 30);

        PredictionEngine.CalendarData calData =
                engine.buildCalendarData(prediction, cycles, monthStart, monthEnd);

        assertNotNull("Calendar data should not be null", calData);
        assertFalse("Should have predicted days", calData.predictedDays.isEmpty());
        assertFalse("Should have fertile days", calData.fertileDays.isEmpty());
        assertFalse("Should have ovulation days", calData.ovulationDays.isEmpty());
    }

    @Test
    public void calendar_contraceptionModeSuppressesFertile() {
        fakeSettings.trackingMode = TrackingMode.NO_PERIODS_CONTRACEPTION;

        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(base, base - 28, base - 56);

        PredictionEngine engine = new PredictionEngine(fakeSettings);
        CyclePrediction prediction = engine.predict(cycles);

        LocalDate monthStart = LocalDate.of(2026, 8, 1);
        LocalDate monthEnd = LocalDate.of(2026, 8, 31);

        PredictionEngine.CalendarData calData =
                engine.buildCalendarData(prediction, cycles, monthStart, monthEnd);

        assertTrue("Contraception mode: fertile days should be empty",
                calData.fertileDays.isEmpty());
        assertTrue("Contraception mode: ovulation days should be empty",
                calData.ovulationDays.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PHASE-SPECIFIC CARE MESSAGE TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void careEngine_menstrualPhaseMessage() {
        String message = MochiCareEngine.generateCareMessage(null, 3, "MENSTRUAL");
        assertNotNull("Menstrual phase message should not be null", message);
        assertFalse("Should not be empty", message.isEmpty());
    }

    @Test
    public void careEngine_follicularPhaseMessage() {
        String message = MochiCareEngine.generateCareMessage(null, 8, "FOLLICULAR");
        assertNotNull("Follicular phase message should not be null", message);
    }

    @Test
    public void careEngine_ovulatoryPhaseMessage() {
        String message = MochiCareEngine.generateCareMessage(null, 14, "OVULATORY");
        assertNotNull("Ovulatory phase message should not be null", message);
    }

    @Test
    public void careEngine_lutealPhaseMessage() {
        String message = MochiCareEngine.generateCareMessage(null, 22, "LUTEAL");
        assertNotNull("Luteal phase message should not be null", message);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REGULARITY SCORE BOUNDARY TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void regularityScore_perfectConsistency() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base, base - 28, base - 56, base - 84, base - 112
        );

        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);
        assertEquals("Perfect consistency should yield 100", 100, stats.regularityScore);
    }

    @Test
    public void regularityScore_neverNegative() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base, base - 20, base - 70, base - 85
        );

        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);
        assertTrue("Regularity score should never be negative, got " + stats.regularityScore,
                stats.regularityScore >= 0);
    }

    @Test
    public void regularityScore_neverExceeds100() {
        long base = LocalDate.of(2026, 7, 1).toEpochDay();
        List<CycleEntry> cycles = makeCyclesFromStarts(
                base, base - 28, base - 56, base - 84
        );

        CycleStatsCalculator.CycleStats stats = CycleStatsCalculator.compute(cycles, false);
        assertTrue("Regularity score should never exceed 100, got " + stats.regularityScore,
                stats.regularityScore <= 100);
    }
}
