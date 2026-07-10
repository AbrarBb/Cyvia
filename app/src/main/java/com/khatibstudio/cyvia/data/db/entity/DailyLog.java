package com.khatibstudio.cyvia.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.khatibstudio.cyvia.data.model.Mood;

/**
 * Room entity representing a user's log for a single calendar day.
 *
 * Only one DailyLog record exists per date (enforced by UNIQUE index).
 * Symptoms are stored as a comma-separated string of SymptomTag IDs.
 * The `intimacy` field is suppressed in the UI when minor-safe mode is enabled.
 */
@Entity(
    tableName = "daily_logs",
    indices = { @Index(value = "date", unique = true) }
)
public class DailyLog {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Date stored as epoch-day (LocalDate.toEpochDay()). Unique per row. */
    @ColumnInfo(name = "date")
    public long date;

    /**
     * Comma-separated list of SymptomTag IDs logged for this day.
     * E.g. "1,4,7". Empty string = no symptoms logged.
     */
    @ColumnInfo(name = "symptom_ids")
    public String symptomIds = "";

    /** The user's mood for this day. Null = not logged. */
    @ColumnInfo(name = "mood")
    public Mood mood;

    /**
     * Basal body temperature in °C (for BBT/ovulation tracking).
     * Float stored as-is; null = not recorded.
     */
    @ColumnInfo(name = "temperature")
    public Float temperature;

    /** Body weight in kg. Null = not recorded. */
    @ColumnInfo(name = "weight")
    public Float weight;

    /** Free-text notes for the day. */
    @ColumnInfo(name = "notes")
    public String notes;

    /**
     * Whether the user logged intimacy for this day.
     * Null = not recorded (or minor-safe mode hides this field entirely).
     * The UI reads SettingsRepository.isMinorSafeMode() before showing this field.
     */
    @ColumnInfo(name = "intimacy")
    public Boolean intimacy;

    /**
     * Whether the user took a pill (birth control or period relief) today.
     * Null = not recorded. Stored in the Medicine section of the daily log.
     */
    @ColumnInfo(name = "pills_taken")
    public Boolean pillsTaken;

    @ColumnInfo(name = "sex_type")
    public String sexType;

    @ColumnInfo(name = "exercise_type")
    public String exerciseType;

    @ColumnInfo(name = "discharge_type")
    public String dischargeType;

    @ColumnInfo(name = "weight_unit")
    public String weightUnit;

    /** No-arg constructor required by Room. */
    public DailyLog() {}

    /** Convenience constructor with just a date. */
    @Ignore
    public DailyLog(long date) {
        this.date = date;
    }
}
