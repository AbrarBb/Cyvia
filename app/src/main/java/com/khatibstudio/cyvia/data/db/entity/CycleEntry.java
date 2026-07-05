package com.khatibstudio.cyvia.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.khatibstudio.cyvia.data.model.FlowIntensity;

/**
 * Room entity representing a single menstrual cycle.
 *
 * Dates are stored as epoch-day longs (LocalDate.toEpochDay()) using
 * the Converters class. endDate is null while the period is ongoing.
 *
 * The `excluded` flag allows the user to mark a cycle as "not a normal cycle"
 * (e.g., after pregnancy, long contraceptive gap) so it won't skew predictions.
 */
@Entity(tableName = "cycle_entries")
public class CycleEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Start date stored as epoch-day (LocalDate.toEpochDay()). */
    @ColumnInfo(name = "start_date")
    public long startDate;

    /**
     * End date stored as epoch-day. -1 means the period is still ongoing.
     * Use Converters.epochDayToLocalDate() to convert back.
     */
    @ColumnInfo(name = "end_date")
    public long endDate = -1L;

    /** Flow intensity for this cycle (overall, or most recent day if ongoing). */
    @ColumnInfo(name = "flow_intensity")
    public FlowIntensity flowIntensity = FlowIntensity.MEDIUM;

    /** Optional cycle-level notes. */
    @ColumnInfo(name = "notes")
    public String notes;

    /**
     * When true this cycle is excluded from the rolling average calculation.
     * Set by the user for gaps caused by pregnancy, illness, or contraception.
     */
    @ColumnInfo(name = "excluded")
    public boolean excluded = false;

    /** No-arg constructor required by Room. */
    public CycleEntry() {}

    /** Convenience constructor for logging a new period start. */
    @Ignore
    public CycleEntry(long startDate, FlowIntensity flowIntensity) {
        this.startDate = startDate;
        this.flowIntensity = flowIntensity;
    }

    /** Returns the duration in days, or -1 if the period is still ongoing. */
    public int getDurationDays() {
        if (endDate < 0) return -1;
        return (int) (endDate - startDate) + 1;
    }

    /** True if this cycle is currently ongoing (no end date set). */
    public boolean isOngoing() {
        return endDate < 0;
    }
}
