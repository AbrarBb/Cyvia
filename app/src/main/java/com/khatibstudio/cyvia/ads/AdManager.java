package com.khatibstudio.cyvia.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
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

    // ─── Test Ad Unit IDs (replace with real IDs before publishing) ───────
    // Real IDs will be provided by the developer.
    // DO NOT hardcode real IDs in version control.
    public static final String BANNER_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/6300978111";       // Google test banner
    public static final String INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/1033173712";       // Google test interstitial
    public static final String REWARDED_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5224354917";       // Google test rewarded

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

        container.setVisibility(View.VISIBLE);
        CyviaApplication.from(activity).initAdMobIfNeeded(status -> {
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed() || activity.isFinishing()) return;
                AdView adView = new AdView(activity);
                adView.setAdUnitId(BANNER_AD_UNIT_ID);
                adView.setAdSize(AdSize.BANNER);

                container.removeAllViews();
                container.addView(adView);

                AdRequest adRequest = new AdRequest.Builder().build();
                adView.loadAd(adRequest);
            });
        });
        // Banner fails gracefully — if offline, container stays but shows nothing visible
    }

    // ─── Interstitial (Disabled by Policy) ───────────────────────────────

    /**
     * Interstitial ads are disabled by policy to protect user privacy and avoid intrusive UX
     * during intimate health log entry moments.
     */
    public void preloadInterstitial(Context context) {
        // No-op: Interstitials disabled
        Log.d(TAG, "Interstitial ads disabled by policy.");
    }

    /**
     * Interstitial ads are disabled by policy.
     */
    public void maybeShowInterstitial(Activity activity) {
        // No-op: Never show full-screen intrusive ads on saving health logs
        Log.d(TAG, "Interstitial ads disabled by policy.");
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
        if (settings.isAdsRemoved()) return;
        if (rewardedAd != null) return;

        CyviaApplication.from(context).initAdMobIfNeeded(status -> {
            AdRequest adRequest = new AdRequest.Builder().build();
            RewardedAd.load(context, REWARDED_AD_UNIT_ID, adRequest,
                    new RewardedAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull RewardedAd ad) {
                            rewardedAd = ad;
                            Log.d(TAG, "Rewarded ad loaded");
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError error) {
                            Log.d(TAG, "Rewarded ad failed to load: " + error.getMessage());
                            rewardedAd = null;
                        }
                    });
        });
    }

    public void showRewardedAd(Activity activity, Runnable onRewardEarned) {
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
            displayLoadedRewardedAd(activity, onRewardEarned);
        } else {
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(activity);
            progressDialog.setMessage("Loading video ad to unlock report...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            CyviaApplication.from(activity).initAdMobIfNeeded(status -> {
                AdRequest adRequest = new AdRequest.Builder().build();
                RewardedAd.load(activity, REWARDED_AD_UNIT_ID, adRequest,
                        new RewardedAdLoadCallback() {
                            @Override
                            public void onAdLoaded(@NonNull RewardedAd ad) {
                                if (progressDialog.isShowing()) progressDialog.dismiss();
                                rewardedAd = ad;
                                displayLoadedRewardedAd(activity, onRewardEarned);
                            }

                            @Override
                            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                                if (progressDialog.isShowing()) progressDialog.dismiss();
                                Log.d(TAG, "Rewarded ad failed to load: " + error.getMessage());
                                lastRewardedAdShowTimeMs = System.currentTimeMillis();
                                android.widget.Toast.makeText(activity, "Ad temporarily unavailable. Generating report...", android.widget.Toast.LENGTH_SHORT).show();
                                if (onRewardEarned != null) onRewardEarned.run();
                            }
                        });
            });
        }
    }

    private void displayLoadedRewardedAd(Activity activity, Runnable onRewardEarned) {
        if (rewardedAd == null) return;
        final boolean[] rewardEarned = {false};
        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                rewardedAd = null;
                preloadRewarded(activity);
                if (rewardEarned[0]) {
                    if (onRewardEarned != null) onRewardEarned.run();
                } else {
                    android.widget.Toast.makeText(activity, "Please watch the full ad to generate your Doctor Report ✨", android.widget.Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                rewardedAd = null;
                preloadRewarded(activity);
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
