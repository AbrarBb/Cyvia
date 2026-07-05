package com.khatibstudio.cyvia.worker;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.khatibstudio.cyvia.backup.BackupManager;

/**
 * Worker that automatically runs every month (or on demand) to export and overwrite
 * the user's backup inside their selected Drive or device folder.
 */
public class AutoBackupWorker extends Worker {

    private static final String TAG = "AutoBackupWorker";
    public static final String WORK_NAME = "CyviaAutoBackupWork";

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("auto_backup_enabled", false);

        if (!enabled) {
            return Result.success();
        }

        try {
            BackupManager backupManager = new BackupManager(context);
            BackupManager.BackupResult result = backupManager.backupToLocalAuto();
            if (result.success) {
                Log.d(TAG, "Auto-backup succeeded: " + result.message);
                return Result.success();
            } else {
                Log.e(TAG, "Auto-backup failed: " + result.message);
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error running AutoBackupWorker", e);
            return Result.failure();
        }
    }
}
