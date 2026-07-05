package com.khatibstudio.cyvia.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.khatibstudio.cyvia.data.db.converter.Converters;
import com.khatibstudio.cyvia.data.db.dao.CycleEntryDao;
import com.khatibstudio.cyvia.data.db.dao.DailyLogDao;
import com.khatibstudio.cyvia.data.db.dao.SymptomTagDao;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.db.entity.SymptomTag;
import com.khatibstudio.cyvia.data.model.SymptomCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Room database singleton for Cyvia.
 *
 * - Version 2 — added icon_key column to symptom_tags for Kawaii icons and uploaded images
 */
@Database(
    entities = { CycleEntry.class, DailyLog.class, SymptomTag.class },
    version = 2,
    exportSchema = false
)
@TypeConverters({ Converters.class })
public abstract class CyviaDatabase extends RoomDatabase {

    public abstract CycleEntryDao cycleEntryDao();
    public abstract DailyLogDao dailyLogDao();
    public abstract SymptomTagDao symptomTagDao();

    // ─── Singleton ───────────────────────────────────────────────────────

    private static volatile CyviaDatabase INSTANCE;

    /** Thread pool for all database writes / background queries. */
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(4);

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE symptom_tags ADD COLUMN icon_key TEXT");
        }
    };

    public static CyviaDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (CyviaDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            CyviaDatabase.class,
                            "cyvia_database"
                    )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .addCallback(sRoomDatabaseCallback)
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    // ─── First-run seed callback ─────────────────────────────────────────

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                if (INSTANCE != null) {
                    SymptomTagDao dao = INSTANCE.symptomTagDao();
                    if (dao.getDefaultSymptomCount() == 0) {
                        dao.insertAllSymptomTags(buildDefaultSymptoms());
                    }
                }
            });
        }
    };

    /** Default built-in symptom set — matches strings.xml labels. */
    private static List<SymptomTag> buildDefaultSymptoms() {
        List<SymptomTag> list = new ArrayList<>();

        // Physical
        list.add(SymptomTag.defaultSymptom("Cramps", SymptomCategory.PHYSICAL, "ic_forecast_cramps"));
        list.add(SymptomTag.defaultSymptom("Headache", SymptomCategory.PHYSICAL, "ic_forecast_aches"));
        list.add(SymptomTag.defaultSymptom("Bloating", SymptomCategory.PHYSICAL, "ic_kawaii_pompom"));
        list.add(SymptomTag.defaultSymptom("Backache", SymptomCategory.PHYSICAL, "ic_forecast_aches"));
        list.add(SymptomTag.defaultSymptom("Acne", SymptomCategory.PHYSICAL, "ic_forecast_acne"));
        list.add(SymptomTag.defaultSymptom("Fatigue", SymptomCategory.PHYSICAL, "ic_forecast_tired"));
        list.add(SymptomTag.defaultSymptom("Nausea", SymptomCategory.PHYSICAL, "ic_kawaii_keroppi"));
        list.add(SymptomTag.defaultSymptom("Tender breasts", SymptomCategory.PHYSICAL, "ic_kawaii_melody"));
        list.add(SymptomTag.defaultSymptom("Food cravings", SymptomCategory.PHYSICAL, "ic_kawaii_cinna"));
        list.add(SymptomTag.defaultSymptom("Insomnia", SymptomCategory.PHYSICAL, "ic_mood_tired"));
        list.add(SymptomTag.defaultSymptom("Hot flashes", SymptomCategory.PHYSICAL, "ic_mood_anxious"));
        list.add(SymptomTag.defaultSymptom("Discharge", SymptomCategory.PHYSICAL, "ic_kawaii_kitty"));
        list.add(SymptomTag.defaultSymptom("Spotting", SymptomCategory.PHYSICAL, "ic_kawaii_kuromi"));

        // Emotional
        list.add(SymptomTag.defaultSymptom("Mood swings", SymptomCategory.EMOTIONAL, "ic_mood_sensitive"));
        list.add(SymptomTag.defaultSymptom("Anxiety", SymptomCategory.EMOTIONAL, "ic_mood_anxious"));
        list.add(SymptomTag.defaultSymptom("Low energy", SymptomCategory.EMOTIONAL, "ic_mood_tired"));
        list.add(SymptomTag.defaultSymptom("Irritability", SymptomCategory.EMOTIONAL, "ic_mood_irritable"));

        return list;
    }
}
