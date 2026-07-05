package com.khatibstudio.cyvia;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.ads.MobileAds;
import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.LogRepository;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.data.repository.SymptomRepository;

import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Application class — initialises singletons and applies the saved theme on startup.
 *
 * All repositories are created here and accessed as singletons throughout the app.
 * This avoids Hilt/Dagger while still providing clean dependency management.
 */
public class CyviaApplication extends Application {

    public static final String NOTIF_CHANNEL_REMINDERS = "cyvia_reminders";

    // ─── Singleton repositories ───────────────────────────────────────────
    private CycleRepository cycleRepository;
    private LogRepository logRepository;
    private SymptomRepository symptomRepository;
    private SettingsRepository settingsRepository;

    // AdMob initialization state
    private boolean adMobInitialized = false;
    private boolean adMobInitializing = false;
    private final List<OnInitializationCompleteListener> adMobListeners = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply saved theme before any Activity is created
        applyTheme();

        // Initialise Room database (lazy — actual DB file is only created on first access)
        CyviaDatabase.getDatabase(this);

        // Initialise repositories
        settingsRepository = new SettingsRepository(this);
        cycleRepository = new CycleRepository(this);
        logRepository = new LogRepository(this);
        symptomRepository = new SymptomRepository(this);

        // NOTE: AdMob is NOT initialized here to avoid ANR on BOOT_COMPLETED.
        // Call initAdMobIfNeeded() from your first Activity instead.

        // Create notification channel (required for Android 8.0+)
        createNotificationChannels();

        // Initialize WorkManager reminder & backup scheduling asynchronously on startup
        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            com.khatibstudio.cyvia.worker.BootReceiver.scheduleReminders(this);
            com.khatibstudio.cyvia.worker.BootReceiver.scheduleAutoBackup(this);
        });

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(android.app.Activity activity) {
                if (++startedActivityCount == 1 && !isActivityChangingConfigurations) {
                    if (suppressNextLock) {
                        suppressNextLock = false;
                        isAppUnlocked = true;
                    } else {
                        android.content.SharedPreferences prefs = getSharedPreferences("cyvia_settings", MODE_PRIVATE);
                        boolean enabled = prefs.getBoolean("app_lock_enabled", false);
                        String pin = prefs.getString("app_lock_pin", null);
                        if (enabled && pin != null && !isAppUnlocked && !(activity instanceof com.khatibstudio.cyvia.ui.pin.PinLockActivity)) {
                            com.khatibstudio.cyvia.ui.pin.PinLockActivity.startUnlock(activity);
                        }
                    }
                }
            }

            @Override
            public void onActivityResumed(android.app.Activity activity) {}

            @Override
            public void onActivityPaused(android.app.Activity activity) {}

            @Override
            public void onActivityStopped(android.app.Activity activity) {
                isActivityChangingConfigurations = activity.isChangingConfigurations();
                if (--startedActivityCount == 0 && !isActivityChangingConfigurations) {
                    isAppUnlocked = false;
                }
            }

            @Override
            public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {}

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }

    private int startedActivityCount = 0;
    private boolean isActivityChangingConfigurations = false;
    private static boolean isAppUnlocked = false;
    private static boolean suppressNextLock = false;

    public static void suppressLockOnce() {
        suppressNextLock = true;
    }

    public static void onAppUnlocked() {
        isAppUnlocked = true;
    }

    /**
     * Lazily initializes AdMob on a background thread without blocking UI.
     * Queues callbacks until initialization is complete.
     */
    public void initAdMobIfNeeded(OnInitializationCompleteListener listener) {
        synchronized (this) {
            if (adMobInitialized) {
                if (listener != null) listener.onInitializationComplete(null);
                return;
            }
            if (listener != null) {
                adMobListeners.add(listener);
            }
            if (adMobInitializing) {
                return;
            }
            adMobInitializing = true;
        }

        CyviaDatabase.databaseWriteExecutor.execute(() ->
                MobileAds.initialize(this, status -> {
                    List<OnInitializationCompleteListener> toNotify;
                    synchronized (CyviaApplication.this) {
                        adMobInitialized = true;
                        adMobInitializing = false;
                        toNotify = new ArrayList<>(adMobListeners);
                        adMobListeners.clear();
                    }
                    for (OnInitializationCompleteListener l : toNotify) {
                        l.onInitializationComplete(status);
                    }
                })
        );
    }

    // ─── Theme ────────────────────────────────────────────────────────────

    private void applyTheme() {
        String mode = new SettingsRepository(this).getThemeMode();
        switch (mode) {
            case SettingsRepository.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case SettingsRepository.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default: // SYSTEM
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    // ─── Notification channels ────────────────────────────────────────────

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_REMINDERS,
                    getString(R.string.notif_channel_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(getString(R.string.notif_channel_reminders_desc));
            channel.setShowBadge(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // ─── Singleton accessors ──────────────────────────────────────────────

    public static CyviaApplication from(android.content.Context context) {
        return (CyviaApplication) context.getApplicationContext();
    }

    public CycleRepository getCycleRepository() {
        return cycleRepository;
    }

    public LogRepository getLogRepository() {
        return logRepository;
    }

    public SymptomRepository getSymptomRepository() {
        return symptomRepository;
    }

    public SettingsRepository getSettingsRepository() {
        return settingsRepository;
    }
}
