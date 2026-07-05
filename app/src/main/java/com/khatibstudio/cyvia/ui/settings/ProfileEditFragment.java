package com.khatibstudio.cyvia.ui.settings;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.data.model.TrackingMode;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.databinding.FragmentProfileEditBinding;
import com.khatibstudio.cyvia.util.KawaiiIconUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Standalone Profile Edit screen.
 * Allows editing PFP, Name, Age, Tracking Goals, and Cycle Averages.
 */
public class ProfileEditFragment extends Fragment {

    private static final String TAG = "ProfileEditFragment";
    private FragmentProfileEditBinding binding;
    private SettingsRepository settings;
    private String selectedPfpKey = "ic_kawaii_melody";

    private static final String[] TRACKING_MODE_LABELS = {
            "Regular tracking", "Irregular cycles", "Trying to conceive",
            "Avoiding pregnancy", "No periods (contraception)", "Postpartum", "Perimenopause"
    };
    private static final TrackingMode[] TRACKING_MODES = {
            TrackingMode.REGULAR, TrackingMode.IRREGULAR, TrackingMode.TRYING_TO_CONCEIVE,
            TrackingMode.AVOIDING_PREGNANCY, TrackingMode.NO_PERIODS_CONTRACEPTION,
            TrackingMode.POSTPARTUM, TrackingMode.PERIMENOPAUSE
    };

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        InputStream is = getContext().getContentResolver().openInputStream(uri);
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (is != null) is.close();

                        if (bmp != null) {
                            File file = new File(requireContext().getFilesDir(), "pfp_" + System.currentTimeMillis() + ".png");
                            FileOutputStream fos = new FileOutputStream(file);
                            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
                            fos.close();

                            selectedPfpKey = file.getAbsolutePath();
                            KawaiiIconUtil.loadIcon(requireContext(), binding.ivCurrentAvatar, selectedPfpKey, R.drawable.ic_kawaii_melody);
                            refreshAvatarGrid();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to copy custom PFP image", e);
                        Toast.makeText(requireContext(), "Failed to load photo", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = CyviaApplication.from(requireContext()).getSettingsRepository();
        selectedPfpKey = settings.getUserPfp();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupValues();
        setupAvatarGrid();
        setupListeners();
    }

    private void setupValues() {
        KawaiiIconUtil.loadIcon(requireContext(), binding.ivCurrentAvatar, selectedPfpKey, R.drawable.ic_kawaii_melody);

        binding.etProfileName.setText(settings.getUserName());
        binding.etProfileAge.setText(String.valueOf(settings.getUserAge()));

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, TRACKING_MODE_LABELS);
        ((AutoCompleteTextView) binding.spinnerProfileGoal).setAdapter(modeAdapter);
        int modeIdx = indexOfMode(settings.getTrackingMode());
        binding.spinnerProfileGoal.setText(TRACKING_MODE_LABELS[modeIdx], false);

        int cycleLen = settings.getAvgCycleLength();
        int periodLen = settings.getAvgPeriodLength();
        binding.sliderCycleLen.setValue(cycleLen);
        binding.sliderPeriodLen.setValue(periodLen);
        binding.tvCycleLenLabel.setText("Average Cycle Length: " + cycleLen + " days");
        binding.tvPeriodLenLabel.setText("Average Period Length: " + periodLen + " days");
    }

    private void setupAvatarGrid() {
        refreshAvatarGrid();
    }

    private void refreshAvatarGrid() {
        if (binding == null) return;
        binding.layoutAvatarGrid.removeAllViews();

        for (String key : KawaiiIconUtil.PRESET_KAWAII_ICONS) {
            boolean isSel = key.equals(selectedPfpKey);
            View badge = LayoutInflater.from(requireContext()).inflate(R.layout.item_kawaii_selector_badge, binding.layoutAvatarGrid, false);
            MaterialCardView card = badge.findViewById(R.id.card_kawaii_badge);
            ImageView iv = badge.findViewById(R.id.iv_kawaii_icon);
            badge.findViewById(R.id.tv_kawaii_label).setVisibility(View.GONE);

            KawaiiIconUtil.loadIcon(requireContext(), iv, key, R.drawable.ic_kawaii_melody);

            if (isSel) {
                card.setStrokeColor(requireContext().getColor(R.color.cyvia_primary));
                card.setStrokeWidth((int) (2.5f * getResources().getDisplayMetrics().density));
                card.setCardBackgroundColor(requireContext().getColor(R.color.cyvia_primary_container));
            } else {
                card.setStrokeColor(requireContext().getColor(R.color.cyvia_outline));
                card.setStrokeWidth((int) (1.2f * getResources().getDisplayMetrics().density));
                card.setCardBackgroundColor(requireContext().getColor(R.color.cyvia_surface));
            }

            badge.setOnClickListener(v -> {
                selectedPfpKey = key;
                KawaiiIconUtil.loadIcon(requireContext(), binding.ivCurrentAvatar, selectedPfpKey, R.drawable.ic_kawaii_melody);
                refreshAvatarGrid();
            });

            binding.layoutAvatarGrid.addView(badge);
        }
    }

    private void setupListeners() {
        binding.btnBackProfile.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.btnUploadPfp.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        binding.sliderCycleLen.addOnChangeListener((slider, value, fromUser) ->
                binding.tvCycleLenLabel.setText("Average Cycle Length: " + ((int) value) + " days"));

        binding.sliderPeriodLen.addOnChangeListener((slider, value, fromUser) ->
                binding.tvPeriodLenLabel.setText("Average Period Length: " + ((int) value) + " days"));

        binding.spinnerProfileGoal.setOnItemClickListener((parent, view, position, id) ->
                settings.setTrackingMode(TRACKING_MODES[position]));

        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        settings.setUserPfp(selectedPfpKey);

        String name = binding.etProfileName.getText() != null ? binding.etProfileName.getText().toString().trim() : "";
        settings.setUserName(name);

        String ageStr = binding.etProfileAge.getText() != null ? binding.etProfileAge.getText().toString().trim() : "";
        try {
            if (!ageStr.isEmpty()) settings.setUserAge(Integer.parseInt(ageStr));
        } catch (NumberFormatException ignored) {}

        settings.setAvgCycleLength((int) binding.sliderCycleLen.getValue());
        settings.setAvgPeriodLength((int) binding.sliderPeriodLen.getValue());

        Toast.makeText(requireContext(), "Profile updated successfully! ✨", Toast.LENGTH_SHORT).show();
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    private int indexOfMode(TrackingMode mode) {
        for (int i = 0; i < TRACKING_MODES.length; i++) {
            if (TRACKING_MODES[i] == mode) return i;
        }
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
