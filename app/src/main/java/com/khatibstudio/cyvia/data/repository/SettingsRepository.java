package com.khatibstudio.cyvia.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.khatibstudio.cyvia.data.model.TrackingMode;

/**
 * Repository for user settings stored in SharedPreferences.
 *
 * All keys are private constants to prevent typos across the codebase.
 * The file name "cyvia_prefs" is also excluded from Android auto-backup
 * in backup_rules.xml (privacy promise).
 */
public class SettingsRepository {

    private static final String PREFS_FILE = "cyvia_prefs";

    // ─── Keys ────────────────────────────────────────────────────────────
    private static final String KEY_AVG_CYCLE_LENGTH = "avg_cycle_length";
    private static final String KEY_AVG_PERIOD_LENGTH = "avg_period_length";
    private static final String KEY_TRACKING_MODE = "tracking_mode";
    private static final String KEY_MINOR_SAFE_MODE = "minor_safe_mode";
    private static final String KEY_TRACK_INTIMACY = "track_intimacy";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_ACCENT_COLOR = "accent_color";
    private static final String KEY_NOTIF_PERIOD = "notif_period";
    private static final String KEY_NOTIF_PERIOD_DAYS = "notif_period_days";
    private static final String KEY_NOTIF_OVULATION = "notif_ovulation";
    private static final String KEY_NOTIF_LOG = "notif_log";
    private static final String KEY_ADS_REMOVED = "ads_removed";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_LAST_INTERSTITIAL_TIME = "last_interstitial_time";

    // ─── Theme mode constants ─────────────────────────────────────────────
    public static final String THEME_LIGHT = "LIGHT";
    public static final String THEME_DARK = "DARK";
    public static final String THEME_SYSTEM = "SYSTEM";

    private final SharedPreferences prefs;
    private final SharedPreferences extraPrefs;

    public SettingsRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        extraPrefs = context.getApplicationContext()
                .getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE);
    }

    // ─── Onboarding ───────────────────────────────────────────────────────

    public boolean isOnboardingComplete() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public void setOnboardingComplete(boolean complete) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    // ─── Cycle settings ──────────────────────────────────────────────────

    public int getAvgCycleLength() {
        return prefs.getInt(KEY_AVG_CYCLE_LENGTH, 28);
    }

    public void setAvgCycleLength(int days) {
        prefs.edit().putInt(KEY_AVG_CYCLE_LENGTH, days).apply();
    }

    public int getAvgPeriodLength() {
        return prefs.getInt(KEY_AVG_PERIOD_LENGTH, 5);
    }

    public void setAvgPeriodLength(int days) {
        prefs.edit().putInt(KEY_AVG_PERIOD_LENGTH, days).apply();
    }

    // ─── Tracking mode ───────────────────────────────────────────────────

    public TrackingMode getTrackingMode() {
        String stored = prefs.getString(KEY_TRACKING_MODE, TrackingMode.REGULAR.name());
        try {
            return TrackingMode.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return TrackingMode.REGULAR;
        }
    }

    public void setTrackingMode(TrackingMode mode) {
        prefs.edit().putString(KEY_TRACKING_MODE, mode.name()).apply();
    }

    // ─── Safety & privacy ────────────────────────────────────────────────

    public boolean isMinorSafeMode() {
        return prefs.getBoolean(KEY_MINOR_SAFE_MODE, false);
    }

    public void setMinorSafeMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_MINOR_SAFE_MODE, enabled).apply();
    }

    public boolean isTrackIntimacyEnabled() {
        return prefs.getBoolean(KEY_TRACK_INTIMACY, false);
    }

    public void setTrackIntimacyEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRACK_INTIMACY, enabled).apply();
    }

    // ─── Appearance ──────────────────────────────────────────────────────

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, THEME_LIGHT);
    }

    public void setThemeMode(String mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public String getAccentColor() {
        return prefs.getString(KEY_ACCENT_COLOR, "LAVENDER");
    }

    public void setAccentColor(String color) {
        prefs.edit().putString(KEY_ACCENT_COLOR, color).apply();
    }

    // ─── Notifications ───────────────────────────────────────────────────

    public boolean isPeriodNotifEnabled() {
        return prefs.getBoolean(KEY_NOTIF_PERIOD, true);
    }

    public void setPeriodNotifEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_PERIOD, enabled).apply();
    }

    /** Number of days before predicted period to send reminder (default: 2). */
    public int getPeriodNotifDaysBefore() {
        return prefs.getInt(KEY_NOTIF_PERIOD_DAYS, 2);
    }

    public void setPeriodNotifDaysBefore(int days) {
        prefs.edit().putInt(KEY_NOTIF_PERIOD_DAYS, days).apply();
    }

    public boolean isOvulationNotifEnabled() {
        return prefs.getBoolean(KEY_NOTIF_OVULATION, false);
    }

    public void setOvulationNotifEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_OVULATION, enabled).apply();
    }

    public boolean isLogReminderEnabled() {
        return prefs.getBoolean(KEY_NOTIF_LOG, false);
    }

    public void setLogReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_LOG, enabled).apply();
    }

    // ─── Ads / Billing ───────────────────────────────────────────────────

    public boolean isAdsRemoved() {
        return prefs.getBoolean(KEY_ADS_REMOVED, false);
    }

    public void setAdsRemoved(boolean removed) {
        prefs.edit().putBoolean(KEY_ADS_REMOVED, removed).apply();
    }

    /** Timestamp of last shown interstitial (used for 8-min cooldown). */
    public long getLastInterstitialTime() {
        return prefs.getLong(KEY_LAST_INTERSTITIAL_TIME, 0L);
    }

    public void setLastInterstitialTime(long timestamp) {
        prefs.edit().putLong(KEY_LAST_INTERSTITIAL_TIME, timestamp).apply();
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }

    public String getUserPfp() {
        return prefs.getString("user_pfp", "ic_kawaii_melody");
    }

    public void setUserPfp(String pfp) {
        prefs.edit().putString("user_pfp", pfp).apply();
    }

    public int getUserAge() {
        return prefs.getInt("user_age", 25);
    }

    public void setUserAge(int age) {
        prefs.edit().putInt("user_age", age).apply();
    }

    // ─── Utilities ───────────────────────────────────────────────────────

    public boolean isMinimalistMode() {
        return isMinorSafeMode() || extraPrefs.getBoolean("minimal_mode", false);
    }

    /**
     * Whether fertile window and ovulation data should be shown.
     * Suppressed for modes where pregnancy tracking is irrelevant or unwanted.
     */
    public boolean shouldShowFertileWindow() {
        if (isMinimalistMode() || !isTrackIntimacyEnabled()) {
            return false;
        }
        TrackingMode mode = getTrackingMode();
        return mode == TrackingMode.TRYING_TO_CONCEIVE
                || mode == TrackingMode.AVOIDING_PREGNANCY
                || mode == TrackingMode.REGULAR
                || mode == TrackingMode.IRREGULAR;
    }

    /**
     * Whether prediction reliability caveats should be shown
     * (perimenopause and postpartum modes have wider uncertainty).
     */
    public boolean shouldShowReliabilityCaveat() {
        TrackingMode mode = getTrackingMode();
        return mode == TrackingMode.PERIMENOPAUSE || mode == TrackingMode.POSTPARTUM;
    }

    /** Clears all settings (called by "Delete all data"). */
    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
