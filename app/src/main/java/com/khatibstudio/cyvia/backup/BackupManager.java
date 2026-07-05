package com.khatibstudio.cyvia.backup;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.db.entity.SymptomTag;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles local backup and restore using Android's Storage Access Framework (SAF).
 *
 * Export: queries all Room tables → serialises to JSON → writes to a temp file
 *         → launches Android share sheet so the user can save anywhere they want
 *         (email to themselves, save to Drive manually, SD card, etc.).
 *         The app itself has zero cloud dependency.
 *
 * Import: reads a JSON file the user selects → validates schema → merges into Room
 *         (existing records are kept; new records are inserted; no duplicates).
 */
public class BackupManager {

    private static final String TAG = "BackupManager";
    private static final String BACKUP_VERSION = "1";
    private static final String FILE_AUTHORITY_SUFFIX = ".fileprovider";

    private final Context context;
    private final Gson gson;

    // ─── Backup model ─────────────────────────────────────────────────────

    /** Root JSON object for the backup file. */
    private static class BackupData {
        String backupVersion = BACKUP_VERSION;
        String exportedAt;          // ISO date string
        List<CycleEntry> cycles;
        List<DailyLog> dailyLogs;
        List<SymptomTag> customSymptoms;  // only user-created ones
        boolean notifDiscreet;
        boolean minimalMode;
        boolean minorSafeMode;
    }

    /** Result object returned to the UI after export or import. */
    public static class BackupResult {
        public final boolean success;
        public final String message;
        public final int cycleCount;
        public final int logCount;

        public BackupResult(boolean success, String message, int cycleCount, int logCount) {
            this.success = success;
            this.message = message;
            this.cycleCount = cycleCount;
            this.logCount = logCount;
        }

        public static BackupResult error(String message) {
            return new BackupResult(false, message, 0, 0);
        }
    }

    // ─── Constructor ─────────────────────────────────────────────────────

    public BackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class,
                        (JsonSerializer<LocalDate>) (src, type, ctx) ->
                                ctx.serialize(src.toEpochDay()))
                .registerTypeAdapter(LocalDate.class,
                        (JsonDeserializer<LocalDate>) (json, type, ctx) ->
                                LocalDate.ofEpochDay(json.getAsLong()))
                .setPrettyPrinting()
                .create();
    }

    // ─── Export ───────────────────────────────────────────────────────────

    /**
     * Exports all data to a JSON file and launches the Android share sheet.
     *
     * Must be called from a background thread (uses Room synchronous queries).
     *
     * @return A {@link BackupResult} indicating success or failure.
     *         On success the share sheet Intent is also launched.
     */
    public BackupResult exportAndShare() {
        CyviaDatabase db = CyviaDatabase.getDatabase(context);

        List<CycleEntry> cycles = db.cycleEntryDao().getAllCyclesSync();
        List<DailyLog> logs = db.dailyLogDao().getAllLogsSync();
        List<SymptomTag> customSymptoms = db.symptomTagDao().getAllSymptomTagsSync();
        // Filter to only custom symptoms for export
        customSymptoms.removeIf(s -> !s.isCustom);

        BackupData data = new BackupData();
        data.exportedAt = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        data.cycles = cycles;
        data.dailyLogs = logs;
        data.customSymptoms = customSymptoms;
        android.content.SharedPreferences prefs = context.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE);
        data.notifDiscreet = prefs.getBoolean("notif_discreet", false);
        data.minimalMode = prefs.getBoolean("minimal_mode", false);
        data.minorSafeMode = prefs.getBoolean("minor_safe_mode", false);

        String json = gson.toJson(data);

        // Write to a temp file in the app's cache directory
        String fileName = "cyvia_backup_" + data.exportedAt + ".json";
        File backupFile = new File(context.getCacheDir(), fileName);

        try (FileWriter writer = new FileWriter(backupFile)) {
            writer.write(json);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write backup file", e);
            return BackupResult.error("Could not write backup file: " + e.getMessage());
        }

        // Create a content URI via FileProvider and launch share sheet
        Uri fileUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + FILE_AUTHORITY_SUFFIX,
                backupFile
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/json");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Cyvia backup – " + data.exportedAt);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooser = Intent.createChooser(shareIntent, "Save backup to…");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);

        return new BackupResult(true, "Backup saved successfully",
                cycles.size(), logs.size());
    }

    // ─── Direct Drive / Folder Auto-Backup ────────────────────────────────

    /**
     * Directly backs up and overwrites the backup file inside the user's selected drive/storage folder.
     * Auto-creates "Cyvia Backups" folder and overwrites "cyvia_autobackup.json".
     */
    public BackupResult backupToDriveFolder(Uri treeUri) {
        if (treeUri == null) {
            return BackupResult.error("No backup folder selected.");
        }
        DocumentFile rootTree = DocumentFile.fromTreeUri(context, treeUri);
        if (rootTree == null || !rootTree.canWrite()) {
            return BackupResult.error("Cannot write to selected drive folder. Please select the folder again.");
        }

        DocumentFile backupDir = rootTree.findFile("Cyvia Backups");
        if (backupDir == null || !backupDir.isDirectory()) {
            backupDir = rootTree.createDirectory("Cyvia Backups");
        }
        if (backupDir == null) {
            return BackupResult.error("Failed to create 'Cyvia Backups' folder in drive.");
        }

        CyviaDatabase db = CyviaDatabase.getDatabase(context);
        List<CycleEntry> cycles = db.cycleEntryDao().getAllCyclesSync();
        List<DailyLog> logs = db.dailyLogDao().getAllLogsSync();
        List<SymptomTag> customSymptoms = db.symptomTagDao().getAllSymptomTagsSync();
        customSymptoms.removeIf(s -> !s.isCustom);

        BackupData data = new BackupData();
        data.exportedAt = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        data.cycles = cycles;
        data.dailyLogs = logs;
        data.customSymptoms = customSymptoms;
        android.content.SharedPreferences prefs = context.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE);
        data.notifDiscreet = prefs.getBoolean("notif_discreet", false);
        data.minimalMode = prefs.getBoolean("minimal_mode", false);
        data.minorSafeMode = prefs.getBoolean("minor_safe_mode", false);

        String json = gson.toJson(data);

        // Check if file exists and delete it so we cleanly overwrite
        DocumentFile existingFile = backupDir.findFile("cyvia_autobackup.json");
        if (existingFile != null) {
            existingFile.delete();
        }

        DocumentFile backupFile = backupDir.createFile("application/json", "cyvia_autobackup.json");
        if (backupFile == null) {
            return BackupResult.error("Failed to create backup file in 'Cyvia Backups'.");
        }

        try (OutputStream out = context.getContentResolver().openOutputStream(backupFile.getUri());
             OutputStreamWriter writer = new OutputStreamWriter(out)) {
            writer.write(json);
            writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error writing to drive backup file", e);
            return BackupResult.error("Error writing backup to folder: " + e.getMessage());
        }

        // Save last backup timestamp
        prefs.edit().putString("last_auto_backup_date", data.exportedAt).apply();

        return new BackupResult(true, "Auto-backup updated in 'Cyvia Backups/cyvia_autobackup.json'",
                cycles.size(), logs.size());
    }

    /**
     * Automatically saves a local backup file (cyvia_autobackup.json) to local app storage.
     */
    public BackupResult backupToLocalAuto() {
        CyviaDatabase db = CyviaDatabase.getDatabase(context);
        List<CycleEntry> cycles = db.cycleEntryDao().getAllCyclesSync();
        List<DailyLog> logs = db.dailyLogDao().getAllLogsSync();
        List<SymptomTag> customSymptoms = db.symptomTagDao().getAllSymptomTagsSync();
        customSymptoms.removeIf(s -> !s.isCustom);

        BackupData data = new BackupData();
        data.exportedAt = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        data.cycles = cycles;
        data.dailyLogs = logs;
        data.customSymptoms = customSymptoms;
        android.content.SharedPreferences prefs = context.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE);
        data.notifDiscreet = prefs.getBoolean("notif_discreet", false);
        data.minimalMode = prefs.getBoolean("minimal_mode", false);
        data.minorSafeMode = prefs.getBoolean("minor_safe_mode", false);

        String json = gson.toJson(data);

        File backupDir = new File(context.getFilesDir(), "CyviaBackups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        File backupFile = new File(backupDir, "cyvia_autobackup.json");

        try (FileWriter writer = new FileWriter(backupFile, false)) {
            writer.write(json);
            writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error writing local auto backup file", e);
            return BackupResult.error("Error saving local auto backup: " + e.getMessage());
        }

        prefs.edit().putString("last_auto_backup_date", data.exportedAt).apply();

        return new BackupResult(true, "Auto-backup saved locally", cycles.size(), logs.size());
    }

    /**
     * Restores directly from "Cyvia Backups/cyvia_autobackup.json" inside the user's drive tree URI.
     */
    public BackupResult restoreFromDriveFolder(Uri treeUri) {
        if (treeUri == null) {
            return BackupResult.error("No backup folder selected.");
        }
        DocumentFile rootTree = DocumentFile.fromTreeUri(context, treeUri);
        if (rootTree == null || !rootTree.canRead()) {
            return BackupResult.error("Cannot read selected drive folder.");
        }

        DocumentFile backupDir = rootTree.findFile("Cyvia Backups");
        if (backupDir == null || !backupDir.isDirectory()) {
            return BackupResult.error("'Cyvia Backups' folder not found in selected location.");
        }

        DocumentFile backupFile = backupDir.findFile("cyvia_autobackup.json");
        if (backupFile == null || !backupFile.exists()) {
            return BackupResult.error("'cyvia_autobackup.json' not found in 'Cyvia Backups' folder.");
        }

        return importFromUri(backupFile.getUri());
    }

    // ─── Import ───────────────────────────────────────────────────────────

    /**
     * Reads a backup JSON file from the given URI and merges it into Room.
     *
     * Must be called from a background thread.
     *
     * Merge strategy:
     *   - CycleEntry: insert with REPLACE on conflict (Room handles via insertCycleEntry)
     *   - DailyLog: insert with REPLACE on conflict (unique date index merges naturally)
     *   - SymptomTag: insert with IGNORE on conflict (don't overwrite built-ins)
     *
     * @param fileUri  URI from ACTION_OPEN_DOCUMENT result.
     * @return A {@link BackupResult} describing what was restored.
     */
    public BackupResult importFromUri(Uri fileUri) {
        String json;
        try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
             InputStreamReader reader = new InputStreamReader(inputStream)) {

            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            json = sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Failed to read backup file", e);
            return BackupResult.error("Could not read file. Is it a valid Cyvia backup?");
        }

        BackupData data;
        try {
            data = gson.fromJson(json, BackupData.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse backup JSON", e);
            return BackupResult.error("Invalid backup file format.");
        }

        if (data == null || data.backupVersion == null) {
            return BackupResult.error("This file does not appear to be a Cyvia backup.");
        }

        CyviaDatabase db = CyviaDatabase.getDatabase(context);
        int cycleCount = 0;
        int logCount = 0;

        // Restore cycles
        if (data.cycles != null) {
            for (CycleEntry entry : data.cycles) {
                entry.id = 0; // Let Room auto-assign a new ID to avoid conflicts
                db.cycleEntryDao().insertCycleEntry(entry);
                cycleCount++;
            }
        }

        // Restore daily logs (REPLACE on unique date — safe merge)
        if (data.dailyLogs != null) {
            for (DailyLog log : data.dailyLogs) {
                log.id = 0;
                db.dailyLogDao().insertOrReplaceDailyLog(log);
                logCount++;
            }
        }

        // Restore custom symptoms (IGNORE on conflict — safe merge)
        if (data.customSymptoms != null) {
            for (SymptomTag tag : data.customSymptoms) {
                if (tag.isCustom) { // extra safety check
                    tag.id = 0;
                    db.symptomTagDao().insertSymptomTag(tag);
                }
            }
        }

        android.content.SharedPreferences.Editor edit = context.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE).edit();
        if (data.notifDiscreet) edit.putBoolean("notif_discreet", true);
        if (data.minimalMode) edit.putBoolean("minimal_mode", true);
        if (data.minorSafeMode) edit.putBoolean("minor_safe_mode", true);
        edit.apply();

        return new BackupResult(true,
                "Restored " + cycleCount + " cycles and " + logCount + " daily logs",
                cycleCount, logCount);
    }
}
