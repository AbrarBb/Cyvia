package com.khatibstudio.cyvia;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.khatibstudio.cyvia.billing.BillingManager;
import com.khatibstudio.cyvia.databinding.ActivityMainBinding;
import com.khatibstudio.cyvia.ui.log.DailyLogBottomSheet;
import com.khatibstudio.cyvia.ui.onboarding.OnboardingActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * Single-activity host for all main navigation destinations.
 *
 * Checks onboarding on first launch and redirects to OnboardingActivity.
 * Initialises BillingManager for purchase restore.
 * The bottom navigation's center item (nav_log) opens DailyLogBottomSheet
 * instead of navigating to a fragment.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private BillingManager billingManager;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Permission handled gracefully — if denied, notification settings toggle can guide user later
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CyviaApplication app = CyviaApplication.from(this);

        // Apply Premium Theme if selected
        String accentColor = app.getSettingsRepository().getAccentColor();
        if ("PINK".equals(accentColor)) {
            setTheme(R.style.Theme_Cyvia_Pink);
        } else if ("MINT".equals(accentColor)) {
            setTheme(R.style.Theme_Cyvia_Mint);
        } else if ("OCEAN".equals(accentColor)) {
            setTheme(R.style.Theme_Cyvia_Ocean);
        }

        super.onCreate(savedInstanceState);

        // Check if onboarding is needed (before inflating main layout)
        if (!app.getSettingsRepository().isOnboardingComplete()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();
        setupBilling(app);
        requestNotificationPermissionIfNeeded();
        app.initAdMobIfNeeded(null);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        // Wire up Home, Calendar, Insights, Settings to NavController
        // The center item (nav_log) is intercepted below to open BottomSheet
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_log) {
                openDailyLogSheet();
                return false; // Don't visually select the center item
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // Keep bottom nav selection in sync with back stack
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            MenuItem item = binding.bottomNav.getMenu().findItem(destId);
            if (item != null) {
                item.setChecked(true);
            }
        });
    }

    // ─── Daily Log ────────────────────────────────────────────────────────

    private void openDailyLogSheet() {
        DailyLogBottomSheet sheet = DailyLogBottomSheet.newInstance(null); // null = today
        sheet.show(getSupportFragmentManager(), DailyLogBottomSheet.TAG);
    }

    // ─── Billing ──────────────────────────────────────────────────────────

    private void setupBilling(CyviaApplication app) {
        billingManager = new BillingManager(this, app.getSettingsRepository());
        billingManager.startConnection();
    }

    public BillingManager getBillingManager() {
        return billingManager;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billingManager != null) {
            billingManager.endConnection();
        }
    }
}
