package com.khatibstudio.cyvia.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.khatibstudio.cyvia.BuildConfig;
import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;

import java.util.concurrent.TimeUnit;

/**
 * Centralized ad management for Cyvia.
 *
 * PLACEMENT RULES (enforced here — do NOT bypass):
 *   - Banner: Home and Calendar screens only. Persistent at bottom. Never overlaps tappable UI.
 *   - Interstitial: Completely disabled and removed. Never show full-screen ads automatically after saving health logs or during app navigation.
 *   - No full-screen ad on launch.
 *   - All ad loading fails gracefully — never blocks UI, never crashes if offline.
 *
 * Both ad unit IDs are Google's official test IDs during development.
 * Replace with real IDs before publishing to Play Store.
 */
public class AdManager {

    private static final String TAG = "AdManager";

    // ─── Test Ad Unit IDs ─────────────────────────────────────────────────
    public static final String BANNER_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/6300978111";       // Test Banner
    public static final String INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/1033173712";       // Test Interstitial
    public static final String REWARDED_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5224354917";       // Test Rewarded
    public static final String REPORT_REWARDED_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5224354917";       // Test Rewarded
    public static final String THEME_REWARDED_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5224354917";       // Test Rewarded

    /** Minimum time between interstitial shows (8 minutes). */
    private static final long INTERSTITIAL_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(8);
    /** Minimum time between rewarded ad shows (10 minutes session cooldown). */
    private static final long REWARDED_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(10);
    private static long lastRewardedAdShowTimeMs = 0;

    private final SettingsRepository settings;
    private static InterstitialAd interstitialAd;
    private static RewardedAd rewardedAd;
    private boolean isLoggingInProgress = false;

    public AdManager(SettingsRepository settings) {
        this.settings = settings;
    }

    // ─── Banner ──────────────────────────────────────────────────────────

    /**
     * Attaches an AdMob banner to the given container.
     * Call only from HomeFragment and CalendarFragment.
     * Does nothing (hides container) if ads are removed.
     *
     * @param activity  The host activity (needed for WindowManager for adaptive banner).
     * @param container The FrameLayout that will contain the banner.
     */
    public void attachBanner(Activity activity, FrameLayout container) {
        if (settings.isAdsRemoved()) {
            container.setVisibility(View.GONE);
            return;
        }

        CyviaApplication.from(activity).initAdMobIfNeeded(status -> {
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed() || activity.isFinishing()) return;
                AdView adView = new AdView(activity);
                adView.setAdUnitId(BANNER_AD_UNIT_ID);
                adView.setAdSize(AdSize.BANNER);

                adView.setAdListener(new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        Log.d(TAG, "Banner ad loaded successfully (" + BANNER_AD_UNIT_ID + ").");
                        container.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        Log.w(TAG, "Banner ad failed to load (" + BANNER_AD_UNIT_ID + "): " + error.getMessage() + " [Code: " + error.getCode() + "]");
                        container.setVisibility(View.GONE);
                    }
                });

                container.removeAllViews();
                container.addView(adView);
                adView.loadAd(new AdRequest.Builder().build());
            });
        });
    }

    // ─── Interstitial (Save Log Interstitial) ─────────────────────────────
    public void preloadInterstitial(Context context) {
        if (settings.isAdsRemoved()) return;
        if (interstitialAd != null) return;

        CyviaApplication.from(context).initAdMobIfNeeded(status -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                AdRequest adRequest = new AdRequest.Builder().build();
                InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
                        new InterstitialAdLoadCallback() {
                            @Override
                            public void onAdLoaded(@NonNull InterstitialAd ad) {
                                interstitialAd = ad;
                                Log.d(TAG, "Interstitial ad loaded (" + INTERSTITIAL_AD_UNIT_ID + ")");
                            }

                            @Override
                            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                                Log.w(TAG, "Interstitial ad failed to load (" + INTERSTITIAL_AD_UNIT_ID + "): " + error.getMessage() + " [Code: " + error.getCode() + "]");
                                interstitialAd = null;
                            }
                        });
            });
        });
    }

    public void maybeShowInterstitial(Activity activity) {
        maybeShowInterstitial(activity, null);
    }

    public void maybeShowInterstitial(Activity activity, Runnable onDismissed) {
        if (settings.isAdsRemoved() || isLoggingInProgress) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - settings.getLastInterstitialTime() < INTERSTITIAL_COOLDOWN_MS) {
            Log.d(TAG, "Interstitial skipped due to cooldown.");
            if (onDismissed != null) onDismissed.run();
            return;
        }

        showInterstitialAd(activity, onDismissed);
    }

    /**
     * Shows an interstitial immediately if ready and eligible (skips the 8-minute cooldown check).
     * Ideal for deliberate user actions like theme switching to dark mode where an ad is expected.
     */
    public void showInterstitialAd(Activity activity, Runnable onDismissed) {
        if (settings.isAdsRemoved()) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        if (interstitialAd != null) {
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    settings.setLastInterstitialTime(System.currentTimeMillis());
                    preloadInterstitial(activity);
                    if (onDismissed != null) onDismissed.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                    interstitialAd = null;
                    preloadInterstitial(activity);
                    if (onDismissed != null) onDismissed.run();
                }
            });
            interstitialAd.show(activity);
        } else {
            preloadInterstitial(activity);
            if (onDismissed != null) onDismissed.run();
        }
    }

    // ─── State control ───────────────────────────────────────────────────

    /**
     * Call when the user opens the daily log sheet.
     * Prevents the interstitial from showing mid-log.
     */
    public void onLoggingStarted() {
        isLoggingInProgress = true;
    }

    /**
     * Call when the log sheet is fully dismissed (after save/cancel).
     * Re-enables interstitial eligibility.
     */
    public void onLoggingFinished() {
        isLoggingInProgress = false;
    }

    /**
     * Called when the Remove Ads purchase completes.
     * Destroys any pre-loaded interstitial.
     */
    public void onAdsRemoved() {
        interstitialAd = null;
        rewardedAd = null;
    }

    // ─── Rewarded Ad ─────────────────────────────────────────────────────

    public void preloadRewarded(Context context) {
        preloadRewarded(context, REWARDED_AD_UNIT_ID);
    }

    public void preloadRewarded(Context context, String adUnitId) {
        if (settings.isAdsRemoved()) return;
        if (rewardedAd != null) return;

        CyviaApplication.from(context).initAdMobIfNeeded(status -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                AdRequest adRequest = new AdRequest.Builder().build();
                RewardedAd.load(context, adUnitId, adRequest,
                        new RewardedAdLoadCallback() {
                            @Override
                            public void onAdLoaded(@NonNull RewardedAd ad) {
                                rewardedAd = ad;
                                Log.d(TAG, "Rewarded ad loaded (" + adUnitId + ")");
                            }

                            @Override
                            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                                Log.w(TAG, "Rewarded ad failed to load (" + adUnitId + "): " + error.getMessage() + " [Code: " + error.getCode() + "]");
                                rewardedAd = null;
                            }
                        });
            });
        });
    }

    public void showRewardedAd(Activity activity, Runnable onRewardEarned) {
        showRewardedAd(activity, REWARDED_AD_UNIT_ID, onRewardEarned);
    }

    public void showRewardedAd(Activity activity, String adUnitId, Runnable onRewardEarned) {
        if (settings.isAdsRemoved()) {
            if (onRewardEarned != null) onRewardEarned.run();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRewardedAdShowTimeMs < REWARDED_COOLDOWN_MS) {
            Log.d(TAG, "Rewarded ad skipped due to 10-minute session cooldown.");
            if (onRewardEarned != null) onRewardEarned.run();
            return;
        }

        if (rewardedAd != null) {
            displayLoadedRewardedAd(activity, adUnitId, onRewardEarned);
        } else {
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(activity);
            progressDialog.setMessage("Loading video ad to unlock...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            CyviaApplication.from(activity).initAdMobIfNeeded(status -> {
                new Handler(Looper.getMainLooper()).post(() -> {
                    AdRequest adRequest = new AdRequest.Builder().build();
                    RewardedAd.load(activity, adUnitId, adRequest,
                            new RewardedAdLoadCallback() {
                                @Override
                                public void onAdLoaded(@NonNull RewardedAd ad) {
                                    if (progressDialog.isShowing()) progressDialog.dismiss();
                                    rewardedAd = ad;
                                    displayLoadedRewardedAd(activity, adUnitId, onRewardEarned);
                                }

                                @Override
                                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                                    if (progressDialog.isShowing()) progressDialog.dismiss();
                                    Log.w(TAG, "Rewarded ad failed to load (" + adUnitId + "): " + error.getMessage() + " [Code: " + error.getCode() + "]");
                                    lastRewardedAdShowTimeMs = System.currentTimeMillis();
                                    android.widget.Toast.makeText(activity, "Ad temporarily unavailable. Continuing...", android.widget.Toast.LENGTH_SHORT).show();
                                    if (onRewardEarned != null) onRewardEarned.run();
                                }
                            });
                });
            });
        }
    }

    private void displayLoadedRewardedAd(Activity activity, String adUnitId, Runnable onRewardEarned) {
        if (rewardedAd == null) return;
        final boolean[] rewardEarned = {false};
        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                rewardedAd = null;
                preloadRewarded(activity, adUnitId);
                if (rewardEarned[0]) {
                    if (onRewardEarned != null) onRewardEarned.run();
                } else {
                    android.widget.Toast.makeText(activity, "Please watch the full ad to earn your reward ✨", android.widget.Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                rewardedAd = null;
                preloadRewarded(activity, adUnitId);
                lastRewardedAdShowTimeMs = System.currentTimeMillis();
                if (onRewardEarned != null) onRewardEarned.run();
            }
        });
        rewardedAd.show(activity, rewardItem -> {
            rewardEarned[0] = true;
            lastRewardedAdShowTimeMs = System.currentTimeMillis();
        });
    }
}
