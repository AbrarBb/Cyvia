# Cyvia – Private Period Tracker

<p align="center">
  <b>A modern, 100% offline, privacy-first menstrual cycle and health tracker for Android.</b><br>
  Powered by the empathetic <b>Mochi Care Engine</b> and a transparent, customizable <b>Prediction Engine</b>.
</p>

---

##  Overview

**Cyvia** is a comprehensive, privacy-respecting period and cycle tracking application built for Android. In an era where health data privacy is paramount, Cyvia is designed from the ground up to operate **100% offline and locally by default**. Your sensitive health data never touches a cloud server, requires no account registration, and remains completely entirely under your control.

Beyond tracking, Cyvia introduces the **Mochi Care Engine**—an intelligent, dynamic well-being companion that delivers tailored, empathetic guidance based on your biological cycle phase, time of day, logged symptoms, and emotional state.

---

##  Key Features

###  100% Offline & Privacy-First
* **Local SQLite Storage**: All health logs, cycle entries, and custom symptoms are stored exclusively on your device using Android's Room Database.
* **Zero Cloud Dependency**: No servers, no tracking scripts, no account required to use the full feature set.
* **PIN Lock & Biometric Protection**: Secure your personal health data from prying eyes with built-in app locking (`PinLockActivity`).
* **OS Backup Protection**: Explicitly configured with `allowBackup="false"` to prevent unencrypted automatic operating system cloud syncs.

###  No Vendor Lock-In (SAF Backup & Restore)
* **Storage Access Framework (SAF)**: Export your entire health history to a clean, schema-validated JSON file and save it anywhere you choose—Google Drive, local storage, SD card, or email (`BackupManager`).
* **Automated SAF Folder Backup**: Configure a persistent public folder or Google Drive location to automatically save and overwrite your monthly auto-backups without losing them on app uninstall.
* **Seamless Restoration**: Import your backup files or restore directly from your configured auto-backup folder with a single click.

###  Mochi Care Engine (Dynamic Well-Being Companion)
* **Context-Aware Advice**: Generates personalized care messages tailored to:
  * **Time of Day**: Morning motivation, afternoon check-ins, and evening wind-down routines.
  * **Biological Phase**: Customized guidance for Menstrual, Follicular, Ovulatory, and Luteal phases.
  * **Physical Symptoms & Mood**: Responsive comfort for cramps, headaches, bloating, fatigue, anxiety, and more.
* **Refreshed Post-Bleed Empathy**: Delivers warm, encouraging care advice right after your period ends to ease you into the follicular phase.
* **Empathetic Brand Voice**: Supportive, guilt-free tone delivered in concise 2-line messages without emoji clutter (`MochiCareEngine`).

###  Transparent & Smart Prediction Engine
* **Rolling Average Algorithm**: Calculates next period and ovulation dates based on your most recent non-excluded cycles (`PredictionEngine`).
* **Smart Gap Exclusion**: Automatically filters out tracking gaps (due to pregnancy, contraception, or breaks) so anomalies never distort your cycle averages—solving a major pain point in competitor apps!
* **7 Custom Tracking Modes**:
  * `Regular` & `Irregular`: Adjusted confidence intervals for cycle predictions.
  * `Trying to Conceive (TTC)`: Highlights ovulation and fertile windows.
  * `Avoiding Pregnancy`: Clear fertile window awareness.
  * `No Periods (Contraception)`: Intelligently suppresses fertile window and pregnancy messaging.
  * `Postpartum` & `Perimenopause`: Shows predictions with realistic reliability caveats rather than false precision.

### 📝 Comprehensive Logging & Mochi Character Poses
* **Consolidated Two-Section Logs**: Easily track moods and symptoms across just two unified sections: "How are you feeling?" (Moods) and "Physical Symptoms".
* **Flow Intensity Cards**: Select your cycle flow level (Spotting, Light, Medium, Heavy) using intuitive, custom card selectors instead of plain text toggles.
* **Mochi Poses Only**: Choose from 13+ expressive kawaii-style Mochi cat poses (reading, drinking tea, stretching, sparkles, heart eyes, cozy, hugging, waving, sleeping, smiling, sick, celebrating, etc.) matching your logged feelings.
* **Health Logging Dimensions**:
  * **Intimacy & Sex**: Log status as `Nope` (broken heart), `Protected` (heart in shield), or `Unprotected` (love heart).
  * **Physical Activity**: Log exercise type: `No Exercise` (chill Mochi), `Running`, `Cycling`, `Gym`, `Aerobics & Dance`, `Swimming`, or `Yoga`.
  * **Vaginal Discharge**: Log discharge type: `Excessive White`, `Smelly` (nose-closed Mochi), `Creamy`, `Watery`, `Brownish`, or `Yellowish`.
  * **Weight Tracking**: Side-by-side with Temperature, with a dynamic units selector (`kg` / `lbs`).
* **Custom Image Uploads**: Upload your own photos or custom icons from your device gallery to personalize symptom tracking!

### 📊 Rich Visualizations & Offline Analytics
* **Interactive Cycle Ring**: A custom animated circular chart displaying your current cycle day, period length, follicular phase, fertile window, and ovulation marker with pulse/glow animations (`CycleRingView`).
* **Multi-Month Calendar View**: Colour-coded calendar showing confirmed period days, predicted future periods (projected up to **12 cycles / 1 year ahead**!), ovulation days, and fertile windows (`CalendarFragment`).
* **Calendar Sex Life Prediction**: A minimal prediction card positioned beneath the calendar. It groups dates of the selected month into `No Sex` (during period), `Protected Sex` (fertile/ovulation days), and `Unprotected Sex` (low-risk days), adjusting advice automatically if the user is in `Trying to Conceive` mode.
* **Health Insights & Charts**: View your Regularity Score (0–100), shortest/longest cycles, average cycle/period lengths, and visual charts powered offline by MPAndroidChart. Symptoms chart automatically filters out inactive tags to maintain a clean display (`CycleStatsCalculator`, `InsightsFragment`).

### 🔔 Discreet Reminders & Background Workers
* **WorkManager Integration**: Reliable, energy-efficient background scheduling for period reminders, ovulation alerts, and daily check-ins (`ReminderWorker`, `AutoBackupWorker`).
* **Discreet Notifications**: Friendly, reassuring notification copy that respects your privacy. Zero spam, upsells, or subscription nags.

### 💰 User-Friendly Monetization & Testing
* **Non-Intrusive Banner Ads**: Displayed strictly at the bottom of select screens (Home and Calendar) without obstructing any tappable UI (`AdManager`).
* **Future Calendar Navigation Ad-Gate**: Restored and refined the next-month ad gate. Navigating to the current and next month is free; navigating beyond 1 month in the future requires viewing a rewarded ad unless ads are removed.
* **100% Ad-Free Logging**: Zero interstitial popups or automatic ad interruptions during log saving or day-to-day use.
* **One-Time "Remove Ads" IAP**: Simple, non-consumable Google Play Billing integration to remove ads permanently with a single purchase (`BillingManager`).

---

##  Technology Stack

| Category | Technology / Library | Description |
| :--- | :--- | :--- |
| **Core Language** | **Java 17** | Modern Java syntax with Core Library Desugaring (`java.time` support on API 24+). |
| **Android SDK** | **API 24 to API 35** | Supports Android 7.0 (Nougat) up to Android 15. |
| **Architecture** | **MVVM & Repository Pattern** | Clean separation of concerns using AndroidX `ViewModel`, `LiveData`, and Single Source of Truth repositories. |
| **UI & Presentation** | **Material Design 3** | Modern components, ViewBinding, custom Canvas drawing (`CycleRingView`), and Navigation Component. |
| **Database & ORM** | **Room Database v2.6** | Type-safe SQLite mapping with DAOs, Entities, and TypeConverters (`CyviaDatabase`). |
| **Background Tasks** | **WorkManager v2.9** | Reliable scheduling for reminders, notifications, and auto-backups. |
| **JSON Serialization** | **Google Gson v2.10** | Schema-validated serialization/deserialization for backup export and import. |
| **Offline Charting** | **MPAndroidChart v3.1** | High-performance visual analytics and cycle statistic charting. |
| **Monetization** | **Play Services Ads v23.2** | Google AdMob banner and rewarded ad integration. |
| **In-App Billing** | **BillingClient v6.2** | Google Play Billing Library for one-time Remove Ads purchase. |
| **Concurrency & Files** | **Guava v32.1 & DocumentFile** | ListenableFuture utilities and Storage Access Framework file handling. |

---

## 📁 Project Structure

```text
com.khatibstudio.cyvia
├── CyviaApplication.java       # Application class, Dependency Injection & Repository initialization
├── MainActivity.java           # Single-activity host and navigation controller
├── ads/
│   └── AdManager.java          # Centralized ad management & placement enforcement
├── backup/
│   └── BackupManager.java      # SAF JSON export/import and schema validation
├── billing/
│   └── BillingManager.java     # Google Play Billing one-time purchase handler
├── data/
│   ├── db/                     # Room Database, DAOs, Entities (CycleEntry, DailyLog, SymptomTag), Converters
│   ├── model/                  # Domain models, Enums (Mood, TrackingMode, FlowIntensity, etc.)
│   └── repository/             # Repositories (CycleRepository, LogRepository, SettingsRepository, SymptomRepository)
├── domain/
│   ├── CycleStatsCalculator.java # Analytics and Regularity Score (0-100) engine
│   ├── MochiCareEngine.java      # Dynamic time/phase/mood well-being advice generator
│   └── PredictionEngine.java     # Rolling average cycle & fertile window prediction algorithm
├── ui/
│   ├── calendar/               # Multi-month colour-coded calendar & adapter
│   ├── home/                   # Home dashboard & animated CycleRingView
│   ├── insights/               # Charts, statistics, and regularity insights
│   ├── log/                    # Daily logging bottom sheet (mood, flow, symptoms, custom tags)
│   ├── onboarding/             # Initial setup, privacy briefing, and cycle preferences
│   ├── pin/                    # PIN lock and authentication screen
│   └── settings/               # App configuration, backup/restore, tracking modes, and IAP
├── util/
│   └── KawaiiIconUtil.java     # Built-in kawaii characters and custom image URI loader
└── worker/
    ├── AutoBackupWorker.java   # Automated background backup job
    ├── BootReceiver.java       # Reschedules notifications after device reboot
    └── ReminderWorker.java     # Discreet period, ovulation, and logging reminders
```

---

##  Getting Started

### Prerequisites
* **Android Studio**: Koala / Ladybug or newer recommended.
* **JDK**: Java 17 or higher.
* **Android SDK**: Compile SDK 35 (Android 15).

### Building Locally
1. Clone the repository and open the project in **Android Studio**.
2. Sync Gradle dependencies:
   ```bash
   ./gradlew build
   ```
3. Run the application on an emulator or physical device (API 24+):
   ```bash
   ./gradlew installDebug
   ```

> [!NOTE]
> **AdMob Test IDs**: By default, `app/build.gradle` and `AdManager.java` are configured with Google's official test ad unit IDs for development. Be sure to replace these with real AdMob unit IDs before publishing to the Google Play Store.

---

##  License & Privacy
Cyvia is dedicated to user privacy and transparent health tracking. All personal data generated within the application remains strictly on the user's local device.
