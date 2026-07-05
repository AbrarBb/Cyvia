package com.khatibstudio.cyvia.ui.onboarding;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.khatibstudio.cyvia.databinding.FragmentOnboardingLastPeriodBinding;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class OnboardingLastPeriodFragment extends OnboardingPageFragment {

    private FragmentOnboardingLastPeriodBinding binding;
    private LocalDate selectedDate = null;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public static OnboardingLastPeriodFragment newInstance() {
        return new OnboardingLastPeriodFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingLastPeriodBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.cardDatePicker.setOnClickListener(v -> showDatePicker());
        binding.btnSkipDate.setOnClickListener(v -> {
            selectedDate = LocalDate.now();
            binding.tvSelectedDate.setText(selectedDate.format(FMT));
        });
    }

    private void showDatePicker() {
        LocalDate initial = selectedDate != null ? selectedDate : LocalDate.now();
        Calendar cal = Calendar.getInstance();
        cal.set(initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());

        new DatePickerDialog(requireContext(),
                (datePicker, year, month, day) -> {
                    selectedDate = LocalDate.of(year, month + 1, day);
                    binding.tvSelectedDate.setText(selectedDate.format(FMT));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    /** Returns the selected date (null if user did not pick one). */
    public @Nullable LocalDate getSelectedDate() {
        return selectedDate;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
