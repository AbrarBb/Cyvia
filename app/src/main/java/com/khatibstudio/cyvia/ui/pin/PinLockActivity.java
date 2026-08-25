package com.khatibstudio.cyvia.ui.pin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.khatibstudio.cyvia.CyviaApplication;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;

import java.util.concurrent.Executor;

public class PinLockActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "pin_mode";
    public static final int MODE_UNLOCK = 0;
    public static final int MODE_SETUP = 1;
    public static final int MODE_VERIFY_DISABLE = 2;
    public static final int MODE_CHANGE = 3;

    private int mode = MODE_UNLOCK;
    private StringBuilder currentPin = new StringBuilder();
    private String firstEnteredPin = null;
    private boolean isConfirming = false;
    private boolean isChangingOldVerified = false;

    private TextView tvTitle, tvPrompt, tvError;
    private ImageView imgLockIcon;
    private View btnFingerprint;
    private View[] dotViews;
    private SharedPreferences prefs;
    private SettingsRepository settingsRepository;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    public static void startUnlock(Context context) {
        Intent intent = new Intent(context, PinLockActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_UNLOCK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    public static Intent getIntent(Context context, int mode) {
        Intent intent = new Intent(context, PinLockActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_lock);

        prefs = getSharedPreferences("cyvia_settings", Context.MODE_PRIVATE);
        settingsRepository = new SettingsRepository(this);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);

        tvTitle = findViewById(R.id.tv_pin_title);
        tvPrompt = findViewById(R.id.tv_pin_prompt);
        tvError = findViewById(R.id.tv_pin_error);
        imgLockIcon = findViewById(R.id.img_lock_icon);
        btnFingerprint = findViewById(R.id.btn_fingerprint);

        dotViews = new View[]{
                findViewById(R.id.dot_view_1),
                findViewById(R.id.dot_view_2),
                findViewById(R.id.dot_view_3),
                findViewById(R.id.dot_view_4)
        };

        setupModeUI();
        setupNumPad();
        setupAvatarIcon();
        
        if (mode == MODE_UNLOCK) {
            setupBiometrics();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mode == MODE_UNLOCK) {
                    // Minimize app rather than bypassing lock
                    moveTaskToBack(true);
                } else {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        });
    }

    private void setupModeUI() {
        tvError.setVisibility(View.INVISIBLE);
        if (mode == MODE_UNLOCK) {
            tvTitle.setText("Cyvia App Lock");
            tvPrompt.setText("Enter your 4-digit PIN");
        } else if (mode == MODE_SETUP) {
            tvTitle.setText("Set Up PIN");
            tvPrompt.setText("Create a 4-digit PIN");
        } else if (mode == MODE_VERIFY_DISABLE) {
            tvTitle.setText("Disable App Lock");
            tvPrompt.setText("Enter your current 4-digit PIN");
        } else if (mode == MODE_CHANGE) {
            tvTitle.setText("Change PIN");
            tvPrompt.setText("Enter your current 4-digit PIN");
        }
    }

    private void setupNumPad() {
        int[] numIds = {
                R.id.btn_num_0, R.id.btn_num_1, R.id.btn_num_2,
                R.id.btn_num_3, R.id.btn_num_4, R.id.btn_num_5,
                R.id.btn_num_6, R.id.btn_num_7, R.id.btn_num_8, R.id.btn_num_9
        };

        for (int i = 0; i < numIds.length; i++) {
            final int digit = i;
            findViewById(numIds[i]).setOnClickListener(v -> onDigitPressed(String.valueOf(digit)));
        }

        findViewById(R.id.btn_backspace).setOnClickListener(v -> onBackspacePressed());
    }

    private void onDigitPressed(String digit) {
        if (currentPin.length() < 4) {
            currentPin.append(digit);
            updateDots();
            if (currentPin.length() == 4) {
                new Handler(Looper.getMainLooper()).postDelayed(this::onPinCompleted, 150);
            }
        }
    }

    private void onBackspacePressed() {
        if (currentPin.length() > 0) {
            currentPin.deleteCharAt(currentPin.length() - 1);
            tvError.setVisibility(View.INVISIBLE);
            updateDots();
        }
    }

    private void updateDots() {
        int length = currentPin.length();
        for (int i = 0; i < dotViews.length; i++) {
            if (i < length) {
                dotViews[i].setBackgroundResource(R.drawable.bg_circle_dot_filled);
                dotViews[i].setBackgroundTintList(null);
            } else {
                dotViews[i].setBackgroundResource(R.drawable.bg_circle_dot_empty);
                dotViews[i].setBackgroundTintList(null);
            }
        }
    }

    private void onPinCompleted() {
        String entered = currentPin.toString();
        String savedPin = prefs.getString("app_lock_pin", "");

        if (mode == MODE_UNLOCK) {
            if (entered.equals(savedPin)) {
                CyviaApplication.onAppUnlocked();
                finish();
            } else {
                showError("Incorrect PIN. Try again.");
            }
        } else if (mode == MODE_VERIFY_DISABLE) {
            if (entered.equals(savedPin)) {
                prefs.edit().putBoolean("app_lock_enabled", false).remove("app_lock_pin").apply();
                Toast.makeText(this, "App Lock disabled", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                showError("Incorrect PIN. Try again.");
            }
        } else if (mode == MODE_CHANGE) {
            if (!isChangingOldVerified) {
                if (entered.equals(savedPin)) {
                    isChangingOldVerified = true;
                    currentPin.setLength(0);
                    updateDots();
                    tvError.setVisibility(View.INVISIBLE);
                    tvPrompt.setText("Create a new 4-digit PIN");
                } else {
                    showError("Incorrect current PIN.");
                }
            } else {
                handleSetupStep(entered);
            }
        } else if (mode == MODE_SETUP) {
            handleSetupStep(entered);
        }
    }

    private void handleSetupStep(String entered) {
        if (!isConfirming) {
            firstEnteredPin = entered;
            isConfirming = true;
            currentPin.setLength(0);
            updateDots();
            tvError.setVisibility(View.INVISIBLE);
            tvPrompt.setText("Confirm your 4-digit PIN");
        } else {
            if (entered.equals(firstEnteredPin)) {
                prefs.edit().putBoolean("app_lock_enabled", true).putString("app_lock_pin", entered).apply();
                Toast.makeText(this, "PIN Lock enabled successfully!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                isConfirming = false;
                firstEnteredPin = null;
                showError("PINs don't match. Create again.");
                tvPrompt.setText(mode == MODE_CHANGE ? "Create a new 4-digit PIN" : "Create a 4-digit PIN");
            }
        }
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
        // Shake dots
        View dotsRow = findViewById(R.id.layout_pin_dots);
        dotsRow.animate().translationX(-20).setDuration(50).withEndAction(() ->
                dotsRow.animate().translationX(20).setDuration(50).withEndAction(() ->
                        dotsRow.animate().translationX(-10).setDuration(50).withEndAction(() ->
                                dotsRow.animate().translationX(0).setDuration(50).start()
                        ).start()
                ).start()
        ).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            currentPin.setLength(0);
            updateDots();
        }, 500);
    }
    
    private void setupAvatarIcon() {
        String pack = settingsRepository.getAvatarPack();
        if ("KITTY".equals(pack)) {
            imgLockIcon.setImageResource(R.drawable.ic_kitty_mood_normal);
            imgLockIcon.setImageTintList(null); // Remove tint so full colors show
        } else if ("BUNNY".equals(pack)) {
            imgLockIcon.setImageResource(R.drawable.ic_bunny_mood_normal);
            imgLockIcon.setImageTintList(null);
        } else {
            // Default Mochi
            imgLockIcon.setImageResource(R.drawable.ic_mochi_smiling);
            imgLockIcon.setImageTintList(null);
        }
    }
    
    private void setupBiometrics() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            return; // Biometrics not available or enrolled
        }
        
        btnFingerprint.setVisibility(View.VISIBLE);
        btnFingerprint.setOnClickListener(v -> showBiometricPrompt());

        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // Just fall back to PIN
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                CyviaApplication.onAppUnlocked();
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                showError("Fingerprint not recognized");
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Cyvia")
                .setSubtitle("Confirm your fingerprint or face to continue")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();
                
        // Auto show on first load
        showBiometricPrompt();
    }
    
    private void showBiometricPrompt() {
        if (biometricPrompt != null && promptInfo != null) {
            biometricPrompt.authenticate(promptInfo);
        }
    }
}
