package com.khatibstudio.cyvia.ui.log;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.db.entity.CycleEntry;
import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.db.entity.SymptomTag;
import com.khatibstudio.cyvia.data.model.FlowIntensity;
import com.khatibstudio.cyvia.data.model.Mood;
import com.khatibstudio.cyvia.data.model.SymptomCategory;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.LogRepository;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.data.repository.SymptomRepository;
import com.khatibstudio.cyvia.databinding.BottomSheetDailyLogBinding;
import com.khatibstudio.cyvia.util.KawaiiIconUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bottom sheet dialog for logging daily symptoms, mood, flow, notes, etc.
 * Features Kawaii character icon grid selectors and custom image upload support.
 */
public class DailyLogBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "DailyLogBottomSheet";
    private static final String ARG_DATE = "arg_date";

    private BottomSheetDailyLogBinding binding;
    private LogRepository logRepository;
    private SymptomRepository symptomRepository;
    private CycleRepository cycleRepository;
    private SettingsRepository settings;

    private LocalDate logDate;
    private DailyLog existingLog;
    private final Set<Integer> selectedSymptomIds = new HashSet<>();
    private Mood selectedMood = null;
    private FlowIntensity selectedFlow = null;

    private List<SymptomTag> allTagsList = new ArrayList<>();
    private String tempCustomIconKey = "ic_kawaii_melody";
    private ImageView tempUploadPreviewIv = null;
    private View tempUploadLayout = null;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        InputStream is = getContext().getContentResolver().openInputStream(uri);
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (is != null) is.close();

                        if (bmp != null) {
                            File file = new File(requireContext().getFilesDir(), "kawaii_" + System.currentTimeMillis() + ".png");
                            FileOutputStream fos = new FileOutputStream(file);
                            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
                            fos.close();

                            tempCustomIconKey = file.getAbsolutePath();
                            if (tempUploadPreviewIv != null && tempUploadLayout != null) {
                                tempUploadPreviewIv.setImageBitmap(bmp);
                                tempUploadLayout.setVisibility(View.VISIBLE);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to copy custom icon image", e);
                    }
                }
            });

    public static DailyLogBottomSheet newInstance(@Nullable LocalDate date) {
        DailyLogBottomSheet fragment = new DailyLogBottomSheet();
        Bundle args = new Bundle();
        if (date != null) {
            args.putString(ARG_DATE, date.toString());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_Cyvia_BottomSheetDialog);

        CyviaApplication app = CyviaApplication.from(requireContext());
        logRepository = app.getLogRepository();
        symptomRepository = app.getSymptomRepository();
        cycleRepository = app.getCycleRepository();
        settings = app.getSettingsRepository();

        if (getArguments() != null && getArguments().containsKey(ARG_DATE)) {
            logDate = LocalDate.parse(getArguments().getString(ARG_DATE));
            if (logDate.isAfter(LocalDate.now())) {
                logDate = LocalDate.now();
            }
        } else {
            logDate = LocalDate.now();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDailyLogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupDateNavigation();

        if (!settings.isMinorSafeMode() && settings.isTrackIntimacyEnabled()) {
            binding.switchIntimacy.setVisibility(View.VISIBLE);
        } else {
            binding.switchIntimacy.setVisibility(View.GONE);
        }

        setupFlowToggle();
        loadExistingLog();
        observeSymptomTags();

        binding.btnSaveLog.setOnClickListener(v -> saveLog());
        binding.btnAddCustomKawaii.setOnClickListener(v -> showAddCustomKawaiiDialog());
    }

    private void setupDateNavigation() {
        updateDateHeader();

        binding.btnPrevDate.setOnClickListener(v -> changeLogDate(logDate.minusDays(1)));
        binding.btnNextDate.setOnClickListener(v -> {
            if (logDate.isBefore(LocalDate.now())) {
                changeLogDate(logDate.plusDays(1));
            }
        });
        binding.cardDateSelector.setOnClickListener(v -> showDatePicker());
    }

    private void updateDateHeader() {
        if (binding == null) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        String dateStr;
        if (logDate.equals(LocalDate.now())) {
            dateStr = "Today • " + logDate.format(fmt);
        } else if (logDate.equals(LocalDate.now().minusDays(1))) {
            dateStr = "Yesterday • " + logDate.format(fmt);
        } else {
            dateStr = logDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"));
        }
        binding.tvLogTitle.setText(dateStr);

        binding.btnNextDate.setAlpha(logDate.isBefore(LocalDate.now()) ? 1.0f : 0.3f);
        binding.btnNextDate.setEnabled(logDate.isBefore(LocalDate.now()));
    }

    private void changeLogDate(LocalDate newDate) {
        if (newDate.isAfter(LocalDate.now())) return;
        logDate = newDate;
        updateDateHeader();

        selectedMood = null;
        selectedFlow = null;
        selectedSymptomIds.clear();
        binding.etNotes.setText("");
        binding.etTemperature.setText("");
        binding.switchIntimacy.setChecked(false);
        binding.toggleFlow.clearChecked();

        loadExistingLog();
        refreshAllKawaiiRows();
    }

    private void showDatePicker() {
        android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    LocalDate picked = LocalDate.of(year, month + 1, dayOfMonth);
                    if (!picked.isAfter(LocalDate.now())) {
                        changeLogDate(picked);
                    }
                },
                logDate.getYear(),
                logDate.getMonthValue() - 1,
                logDate.getDayOfMonth()
        );
        dpd.getDatePicker().setMaxDate(System.currentTimeMillis());
        dpd.show();
    }

    private void setupFlowToggle() {
        binding.toggleFlow.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) { selectedFlow = null; return; }
            if (checkedId == R.id.btn_flow_spotting) selectedFlow = FlowIntensity.SPOTTING;
            else if (checkedId == R.id.btn_flow_light) selectedFlow = FlowIntensity.LIGHT;
            else if (checkedId == R.id.btn_flow_medium) selectedFlow = FlowIntensity.MEDIUM;
            else if (checkedId == R.id.btn_flow_heavy) selectedFlow = FlowIntensity.HEAVY;
        });
    }

    private void loadExistingLog() {
        logRepository.getLogForDate(logDate).observe(getViewLifecycleOwner(), log -> {
            existingLog = log;
            if (log == null) {
                if (binding != null) binding.btnDeleteLog.setVisibility(View.GONE);
                return;
            }
            if (binding != null) {
                binding.btnDeleteLog.setVisibility(View.VISIBLE);
                binding.btnDeleteLog.setOnClickListener(v -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete log entry?")
                            .setMessage("Are you sure you want to remove this daily log entry? This cannot be undone.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, i) -> {
                                logRepository.deleteLog(log);
                                dismiss();
                            })
                            .show();
                });
            }

            if (log.notes != null) binding.etNotes.setText(log.notes);
            if (log.temperature != null) {
                binding.etTemperature.setText(String.valueOf(log.temperature));
            }
            if (log.intimacy != null) binding.switchIntimacy.setChecked(log.intimacy);

            selectedMood = log.mood;

            if (!TextUtils.isEmpty(log.symptomIds)) {
                for (String idStr : log.symptomIds.split(",")) {
                    try { selectedSymptomIds.add(Integer.parseInt(idStr.trim())); }
                    catch (NumberFormatException ignored) {}
                }
            }
            refreshAllKawaiiRows();
        });

        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            List<CycleEntry> cycles = cycleRepository.getAllCyclesSync();
            FlowIntensity foundFlow = null;
            if (cycles != null) {
                long targetDay = logDate.toEpochDay();
                for (CycleEntry cycle : cycles) {
                    long start = cycle.startDate;
                    long end = cycle.isOngoing() ? LocalDate.now().toEpochDay() : cycle.endDate;
                    if (targetDay >= start && targetDay <= end) {
                        foundFlow = cycle.flowIntensity;
                        break;
                    }
                }
            }
            FlowIntensity finalFlow = foundFlow;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding == null) return;
                    selectedFlow = finalFlow;
                    binding.toggleFlow.clearChecked();
                    if (finalFlow == FlowIntensity.SPOTTING) binding.toggleFlow.check(R.id.btn_flow_spotting);
                    else if (finalFlow == FlowIntensity.LIGHT) binding.toggleFlow.check(R.id.btn_flow_light);
                    else if (finalFlow == FlowIntensity.MEDIUM) binding.toggleFlow.check(R.id.btn_flow_medium);
                    else if (finalFlow == FlowIntensity.HEAVY) binding.toggleFlow.check(R.id.btn_flow_heavy);
                });
            }
        });
    }

    private void observeSymptomTags() {
        symptomRepository.getAllSymptomTags().observe(getViewLifecycleOwner(), tags -> {
            allTagsList = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
            allTagsList.sort((t1, t2) -> {
                if (t1.category != t2.category) {
                    return t1.category.compareTo(t2.category);
                }
                String l1 = t1.label != null ? t1.label : "";
                String l2 = t2.label != null ? t2.label : "";
                return l1.compareToIgnoreCase(l2);
            });
            refreshAllKawaiiRows();
        });
    }

    private void refreshAllKawaiiRows() {
        if (binding == null) return;
        binding.layoutMoodSelector.removeAllViews();
        binding.layoutPhysicalSymptoms.removeAllViews();
        binding.layoutEmotionalSymptoms.removeAllViews();

        Set<String> seenMoods = new HashSet<>();
        Set<String> seenPhysical = new HashSet<>();
        Set<String> seenEmotional = new HashSet<>();

        // 1. Built-in Moods
        for (Mood m : Mood.values()) {
            String label = m.name().substring(0, 1).toUpperCase() + m.name().substring(1).toLowerCase();
            seenMoods.add(label.toLowerCase());
            boolean isSel = (selectedMood == m);
            addKawaiiBadgeView(binding.layoutMoodSelector, label, null, KawaiiIconUtil.getMoodIconRes(m), isSel, v -> {
                selectedMood = (selectedMood == m) ? null : m;
                refreshAllKawaiiRows();
            });
        }

        // 2. Custom Moods and All Symptom Tags
        for (SymptomTag tag : allTagsList) {
            String norm = tag.label != null ? tag.label.trim().toLowerCase() : "";
            boolean isSel = selectedSymptomIds.contains(tag.id);
            int fallback = tag.category == SymptomCategory.EMOTIONAL ? R.drawable.ic_mood_sensitive : R.drawable.ic_forecast_cramps;

            if (tag.category == SymptomCategory.MOOD) {
                if (!seenMoods.contains(norm)) {
                    seenMoods.add(norm);
                    addKawaiiBadgeView(binding.layoutMoodSelector, tag.label, tag.iconKey, R.drawable.ic_mood_happy, isSel, v -> {
                        toggleSymptomSelection(tag.id);
                    });
                }
            } else if (tag.category == SymptomCategory.PHYSICAL) {
                if (!seenPhysical.contains(norm)) {
                    seenPhysical.add(norm);
                    addKawaiiBadgeView(binding.layoutPhysicalSymptoms, tag.label, tag.iconKey, fallback, isSel, v -> {
                        toggleSymptomSelection(tag.id);
                    });
                }
            } else {
                if (!seenEmotional.contains(norm) && !seenMoods.contains(norm)) {
                    seenEmotional.add(norm);
                    addKawaiiBadgeView(binding.layoutEmotionalSymptoms, tag.label, tag.iconKey, fallback, isSel, v -> {
                        toggleSymptomSelection(tag.id);
                    });
                }
            }
        }

        // Ensure rich Physical Condition options are present even if db wasn't re-seeded
        String[] extraPhysical = {"Dizziness", "Chills", "Brain fog", "Joint pain", "Constipation", "Diarrhea", "Neck ache", "Ovulation pain", "Lower back pain", "Tender nipples", "Sweet cravings", "Salty cravings", "Sensitive skin", "Water retention"};
        for (String extra : extraPhysical) {
            if (!seenPhysical.contains(extra.toLowerCase())) {
                seenPhysical.add(extra.toLowerCase());
                addKawaiiBadgeView(binding.layoutPhysicalSymptoms, extra, null, R.drawable.ic_forecast_aches, false, v -> {
                    symptomRepository.addCustomSymptom(extra, com.khatibstudio.cyvia.data.model.SymptomCategory.PHYSICAL);
                    android.widget.Toast.makeText(requireContext(), "Added " + extra + "! Tap again to select.", android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    private void toggleSymptomSelection(int tagId) {
        if (selectedSymptomIds.contains(tagId)) selectedSymptomIds.remove(tagId);
        else selectedSymptomIds.add(tagId);
        refreshAllKawaiiRows();
    }

    private View addKawaiiBadgeView(ViewGroup parent, String label, String iconKey, int fallbackResId, boolean isSelected, View.OnClickListener onClick) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_kawaii_selector_badge, parent, false);
        MaterialCardView card = view.findViewById(R.id.card_kawaii_badge);
        ImageView iv = view.findViewById(R.id.iv_kawaii_icon);
        TextView tv = view.findViewById(R.id.tv_kawaii_label);

        tv.setText(label);
        KawaiiIconUtil.loadIcon(requireContext(), iv, iconKey, label, fallbackResId);

        if (isSelected) {
            card.setStrokeColor(requireContext().getColor(R.color.cyvia_primary));
            card.setStrokeWidth((int) (2.5f * getResources().getDisplayMetrics().density));
            card.setCardBackgroundColor(requireContext().getColor(R.color.cyvia_primary_container));
            tv.setTextColor(requireContext().getColor(R.color.cyvia_primary));
        } else {
            card.setStrokeColor(requireContext().getColor(R.color.cyvia_outline));
            card.setStrokeWidth((int) (1.2f * getResources().getDisplayMetrics().density));
            card.setCardBackgroundColor(requireContext().getColor(R.color.cyvia_surface));
            tv.setTextColor(requireContext().getColor(R.color.cyvia_on_surface));
        }

        view.setOnClickListener(onClick);
        parent.addView(view);
        return view;
    }

    private void showAddCustomKawaiiDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_custom_item, null);
        TextInputEditText etLabel = dialogView.findViewById(R.id.et_custom_label);
        MaterialButtonToggleGroup toggleType = dialogView.findViewById(R.id.toggle_custom_type);
        View btnUpload = dialogView.findViewById(R.id.btn_upload_image);
        tempUploadLayout = dialogView.findViewById(R.id.layout_uploaded_preview);
        tempUploadPreviewIv = dialogView.findViewById(R.id.iv_uploaded_preview);
        LinearLayout layoutPresets = dialogView.findViewById(R.id.layout_preset_icons);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_custom);
        View btnSave = dialogView.findViewById(R.id.btn_save_custom);

        toggleType.check(R.id.btn_type_symptom);
        tempCustomIconKey = "ic_kawaii_melody";

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        for (String key : KawaiiIconUtil.PRESET_KAWAII_ICONS) {
            boolean sel = key.equals(tempCustomIconKey);
            View badge = addKawaiiBadgeView(layoutPresets, "", key, R.drawable.ic_kawaii_melody, sel, v -> {
                tempCustomIconKey = key;
                if (tempUploadLayout != null) tempUploadLayout.setVisibility(View.GONE);
                for (int i = 0; i < layoutPresets.getChildCount(); i++) {
                    View child = layoutPresets.getChildAt(i);
                    MaterialCardView c = child.findViewById(R.id.card_kawaii_badge);
                    if (c != null) {
                        boolean isThis = child == v;
                        c.setStrokeColor(requireContext().getColor(isThis ? R.color.cyvia_primary : R.color.cyvia_outline));
                        c.setStrokeWidth((int) ((isThis ? 2.5f : 1.2f) * getResources().getDisplayMetrics().density));
                        c.setCardBackgroundColor(requireContext().getColor(isThis ? R.color.cyvia_primary_container : R.color.cyvia_surface));
                    }
                }
            });
        }

        btnUpload.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String label = etLabel.getText() != null ? etLabel.getText().toString().trim() : "";
            if (TextUtils.isEmpty(label)) {
                etLabel.setError("Please enter a name");
                return;
            }
            int checkedId = toggleType.getCheckedButtonId();
            SymptomCategory cat = (checkedId == R.id.btn_type_mood) ? SymptomCategory.MOOD : SymptomCategory.PHYSICAL;
            symptomRepository.addCustomSymptom(label, cat, tempCustomIconKey);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveLog() {
        DailyLog log = existingLog != null ? existingLog : new DailyLog();
        log.date = logDate.toEpochDay();
        log.mood = selectedMood;

        List<String> ids = new ArrayList<>();
        for (int id : selectedSymptomIds) ids.add(String.valueOf(id));
        log.symptomIds = TextUtils.join(",", ids);

        String notesText = binding.etNotes.getText() != null
                ? binding.etNotes.getText().toString().trim() : "";
        log.notes = notesText.isEmpty() ? null : notesText;

        String tempText = binding.etTemperature.getText() != null
                ? binding.etTemperature.getText().toString().trim() : "";
        try {
            log.temperature = tempText.isEmpty() ? null : Float.parseFloat(tempText);
        } catch (NumberFormatException e) {
            log.temperature = null;
        }

        if (binding.switchIntimacy.getVisibility() == View.VISIBLE) {
            log.intimacy = binding.switchIntimacy.isChecked();
        }

        logRepository.saveLog(log);

        FlowIntensity flowToSave = selectedFlow;
        long targetDay = logDate.toEpochDay();
        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            if (flowToSave != null) {
                List<CycleEntry> cycles = cycleRepository.getAllCyclesSync();
                boolean handled = false;
                if (cycles != null) {
                    for (CycleEntry cycle : cycles) {
                        long start = cycle.startDate;
                        long end = cycle.isOngoing() ? LocalDate.now().toEpochDay() : cycle.endDate;
                        if (targetDay >= start - 2 && targetDay <= end + 2) {
                            if (targetDay < start) {
                                cycle.startDate = targetDay;
                            }
                            if (targetDay > end || cycle.isOngoing()) {
                                if (!cycle.isOngoing() && targetDay > end) {
                                    cycle.endDate = targetDay;
                                }
                            }
                            cycle.flowIntensity = flowToSave;
                            cycleRepository.updateCycle(cycle);
                            handled = true;
                            break;
                        }
                    }
                }
                if (!handled) {
                    CycleEntry newCycle = new CycleEntry(targetDay, flowToSave);
                    if (logDate.isBefore(LocalDate.now().minusDays(5))) {
                        newCycle.endDate = targetDay + 4;
                    }
                    cycleRepository.insertCycle(newCycle);
                }
            }
        });

        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
