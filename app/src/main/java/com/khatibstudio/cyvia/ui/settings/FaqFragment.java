package com.khatibstudio.cyvia.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.khatibstudio.cyvia.databinding.FragmentFaqBinding;

public class FaqFragment extends Fragment {

    private FragmentFaqBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFaqBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // Set default accordion states (Q1 open, others closed)
        setupAccordion(binding.cardQ1, binding.tvAns1, binding.ivArrow1, true);
        setupAccordion(binding.cardQ2, binding.tvAns2, binding.ivArrow2, false);
        setupAccordion(binding.cardQ3, binding.tvAns3, binding.ivArrow3, false);
        setupAccordion(binding.cardQ4, binding.tvAns4, binding.ivArrow4, false);
        setupAccordion(binding.cardQ5, binding.tvAns5, binding.ivArrow5, false);
        setupAccordion(binding.cardQ6, binding.tvAns6, binding.ivArrow6, false);
    }

    private void setupAccordion(View card, TextView answer, ImageView arrow, boolean openByDefault) {
        if (openByDefault) {
            answer.setVisibility(View.VISIBLE);
            arrow.setRotation(90f);
        } else {
            answer.setVisibility(View.GONE);
            arrow.setRotation(0f);
        }

        card.setOnClickListener(v -> {
            if (answer.getVisibility() == View.VISIBLE) {
                answer.setVisibility(View.GONE);
                arrow.animate().rotation(0f).setDuration(200).start();
            } else {
                answer.setVisibility(View.VISIBLE);
                arrow.animate().rotation(90f).setDuration(200).start();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
