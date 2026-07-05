package com.khatibstudio.cyvia.ui.onboarding;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

/**
 * ViewPager2 adapter for the 4-page onboarding flow.
 */
public class OnboardingPagerAdapter extends FragmentStateAdapter {

    private final List<OnboardingPageFragment> pages;

    public OnboardingPagerAdapter(@NonNull FragmentActivity activity,
                                   List<OnboardingPageFragment> pages) {
        super(activity);
        this.pages = pages;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return pages.get(position);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }
}
