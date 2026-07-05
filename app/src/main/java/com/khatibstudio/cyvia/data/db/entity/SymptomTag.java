package com.khatibstudio.cyvia.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.khatibstudio.cyvia.data.model.SymptomCategory;

/**
 * Room entity for a symptom tag.
 *
 * The database is pre-populated with default built-in symptoms on first launch.
 * Users can also create their own custom symptoms (isCustom = true).
 * Custom symptoms can be deleted; built-in ones cannot.
 */
@Entity(tableName = "symptom_tags")
public class SymptomTag {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Display name of the symptom. */
    @ColumnInfo(name = "label")
    public String label;

    /**
     * True if this symptom was created by the user.
     * False = built-in default (cannot be deleted by the user).
     */
    @ColumnInfo(name = "is_custom")
    public boolean isCustom = false;

    /** Category used for grouping in the log UI. */
    @ColumnInfo(name = "category")
    public SymptomCategory category = SymptomCategory.PHYSICAL;

    /**
     * Resource name or file/content URI for custom or kawaii icons.
     * E.g. "ic_kawaii_melody" or "file:///...".
     */
    @ColumnInfo(name = "icon_key")
    public String iconKey;

    /** No-arg constructor required by Room. */
    public SymptomTag() {}

    /** Convenience constructor for creating a custom symptom. */
    @Ignore
    public SymptomTag(String label, SymptomCategory category, boolean isCustom, String iconKey) {
        this.label = label;
        this.category = category;
        this.isCustom = isCustom;
        this.iconKey = iconKey;
    }

    @Ignore
    public SymptomTag(String label, SymptomCategory category, boolean isCustom) {
        this(label, category, isCustom, null);
    }

    /** Factory method for a built-in default symptom. */
    public static SymptomTag defaultSymptom(String label, SymptomCategory category, String iconKey) {
        return new SymptomTag(label, category, false, iconKey);
    }

    public static SymptomTag defaultSymptom(String label, SymptomCategory category) {
        return new SymptomTag(label, category, false, null);
    }

    /** Factory method for a user-created custom symptom. */
    public static SymptomTag customSymptom(String label, SymptomCategory category, String iconKey) {
        return new SymptomTag(label, category, true, iconKey);
    }

    public static SymptomTag customSymptom(String label, SymptomCategory category) {
        return new SymptomTag(label, category, true, null);
    }
}
