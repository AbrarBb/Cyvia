package com.khatibstudio.cyvia.worker;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.khatibstudio.cyvia.data.repository.SettingsRepository;

import java.util.concurrent.TimeUnit;

/**
 * Utility class to schedule WorkManager reminders when settings change.
 *
 * Note: WorkManager automatically persists periodic requests across device reboots
 * and app updates via its own internal RescheduleReceiver.
 */
public class BootReceiver {

    /**
     * (Re)schedules all enabled reminders.
     * Called from SettingsFragment whenever any reminder toggle changes.
     */
    public static void scheduleReminders(Context context) {
        SettingsRepository settings = new SettingsRepository(context);
        WorkManager workManager = WorkManager.getInstance(context);

        // ─── Period reminder ──────────────────────────────────────────────
        if (settings.isPeriodNotifEnabled()) {
            int daysBefore = settings.getPeriodNotifDaysBefore();
            Data periodData = new Data.Builder()
                    .putString(ReminderWorker.KEY_TYPE, ReminderWorker.TYPE_PERIOD)
                    .putInt(ReminderWorker.KEY_DAYS_UNTIL, daysBefore)
                    .build();

            // Run daily — the worker checks if a notification is actually appropriate today
            PeriodicWorkRequest periodWork = new PeriodicWorkRequest.Builder(
                    ReminderWorker.class, 1, TimeUnit.DAYS)
                    .setInputData(periodData)
                    .build();

            workManager.enqueueUniquePeriodicWork(
                    "period_reminder",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodWork
            );
        } else {
            workManager.cancelUniqueWork("period_reminder");
        }

        // ─── Ovulation reminder ───────────────────────────────────────────
        if (settings.isOvulationNotifEnabled()) {
            Data ovulationData = new Data.Builder()
                    .putString(ReminderWorker.KEY_TYPE, ReminderWorker.TYPE_OVULATION)
                    .build();

            PeriodicWorkRequest ovulationWork = new PeriodicWorkRequest.Builder(
                    ReminderWorker.class, 1, TimeUnit.DAYS)
                    .setInputData(ovulationData)
                    .build();

            workManager.enqueueUniquePeriodicWork(
                    "ovulation_reminder",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    ovulationWork
            );
        } else {
            workManager.cancelUniqueWork("ovulation_reminder");
        }

        // ─── Daily log reminder ───────────────────────────────────────────
        if (settings.isLogReminderEnabled()) {
            Data logData = new Data.Builder()
                    .putString(ReminderWorker.KEY_TYPE, ReminderWorker.TYPE_LOG_REMINDER)
                    .build();

            PeriodicWorkRequest logWork = new PeriodicWorkRequest.Builder(
                    ReminderWorker.class, 1, TimeUnit.DAYS)
                    .setInputData(logData)
                    .build();

            workManager.enqueueUniquePeriodicWork(
                    "log_reminder",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    logWork
            );
        } else {
            workManager.cancelUniqueWork("log_reminder");
        }
    }

    /**
     * Schedules auto-backup to run every 30 days if enabled by the user.
     */
    public static void scheduleAutoBackup(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("auto_backup_enabled", false);
        WorkManager workManager = WorkManager.getInstance(context);

        if (enabled) {
            PeriodicWorkRequest backupWork = new PeriodicWorkRequest.Builder(
                    AutoBackupWorker.class, 30, TimeUnit.DAYS)
                    .build();
            workManager.enqueueUniquePeriodicWork(
                    AutoBackupWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    backupWork
            );
        } else {
            workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME);
        }
    }
}
