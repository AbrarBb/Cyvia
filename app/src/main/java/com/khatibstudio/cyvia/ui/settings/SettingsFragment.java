package com.khatibstudio.cyvia.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.khatibstudio.cyvia.BuildConfig;
import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.MainActivity;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.backup.BackupManager;
import com.khatibstudio.cyvia.billing.BillingManager;
import com.khatibstudio.cyvia.data.model.TrackingMode;
import com.khatibstudio.cyvia.data.repository.CycleRepository;
import com.khatibstudio.cyvia.data.repository.LogRepository;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;
import com.khatibstudio.cyvia.data.repository.SymptomRepository;
import com.khatibstudio.cyvia.databinding.FragmentSettingsBinding;
import com.khatibstudio.cyvia.ui.onboarding.OnboardingActivity;
import com.khatibstudio.cyvia.worker.BootReceiver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings screen fragment.
 * All changes are persisted immediately to SharedPreferences.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsRepository settings;
    private CycleRepository cycleRepository;
    private LogRepository logRepository;
    private SymptomRepository symptomRepository;
    private BackupManager backupManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // SAF launchers for import/export
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<Intent> pinLockLauncher;

    // Tracking mode labels
    private static final String[] TRACKING_MODE_LABELS = {
            "Regular tracking", "Irregular cycles", "Trying to conceive",
            "Avoiding pregnancy", "No periods (contraception)", "Postpartum", "Perimenopause"
    };
    private static final TrackingMode[] TRACKING_MODES = {
            TrackingMode.REGULAR, TrackingMode.IRREGULAR, TrackingMode.TRYING_TO_CONCEIVE,
            TrackingMode.AVOIDING_PREGNANCY, TrackingMode.NO_PERIODS_CONTRACEPTION,
            TrackingMode.POSTPARTUM, TrackingMode.PERIMENOPAUSE
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CyviaApplication app = CyviaApplication.from(requireContext());
        settings = app.getSettingsRepository();
        cycleRepository = app.getCycleRepository();
        logRepository = app.getLogRepository();
        symptomRepository = app.getSymptomRepository();
        backupManager = new BackupManager(requireContext());

        pinLockLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> updateAppLockUI()
        );

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) performImport(uri); }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        populateCurrentValues();
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAppLockUI();
        updateProfileSummary();
    }

    private void updateProfileSummary() {
        if (binding == null) return;
        String name = settings.getUserName();
        binding.tvSummaryName.setText(name.isEmpty() ? "My Profile" : name);
        int modeIdx = indexOfMode(settings.getTrackingMode());
        binding.tvSummaryDetails.setText(TRACKING_MODE_LABELS[modeIdx] + " · Age " + settings.getUserAge());
        com.khatibstudio.cyvia.util.KawaiiIconUtil.loadIcon(requireContext(), binding.ivSummaryAvatar, settings.getUserPfp(), R.drawable.ic_kawaii_melody);
    }

    // ─── Pre-fill saved values ────────────────────────────────────────────

    private void populateCurrentValues() {
        updateProfileSummary();

        // Notifications
        binding.switchNotifPeriod.setChecked(settings.isPeriodNotifEnabled());
        binding.switchNotifOvulation.setChecked(settings.isOvulationNotifEnabled());
        binding.switchNotifLog.setChecked(settings.isLogReminderEnabled());
        binding.switchNotifDiscreet.setChecked(requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE).getBoolean("notif_discreet", false));

        // Minor-safe & Minimal mode
        updateAppLockUI();
        binding.switchMinorSafe.setChecked(settings.isMinorSafeMode());
        binding.switchTrackIntimacy.setChecked(settings.isTrackIntimacyEnabled());
        binding.switchTrackIntimacy.setEnabled(!settings.isMinorSafeMode());
        binding.switchMinimalMode.setChecked(requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE).getBoolean("minimal_mode", false));

        // Theme
        String theme = settings.getThemeMode();
        if (SettingsRepository.THEME_LIGHT.equals(theme)) binding.btnThemeLight.setChecked(true);
        else if (SettingsRepository.THEME_DARK.equals(theme)) binding.btnThemeDark.setChecked(true);
        else binding.btnThemeSystem.setChecked(true);

        // Auto Backup
        binding.switchAutoBackupMonthly.setChecked(requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE).getBoolean("auto_backup_enabled", false));

        // Ads removed
        if (settings.isAdsRemoved()) {
            binding.btnRemoveAds.setEnabled(false);
            binding.btnRemoveAds.setText("✓ Purchased");
        }

        // Version
        try {
            String version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            binding.tvVersion.setText(getString(R.string.settings_version, version));
        } catch (Exception ignored) {}
    }

    // ─── Listeners ────────────────────────────────────────────────────────

    private void setupListeners() {
        // Profile summary card -> navigate to ProfileEditFragment
        View.OnClickListener openProfile = v ->
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_profile_edit);
        binding.cardProfileSummary.setOnClickListener(openProfile);
        binding.btnEditProfile.setOnClickListener(openProfile);

        // Notifications
        binding.switchNotifPeriod.setOnCheckedChangeListener((v, checked) -> {
            settings.setPeriodNotifEnabled(checked);
            BootReceiver.scheduleReminders(requireContext());
        });
        binding.switchNotifOvulation.setOnCheckedChangeListener((v, checked) -> {
            settings.setOvulationNotifEnabled(checked);
            BootReceiver.scheduleReminders(requireContext());
        });
        binding.switchNotifLog.setOnCheckedChangeListener((v, checked) -> {
            settings.setLogReminderEnabled(checked);
            BootReceiver.scheduleReminders(requireContext());
        });
        binding.switchNotifDiscreet.setOnCheckedChangeListener((v, checked) ->
                requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("notif_discreet", checked).apply());

        // Minor-safe & Minimal mode
        binding.switchMinorSafe.setOnCheckedChangeListener((v, checked) -> {
            settings.setMinorSafeMode(checked);
            binding.switchTrackIntimacy.setEnabled(!checked);
            if (checked) {
                binding.switchTrackIntimacy.setChecked(false);
                settings.setTrackIntimacyEnabled(false);
            }
        });
        binding.switchTrackIntimacy.setOnCheckedChangeListener((v, checked) ->
                settings.setTrackIntimacyEnabled(checked));
        binding.switchMinimalMode.setOnCheckedChangeListener((v, checked) ->
                requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("minimal_mode", checked).apply());

        // Theme toggle
        binding.toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String mode;
            if (checkedId == R.id.btn_theme_light) mode = SettingsRepository.THEME_LIGHT;
            else if (checkedId == R.id.btn_theme_dark) mode = SettingsRepository.THEME_DARK;
            else mode = SettingsRepository.THEME_SYSTEM;
            settings.setThemeMode(mode);
            applyTheme(mode);
        });

        // Export
        binding.btnExport.setOnClickListener(v -> {
            CyviaApplication.suppressLockOnce();
            performExport();
        });

        // Import
        binding.btnImport.setOnClickListener(v -> {
            CyviaApplication.suppressLockOnce();
            importLauncher.launch(new String[]{"application/json", "*/*"});
        });

        // Local Auto-Backup control
        binding.switchAutoBackupMonthly.setOnCheckedChangeListener((v, checked) -> {
            requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("auto_backup_enabled", checked).apply();
            BootReceiver.scheduleAutoBackup(requireContext());
            if (checked) {
                executor.execute(() -> {
                    BackupManager.BackupResult res = backupManager.backupToLocalAuto();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Auto-backup enabled & saved locally!", Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });

        // Delete all
        binding.btnDeleteAll.setOnClickListener(v -> confirmDeleteAll());

        // Help & FAQ
        binding.cardFaq.setOnClickListener(v ->
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_faq));

        // Remove Ads
        binding.btnRemoveAds.setOnClickListener(v -> launchRemoveAdsPurchase());
    }

    private void updateAppLockUI() {
        if (binding == null) return;
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("cyvia_settings", android.content.Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("app_lock_enabled", false);
        binding.switchAppLock.setOnCheckedChangeListener(null);
        binding.switchAppLock.setChecked(enabled);
        binding.rowChangePin.setVisibility(enabled ? View.VISIBLE : View.GONE);
        setupAppLockListeners();
    }

    private void setupAppLockListeners() {
        if (binding == null) return;
        binding.switchAppLock.setOnCheckedChangeListener((v, checked) -> {
            if (checked) {
                pinLockLauncher.launch(com.khatibstudio.cyvia.ui.pin.PinLockActivity.getIntent(requireContext(), com.khatibstudio.cyvia.ui.pin.PinLockActivity.MODE_SETUP));
            } else {
                pinLockLauncher.launch(com.khatibstudio.cyvia.ui.pin.PinLockActivity.getIntent(requireContext(), com.khatibstudio.cyvia.ui.pin.PinLockActivity.MODE_VERIFY_DISABLE));
            }
        });
        binding.rowChangePin.setOnClickListener(v -> {
            pinLockLauncher.launch(com.khatibstudio.cyvia.ui.pin.PinLockActivity.getIntent(requireContext(), com.khatibstudio.cyvia.ui.pin.PinLockActivity.MODE_CHANGE));
        });
    }

    // ─── Theme ────────────────────────────────────────────────────────────

    private void applyTheme(String mode) {
        switch (mode) {
            case SettingsRepository.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case SettingsRepository.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    // ─── Export ───────────────────────────────────────────────────────────

    private void performExport() {
        executor.execute(() -> {
            BackupManager.BackupResult result = backupManager.exportAndShare();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (result.success) {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // ─── Import ───────────────────────────────────────────────────────────

    private void performImport(Uri uri) {
        executor.execute(() -> {
            BackupManager.BackupResult result = backupManager.importFromUri(uri);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ─── Delete all ───────────────────────────────────────────────────────

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_delete_all_title))
                .setMessage(getString(R.string.dialog_delete_all_message))
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .setPositiveButton(getString(R.string.dialog_delete_all_confirm), (dialog, which) -> {
                    cycleRepository.deleteAll();
                    logRepository.deleteAll();
                    symptomRepository.deleteAll();

                    boolean adsRemoved = settings.isAdsRemoved();
                    settings.clearAll();
                    if (adsRemoved) {
                        settings.setAdsRemoved(true);
                    }

                    Toast.makeText(requireContext(), "All data deleted. Starting setup...", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(requireContext(), OnboardingActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .show();
    }

    // ─── Billing ──────────────────────────────────────────────────────────

    private void launchRemoveAdsPurchase() {
        if (!(getActivity() instanceof MainActivity)) return;
        BillingManager billing = ((MainActivity) getActivity()).getBillingManager();
        billing.launchRemoveAdsPurchase(getActivity(), new BillingManager.BillingCallback() {
            @Override
            public void onPurchaseSuccess() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    binding.btnRemoveAds.setEnabled(false);
                    binding.btnRemoveAds.setText(getString(R.string.settings_remove_ads_purchased));
                    Toast.makeText(requireContext(), "Ads removed! Thank you!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onPurchaseError(String message) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show());
            }
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
