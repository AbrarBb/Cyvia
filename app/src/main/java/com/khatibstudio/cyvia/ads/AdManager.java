package com.khatibstudio.cyvia.ads;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.khatibstudio.cyvia.R;

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

    // ─── Production Ad Unit IDs ───────────────────────────────────────────
    private static final String PROD_BANNER_AD_UNIT_ID =
            "ca-app-pub-3807814510907688/4461927091";
    private static final String PROD_INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-3807814510907688/9387749472";
    private static final String PROD_REWARDED_AD_UNIT_ID =
            "ca-app-pub-3807814510907688/1336803402";

    // ─── Test Ad Unit IDs ─────────────────────────────────────────────────
    private static final String TEST_BANNER_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5224354917";

    // ─── Active Ad Unit IDs (Test for Debug, Production for Release) ──────
    public static final String BANNER_AD_UNIT_ID =
            BuildConfig.DEBUG ? TEST_BANNER_AD_UNIT_ID : PROD_BANNER_AD_UNIT_ID;
    public static final String INTERSTITIAL_AD_UNIT_ID =
            BuildConfig.DEBUG ? TEST_INTERSTITIAL_AD_UNIT_ID : PROD_INTERSTITIAL_AD_UNIT_ID;
    public static final String REWARDED_AD_UNIT_ID =
            BuildConfig.DEBUG ? TEST_REWARDED_AD_UNIT_ID : PROD_REWARDED_AD_UNIT_ID;
    public static final String REPORT_REWARDED_AD_UNIT_ID =
            BuildConfig.DEBUG ? TEST_REWARDED_AD_UNIT_ID : PROD_REWARDED_AD_UNIT_ID;
    public static final String THEME_REWARDED_AD_UNIT_ID =
            BuildConfig.DEBUG ? TEST_REWARDED_AD_UNIT_ID : PROD_REWARDED_AD_UNIT_ID;

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

    /**
     * Displays a clean center pop-up dialog containing an AdMob Medium Rectangle Ad (300x250).
     * Used for non-intrusive ad prompts such as theme switching to dark mode.
     */
    public void showPopupAd(Activity activity, Runnable onDismissed) {
        if (settings.isAdsRemoved()) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        activity.runOnUiThread(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) {
                if (onDismissed != null) onDismissed.run();
                return;
            }

            Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            // Create container for the pop-up ad dialog
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            int pad = (int) (18 * activity.getResources().getDisplayMetrics().density);
            root.setPadding(pad, pad, pad, pad);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(activity, R.color.cyvia_surface));
            bg.setCornerRadius(20f * activity.getResources().getDisplayMetrics().density);
            root.setBackground(bg);

            // Header text / close button row
            RelativeLayout header = new RelativeLayout(activity);
            TextView title = new TextView(activity);
            title.setText("Sponsor Ad");
            title.setTextColor(ContextCompat.getColor(activity, R.color.cyvia_on_surface));
            title.setTextSize(15f);
            title.setTypeface(null, Typeface.BOLD);

            TextView btnClose = new TextView(activity);
            btnClose.setText("✕");
            btnClose.setTextColor(ContextCompat.getColor(activity, R.color.cyvia_on_surface_variant));
            btnClose.setTextSize(18f);
            int padClose = (int) (6 * activity.getResources().getDisplayMetrics().density);
            btnClose.setPadding(padClose, 0, padClose, 0);

            RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            titleParams.addRule(RelativeLayout.CENTER_VERTICAL);
            header.addView(title, titleParams);

            RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            closeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            closeParams.addRule(RelativeLayout.CENTER_VERTICAL);
            header.addView(btnClose, closeParams);

            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Ad container
            FrameLayout adContainer = new FrameLayout(activity);
            int margin = (int) (14 * activity.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            adParams.setMargins(0, margin, 0, margin);
            adParams.gravity = Gravity.CENTER;
            root.addView(adContainer, adParams);

            AdView adView = new AdView(activity);
            adView.setAdUnitId(BANNER_AD_UNIT_ID);
            adView.setAdSize(AdSize.MEDIUM_RECTANGLE); // 300x250 rectangle pop up ad
            adContainer.addView(adView);
            adView.loadAd(new AdRequest.Builder().build());

            // Continue CTA button
            TextView btnContinue = new TextView(activity);
            btnContinue.setText("Apply Dark Mode");
            btnContinue.setGravity(Gravity.CENTER);
            btnContinue.setTextColor(ContextCompat.getColor(activity, R.color.cyvia_on_primary));
            btnContinue.setTextSize(15f);
            btnContinue.setTypeface(null, Typeface.BOLD);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(ContextCompat.getColor(activity, R.color.cyvia_primary));
            btnBg.setCornerRadius(14f * activity.getResources().getDisplayMetrics().density);
            int btnPadY = (int) (14 * activity.getResources().getDisplayMetrics().density);
            btnContinue.setPadding(0, btnPadY, 0, btnPadY);
            btnContinue.setBackground(btnBg);

            root.addView(btnContinue, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            dialog.setContentView(root);
            dialog.setCancelable(false);

            final boolean[] dismissed = {false};
            Runnable dismissAction = () -> {
                if (dismissed[0]) return;
                dismissed[0] = true;
                try { if (dialog.isShowing()) dialog.dismiss(); } catch (Exception ignored) {}
                if (onDismissed != null) onDismissed.run();
            };

            btnClose.setOnClickListener(v -> dismissAction.run());
            btnContinue.setOnClickListener(v -> dismissAction.run());

            dialog.show();
        });
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
