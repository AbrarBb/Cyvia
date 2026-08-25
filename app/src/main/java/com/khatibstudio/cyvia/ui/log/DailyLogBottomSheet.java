package com.khatibstudio.cyvia.ui.log;

import android.app.Dialog;
import android.content.DialogInterface;
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
import com.khatibstudio.cyvia.ads.AdManager;
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
 * Features Kawaii character icon grid selectors and custom image upload
 * support.
 */
public class DailyLogBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "DailyLogBottomSheet";
    private static final String ARG_DATE = "arg_date";

    private BottomSheetDailyLogBinding binding;
    private LogRepository logRepository;
    private SymptomRepository symptomRepository;
    private CycleRepository cycleRepository;
    private SettingsRepository settings;
    private AdManager adManager;

    private LocalDate logDate;
    private DailyLog existingLog;
    private final Set<Integer> selectedSymptomIds = new HashSet<>();
    private Mood selectedMood = null;
    private FlowIntensity selectedFlow = null;
    private Boolean pillsTaken = null; // null = not recorded
    private String selectedSex = null;
    private String selectedActivity = null;
    private String selectedDischarge = null;
    private String weightUnit = "KG";

    private List<SymptomTag> allTagsList = new ArrayList<>();
    private String tempCustomIconKey = "ic_kawaii_melody";
    private ImageView tempUploadPreviewIv = null;
    private View tempUploadLayout = null;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        InputStream is = getContext().getContentResolver().openInputStream(uri);
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (is != null)
                            is.close();

                        if (bmp != null) {
                            File file = new File(requireContext().getFilesDir(),
                                    "kawaii_" + System.currentTimeMillis() + ".png");
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
        adManager = new AdManager(settings);
        adManager.preloadInterstitial(requireContext());
        adManager.onLoggingStarted();

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
            binding.layoutSexSection.setVisibility(View.VISIBLE);
        } else {
            binding.layoutSexSection.setVisibility(View.GONE);
        }

        binding.toggleWeightUnit.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_unit_kg) {
                    weightUnit = "KG";
                    binding.layoutWeight.setSuffixText("kg");
                } else if (checkedId == R.id.btn_unit_lbs) {
                    weightUnit = "LBS";
                    binding.layoutWeight.setSuffixText("lbs");
                }
            }
        });

        setupFlowSelector();
        loadExistingLog();
        observeSymptomTags();

        binding.btnSaveLog.setOnClickListener(v -> saveLog());
        // Custom symptom button was replaced by the curated 13-item physical list
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
        if (binding == null)
            return;
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
        if (newDate.isAfter(LocalDate.now()))
            return;
        logDate = newDate;
        updateDateHeader();

        // Reset all sections — every section is independent, no auto-select
        selectedMood = null;
        selectedFlow = null;
        selectedSymptomIds.clear();
        pillsTaken = null;
        selectedSex = null;
        selectedActivity = null;
        selectedDischarge = null;
        weightUnit = "KG";
        binding.etNotes.setText("");
        binding.etTemperature.setText("");
        binding.etWeight.setText("");
        binding.toggleWeightUnit.check(R.id.btn_unit_kg);
        binding.layoutWeight.setSuffixText("kg");
        refreshFlowSelector();
        refreshAllKawaiiRows();

        loadExistingLog();
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
                logDate.getDayOfMonth());
        dpd.getDatePicker().setMaxDate(System.currentTimeMillis());
        dpd.show();
    }

    private void setupFlowSelector() {
        refreshFlowSelector();
    }

    /**
     * 6 independent flow options — None + 5 blood-intensity options.
     * Tap to select, tap again to deselect. No auto-selection on open.
     */
    private void refreshFlowSelector() {
        if (binding == null)
            return;
        binding.layoutFlowSelector.removeAllViews();

        // Option 0: No Flow / Normal Day (no blood icon)
        boolean noneSelected = (selectedFlow == null && pillsTaken == null);
        // We track "None" selection via a local boolean to distinguish
        // "not set" from "explicitly chose None".
        // Use a tag on the parent layout for this.
        Object noneTag = binding.layoutFlowSelector.getTag();
        boolean noneExplicit = Boolean.TRUE.equals(noneTag);

        int noFlowIcon = KawaiiIconUtil.getAvatarDrawableForPose(requireContext(), R.drawable.ic_mochi_smiling);
        addKawaiiBadgeView(binding.layoutFlowSelector, "No Flow", null, noFlowIcon, noneExplicit,
                v -> {
                    if (Boolean.TRUE.equals(binding.layoutFlowSelector.getTag())) {
                        binding.layoutFlowSelector.setTag(null); // deselect
                    } else {
                        selectedFlow = null;
                        binding.layoutFlowSelector.setTag(Boolean.TRUE);
                    }
                    refreshFlowSelector();
                });

        // Options 1-5: Blood flow intensities
        FlowIntensity[] intensities = FlowIntensity.values();
        for (FlowIntensity intensity : intensities) {
            String label;
            int iconRes;
            switch (intensity) {
                case SPOTTING:
                    label = "Spotting";
                    iconRes = R.drawable.ic_flow_spotting;
                    break;
                case LIGHT:
                    label = "Light";
                    iconRes = R.drawable.ic_flow_light;
                    break;
                case MEDIUM:
                    label = "Medium";
                    iconRes = R.drawable.ic_flow_medium;
                    break;
                case HEAVY:
                    label = "Heavy";
                    iconRes = R.drawable.ic_flow_heavy;
                    break;
                case VERY_HEAVY:
                    label = "Very Heavy";
                    iconRes = R.drawable.ic_flow_very_heavy;
                    break;
                default:
                    label = "Spotting";
                    iconRes = R.drawable.ic_flow_spotting;
                    break;
            }
            boolean isSel = (selectedFlow == intensity);
            addKawaiiBadgeView(binding.layoutFlowSelector, label, null, iconRes, isSel, v -> {
                if (selectedFlow == intensity) {
                    selectedFlow = null;
                } else {
                    selectedFlow = intensity;
                    binding.layoutFlowSelector.setTag(null); // clear 'No Flow' explicit flag
                }
                refreshFlowSelector();
            });
        }
    }

    private void loadExistingLog() {
        logRepository.getLogForDate(logDate).observe(getViewLifecycleOwner(), log -> {
            existingLog = log;
            if (log == null) {
                if (binding != null)
                    binding.btnDeleteLog.setVisibility(View.GONE);
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
                                long tDay = log.date;
                                CyviaDatabase.databaseWriteExecutor.execute(() -> {
                                    removeFlowForDate(tDay);
                                });
                                dismiss();
                            })
                            .show();
                });
            }

            // Restore previously saved values (all sections remain null if not saved)
            if (log.notes != null)
                binding.etNotes.setText(log.notes);
            if (log.temperature != null) {
                binding.etTemperature.setText(String.valueOf(log.temperature));
            }
            if (log.weight != null) {
                binding.etWeight.setText(String.valueOf(log.weight));
            }
            weightUnit = log.weightUnit != null ? log.weightUnit : "KG";
            if ("LBS".equals(weightUnit)) {
                binding.toggleWeightUnit.check(R.id.btn_unit_lbs);
                binding.layoutWeight.setSuffixText("lbs");
            } else {
                binding.toggleWeightUnit.check(R.id.btn_unit_kg);
                binding.layoutWeight.setSuffixText("kg");
            }

            selectedSex = log.sexType;
            selectedActivity = log.exerciseType;
            selectedDischarge = log.dischargeType;

            selectedMood = log.mood; // null if not saved
            pillsTaken = log.pillsTaken; // null if not saved

            if (!TextUtils.isEmpty(log.symptomIds)) {
                for (String idStr : log.symptomIds.split(",")) {
                    try {
                        selectedSymptomIds.add(Integer.parseInt(idStr.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Restore flow from the log's linked cycle if it was saved before
            long targetDay = logDate.toEpochDay();
            CyviaDatabase.databaseWriteExecutor.execute(() -> {
                List<CycleEntry> cycles = cycleRepository.getAllCyclesSync();
                FlowIntensity foundFlow = null;
                if (cycles != null) {
                    for (CycleEntry cycle : cycles) {
                        long start = cycle.startDate;
                        long end = cycle.isOngoing() ? LocalDate.now().toEpochDay() : cycle.endDate;
                        if (targetDay >= start && targetDay <= end) {
                            foundFlow = cycle.flowIntensity;
                            break;
                        }
                    }
                }
                final FlowIntensity finalFlow = foundFlow;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding == null)
                            return;
                        selectedFlow = finalFlow;
                        refreshFlowSelector();
                    });
                }
            });

            refreshAllKawaiiRows();
        });
        // NOTE: Flow is NOT auto-populated from cycle history anymore.
        // Each section starts empty until user explicitly taps.
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

    /**
     * Rebuilds the mood (11 Mochi), physical (13 Mochi), and medicine sections.
     * All sections are fully independent — nothing is auto-selected.
     */
    private void refreshAllKawaiiRows() {
        if (binding == null)
            return;
        binding.layoutMoodSelector.removeAllViews();
        binding.layoutPhysicalSymptoms.removeAllViews();
        if (binding.layoutMedicineSelector != null) {
            binding.layoutMedicineSelector.removeAllViews();
        }

        // ── Section 1: How are you feeling? (11 Mochi moods) ───────────────
        String[] moodLabels = {
                "Normal", "Happy", "Sad", "Calm", "Anxious",
                "Energetic", "Sensitive", "Romantic", "Lonely", "Mood Swing", "Food Craving"
        };
        Mood[] moodValues = {
                Mood.NORMAL, Mood.HAPPY, Mood.SAD, Mood.CALM, Mood.ANXIOUS,
                Mood.ENERGETIC, Mood.SENSITIVE, Mood.ROMANTIC, Mood.LONELY, Mood.MOOD_SWING, Mood.FOOD_CRAVING
        };
        for (int i = 0; i < moodValues.length; i++) {
            final Mood m = moodValues[i];
            boolean isSel = (selectedMood == m);
            addKawaiiBadgeView(binding.layoutMoodSelector, moodLabels[i], null,
                    KawaiiIconUtil.getMoodIconRes(requireContext(), m), isSel, v -> {
                        selectedMood = (selectedMood == m) ? null : m;
                        refreshAllKawaiiRows();
                    });
        }

        // Also append custom moods to the mood selector row
        for (SymptomTag tag : allTagsList) {
            if (tag.category != SymptomCategory.MOOD || !tag.isCustom)
                continue;
            boolean isSel = selectedSymptomIds.contains(tag.id);
            addKawaiiBadgeView(binding.layoutMoodSelector, tag.label,
                    tag.iconKey, R.drawable.ic_mochi_smiling, isSel, true,
                    v -> toggleSymptomSelection(tag.id));
        }

        // ── Section 2: Physical Condition (13 curated symptoms + custom) ─────
        List<SymptomTag> physicalSymptoms = new ArrayList<>(CyviaDatabase.buildDefaultSymptoms());
        for (SymptomTag tag : allTagsList) {
            if (tag.category == SymptomCategory.PHYSICAL && tag.isCustom) {
                physicalSymptoms.add(tag);
            }
        }

        for (SymptomTag tag : physicalSymptoms) {
            boolean isSel = selectedSymptomIds.contains(tag.id);
            addKawaiiBadgeView(binding.layoutPhysicalSymptoms, tag.label,
                    tag.iconKey, R.drawable.ic_mochi_smiling, isSel, true,
                    v -> toggleSymptomSelection(tag.id));
        }

        // ── Section 3: Medicine — Take Pill? ──────────────────────────────
        if (binding.layoutMedicineSelector != null) {
            boolean pillSel = Boolean.TRUE.equals(pillsTaken);
            addKawaiiBadgeView(binding.layoutMedicineSelector, "Take Pill",
                    null, R.drawable.ic_medicine_pill, pillSel, v -> {
                        pillsTaken = Boolean.TRUE.equals(pillsTaken) ? null : Boolean.TRUE;
                        refreshAllKawaiiRows();
                    });
        }

        // ── Section 4: Vaginal Discharge ───────────────────────────────────
        if (binding.layoutDischargeSelector != null) {
            binding.layoutDischargeSelector.removeAllViews();
            String[] dischargeLabels = {
                    "Excessive White", "Smelly", "Creamy Texture", "Watery Texture", "Brownish", "Yellowish"
            };
            String[] dischargeKeys = {
                    "EXCESSIVE_WHITE", "SMELLY", "CREAMY", "WATERY", "BROWNISH", "YELLOWISH"
            };
            int[] dischargeIcons = {
                    R.drawable.ic_discharge_excessive_white,
                    R.drawable.ic_discharge_smelly,
                    R.drawable.ic_discharge_creamy,
                    R.drawable.ic_discharge_watery,
                    R.drawable.ic_discharge_brownish,
                    R.drawable.ic_discharge_yellowish
            };
            for (int i = 0; i < dischargeKeys.length; i++) {
                final String dk = dischargeKeys[i];
                boolean isSel = dk.equals(selectedDischarge);
                addKawaiiBadgeView(binding.layoutDischargeSelector, dischargeLabels[i], null,
                        dischargeIcons[i], isSel, v -> {
                            selectedDischarge = dk.equals(selectedDischarge) ? null : dk;
                            refreshAllKawaiiRows();
                        });
            }
        }

        // ── Section 5: Physical Activity ───────────────────────────────────
        if (binding.layoutActivitySelector != null) {
            binding.layoutActivitySelector.removeAllViews();
            String[] activityLabels = {
                    "No Exercise", "Running", "Cycling", "Gym", "Aerobics & Dance", "Swimming", "Yoga"
            };
            String[] activityKeys = {
                    "NO_EXERCISE", "RUNNING", "CYCLING", "GYM", "AEROBIC_DANCE", "SWIMMING", "YOGA"
            };
            int[] activityIcons = {
                    R.drawable.ic_activity_no_exercise,
                    R.drawable.ic_activity_running,
                    R.drawable.ic_activity_cycling,
                    R.drawable.ic_activity_gym,
                    R.drawable.ic_activity_dance,
                    R.drawable.ic_activity_swimming,
                    R.drawable.ic_activity_yoga
            };
            for (int i = 0; i < activityKeys.length; i++) {
                final String ak = activityKeys[i];
                boolean isSel = ak.equals(selectedActivity);
                addKawaiiBadgeView(binding.layoutActivitySelector, activityLabels[i], null,
                        activityIcons[i], isSel, v -> {
                            selectedActivity = ak.equals(selectedActivity) ? null : ak;
                            refreshAllKawaiiRows();
                        });
            }
        }

        // ── Section 6: Sex (only if intimacy enabled/not minor safe) ──────
        if (binding.layoutSexSelector != null && binding.layoutSexSection.getVisibility() == View.VISIBLE) {
            binding.layoutSexSelector.removeAllViews();
            String[] sexLabels = {
                    "Nope", "Protected", "Unprotected"
            };
            String[] sexKeys = {
                    "NOPE", "PROTECTED", "UNPROTECTED"
            };
            int[] sexIcons = {
                    R.drawable.ic_sex_nope,
                    R.drawable.ic_sex_protected,
                    R.drawable.ic_sex_unprotected
            };
            for (int i = 0; i < sexKeys.length; i++) {
                final String sk = sexKeys[i];
                boolean isSel = sk.equals(selectedSex);
                addKawaiiBadgeView(binding.layoutSexSelector, sexLabels[i], null,
                        sexIcons[i], isSel, v -> {
                            selectedSex = sk.equals(selectedSex) ? null : sk;
                            refreshAllKawaiiRows();
                        });
            }
        }
    }

    private void toggleSymptomSelection(int tagId) {
        if (selectedSymptomIds.contains(tagId))
            selectedSymptomIds.remove(tagId);
        else
            selectedSymptomIds.add(tagId);
        refreshAllKawaiiRows();
    }

    private View addKawaiiBadgeView(ViewGroup parent, String label, String iconKey, int fallbackResId,
            boolean isSelected, View.OnClickListener onClick) {
        return addKawaiiBadgeView(parent, label, iconKey, fallbackResId, isSelected, false, onClick);
    }

    private View addKawaiiBadgeView(ViewGroup parent, String label, String iconKey, int fallbackResId,
            boolean isSelected, boolean useSmartFallback, View.OnClickListener onClick) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_kawaii_selector_badge, parent, false);
        MaterialCardView card = view.findViewById(R.id.card_kawaii_badge);
        ImageView iv = view.findViewById(R.id.iv_kawaii_icon);
        TextView tv = view.findViewById(R.id.tv_kawaii_label);

        tv.setText(label);
        KawaiiIconUtil.loadIcon(requireContext(), iv, iconKey, useSmartFallback ? label : null, fallbackResId);

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
                if (tempUploadLayout != null)
                    tempUploadLayout.setVisibility(View.GONE);
                for (int i = 0; i < layoutPresets.getChildCount(); i++) {
                    View child = layoutPresets.getChildAt(i);
                    MaterialCardView c = child.findViewById(R.id.card_kawaii_badge);
                    if (c != null) {
                        boolean isThis = child == v;
                        c.setStrokeColor(
                                requireContext().getColor(isThis ? R.color.cyvia_primary : R.color.cyvia_outline));
                        c.setStrokeWidth((int) ((isThis ? 2.5f : 1.2f) * getResources().getDisplayMetrics().density));
                        c.setCardBackgroundColor(requireContext()
                                .getColor(isThis ? R.color.cyvia_primary_container : R.color.cyvia_surface));
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
        log.pillsTaken = pillsTaken;

        List<String> ids = new ArrayList<>();
        for (int id : selectedSymptomIds)
            ids.add(String.valueOf(id));
        log.symptomIds = TextUtils.join(",", ids);

        String notesText = binding.etNotes.getText() != null
                ? binding.etNotes.getText().toString().trim()
                : "";
        log.notes = notesText.isEmpty() ? null : notesText;

        String tempText = binding.etTemperature.getText() != null
                ? binding.etTemperature.getText().toString().trim()
                : "";
        try {
            log.temperature = tempText.isEmpty() ? null : Float.parseFloat(tempText);
        } catch (NumberFormatException e) {
            log.temperature = null;
        }

        String weightText = binding.etWeight.getText() != null
                ? binding.etWeight.getText().toString().trim()
                : "";
        try {
            log.weight = weightText.isEmpty() ? null : Float.parseFloat(weightText);
        } catch (NumberFormatException e) {
            log.weight = null;
        }
        log.weightUnit = weightUnit;

        log.sexType = selectedSex;
        log.exerciseType = selectedActivity;
        log.dischargeType = selectedDischarge;

        logRepository.saveLog(log);

        FlowIntensity flowToSave = selectedFlow;
        long targetDay = logDate.toEpochDay();
        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            if (flowToSave != null) {
                if (settings != null && settings.getLastPeriodDeniedEpoch() >= targetDay) {
                    settings.setLastPeriodDeniedEpoch(targetDay - 1);
                }
                List<CycleEntry> cycles = cycleRepository.getAllCyclesSync();
                boolean handled = false;
                if (cycles != null) {
                    for (CycleEntry cycle : cycles) {
                        long start = cycle.startDate;
                        long end = cycle.isOngoing() ? LocalDate.now().toEpochDay() : cycle.endDate;
                        boolean isSamePeriod = (targetDay - start <= 10 && targetDay - start >= -2);
                        if (isSamePeriod && targetDay >= start - 2 && targetDay <= end + 2) {
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
                    // Trim or end any cycles that overlap or start after the new period date
                    if (cycles != null) {
                        for (CycleEntry c : cycles) {
                            if (c.startDate >= targetDay) {
                                cycleRepository.deleteCycle(c);
                            } else if (c.isOngoing() || c.endDate >= targetDay) {
                                c.endDate = targetDay - 1;
                                cycleRepository.updateCycle(c);
                            }
                        }
                    }
                    CycleEntry newCycle = new CycleEntry(targetDay, flowToSave);
                    if (logDate.isBefore(LocalDate.now().minusDays(5))) {
                        newCycle.endDate = targetDay + 4;
                    }
                    cycleRepository.insertCycle(newCycle);
                }
            } else {
                // User logged no flow -> completely remove or trim any cycle entry spanning this day
                if (settings != null) {
                    settings.setLastPeriodDeniedEpoch(targetDay);
                }
                removeFlowForDate(targetDay);
            }
        });

        if (adManager != null) {
            adManager.onLoggingFinished();
        }
        dismiss();
    }

    private void removeFlowForDate(long targetDay) {
        if (getContext() == null)
            return;
        com.khatibstudio.cyvia.data.db.dao.CycleEntryDao dao = CyviaDatabase.getDatabase(requireContext())
                .cycleEntryDao();
        List<CycleEntry> cycles = dao.getAllCyclesSync();
        if (cycles != null) {
            for (CycleEntry cycle : cycles) {
                long start = cycle.startDate;
                long end = cycle.isOngoing() ? LocalDate.now().toEpochDay() : cycle.endDate;
                if (start >= targetDay) {
                    // Entire cycle started on or after targetDay -> delete it completely
                    dao.deleteCycleEntry(cycle);
                } else if (targetDay <= end) {
                    // Day is inside a past or ongoing cycle -> truncate cycle end to before targetDay
                    cycle.endDate = targetDay - 1;
                    if (cycle.endDate < start) {
                        dao.deleteCycleEntry(cycle);
                    } else {
                        dao.updateCycleEntry(cycle);
                    }
                }
            }
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (adManager != null) {
            adManager.onLoggingFinished();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
