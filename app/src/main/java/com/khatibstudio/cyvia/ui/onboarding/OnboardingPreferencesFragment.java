package com.khatibstudio.cyvia.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.khatibstudio.cyvia.data.model.TrackingMode;
import com.khatibstudio.cyvia.databinding.FragmentOnboardingPreferencesBinding;

public class OnboardingPreferencesFragment extends OnboardingPageFragment {

    private FragmentOnboardingPreferencesBinding binding;
    private TrackingMode selectedMode = TrackingMode.REGULAR;
    private int cycleLength = 28;
    private int periodLength = 5;

    private static final String[] MODE_LABELS = {
            "Regular tracking", "Irregular cycles", "Trying to conceive",
            "Avoiding pregnancy", "No periods (contraception)", "Postpartum", "Perimenopause"
    };
    private static final TrackingMode[] MODE_VALUES = {
            TrackingMode.REGULAR, TrackingMode.IRREGULAR, TrackingMode.TRYING_TO_CONCEIVE,
            TrackingMode.AVOIDING_PREGNANCY, TrackingMode.NO_PERIODS_CONTRACEPTION,
            TrackingMode.POSTPARTUM, TrackingMode.PERIMENOPAUSE
    };

    public static OnboardingPreferencesFragment newInstance() {
        return new OnboardingPreferencesFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingPreferencesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Cycle length slider
        binding.sliderCycleLength.addOnChangeListener((slider, value, fromUser) -> {
            cycleLength = (int) value;
            binding.tvCycleLengthValue.setText(String.valueOf(cycleLength));
        });

        // Period length slider
        binding.sliderPeriodLength.addOnChangeListener((slider, value, fromUser) -> {
            periodLength = (int) value;
            binding.tvPeriodLengthValue.setText(String.valueOf(periodLength));
        });

        // Tracking mode dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, MODE_LABELS);
        binding.spinnerTrackingMode.setAdapter(adapter);
        binding.spinnerTrackingMode.setText(MODE_LABELS[0], false);
        binding.spinnerTrackingMode.setOnItemClickListener((parent, v, position, id) ->
                selectedMode = MODE_VALUES[position]);
    }

    public int getCycleLength() { return cycleLength; }
    public int getPeriodLength() { return periodLength; }
    public TrackingMode getSelectedMode() { return selectedMode; }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
