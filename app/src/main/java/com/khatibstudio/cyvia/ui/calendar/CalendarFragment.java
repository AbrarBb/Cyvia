package com.khatibstudio.cyvia.ui.calendar;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.MainActivity;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.ads.AdManager;
import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.model.CyclePrediction;
import com.khatibstudio.cyvia.data.model.FlowIntensity;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.databinding.FragmentCalendarBinding;
import com.khatibstudio.cyvia.domain.PredictionEngine;
import com.khatibstudio.cyvia.ui.log.DailyLogBottomSheet;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calendar screen fragment — month grid with colour-coded cycle days.
 *
 * Tapping a day shows a brief summary in the bottom panel and offers "Log this day."
 */
public class CalendarFragment extends Fragment {

    private FragmentCalendarBinding binding;
    private CalendarViewModel viewModel;
    private CalendarAdapter adapter;
    private AdManager adManager;
    private android.animation.ObjectAnimator mochiAnimator;
    private PredictionEngine.CalendarData currentCalData;
    private CycleRepository cycleRepository;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(CalendarViewModel.class);
        adManager = new AdManager(CyviaApplication.from(requireContext()).getSettingsRepository());
        adManager.preloadRewarded(requireContext());
        cycleRepository = CyviaApplication.from(requireContext()).getCycleRepository();

        setupCalendarGrid();
        setupNavigation();
        setupObservers();
        setupMochiSupportHub();
        attachBanner();
    }

    // ─── Grid ─────────────────────────────────────────────────────────────

    private void setupCalendarGrid() {
        adapter = new CalendarAdapter();
        binding.recyclerCalendar.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        binding.recyclerCalendar.setAdapter(adapter);
    }

    // ─── Navigation buttons ───────────────────────────────────────────────

    private void setupNavigation() {
        binding.btnPrevMonth.setOnClickListener(v -> {
            viewModel.goToPreviousMonth();
            updateMonthTitle();
        });
        binding.btnNextMonth.setOnClickListener(v -> {
            YearMonth targetMonth = viewModel.getDisplayedMonth().plusMonths(1);
            YearMonth maxFreeMonth = YearMonth.now().plusMonths(1);

            com.khatibstudio.cyvia.data.repository.SettingsRepository settings =
                    CyviaApplication.from(requireContext()).getSettingsRepository();

            if (!settings.isAdsRemoved() && targetMonth.isAfter(maxFreeMonth)) {
                adManager.showRewardedAd(requireActivity(), AdManager.REWARDED_AD_UNIT_ID, () -> {
                    viewModel.goToNextMonth();
                    updateMonthTitle();
                });
            } else {
                viewModel.goToNextMonth();
                updateMonthTitle();
            }
        });
        updateMonthTitle();
    }

    private void updateMonthTitle() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy");
        binding.tvMonthYear.setText(viewModel.getDisplayedMonth().format(fmt));
    }

    // ─── Observers ────────────────────────────────────────────────────────

    private void setupObservers() {
        viewModel.getCalendarData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            currentCalData = data.calData;
            adapter.setData(data.month, data.calData, data.loggedDates, this::onDayTapped);
            updateMochiSupportCard(LocalDate.now());
            updatePeriodConfirmBanner(data.calData, data.prediction);
            updateSexLifePrediction(data.month, data.calData, data.prediction);
        });
    }

    // ─── Period Confirmation Banner ───────────────────────────────────────

    /**
     * Shows a "Period Ends — Yes / No" banner when the period is active today,
     * or a "Has your period started?" banner when the predicted start date arrives.
     * Yes = confirm and update cycle data. No = dismiss for today.
     */
    private void updatePeriodConfirmBanner(PredictionEngine.CalendarData calData, CyclePrediction prediction) {
        if (binding == null || binding.cardPeriodConfirm == null) return;

        LocalDate today = LocalDate.now();

        // Check prefs: was banner dismissed for today?
        String dismissedDate = requireContext()
                .getSharedPreferences("cyvia_prefs", android.content.Context.MODE_PRIVATE)
                .getString("period_confirm_dismissed_date", "");
        if (today.toString().equals(dismissedDate)) {
            binding.cardPeriodConfirm.setVisibility(View.GONE);
            return;
        }

        boolean isPeriodDay = calData != null && calData.periodDays.contains(today);
        boolean isPredictedStart = prediction != null && prediction.nextPeriodStart != null
                && (today.equals(prediction.nextPeriodStart)
                    || (today.isAfter(prediction.nextPeriodStart)
                        && ChronoUnit.DAYS.between(prediction.nextPeriodStart, today) <= 3));

        if (isPeriodDay) {
            // Do not show the 'Period Ends' banner during active period days
            binding.cardPeriodConfirm.setVisibility(View.GONE);
        } else if (isPredictedStart) {
            // Predicted start has arrived — ask if period started
            long daysLate = ChronoUnit.DAYS.between(prediction.nextPeriodStart, today);
            String label = daysLate > 0 ? "Period " + daysLate + " day(s) late. Has it started?" : "Has your period started?";
            binding.tvPeriodConfirmLabel.setText(label);
            binding.cardPeriodConfirm.setVisibility(View.VISIBLE);

            binding.btnPeriodYes.setOnClickListener(v -> {
                // Confirm period started today
                CyviaDatabase.databaseWriteExecutor.execute(() -> {
                    java.util.List<CycleEntry> cycles = cycleRepository.getAllCyclesSync();
                    // End any ongoing cycles
                    if (cycles != null) {
                        for (CycleEntry c : cycles) {
                            if (c.isOngoing()) {
                                c.endDate = c.startDate + 4;
                                cycleRepository.updateCycle(c);
                            }
                        }
                    }
                    // Create new cycle starting today
                    CycleEntry newCycle = new CycleEntry(today.toEpochDay(), FlowIntensity.MEDIUM);
                    cycleRepository.insertCycle(newCycle);
                });
                binding.cardPeriodConfirm.setVisibility(View.GONE);
                dismissBannerForToday();
            });
            binding.btnPeriodNo.setOnClickListener(v -> {
                binding.cardPeriodConfirm.setVisibility(View.GONE);
                dismissBannerForToday();
            });

        } else {
            binding.cardPeriodConfirm.setVisibility(View.GONE);
        }
    }

    private void dismissBannerForToday() {
        requireContext()
                .getSharedPreferences("cyvia_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("period_confirm_dismissed_date", LocalDate.now().toString()).apply();
    }

    // ─── Mochi Cheering & Support Hub ─────────────────────────────────────

    private void setupMochiSupportHub() {
        if (mochiAnimator != null) mochiAnimator.cancel();
        mochiAnimator = android.animation.ObjectAnimator.ofFloat(binding.imgCalendarMochi, "translationY", -10f, 10f);
        mochiAnimator.setDuration(1300);
        mochiAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        mochiAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        mochiAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        mochiAnimator.start();

        binding.cardMochiSupport.setOnClickListener(v -> {
            binding.imgCalendarMochi.animate()
                    .scaleX(1.22f).scaleY(1.22f).rotationBy(360f)
                    .setDuration(450)
                    .withEndAction(() -> {
                        binding.imgCalendarMochi.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                    }).start();
            cycleSupportMessage();
        });

        updateMochiSupportCard(LocalDate.now());
    }

    private void updateMochiSupportCard(LocalDate date) {
        if (binding == null) return;
        int hour = LocalTime.now().getHour();

        if (currentCalData != null && currentCalData.periodDays.contains(date)) {
            binding.imgCalendarMochi.setImageResource(R.drawable.ic_mochi_hugging);
            binding.tvMochiSupportTitle.setText("Mochi's Warm Cuddles ~ 🌸");
            binding.tvMochiSupportMessage.setText("Period phase care: Keep warm, hydrate with soothing herbal tea, and let Mochi send you comforting energy today ~");
        } else if (currentCalData != null && ((currentCalData.ovulationDays != null && currentCalData.ovulationDays.contains(date)) || date.equals(currentCalData.ovulationDay))) {
            binding.imgCalendarMochi.setImageResource(R.drawable.ic_mochi_celebrating);
            binding.tvMochiSupportTitle.setText("Peak Vitality & Glow! 🎉");
            binding.tvMochiSupportMessage.setText("Estimated Ovulation Day! Your natural vitality and glow are shining bright. Embrace confidence and joy today!");
        } else if (currentCalData != null && currentCalData.fertileDays.contains(date)) {
            binding.imgCalendarMochi.setImageResource(R.drawable.ic_mochi_smiling);
            binding.tvMochiSupportTitle.setText("High Energy Cheer ✨");
            binding.tvMochiSupportMessage.setText("Fertile window detected! A great time for outdoor creativity, social connections, and radiant positivity.");
        } else {
            if (hour < 12) {
                binding.imgCalendarMochi.setImageResource(R.drawable.ic_mochi_waving);
                binding.tvMochiSupportTitle.setText("Rise & Shine Cheer! ☀️");
                binding.tvMochiSupportMessage.setText("Good morning! Mochi is stretching out to send you wonderful morning energy. Sip some water and smile today ~");
            } else if (hour < 18) {
                binding.imgCalendarMochi.setImageResource(R.drawable.ic_mochi_celebrating);
                binding.tvMochiSupportTitle.setText("Afternoon Power Boost! 🚀");
                binding.tvMochiSupportMessage.setText("You are doing amazing today! Relax your shoulders, take a deep breath, and keep up your wonderful momentum!");
            } else {
                binding.imgCalendarMochi.setImageResource(R.drawable.ic_mochi_sleeping);
                binding.tvMochiSupportTitle.setText("Cozy Evening Rest 🌙");
                binding.tvMochiSupportMessage.setText("Time to wind down your day. Snuggle up comfortably—Mochi is watching over your peaceful relaxation tonight ~");
            }
        }
    }

    private void cycleSupportMessage() {
        if (binding == null) return;
        String[] titles = {
                "Mochi Believes in You! 💖",
                "Self-Care Check-in ✨",
                "Radiant Health Vibe 🌸",
                "You Are Unstoppable! 🎉"
        };
        String[] messages = {
                "Your body does amazing things every single day. Take a moment to appreciate your health and inner peace ~",
                "Remember: listening to your body's rhythm is the ultimate self-care. Mochi is always here to support your journey!",
                "Drink a fresh glass of water, adjust your posture, and take a gentle relaxing breath. You got this!",
                "Sending a big fluffy Kawaii cat hug directly to your screen! Keep shining bright today ~ ✨"
        };
        int idx = (int) (Math.random() * titles.length);
        binding.tvMochiSupportTitle.setText(titles[idx]);
        binding.tvMochiSupportMessage.setText(messages[idx]);
    }

    // ─── Day tap ──────────────────────────────────────────────────────────

    private void onDayTapped(LocalDate date) {
        // Highlight the selected date cell
        adapter.setSelectedDate(date);

        // Show the detail panel
        binding.layoutDayDetail.setVisibility(View.VISIBLE);
        updateMochiSupportCard(date);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, MMMM d");
        binding.tvSelectedDate.setText(date.format(fmt));

        boolean isToday = date.equals(LocalDate.now());
        boolean isFuture = date.isAfter(LocalDate.now());
        boolean isPeriodDay = currentCalData != null && currentCalData.periodDays.contains(date);

        // Clear listeners to avoid self-triggering during state restoration
        binding.togglePeriodStarts.clearOnButtonCheckedListeners();

        if (isFuture) {
            binding.tvDaySummary.setText("Future prediction date");
            binding.btnLogSelectedDay.setText("Cannot log future dates");
            binding.btnLogSelectedDay.setEnabled(false);
            binding.btnLogSelectedDay.setAlpha(0.4f);

            // Disable toggling for future dates
            binding.togglePeriodStarts.setEnabled(false);
            binding.btnPeriodStartsYes.setEnabled(false);
            binding.btnPeriodStartsNo.setEnabled(false);
            binding.togglePeriodStarts.check(View.NO_ID);
        } else {
            binding.tvDaySummary.setText(isToday ? "Tap below to log today" : "Tap below to log this day");
            binding.btnLogSelectedDay.setText("Log this day");
            binding.btnLogSelectedDay.setEnabled(true);
            binding.btnLogSelectedDay.setAlpha(1.0f);

            // Enable toggling for past/present dates
            binding.togglePeriodStarts.setEnabled(true);
            binding.btnPeriodStartsYes.setEnabled(true);
            binding.btnPeriodStartsNo.setEnabled(true);

            if (isPeriodDay) {
                binding.togglePeriodStarts.check(R.id.btn_period_starts_yes);
            } else {
                binding.togglePeriodStarts.check(R.id.btn_period_starts_no);
            }
            updatePeriodToggleButtonsStyle(isPeriodDay);

            // Set up checked change listener to dynamically create/delete cycle logs
            binding.togglePeriodStarts.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    boolean isYes = checkedId == R.id.btn_period_starts_yes;
                    updatePeriodToggleButtonsStyle(isYes);

                    if (isYes) {
                        CyviaDatabase.databaseWriteExecutor.execute(() -> {
                            // End any ongoing cycles first
                            java.util.List<CycleEntry> cycles = cycleRepository.getAllCyclesSync();
                            if (cycles != null) {
                                for (CycleEntry c : cycles) {
                                    if (c.isOngoing()) {
                                        long endEpoch = date.toEpochDay() - 1;
                                        if (endEpoch >= c.startDate) {
                                            c.endDate = endEpoch;
                                        } else {
                                            c.endDate = c.startDate;
                                        }
                                        cycleRepository.updateCycle(c);
                                    }
                                }
                            }
                            // Create new cycle starting on this date
                            CycleEntry newCycle = new CycleEntry(date.toEpochDay(), FlowIntensity.MEDIUM);
                            cycleRepository.insertCycle(newCycle);
                        });
                        android.widget.Toast.makeText(requireContext(), "Period started on " + date.format(DateTimeFormatter.ofPattern("MMM d")), android.widget.Toast.LENGTH_SHORT).show();
                    } else if (checkedId == R.id.btn_period_starts_no) {
                        // User unselected or clicked No, clear/trim flow on this date
                        removeFlowForDate(date);
                        android.widget.Toast.makeText(requireContext(), "Period flow removed for " + date.format(DateTimeFormatter.ofPattern("MMM d")), android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        binding.btnLogSelectedDay.setOnClickListener(v -> {
            if (date.isAfter(LocalDate.now())) {
                android.widget.Toast.makeText(requireContext(), "Cannot log symptoms for future dates", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            DailyLogBottomSheet sheet = DailyLogBottomSheet.newInstance(date);
            sheet.show(getParentFragmentManager(), DailyLogBottomSheet.TAG);
        });
    }

    /**
     * Helper to completely remove or trim period flow entries spanning the targeted date.
     */
    private void removeFlowForDate(LocalDate date) {
        long targetDay = date.toEpochDay();
        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            com.khatibstudio.cyvia.data.db.dao.CycleEntryDao dao = CyviaDatabase.getDatabase(requireContext()).cycleEntryDao();
            List<CycleEntry> cycles = dao.getAllCyclesSync();
            if (cycles != null) {
                for (CycleEntry cycle : cycles) {
                    long start = cycle.startDate;
                    long end = cycle.isOngoing() ? LocalDate.now().toEpochDay() : cycle.endDate;
                    if (targetDay >= start && targetDay <= end) {
                        if (start == end || (cycle.isOngoing() && start == targetDay)) {
                            dao.deleteCycleEntry(cycle);
                        } else if (start == targetDay) {
                            cycle.startDate = targetDay + 1;
                            dao.updateCycleEntry(cycle);
                        } else if (end == targetDay || (cycle.isOngoing() && targetDay == LocalDate.now().toEpochDay())) {
                            cycle.endDate = targetDay - 1;
                            if (cycle.endDate < start) {
                                dao.deleteCycleEntry(cycle);
                            } else {
                                dao.updateCycleEntry(cycle);
                            }
                        } else if (start < targetDay && targetDay < end) {
                            cycle.endDate = targetDay - 1;
                            if (cycle.endDate < start) {
                                dao.deleteCycleEntry(cycle);
                            } else {
                                dao.updateCycleEntry(cycle);
                            }
                        }
                        break;
                    }
                }
            }
        });
    }

    /**
     * Dynamically updates the backgrounds and text colors of the Period Starts Yes/No buttons.
     * Yes: Checked = Period Red, Unchecked = Transparent Outline
     * No: Checked = Selected Tonal container, Unchecked = Transparent Outline
     */
    private void updatePeriodToggleButtonsStyle(boolean isPeriod) {
        if (getContext() == null) return;
        int periodRed = requireContext().getColor(R.color.period_red);
        int onPrimary = requireContext().getColor(R.color.cyvia_on_primary);
        int transparent = requireContext().getColor(R.color.transparent);
        int outline = requireContext().getColor(R.color.cyvia_outline);
        int onSurfaceVariant = requireContext().getColor(R.color.cyvia_on_surface_variant);
        int primaryContainer = requireContext().getColor(R.color.cyvia_primary_container);
        int onPrimaryContainer = requireContext().getColor(R.color.cyvia_on_primary_container);

        if (isPeriod) {
            // Yes is checked (Red background, white text)
            binding.btnPeriodStartsYes.setBackgroundTintList(android.content.res.ColorStateList.valueOf(periodRed));
            binding.btnPeriodStartsYes.setTextColor(onPrimary);
            binding.btnPeriodStartsYes.setStrokeColor(android.content.res.ColorStateList.valueOf(periodRed));

            // No is unchecked (Outlined transparent background, neutral text)
            binding.btnPeriodStartsNo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(transparent));
            binding.btnPeriodStartsNo.setTextColor(onSurfaceVariant);
            binding.btnPeriodStartsNo.setStrokeColor(android.content.res.ColorStateList.valueOf(outline));
        } else {
            // Yes is unchecked (Outlined transparent background, neutral text)
            binding.btnPeriodStartsYes.setBackgroundTintList(android.content.res.ColorStateList.valueOf(transparent));
            binding.btnPeriodStartsYes.setTextColor(onSurfaceVariant);
            binding.btnPeriodStartsYes.setStrokeColor(android.content.res.ColorStateList.valueOf(outline));

            // No is checked (Default selected lavender background and text)
            binding.btnPeriodStartsNo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryContainer));
            binding.btnPeriodStartsNo.setTextColor(onPrimaryContainer);
            binding.btnPeriodStartsNo.setStrokeColor(android.content.res.ColorStateList.valueOf(outline));
        }
    }

    // ─── Sex Life Prediction Card ─────────────────────────────────────────

    private void updateSexLifePrediction(YearMonth month, PredictionEngine.CalendarData calData, CyclePrediction prediction) {
        if (binding == null || binding.cardSexLifePrediction == null) return;

        com.khatibstudio.cyvia.data.repository.SettingsRepository settings =
                com.khatibstudio.cyvia.CyviaApplication.from(requireContext()).getSettingsRepository();

        if (settings.isMinorSafeMode() || !settings.isTrackIntimacyEnabled()) {
            binding.cardSexLifePrediction.setVisibility(View.GONE);
            return;
        }

        binding.cardSexLifePrediction.setVisibility(View.VISIBLE);

        boolean isTtc = settings.getTrackingMode() == com.khatibstudio.cyvia.data.model.TrackingMode.TRYING_TO_CONCEIVE;

        // Group days of the month
        List<LocalDate> nopeDates = new ArrayList<>();
        List<LocalDate> protectedDates = new ArrayList<>();
        List<LocalDate> unprotectedDates = new ArrayList<>();

        int daysInMonth = month.lengthOfMonth();
        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = month.atDay(d);
            boolean isPeriod = (calData != null && (calData.periodDays.contains(date) || calData.predictedDays.contains(date)));
            boolean isHighFertility = (calData != null && (calData.fertileDays.contains(date) || calData.ovulationDays.contains(date)));

            if (isPeriod) {
                nopeDates.add(date);
            } else if (isHighFertility) {
                if (isTtc) {
                    unprotectedDates.add(date);
                } else {
                    protectedDates.add(date);
                }
            } else {
                unprotectedDates.add(date);
            }
        }

        // Row 1: Nope (No sex during period)
        if (!nopeDates.isEmpty()) {
            binding.rowPredNope.setVisibility(View.VISIBLE);
            binding.tvPredNopeTitle.setText("No Sex (Period days)");
            binding.tvPredNopeDates.setText(formatRanges(nopeDates));
        } else {
            binding.rowPredNope.setVisibility(View.GONE);
        }

        // Row 2: Protected Sex
        if (!protectedDates.isEmpty()) {
            binding.rowPredProtected.setVisibility(View.VISIBLE);
            if (isTtc) {
                // Trying to conceive doesn't have "protected sex" periods
                binding.rowPredProtected.setVisibility(View.GONE);
            } else {
                binding.tvPredProtectedTitle.setText("Protected Sex (High chance)");
                binding.tvPredProtectedDates.setText(formatRanges(protectedDates));
            }
        } else {
            binding.rowPredProtected.setVisibility(View.GONE);
        }

        // Row 3: Unprotected Sex
        if (!unprotectedDates.isEmpty()) {
            binding.rowPredUnprotected.setVisibility(View.VISIBLE);
            if (isTtc) {
                binding.tvPredUnprotectedTitle.setText("Unprotected Sex (Other days)");
            } else {
                binding.tvPredUnprotectedTitle.setText("Unprotected Sex (Low chance)");
            }
            binding.tvPredUnprotectedDates.setText(formatRanges(unprotectedDates));
        } else {
            binding.rowPredUnprotected.setVisibility(View.GONE);
        }
    }

    private String formatRanges(List<LocalDate> dates) {
        if (dates.isEmpty()) return "None";
        Collections.sort(dates);
        List<String> ranges = new ArrayList<>();
        LocalDate start = dates.get(0);
        LocalDate prev = start;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        for (int i = 1; i < dates.size(); i++) {
            LocalDate curr = dates.get(i);
            if (ChronoUnit.DAYS.between(prev, curr) == 1) {
                prev = curr;
            } else {
                if (start.equals(prev)) {
                    ranges.add(start.format(fmt));
                } else {
                    ranges.add(start.format(fmt) + " - " + prev.format(fmt));
                }
                start = curr;
                prev = curr;
            }
        }
        if (start.equals(prev)) {
            ranges.add(start.format(fmt));
        } else {
            ranges.add(start.format(fmt) + " - " + prev.format(fmt));
        }
        return String.join(", ", ranges);
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
        if (mochiAnimator != null) {
            mochiAnimator.cancel();
            mochiAnimator = null;
        }
        super.onDestroyView();
        binding = null;
    }
}
