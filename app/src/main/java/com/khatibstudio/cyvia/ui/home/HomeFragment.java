package com.khatibstudio.cyvia.ui.home;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.MainActivity;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.ads.AdManager;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.model.CyclePrediction;
import com.khatibstudio.cyvia.databinding.FragmentHomeBinding;
import com.khatibstudio.cyvia.domain.MochiCareEngine;
import com.khatibstudio.cyvia.ui.log.DailyLogBottomSheet;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;

/**
 * Home screen fragment — the main landing screen of Cyvia.
 *
 * Shows:
 *   - Greeting + Mochi illustration
 *   - Period countdown / current day status
 *   - Cycle day number + current phase
 *   - Fertile window card (if enabled by TrackingMode)
 *   - Quick Log button
 *   - AdMob banner (if ads not removed)
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private AdManager adManager;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        adManager = new AdManager(CyviaApplication.from(requireContext()).getSettingsRepository());

        setupGreeting();
        setupObservers();
        setupClickListeners();
        attachBanner();
        updateMochiStateAndAnimation();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refresh();
        }
        setupGreeting();
        updateMochiStateAndAnimation();
        updateMochiCareMessage();
    }

    private String mochiToastMessage = "Mochi says hi! ~ ♡";
    private android.animation.ObjectAnimator currentMochiAnimator;

    private void updateMochiStateAndAnimation() {
        if (binding == null || binding.imgMochiHome == null || getContext() == null) return;

        int hour = java.time.LocalTime.now().getHour();
        DailyLog todayLog = viewModel.getTodayLog().getValue();
        List<DailyLog> allLogs = viewModel.getAllLogs().getValue();
        Integer cycleDay = viewModel.getCycleDay().getValue();

        int periodLen = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgPeriodLength();
        if (periodLen <= 0) periodLen = 5;

        // Calculate streak & missed logs
        int streak = 0;
        long maxLoggedDay = 0;
        if (allLogs != null && !allLogs.isEmpty()) {
            java.util.List<DailyLog> sorted = new java.util.ArrayList<>(allLogs);
            sorted.sort((a, b) -> Long.compare(b.date, a.date));
            maxLoggedDay = sorted.get(0).date;

            long checkDay = java.time.LocalDate.now().toEpochDay();
            if (sorted.get(0).date != checkDay && sorted.get(0).date != checkDay - 1) {
                streak = 0;
            } else {
                long curr = sorted.get(0).date;
                streak = 1;
                for (int i = 1; i < sorted.size(); i++) {
                    if (sorted.get(i).date == curr - 1) {
                        streak++;
                        curr--;
                    } else if (sorted.get(i).date < curr - 1) {
                        break;
                    }
                }
            }
        }

        int targetDrawable;
        String animType;

        // Priority 1: Sleeping (Night 10 PM - 6 AM)
        if (hour >= 22 || hour < 6) {
            targetDrawable = R.drawable.ic_mochi_sleeping;
            mochiToastMessage = "Zzz... Mochi is resting smoothly for the night 🌙";
            animType = "breathe";
        }
        // Priority 2: Sick (Severe cramps / unwell logged today)
        else if (todayLog != null && todayLog.symptomIds != null &&
                (todayLog.symptomIds.contains("1") || todayLog.symptomIds.contains("2") ||
                 todayLog.symptomIds.contains("4") || todayLog.symptomIds.contains("7"))) {
            targetDrawable = R.drawable.ic_mochi_drinking_tea;
            mochiToastMessage = "Mochi is holding warm healing tea for your cramps and aches 🍵💕";
            animType = "sway";
        }
        // Priority 3: Celebrating (30-day streak OR period finished)
        else if (streak >= 30 || (cycleDay != null && (cycleDay == periodLen || cycleDay == periodLen + 1))) {
            targetDrawable = R.drawable.ic_mochi_sparkles;
            mochiToastMessage = streak >= 30
                    ? "Yay! Mochi is celebrating your amazing " + streak + "-day logging streak with sparkles! ✨"
                    : "Yay! Mochi is celebrating the finish of your period! ✨";
            animType = "bounce";
        }
        // Priority 4: Worried (User missed logs for 7 days)
        else if (allLogs != null && !allLogs.isEmpty() && (java.time.LocalDate.now().toEpochDay() - maxLoggedDay >= 7)) {
            targetDrawable = R.drawable.ic_mochi_worried;
            mochiToastMessage = "Mochi missed checking in with you! Tap Quick Log below 💕";
            animType = "shiver";
        }
        // Priority 5: Happy (User logged today)
        else if (todayLog != null) {
            targetDrawable = R.drawable.ic_mochi_heart_eyes;
            mochiToastMessage = "Purr... Mochi is so proud and loves you for logging today! 🐱♡";
            animType = "bounce_gentle";
        }
        // Priority 6: Phase & Time Fallback
        else {
            int avgLen = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgCycleLength();
            String phase = (cycleDay != null) ? viewModel.getCyclePhase(cycleDay, avgLen) : "FOLLICULAR";
            if ("MENSTRUAL".equals(phase) || (cycleDay != null && cycleDay <= periodLen)) {
                targetDrawable = R.drawable.ic_mochi_cozy;
                mochiToastMessage = "Mochi is wrapped in a cozy blanket sending you warm period hugs ~ ♡";
                animType = "sway";
            } else if ("OVULATORY".equals(phase)) {
                targetDrawable = R.drawable.ic_mochi_heart_eyes;
                mochiToastMessage = "Mochi is glowing with peak vitality during your fertile window! ~ ♡";
                animType = "bounce_gentle";
            } else if ("FOLLICULAR".equals(phase)) {
                if (hour < 12) {
                    targetDrawable = R.drawable.ic_mochi_stretching;
                    mochiToastMessage = "Good morning! Mochi is stretching with fresh rising energy ~ ✨";
                } else {
                    targetDrawable = R.drawable.ic_mochi_reading;
                    mochiToastMessage = "Mochi is reading wellness guides and checking in with you! ~ ♡";
                }
                animType = "bounce_gentle";
            } else {
                // LUTEAL
                if (hour >= 18) {
                    targetDrawable = R.drawable.ic_mochi_cozy;
                    mochiToastMessage = "Mochi is relaxing and staying cozy during your luteal evening ~ 🌸";
                } else {
                    targetDrawable = R.drawable.ic_mochi_drinking_tea;
                    mochiToastMessage = "Mochi is sipping soothing herbal tea for luteal balance ~ 🍵";
                }
                animType = "sway";
            }
        }

        binding.imgMochiHome.setImageResource(targetDrawable);
        applyMochiAnimation(animType);
    }

    private void applyMochiAnimation(String animType) {
        if (binding == null || binding.imgMochiHome == null) return;
        if (currentMochiAnimator != null) {
            currentMochiAnimator.cancel();
        }
        binding.imgMochiHome.setTranslationY(0f);
        binding.imgMochiHome.setRotation(0f);
        binding.imgMochiHome.setScaleX(1f);
        binding.imgMochiHome.setScaleY(1f);

        switch (animType) {
            case "breathe":
                currentMochiAnimator = android.animation.ObjectAnimator.ofFloat(binding.imgMochiHome, "scaleY", 1f, 1.05f);
                currentMochiAnimator.setDuration(1800);
                break;
            case "sway":
                currentMochiAnimator = android.animation.ObjectAnimator.ofFloat(binding.imgMochiHome, "rotation", -4f, 4f);
                currentMochiAnimator.setDuration(1500);
                break;
            case "bounce":
                currentMochiAnimator = android.animation.ObjectAnimator.ofFloat(binding.imgMochiHome, "translationY", 0f, -14f);
                currentMochiAnimator.setDuration(800);
                break;
            case "shiver":
                currentMochiAnimator = android.animation.ObjectAnimator.ofFloat(binding.imgMochiHome, "rotation", -2.5f, 2.5f);
                currentMochiAnimator.setDuration(500);
                break;
            case "bounce_gentle":
            default:
                currentMochiAnimator = android.animation.ObjectAnimator.ofFloat(binding.imgMochiHome, "translationY", 0f, -8f);
                currentMochiAnimator.setDuration(2400);
                break;
        }
        currentMochiAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        currentMochiAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        currentMochiAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        currentMochiAnimator.start();

        binding.imgMochiHome.setOnClickListener(v -> {
            binding.imgMochiHome.animate()
                    .scaleX(1.18f).scaleY(0.85f).setDuration(120)
                    .withEndAction(() -> binding.imgMochiHome.animate().scaleX(1f).scaleY(1f).setDuration(250).start())
                    .start();
            android.widget.Toast.makeText(requireContext(), mochiToastMessage, android.widget.Toast.LENGTH_LONG).show();
        });
    }

    // ─── Setup ────────────────────────────────────────────────────────────

    private void setupGreeting() {
        String name = viewModel.getUserName();
        String greetingWord;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) greetingWord = getString(R.string.greeting_morning);
        else if (hour < 17) greetingWord = getString(R.string.greeting_afternoon);
        else greetingWord = getString(R.string.greeting_evening);

        binding.tvGreeting.setText(greetingWord + (name.isEmpty() ? "" : ", "));
            binding.tvUserName.setText(name);
    }

    private void setupObservers() {
        // Prediction → update status card
        viewModel.getPrediction().observe(getViewLifecycleOwner(), prediction -> {
            if (prediction == null || !prediction.hasData()) {
                binding.tvStatusLabel.setText("Getting started");
                binding.tvStatusMain.setText(getString(R.string.home_no_data));
                binding.tvLowConfidence.setVisibility(View.GONE);
                binding.cardFertileWindow.setVisibility(View.GONE);
                return;
            }
            updateStatusCard(prediction);
        });

        // Cycle day → update day card
        viewModel.getCycleDay().observe(getViewLifecycleOwner(), day -> {
            if (day == null) {
                binding.tvCycleDayNumber.setText("—");
                binding.tvPhaseName.setText("—");
                binding.imgPhaseIcon.setImageResource(R.drawable.ic_phase_menstrual);
                return;
            }
            binding.tvCycleDayNumber.setText(String.valueOf(day));
            updatePhaseDisplay(day);
            updateMochiStateAndAnimation();
        });

        // Today's log → update dynamic care message
        viewModel.getTodayLog().observe(getViewLifecycleOwner(), log -> {
            updateMochiCareMessage();
            updateMochiStateAndAnimation();
        });

        // All logs → evaluate streaks and missed days for Mochi
        viewModel.getAllLogs().observe(getViewLifecycleOwner(), logs -> {
            updateMochiStateAndAnimation();
        });
    }

    private void setupClickListeners() {
        binding.btnQuickLog.setOnClickListener(v -> openDailyLog());
    }

    // ─── Status card ─────────────────────────────────────────────────────

    private void updateStatusCard(CyclePrediction prediction) {
        LocalDate today = LocalDate.now();
        LocalDate nextPeriod = prediction.nextPeriodStart;
        long daysUntil = ChronoUnit.DAYS.between(today, nextPeriod);

        Integer cDay = viewModel.getCycleDay().getValue();
        int cycleDay = (cDay != null && cDay > 0) ? cDay : 1;
        int avgCycleLen = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgCycleLength();
        int avgPeriodLen = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgPeriodLength();
        String phase = viewModel.getCyclePhase(cycleDay, avgCycleLen);

        // Low confidence caveat
        if (prediction.isLowConfidence) {
            binding.tvLowConfidence.setVisibility(View.VISIBLE);
            binding.tvLowConfidence.setText(
                    getString(R.string.home_prediction_low_confidence, prediction.cyclesUsed));
        } else {
            binding.tvLowConfidence.setVisibility(View.GONE);
        }

        boolean minimalMode = requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE).getBoolean("minimal_mode", false);
        boolean hideFertile = minimalMode || CyviaApplication.from(requireContext()).getSettingsRepository().isMinorSafeMode() || !viewModel.shouldShowFertileWindow();

        if (binding.cardMochiBanner != null) {
            binding.cardMochiBanner.setVisibility(minimalMode ? View.GONE : View.VISIBLE);
        }

        if (daysUntil < 0) {
            binding.tvStatusLabel.setText("Period Late");
            long lateDays = Math.abs(daysUntil);
            binding.tvStatusMain.setText(lateDays + (lateDays == 1 ? " Day Late" : " Days Late"));
            binding.tvFertileDates.setVisibility(View.VISIBLE);
            binding.tvFertileDates.setText("Tap Calendar or Quick Log to update your flow status");
        } else if ("MENSTRUAL".equals(phase) || cycleDay <= avgPeriodLen) {
            binding.tvStatusLabel.setText("Period Phase");
            binding.tvStatusMain.setText("Day " + cycleDay + " of Flow");
            binding.tvFertileDates.setVisibility(View.VISIBLE);
            binding.tvFertileDates.setText(hideFertile ? "Rest & self-care today" : "Low fertility · Rest & care");
        } else if ("OVULATORY".equals(phase) || (prediction.fertileWindowStart != null && !today.isBefore(prediction.fertileWindowStart) && !today.isAfter(prediction.fertileWindowEnd))) {
            binding.tvStatusLabel.setText("Ovulation Window");
            binding.tvStatusMain.setText("High Fertility");
            binding.tvFertileDates.setVisibility(View.VISIBLE);
            if (hideFertile) {
                binding.tvFertileDates.setVisibility(View.GONE);
            } else {
                binding.tvFertileDates.setText("High pregnancy chance · Use protection if avoiding");
            }
        } else if ("FOLLICULAR".equals(phase)) {
            binding.tvStatusLabel.setText("Follicular Phase");
            binding.tvStatusMain.setText("Cycle Day " + cycleDay);
            binding.tvFertileDates.setVisibility(View.VISIBLE);
            if (hideFertile) {
                binding.tvFertileDates.setText("Rising energy & vitality");
            } else {
                binding.tvFertileDates.setText("Low pregnancy chance · Unprotected/protected intimacy");
            }
        } else {
            // LUTEAL Phase
            binding.tvStatusLabel.setText("Luteal Phase");
            if (daysUntil > 0) {
                binding.tvStatusMain.setText("Period in " + daysUntil + (daysUntil == 1 ? " day" : " days"));
            } else if (daysUntil == 0) {
                binding.tvStatusMain.setText("Period Today");
            } else {
                binding.tvStatusMain.setText("Day " + cycleDay);
            }
            binding.tvFertileDates.setVisibility(View.VISIBLE);
            binding.tvFertileDates.setText(hideFertile ? "Self-care & PMS comfort" : "Low fertility · PMS comfort & care");
        }

        refreshCycleRing();
    }

    private void refreshCycleRing() {
        if (binding == null || binding.cycleRingView == null || getContext() == null) return;
        int cycleLen = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgCycleLength();
        CyclePrediction prediction = viewModel.getPrediction().getValue();
        if (prediction != null && prediction.averageCycleLength > 0) {
            cycleLen = prediction.averageCycleLength;
        }
        int periodLen = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgPeriodLength();
        if (periodLen <= 0 || periodLen >= cycleLen) {
            periodLen = 5;
        }

        int ovDay = Math.max(periodLen + 6, cycleLen - 14);
        int fertileStart = Math.max(periodLen + 1, ovDay - 2);
        int fertileEnd = Math.min(cycleLen, ovDay + 2);

        Integer day = viewModel.getCycleDay().getValue();
        int currentDay = (day != null && day > 0) ? day : 1;

        boolean minimal = requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE).getBoolean("minimal_mode", false)
                || CyviaApplication.from(requireContext()).getSettingsRepository().isMinorSafeMode();
        binding.cycleRingView.setMinimalMode(minimal);
        binding.cycleRingView.setCycleData(cycleLen, periodLen, fertileStart, fertileEnd, currentDay);
    }

    // ─── Phase display ────────────────────────────────────────────────────

    private void updatePhaseDisplay(int cycleDayNum) {
        int avgCycleLength = 28;
        if (getContext() != null) {
            avgCycleLength = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgCycleLength();
            CyclePrediction pred = viewModel.getPrediction().getValue();
            if (pred != null && pred.averageCycleLength > 0) {
                avgCycleLength = pred.averageCycleLength;
            }
        }
        String phase = viewModel.getCyclePhase(cycleDayNum, avgCycleLength);

        switch (phase) {
            case "MENSTRUAL":
                binding.tvPhaseName.setText(getString(R.string.home_phase_menstrual));
                binding.imgPhaseIcon.setImageResource(R.drawable.ic_phase_menstrual);
                break;
            case "FOLLICULAR":
                binding.tvPhaseName.setText(getString(R.string.home_phase_follicular));
                binding.imgPhaseIcon.setImageResource(R.drawable.ic_phase_follicular);
                break;
            case "OVULATORY":
                binding.tvPhaseName.setText(getString(R.string.home_phase_ovulatory));
                binding.imgPhaseIcon.setImageResource(R.drawable.ic_phase_ovulatory);
                break;
            case "LUTEAL":
                binding.tvPhaseName.setText(getString(R.string.home_phase_luteal));
                binding.imgPhaseIcon.setImageResource(R.drawable.ic_phase_luteal);
                break;
        }

        updateMochiCareMessage();
        refreshCycleRing();
        CyclePrediction pred = viewModel.getPrediction().getValue();
        if (pred != null && pred.hasData()) {
            updateStatusCard(pred);
        }
    }

    private void updateMochiCareMessage() {
        Integer day = viewModel.getCycleDay().getValue();
        int avgLen = 28;
        if (getContext() != null) {
            avgLen = CyviaApplication.from(requireContext()).getSettingsRepository().getAvgCycleLength();
        }
        String phase = (day != null) ? viewModel.getCyclePhase(day, avgLen) : "FOLLICULAR";
        DailyLog todayLog = viewModel.getTodayLog().getValue();

        String careMsg = MochiCareEngine.generateCareMessage(todayLog, day, phase);
        binding.tvMochiMessage.setText(careMsg);
    }

    // ─── Daily log ────────────────────────────────────────────────────────

    private void openDailyLog() {
        DailyLogBottomSheet sheet = DailyLogBottomSheet.newInstance(null); // today
        sheet.show(getParentFragmentManager(), DailyLogBottomSheet.TAG);
    }

    // ─── AdMob banner ─────────────────────────────────────────────────────

    private void attachBanner() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            adManager.attachBanner(activity, binding.adBannerContainer);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentMochiAnimator != null) {
            currentMochiAnimator.cancel();
            currentMochiAnimator = null;
        }
        binding = null;
    }
}
