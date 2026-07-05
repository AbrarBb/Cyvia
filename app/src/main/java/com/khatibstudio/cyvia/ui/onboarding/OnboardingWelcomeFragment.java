package com.khatibstudio.cyvia.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.khatibstudio.cyvia.databinding.FragmentOnboardingWelcomeBinding;

import android.text.Editable;
import android.text.TextWatcher;
import com.khatibstudio.cyvia.CyviaApplication;

public class OnboardingWelcomeFragment extends OnboardingPageFragment {

    private FragmentOnboardingWelcomeBinding binding;
    private String enteredName = "";

    public static OnboardingWelcomeFragment newInstance() {
        return new OnboardingWelcomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (binding != null && binding.etOnboardingName != null) {
            binding.etOnboardingName.setText(enteredName);
            binding.etOnboardingName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (s != null) {
                        enteredName = s.toString().trim();
                        if (getContext() != null && !enteredName.isEmpty()) {
                            CyviaApplication.from(requireContext()).getSettingsRepository().setUserName(enteredName);
                        }
                    }
                }
            });
        }
    }

    public String getUserName() {
        if (binding != null && binding.etOnboardingName != null && binding.etOnboardingName.getText() != null) {
            enteredName = binding.etOnboardingName.getText().toString().trim();
        }
        return enteredName;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null && !enteredName.isEmpty()) {
            CyviaApplication.from(requireContext()).getSettingsRepository().setUserName(enteredName);
        }
    }

    @Override
    public void onDestroyView() {
        if (binding != null && binding.etOnboardingName != null && binding.etOnboardingName.getText() != null) {
            enteredName = binding.etOnboardingName.getText().toString().trim();
        }
        super.onDestroyView();
        binding = null;
    }
}
