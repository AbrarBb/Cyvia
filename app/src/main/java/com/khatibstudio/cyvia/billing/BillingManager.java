package com.khatibstudio.cyvia.billing;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;

import java.util.Collections;
import java.util.List;

/**
 * Manages the Google Play Billing "Remove Ads" one-time purchase.
 *
 * Product ID: remove_ads  (ONE_TIME, non-consumable)
 *
 * UX Rules:
 *   - Purchase is triggered only from the Settings screen (one button).
 *   - Never shown as a popup. Never re-prompted after decline.
 *   - queryPurchasesAsync() is called on every app start to auto-restore
 *     purchases after reinstall or device change.
 *   - On verified purchase: adsRemoved=true is persisted in SharedPreferences.
 */
public class BillingManager implements PurchasesUpdatedListener {

    private static final String TAG = "BillingManager";
    public static final String PRODUCT_ID_REMOVE_ADS = "remove_ads";

    private final Context context;
    private final SettingsRepository settings;
    private final BillingClient billingClient;

    /** Exposed to UI so the "Remove Ads" button can show a spinner. */
    private final MutableLiveData<Boolean> _isPurchasing = new MutableLiveData<>(false);
    public final LiveData<Boolean> isPurchasing = _isPurchasing;

    /** Null until product details are loaded. */
    private ProductDetails removeAdsProductDetails;

    /** Callback interface for the owning Activity/Fragment. */
    public interface BillingCallback {
        void onPurchaseSuccess();
        void onPurchaseError(String message);
    }

    private BillingCallback callback;

    public BillingManager(Context context, SettingsRepository settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;

        billingClient = BillingClient.newBuilder(this.context)
                .enablePendingPurchases()
                .setListener(this)
                .build();
    }

    // ─── Initialisation ───────────────────────────────────────────────────

    /**
     * Connect to Play Billing and restore any existing purchases.
     * Call from the Application or MainActivity.
     */
    public void startConnection() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected");
                    queryExistingPurchases();
                    loadProductDetails();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected — will retry on next purchase attempt");
            }
        });
    }

    // ─── Purchase flow ────────────────────────────────────────────────────

    /**
     * Launch the Play Store purchase sheet for "Remove Ads".
     * Called when the user taps the single "Remove Ads" button in Settings.
     *
     * @param activity The currently visible Activity (required by Billing API).
     * @param cb       Callback for success or error.
     */
    public void launchRemoveAdsPurchase(Activity activity, BillingCallback cb) {
        this.callback = cb;

        if (settings.isAdsRemoved()) {
            // Already purchased — should not reach here, but handle gracefully
            if (cb != null) cb.onPurchaseSuccess();
            return;
        }

        if (removeAdsProductDetails == null) {
            // Product details not yet loaded — try reconnecting
            if (cb != null) cb.onPurchaseError("Store not available. Please try again.");
            startConnection();
            return;
        }

        List<BillingFlowParams.ProductDetailsParams> productList =
                Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(removeAdsProductDetails)
                                .build()
                );

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productList)
                .build();

        _isPurchasing.postValue(true);
        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);

        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            _isPurchasing.postValue(false);
            if (cb != null) cb.onPurchaseError("Could not open store: " + result.getDebugMessage());
        }
    }

    // ─── PurchasesUpdatedListener ─────────────────────────────────────────

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
        _isPurchasing.postValue(false);

        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User cancelled the purchase");
            // Do NOT re-prompt or show any "are you sure?" — user declined, respect that
        } else {
            Log.w(TAG, "Purchase failed: " + result.getDebugMessage());
            if (callback != null) {
                callback.onPurchaseError("Purchase could not be completed. Please try again later.");
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    /** Restore purchases on app start — handles reinstall or device change. */
    private void queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (Purchase purchase : purchases) {
                            if (purchase.getProducts().contains(PRODUCT_ID_REMOVE_ADS)
                                    && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                // Restore: set ads removed without showing any UI
                                settings.setAdsRemoved(true);
                                acknowledgePurchaseIfNeeded(purchase);
                            }
                        }
                    }
                }
        );
    }

    /** Load product details (price, name) from Play Store for display in Settings. */
    private void loadProductDetails() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT_ID_REMOVE_ADS)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                ))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (ProductDetails details : productDetailsList) {
                    if (details.getProductId().equals(PRODUCT_ID_REMOVE_ADS)) {
                        removeAdsProductDetails = details;
                    }
                }
            }
        });
    }

    private void handlePurchase(Purchase purchase) {
        if (!purchase.getProducts().contains(PRODUCT_ID_REMOVE_ADS)) return;
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;

        // Persist immediately
        settings.setAdsRemoved(true);
        acknowledgePurchaseIfNeeded(purchase);

        if (callback != null) {
            callback.onPurchaseSuccess();
        }
    }

    /** Acknowledge the purchase to prevent it from being refunded by Play. */
    private void acknowledgePurchaseIfNeeded(Purchase purchase) {
        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
            billingClient.acknowledgePurchase(params, result ->
                    Log.d(TAG, "Acknowledge result: " + result.getResponseCode())
            );
        }
    }

    /** Returns the formatted price string for display in Settings (e.g. "USD 3.99"). */
    public String getRemoveAdsPriceString() {
        if (removeAdsProductDetails == null) return "";
        ProductDetails.OneTimePurchaseOfferDetails offer =
                removeAdsProductDetails.getOneTimePurchaseOfferDetails();
        return offer != null ? offer.getFormattedPrice() : "";
    }

    public void endConnection() {
        billingClient.endConnection();
    }
}
