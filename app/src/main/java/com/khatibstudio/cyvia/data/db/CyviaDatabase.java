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
 * - Version 3 — added pills_taken column to daily_logs for medicine/pill tracking
 * - Version 4 — added sex_type, exercise_type, discharge_type, and weight_unit columns to daily_logs
 */
@Database(
    entities = { CycleEntry.class, DailyLog.class, SymptomTag.class },
    version = 4,
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

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE daily_logs ADD COLUMN pills_taken INTEGER");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE daily_logs ADD COLUMN sex_type TEXT");
            database.execSQL("ALTER TABLE daily_logs ADD COLUMN exercise_type TEXT");
            database.execSQL("ALTER TABLE daily_logs ADD COLUMN discharge_type TEXT");
            database.execSQL("ALTER TABLE daily_logs ADD COLUMN weight_unit TEXT");
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .addCallback(sRoomDatabaseCallback)
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    // ─── First-run seed & update callback ─────────────────────────────────

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                if (INSTANCE != null) {
                    SymptomTagDao dao = INSTANCE.symptomTagDao();
                    dao.deleteDefaultSymptoms();
                    dao.insertAllSymptomTags(buildDefaultSymptoms());
                }
            });
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            databaseWriteExecutor.execute(() -> {
                if (INSTANCE != null) {
                    SymptomTagDao dao = INSTANCE.symptomTagDao();
                    dao.deleteDefaultSymptoms();
                    dao.insertAllSymptomTags(buildDefaultSymptoms());
                }
            });
        }
    };

    /**
     * Curated default symptom set — 13 physical conditions, all Mochi icons.
     * Fixed IDs are assigned to guarantee consistent logs across migrations.
     */
    public static List<SymptomTag> buildDefaultSymptoms() {
        List<SymptomTag> list = new ArrayList<>();

        list.add(createDefaultSymptom(1, "Everything is fine", "ic_mochi_smiling"));
        list.add(createDefaultSymptom(2, "White discharge",    "ic_mochi_worried"));
        list.add(createDefaultSymptom(3, "Cramps",             "ic_mochi_sick"));
        list.add(createDefaultSymptom(4, "Acne",               "ic_forecast_acne"));
        list.add(createDefaultSymptom(5, "Bloating",           "ic_mochi_cozy"));
        list.add(createDefaultSymptom(6, "Headache",           "ic_mochi_worried"));
        list.add(createDefaultSymptom(7, "Back pain",          "ic_mochi_mood_tired"));
        list.add(createDefaultSymptom(8, "Shoulder pain",      "ic_mochi_stretching"));
        list.add(createDefaultSymptom(9, "Dizziness",          "ic_mochi_mood_anxious"));
        list.add(createDefaultSymptom(10, "Breast pain",        "ic_mochi_mood_sensitive"));
        list.add(createDefaultSymptom(11, "Nausea",             "ic_mochi_sick"));
        list.add(createDefaultSymptom(12, "Fatigue",            "ic_mochi_mood_tired"));
        list.add(createDefaultSymptom(13, "Fever",              "ic_mochi_sick"));

        return list;
    }

    private static SymptomTag createDefaultSymptom(int id, String label, String iconKey) {
        SymptomTag tag = SymptomTag.defaultSymptom(label, SymptomCategory.PHYSICAL, iconKey);
        tag.id = id;
        return tag;
    }
}
