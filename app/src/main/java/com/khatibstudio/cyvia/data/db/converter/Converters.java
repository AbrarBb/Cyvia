package com.khatibstudio.cyvia.data.db.converter;

import androidx.room.TypeConverter;

import com.khatibstudio.cyvia.data.model.FlowIntensity;
import com.khatibstudio.cyvia.data.model.Mood;
import com.khatibstudio.cyvia.data.model.SymptomCategory;
import com.khatibstudio.cyvia.data.model.TrackingMode;

import java.time.LocalDate;

/**
 * Room TypeConverters for all custom types used in entities.
 *
 * LocalDate ↔ Long  : stored as epoch-day (LocalDate.toEpochDay())
 * Enums     ↔ String: stored by name() for readability in SQLite
 */
public class Converters {

    // ─── LocalDate ───────────────────────────────────────────────────────

    @TypeConverter
    public static Long localDateToEpochDay(LocalDate date) {
        return date == null ? null : date.toEpochDay();
    }

    @TypeConverter
    public static LocalDate epochDayToLocalDate(Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay);
    }

    // ─── FlowIntensity ───────────────────────────────────────────────────

    @TypeConverter
    public static String flowIntensityToString(FlowIntensity value) {
        return value == null ? null : value.name();
    }

    @TypeConverter
    public static FlowIntensity stringToFlowIntensity(String value) {
        return value == null ? null : FlowIntensity.valueOf(value);
    }

    // ─── Mood ────────────────────────────────────────────────────────────

    @TypeConverter
    public static String moodToString(Mood value) {
        return value == null ? null : value.name();
    }

    @TypeConverter
    public static Mood stringToMood(String value) {
        return value == null ? null : Mood.valueOf(value);
    }

    // ─── SymptomCategory ─────────────────────────────────────────────────

    @TypeConverter
    public static String symptomCategoryToString(SymptomCategory value) {
        return value == null ? null : value.name();
    }

    @TypeConverter
    public static SymptomCategory stringToSymptomCategory(String value) {
        return value == null ? null : SymptomCategory.valueOf(value);
    }

    // ─── TrackingMode ────────────────────────────────────────────────────

    @TypeConverter
    public static String trackingModeToString(TrackingMode value) {
        return value == null ? null : value.name();
    }

    @TypeConverter
    public static TrackingMode stringToTrackingMode(String value) {
        return value == null ? null : TrackingMode.valueOf(value);
    }
}
