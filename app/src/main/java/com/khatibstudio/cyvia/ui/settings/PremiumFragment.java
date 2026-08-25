package com.khatibstudio.cyvia.ui.settings;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.ads.AdManager;
import com.khatibstudio.cyvia.data.repository.SettingsRepository;

import java.util.Arrays;
import java.util.List;

public class PremiumFragment extends Fragment {

    private SettingsRepository settings;
    private AdManager adManager;

    private LinearLayout containerColors;
    private LinearLayout containerIcons;
    private LinearLayout containerAvatars;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_premium, container, false);
        settings = new SettingsRepository(requireContext());
        adManager = new AdManager(settings);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> androidx.navigation.Navigation.findNavController(v).navigateUp());

        containerColors = view.findViewById(R.id.container_colors);
        containerIcons = view.findViewById(R.id.container_icons);
        containerAvatars = view.findViewById(R.id.container_avatars);

        setupColors();
        setupIcons();
        setupAvatars();

        return view;
    }

    private void setupColors() {
        containerColors.removeAllViews();
        List<PremiumItem> items = Arrays.asList(
                PremiumItem.createColorItem("Color_LAVENDER", "Lavender", ContextCompat.getColor(requireContext(), R.color.cyvia_primary), true),
                PremiumItem.createColorItem("Color_PINK", "Pink", Color.parseColor("#F8BBD0"), false),
                PremiumItem.createColorItem("Color_MINT", "Mint", Color.parseColor("#B2DFDB"), false),
                PremiumItem.createColorItem("Color_OCEAN", "Ocean", Color.parseColor("#BBDEFB"), false)
        );

        for (PremiumItem item : items) {
            View card = createPremiumCard(item, false);
            if (item.isColorItem) {
                ImageView img = card.findViewById(R.id.img_preview);
                img.setImageTintList(ColorStateList.valueOf(item.colorVal));
                img.setImageResource(R.drawable.bg_circle_dot); // A simple circle or use your existing drawable
            }
            
            boolean isSelected = isItemSelected(item);
            MaterialCardView cardView = (MaterialCardView) card;
            if (isSelected) {
                cardView.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.cyvia_primary));
            } else {
                cardView.setStrokeColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
            }
            
            card.setOnClickListener(v -> handleItemClick(item, "Color Theme", () -> {
                String colorName = item.id.replace("Color_", "");
                settings.setAccentColor(colorName);
                requireActivity().recreate();
            }));
            containerColors.addView(card);
        }
    }

    private void setupIcons() {
        containerIcons.removeAllViews();
        List<PremiumItem> items = Arrays.asList(
                new PremiumItem("ICON_DEFAULT", "Purple", R.mipmap.ic_launcher, true, ".MainActivityDefault"),
                new PremiumItem("ICON_PINK", "Pink", R.mipmap.ic_launcher_pink, false, ".MainActivityPink"),
                new PremiumItem("ICON_MINT", "Mint", R.mipmap.ic_launcher_mint, false, ".MainActivityMint"),
                new PremiumItem("ICON_OCEAN", "Ocean", R.mipmap.ic_launcher_ocean, false, ".MainActivityOcean")
        );

        for (PremiumItem item : items) {
            View card = createPremiumCard(item, true);
            
            boolean isSelected = isItemSelected(item);
            MaterialCardView cardView = (MaterialCardView) card;
            if (isSelected) {
                cardView.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.cyvia_primary));
            } else {
                cardView.setStrokeColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
            }
            
            card.setOnClickListener(v -> handleItemClick(item, "App Icon", () -> {
                PackageManager pm = requireContext().getPackageManager();
                String[] allAliases = {".MainActivityDefault", ".MainActivityPink", ".MainActivityMint", ".MainActivityOcean"};
                for (String alias : allAliases) {
                    pm.setComponentEnabledSetting(
                            new android.content.ComponentName(requireContext(), "com.khatibstudio.cyvia" + alias),
                            alias.equals(item.alias) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                    );
                }
                Toast.makeText(requireContext(), "Icon updated! It may take a moment to reflect on the home screen.", Toast.LENGTH_SHORT).show();
            }));
            containerIcons.addView(card);
        }
    }

    private void setupAvatars() {
        containerAvatars.removeAllViews();
        List<PremiumItem> items = Arrays.asList(
                new PremiumItem("AVATAR_MOCHI", "Mochi", R.drawable.ic_mochi_smiling, true),
                new PremiumItem("AVATAR_KITTY", "Kitty", R.drawable.ic_kawaii_pack_kitty, false),
                new PremiumItem("AVATAR_BUNNY", "Bunny", R.drawable.ic_kawaii_pack_bunny, false)
        );

        for (PremiumItem item : items) {
            View card = createPremiumCard(item, true);
            
            boolean isSelected = isItemSelected(item);
            MaterialCardView cardView = (MaterialCardView) card;
            if (isSelected) {
                cardView.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.cyvia_primary));
            } else {
                cardView.setStrokeColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
            }
            
            card.setOnClickListener(v -> handleItemClick(item, "Avatar Pack", () -> {
                settings.setAvatarPack(item.id.replace("AVATAR_", ""));
                Toast.makeText(requireContext(), item.title + " pack selected!", Toast.LENGTH_SHORT).show();
            }));
            containerAvatars.addView(card);
        }
    }

    private View createPremiumCard(PremiumItem item, boolean isImageRes) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_premium_card, null);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        ImageView imgPreview = view.findViewById(R.id.img_preview);
        View overlay = view.findViewById(R.id.overlay_locked);

        tvTitle.setText(item.title);
        if (isImageRes) {
            imgPreview.setImageResource(item.resId);
        }

        boolean isUnlocked = item.isFree || settings.isAdsRemoved() || isItemSelected(item);
        overlay.setVisibility(isUnlocked ? View.GONE : View.VISIBLE);
        imgPreview.setAlpha(isUnlocked ? 1.0f : 0.4f);

        return view;
    }
    
    private boolean isItemSelected(PremiumItem item) {
        if (item.id.startsWith("Color_")) {
            String colorName = item.id.replace("Color_", "");
            return colorName.equals(settings.getAccentColor());
        } else if (item.id.startsWith("AVATAR_")) {
            String packName = item.id.replace("AVATAR_", "");
            // "Mochi" is default, so if getAvatarPack() is "DEFAULT", Mochi is selected
            if (packName.equals("MOCHI") && settings.getAvatarPack().equals("DEFAULT")) return true;
            return packName.equals(settings.getAvatarPack());
        } else if (item.id.startsWith("ICON_")) {
            try {
                PackageManager pm = requireContext().getPackageManager();
                int state = pm.getComponentEnabledSetting(new android.content.ComponentName(requireContext(), "com.khatibstudio.cyvia" + item.alias));
                if (item.alias.equals(".MainActivityDefault")) {
                    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                } else {
                    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                }
            } catch (Exception e) {
                return item.alias.equals(".MainActivityDefault");
            }
        }
        return false;
    }

    private void handleItemClick(PremiumItem item, String typeLabel, Runnable applyAction) {
        if (isItemSelected(item)) return; // Already selected
        
        boolean isUnlocked = item.isFree || settings.isAdsRemoved();
        if (isUnlocked) {
            applyAction.run();
            refreshAllUI();
        } else {
            // Show custom preview dialog
            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_unlock_premium, null);
            TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
            TextView tvDesc = dialogView.findViewById(R.id.tv_dialog_desc);
            ImageView img = dialogView.findViewById(R.id.img_dialog_preview);
            
            tvTitle.setText("Unlock " + typeLabel);
            tvDesc.setText("Unlock the " + item.title + " customization permanently by watching a short video ad?");
            
            if (item.id.startsWith("Color_")) {
                if (item.colorRes != 0) {
                    img.setImageTintList(ColorStateList.valueOf(getResources().getColor(item.colorRes, null)));
                    img.setImageResource(R.drawable.bg_circle_dot);
                } else {
                    img.setImageTintList(ColorStateList.valueOf(item.colorVal));
                    img.setImageResource(R.drawable.bg_circle_dot);
                }
            } else {
                img.setImageResource(item.resId);
            }

            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogView)
                    .create();
                    
            dialogView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
            dialogView.findViewById(R.id.btn_dialog_watch_ad).setOnClickListener(v -> {
                dialog.dismiss();
                adManager.showRewardedAd(requireActivity(), AdManager.THEME_REWARDED_AD_UNIT_ID, () -> {
                    applyAction.run();
                    refreshAllUI();
                });
            });
            
            dialog.show();
        }
    }
    
    private void refreshAllUI() {
        setupColors();
        setupIcons();
        setupAvatars();
    }

    private static class PremiumItem {
        String id;
        String title;
        int resId;
        int colorRes;
        int colorVal;
        boolean isFree;
        boolean isColorItem;
        String alias;

        // For image based
        PremiumItem(String id, String title, int resId, boolean isFree) {
            this.id = id;
            this.title = title;
            this.resId = resId;
            this.isFree = isFree;
            this.isColorItem = false;
        }

        // For Icon alias
        PremiumItem(String id, String title, int resId, boolean isFree, String alias) {
            this.id = id;
            this.title = title;
            this.resId = resId;
            this.isFree = isFree;
            this.alias = alias;
            this.isColorItem = false;
        }

        static PremiumItem createColorItem(String id, String title, int colorVal, boolean isFree) {
            PremiumItem item = new PremiumItem(id, title, 0, isFree);
            item.isColorItem = true;
            item.colorVal = colorVal;
            return item;
        }
    }
}
