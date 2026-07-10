package com.khatibstudio.cyvia.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.khatibstudio.cyvia.R;
import com.khatibstudio.cyvia.data.model.Mood;

import java.io.InputStream;

/**
 * Helper class for handling Kawaii character icons and custom uploaded images.
 */
public class KawaiiIconUtil {

    private static final String TAG = "KawaiiIconUtil";

    public static final String[] PRESET_KAWAII_ICONS = {
            "ic_mochi_reading",
            "ic_mochi_drinking_tea",
            "ic_mochi_stretching",
            "ic_mochi_sparkles",
            "ic_mochi_heart_eyes",
            "ic_mochi_cozy",
            "ic_mochi_hugging",
            "ic_mochi_sleeping",
            "ic_mochi_smiling",
            "ic_mochi_waving",
            "ic_mochi_worried",
            "ic_mochi_sick",
            "ic_mochi_celebrating",
            "ic_mochi_mood_happy",
            "ic_mochi_mood_calm",
            "ic_mochi_mood_sad",
            "ic_mochi_mood_anxious",
            "ic_mochi_mood_irritable",
            "ic_mochi_mood_energetic",
            "ic_mochi_mood_tired",
            "ic_mochi_mood_sensitive",
            "ic_mochi_mood_frisky"
    };

    public static String cleanIconKey(String iconKey) {
        if (iconKey == null) return null;
        switch (iconKey) {
            case "ic_kawaii_pompom": return "ic_mochi_cozy";
            case "ic_kawaii_keroppi": return "ic_mochi_sick";
            case "ic_kawaii_melody": return "ic_mochi_cozy";
            case "ic_kawaii_cinna": return "ic_mochi_drinking_tea";
            case "ic_kawaii_kitty": return "ic_mochi_smiling";
            case "ic_kawaii_kuromi": return "ic_mochi_waving";
            case "ic_kawaii_blackcat": return "ic_mochi_stretching";
            case "ic_mood_happy": return "ic_mochi_mood_happy";
            case "ic_mood_calm": return "ic_mochi_mood_calm";
            case "ic_mood_sad": return "ic_mochi_mood_sad";
            case "ic_mood_anxious": return "ic_mochi_mood_anxious";
            case "ic_mood_irritable": return "ic_mochi_mood_irritable";
            case "ic_mood_energetic": return "ic_mochi_mood_energetic";
            case "ic_mood_tired": return "ic_mochi_mood_tired";
            case "ic_mood_sensitive": return "ic_mochi_mood_sensitive";
            case "ic_mood_frisky": return "ic_mochi_mood_frisky";
            default: return iconKey;
        }
    }

    /**
     * Returns the Mochi-pose drawable resource ID for a built-in Mood.
     * Every value maps to a Mochi character — no other characters used.
     */
    public static int getMoodIconRes(Mood mood) {
        if (mood == null) return R.drawable.ic_mochi_smiling;
        switch (mood) {
            case NORMAL:      return R.drawable.ic_mochi_smiling;
            case HAPPY:       return R.drawable.ic_mochi_mood_happy;
            case SAD:         return R.drawable.ic_mochi_mood_sad;
            case CALM:        return R.drawable.ic_mochi_mood_calm;
            case ANXIOUS:     return R.drawable.ic_mochi_mood_anxious;
            case ENERGETIC:   return R.drawable.ic_mochi_mood_energetic;
            case SENSITIVE:   return R.drawable.ic_mochi_mood_sensitive;
            case ROMANTIC:    return R.drawable.ic_mochi_heart_eyes;
            case LONELY:      return R.drawable.ic_forecast_lonely;
            case MOOD_SWING:  return R.drawable.ic_mochi_mood_irritable;
            case FOOD_CRAVING:return R.drawable.ic_mochi_drinking_tea;
            default:          return R.drawable.ic_mochi_smiling;
        }
    }

    /**
     * Loads an iconKey (resource name or file/content URI) into an ImageView.
     */
    public static void loadIcon(Context context, ImageView iv, String iconKey, int fallbackResId) {
        loadIcon(context, iv, iconKey, null, fallbackResId);
    }

    public static void loadIcon(Context context, ImageView iv, String iconKey, String label, int fallbackResId) {
        if (iv == null || context == null) return;

        iconKey = cleanIconKey(iconKey);
        int finalFallback = getSmartFallbackResId(label, fallbackResId);

        if (TextUtils.isEmpty(iconKey)) {
            iv.setImageResource(finalFallback);
            return;
        }

        // Check if it is a content URI or file path from user upload
        if (iconKey.startsWith("content://") || iconKey.startsWith("file://") || iconKey.startsWith("/")) {
            try {
                Uri uri = iconKey.startsWith("/") ? Uri.parse("file://" + iconKey) : Uri.parse(iconKey);
                InputStream is = context.getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();

                if (bitmap != null) {
                    RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
                    rounded.setCircular(true);
                    iv.setImageDrawable(rounded);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load custom icon URI: " + iconKey, e);
            }
        }

        // Try resource lookup by name
        int resId = context.getResources().getIdentifier(iconKey, "drawable", context.getPackageName());
        if (resId != 0) {
            iv.setImageResource(resId);
        } else {
            iv.setImageResource(finalFallback);
        }
    }

    public static int getSmartFallbackResId(String label, int defaultFallback) {
        if (TextUtils.isEmpty(label)) return defaultFallback;
        String lower = label.trim().toLowerCase();
        if (lower.contains("cramp")) return R.drawable.ic_forecast_cramps;
        if (lower.contains("headache") || lower.contains("ache") || lower.contains("pain") || lower.contains("chills")) return R.drawable.ic_forecast_aches;
        if (lower.contains("bloat")) return R.drawable.ic_kawaii_pompom;
        if (lower.contains("acne") || lower.contains("breakout")) return R.drawable.ic_forecast_acne;
        if (lower.contains("fatigue") || lower.contains("tired") || lower.contains("sleep") || lower.contains("insomnia") || lower.contains("fog")) return R.drawable.ic_mood_tired;
        if (lower.contains("nausea")) return R.drawable.ic_kawaii_keroppi;
        if (lower.contains("tender") || lower.contains("breast")) return R.drawable.ic_kawaii_melody;
        if (lower.contains("craving") || lower.contains("sugar")) return R.drawable.ic_kawaii_cinna;
        if (lower.contains("anxi") || lower.contains("hot flash")) return R.drawable.ic_mood_anxious;
        if (lower.contains("discharge")) return R.drawable.ic_kawaii_kitty;
        if (lower.contains("spotting")) return R.drawable.ic_kawaii_kuromi;
        if (lower.contains("mood") || lower.contains("sensitive")) return R.drawable.ic_mood_sensitive;
        if (lower.contains("dizzy")) return R.drawable.ic_forecast_lonely;
        return defaultFallback;
    }
}
