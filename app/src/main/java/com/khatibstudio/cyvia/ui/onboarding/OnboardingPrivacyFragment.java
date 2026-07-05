package com.khatibstudio.cyvia.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.khatibstudio.cyvia.databinding.FragmentOnboardingPrivacyBinding;

public class OnboardingPrivacyFragment extends OnboardingPageFragment {

    private FragmentOnboardingPrivacyBinding binding;

    public static OnboardingPrivacyFragment newInstance() {
        return new OnboardingPrivacyFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingPrivacyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public boolean isMinorSafeSelected() {
        return binding != null && binding.switchOnboardingMinorSafe != null && binding.switchOnboardingMinorSafe.isChecked();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
