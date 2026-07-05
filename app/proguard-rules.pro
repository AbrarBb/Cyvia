# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep all Room entities and framework classes
-keep class com.khatibstudio.cyvia.data.db.entity.** { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep all DAOs
-keep interface com.khatibstudio.cyvia.data.db.dao.** { *; }

# Keep Gson models for backup/restore serialisation
-keep class com.khatibstudio.cyvia.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Gson TypeAdapter
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Google Mobile Ads (AdMob)
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Navigation Component
-keepnames class androidx.navigation.fragment.NavHostFragment

# Enum classes — prevent R8 from stripping enum names used in SharedPreferences
-keepclassmembers enum com.khatibstudio.cyvia.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
