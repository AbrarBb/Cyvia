package com.khatibstudio.cyvia.ui.calendar;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.MainActivity;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.ads.AdManager;
import com.khatibstudio.cyvia.databinding.FragmentCalendarBinding;
import com.khatibstudio.cyvia.domain.PredictionEngine;
import com.khatibstudio.cyvia.ui.log.DailyLogBottomSheet;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

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
            if (!viewModel.getDisplayedMonth().isBefore(YearMonth.now())) {
                adManager.showRewardedAd(requireActivity(), () -> {
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
        });
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
        // Show the detail panel
        binding.layoutDayDetail.setVisibility(View.VISIBLE);
        updateMochiSupportCard(date);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, MMMM d");
        binding.tvSelectedDate.setText(date.format(fmt));

        boolean isToday = date.equals(LocalDate.now());
        boolean isFuture = date.isAfter(LocalDate.now());

        if (isFuture) {
            binding.tvDaySummary.setText("Future prediction date");
            binding.btnLogSelectedDay.setText("Cannot log future dates");
            binding.btnLogSelectedDay.setEnabled(false);
            binding.btnLogSelectedDay.setAlpha(0.4f);
        } else {
            binding.tvDaySummary.setText(isToday ? "Tap below to log today" : "Tap below to log this day");
            binding.btnLogSelectedDay.setText("Log this day");
            binding.btnLogSelectedDay.setEnabled(true);
            binding.btnLogSelectedDay.setAlpha(1.0f);
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
