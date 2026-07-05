package com.khatibstudio.cyvia.worker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.MainActivity;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.model.CyclePrediction;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.LogRepository;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.domain.PredictionEngine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * WorkManager Worker for sending period, ovulation, and log-reminder notifications.
 *
 * Notification copy follows Mochi's brand voice — friendly, never alarming.
 * HARD RULE: Notifications are NEVER used for upsell, ads, or subscription nags.
 *
 * Input data keys:
 *   "type" (String): "PERIOD" | "OVULATION" | "LOG_REMINDER"
 *   "days_until" (int): number of days until event (used in period body text)
 */
public class ReminderWorker extends Worker {

    public static final String KEY_TYPE = "type";
    public static final String KEY_DAYS_UNTIL = "days_until";

    public static final String TYPE_PERIOD = "PERIOD";
    public static final String TYPE_OVULATION = "OVULATION";
    public static final String TYPE_LOG_REMINDER = "LOG_REMINDER";

    private static final int NOTIF_ID_PERIOD = 1001;
    private static final int NOTIF_ID_OVULATION = 1002;
    private static final int NOTIF_ID_LOG = 1003;

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String type = getInputData().getString(KEY_TYPE);
        if (type == null) return Result.failure();

        boolean forceTest = getInputData().getBoolean("force_test", false);

        switch (type) {
            case TYPE_PERIOD:
                int daysUntil = getInputData().getInt(KEY_DAYS_UNTIL, 2);
                if (forceTest || isPeriodDueInDays(daysUntil)) {
                    showPeriodNotification(daysUntil);
                }
                break;
            case TYPE_OVULATION:
                if (forceTest || isOvulationDueToday()) {
                    showOvulationNotification();
                }
                break;
            case TYPE_LOG_REMINDER:
                if (forceTest || !hasLoggedToday()) {
                    showLogReminderNotification();
                }
                break;
        }

        return Result.success();
    }

    // ─── Validation Helpers ───────────────────────────────────────────────

    private boolean isPeriodDueInDays(int targetDays) {
        try {
            CyviaApplication app = CyviaApplication.from(getApplicationContext());
            CycleRepository cycleRepo = app.getCycleRepository();
            SettingsRepository settings = app.getSettingsRepository();
            List<CycleEntry> cycles = cycleRepo.getAllCyclesSync();
            PredictionEngine engine = new PredictionEngine(settings);
            CyclePrediction prediction = engine.predict(cycles);
            if (prediction != null && prediction.nextPeriodStart != null) {
                long diff = ChronoUnit.DAYS.between(LocalDate.now(), prediction.nextPeriodStart);
                return diff == targetDays;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isOvulationDueToday() {
        try {
            CyviaApplication app = CyviaApplication.from(getApplicationContext());
            CycleRepository cycleRepo = app.getCycleRepository();
            SettingsRepository settings = app.getSettingsRepository();
            List<CycleEntry> cycles = cycleRepo.getAllCyclesSync();
            PredictionEngine engine = new PredictionEngine(settings);
            CyclePrediction prediction = engine.predict(cycles);
            if (prediction != null && prediction.ovulationDay != null) {
                return prediction.ovulationDay.isEqual(LocalDate.now());
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean hasLoggedToday() {
        try {
            CyviaApplication app = CyviaApplication.from(getApplicationContext());
            LogRepository logRepo = app.getLogRepository();
            DailyLog log = logRepo.getLogForDateSync(LocalDate.now());
            return log != null;
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Enqueues an immediate test notification for verification purposes.
     */
    public static void sendImmediateTestNotification(Context context, String type) {
        Data inputData = new Data.Builder()
                .putString(KEY_TYPE, type)
                .putBoolean("force_test", true)
                .putInt(KEY_DAYS_UNTIL, 2)
                .build();
        OneTimeWorkRequest testWork = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInputData(inputData)
                .build();
        WorkManager.getInstance(context).enqueue(testWork);
    }

    // ─── Notification builders ────────────────────────────────────────────

    private void showPeriodNotification(int daysUntil) {
        Context ctx = getApplicationContext();
        boolean discreet = ctx.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE)
                .getBoolean("notif_discreet", false);
        String title = discreet ? "Cyvia" : ctx.getString(R.string.notif_period_title);
        String body = discreet ? "Daily calendar update available. Tap to view." : ctx.getString(R.string.notif_period_body, daysUntil);

        sendNotification(ctx, NOTIF_ID_PERIOD, title, body);
    }

    private void showOvulationNotification() {
        Context ctx = getApplicationContext();
        boolean discreet = ctx.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE)
                .getBoolean("notif_discreet", false);
        String title = discreet ? "Cyvia" : ctx.getString(R.string.notif_ovulation_title);
        String body = discreet ? "Daily calendar update available. Tap to view." : ctx.getString(R.string.notif_ovulation_body);

        sendNotification(ctx, NOTIF_ID_OVULATION, title, body);
    }

    private void showLogReminderNotification() {
        Context ctx = getApplicationContext();
        boolean discreet = ctx.getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE)
                .getBoolean("notif_discreet", false);
        String title = discreet ? "Cyvia" : ctx.getString(R.string.notif_log_title);
        String body = discreet ? "Daily calendar update available. Tap to view." : ctx.getString(R.string.notif_log_body);

        sendNotification(ctx, NOTIF_ID_LOG, title, body);
    }

    private void sendNotification(Context ctx, int notifId, String title, String body) {
        // Tapping the notification opens the app at the home screen
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                ctx, notifId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                ctx, CyviaApplication.NOTIF_CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(ctx);
            manager.notify(notifId, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS permission not granted — fail silently
        }
    }
}
