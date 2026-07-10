package com.khatibstudio.cyvia.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.MainActivity;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.model.FlowIntensity;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.databinding.ActivityOnboardingBinding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Onboarding Activity — shown only once on first launch.
 *
 * 4 pages:
 *   1. Welcome + Mochi
 *   2. Privacy promise
 *   3. Last period date picker
 *   4. Cycle/period length preferences + tracking mode
 *
 * On completion:
 *   - Saves cycle length and period length to SettingsRepository
 *   - Creates a CycleEntry for the selected last period date
 *   - Marks onboarding as complete
 *   - Starts MainActivity
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private OnboardingPagerAdapter pagerAdapter;
    private SettingsRepository settings;
    private CycleRepository cycleRepository;
    private List<OnboardingPageFragment> pages = new ArrayList<>();

    private static final int PAGE_COUNT = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        CyviaApplication app = CyviaApplication.from(this);
        settings = app.getSettingsRepository();
        cycleRepository = app.getCycleRepository();

        setupPager();
        setupPageIndicator();
        setupNavButtons();
    }

    // ─── ViewPager2 ───────────────────────────────────────────────────────

    private void setupPager() {
        pages.clear();
        pages.add(OnboardingWelcomeFragment.newInstance());
        pages.add(OnboardingPrivacyFragment.newInstance());
        pages.add(OnboardingLastPeriodFragment.newInstance());
        pages.add(OnboardingPreferencesFragment.newInstance());

        pagerAdapter = new OnboardingPagerAdapter(this, pages);
        binding.onboardingPager.setAdapter(pagerAdapter);
        binding.onboardingPager.setUserInputEnabled(false); // buttons-only navigation

        binding.onboardingPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
                binding.btnBack.setVisibility(position > 0 ? View.VISIBLE : View.INVISIBLE);
                String nextLabel = position == PAGE_COUNT - 1
                        ? getString(R.string.onboarding_get_started)
                        : getString(R.string.onboarding_next);
                binding.btnNext.setText(nextLabel);
            }
        });
    }

    private void setupNavButtons() {
        binding.btnSkip.setOnClickListener(v -> completeOnboarding(false));
        binding.btnBack.setOnClickListener(v -> {
            int current = binding.onboardingPager.getCurrentItem();
            if (current > 0) binding.onboardingPager.setCurrentItem(current - 1);
        });
        binding.btnNext.setOnClickListener(v -> {
            int current = binding.onboardingPager.getCurrentItem();
            if (current < PAGE_COUNT - 1) {
                binding.onboardingPager.setCurrentItem(current + 1);
            } else {
                completeOnboarding(true);
            }
        });
    }

    // ─── Page indicator ───────────────────────────────────────────────────

    private void setupPageIndicator() {
        for (int i = 0; i < PAGE_COUNT; i++) {
            View dot = new View(this);
            int size = (int) getResources().getDimension(R.dimen.onboarding_indicator_size);
            int margin = (int) getResources().getDimension(R.dimen.onboarding_indicator_spacing);
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(margin);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.bg_calendar_predicted);
            binding.pageIndicator.addView(dot);
        }
        updateIndicators(0);
    }

    private void updateIndicators(int activePosition) {
        for (int i = 0; i < binding.pageIndicator.getChildCount(); i++) {
            View dot = binding.pageIndicator.getChildAt(i);
            dot.setAlpha(i == activePosition ? 1.0f : 0.4f);
            float scale = i == activePosition ? 1.3f : 1.0f;
            dot.setScaleX(scale);
            dot.setScaleY(scale);
        }
    }

    // ─── Complete ─────────────────────────────────────────────────────────

    /**
     * Saves all onboarding choices and starts the main app.
     *
     * @param saveData If true, read values from the last two pages and persist them.
     *                 If false (Skip), use defaults only.
     */
    private void completeOnboarding(boolean saveData) {
        if (saveData && pages.size() >= 4) {
            // Save user name from page 0
            OnboardingWelcomeFragment welcomePage = (OnboardingWelcomeFragment) pages.get(0);
            if (welcomePage != null) {
                String name = welcomePage.getUserName();
                if (!name.isEmpty()) {
                    settings.setUserName(name);
                }
            }

            // Check privacy preferences from page 1
            OnboardingPrivacyFragment privacyPage = (OnboardingPrivacyFragment) pages.get(1);
            if (privacyPage != null && privacyPage.isMinorSafeSelected()) {
                settings.setMinorSafeMode(true);
                settings.setTrackIntimacyEnabled(false);
            } else if (!settings.hasTrackIntimacyPreference()) {
                settings.setTrackIntimacyEnabled(false);
            }

            // Save preferences from the last page
            OnboardingPreferencesFragment prefsPage = (OnboardingPreferencesFragment) pages.get(3);
            if (prefsPage != null) {
                settings.setAvgCycleLength(prefsPage.getCycleLength());
                settings.setAvgPeriodLength(prefsPage.getPeriodLength());
                settings.setTrackingMode(prefsPage.getSelectedMode());
            }

            // Save last period date from page 3
            OnboardingLastPeriodFragment datePage = (OnboardingLastPeriodFragment) pages.get(2);
            if (datePage != null) {
                LocalDate lastPeriodDate = datePage.getSelectedDate();
                if (lastPeriodDate != null) {
                    CycleEntry seed = new CycleEntry(
                            lastPeriodDate.toEpochDay(), FlowIntensity.MEDIUM);
                    cycleRepository.insertCycle(seed);
                }
            }
        }

        settings.setOnboardingComplete(true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
