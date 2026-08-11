# MedOCR — Android (Kotlin + Jetpack Compose)

Native Android port of the MedOCR web app: scan handwritten pathology/lab
lists, run AI OCR (Gemini / Groq / LlamaParse), review the extracted patients,
and append clean rows straight into a Google Sheet — all on-device, no server.

## Stack

- **Kotlin 2.1** + **Jetpack Compose** (Material 3), single-activity
- **Hilt** for DI, **Retrofit + OkHttp + kotlinx.serialization** for networking
- **DataStore Preferences** for settings, **Jetpack Security (EncryptedSharedPreferences)** for API keys
- **Coroutines/Flow**, `ViewModel` + `StateFlow` (MVVM)
- **Coil** for image thumbnails, **Photo Picker** + **CameraX-free camera capture** via `ActivityResultContracts`
- **Google Identity Authorization API** for the Sheets OAuth scope (no App server / no `credentials.json` needed)
- Light / Dark / System theme, matching the web app's indigo/violet accent

## Project layout

```
android/
  app/src/main/java/com/medocr/app/
    data/
      model/       Provider, PatientRecord, DateGroup, ThemeMode, OcrResult...
      remote/       Gemini/Groq/LlamaParse/Sheets Retrofit APIs + OcrJsonParser
      repository/   OcrRepository, SheetsRepository, AuthRepository
      local/        SettingsDataStore (theme/provider/sheet), SecureKeyStore (API keys)
    util/           ImageProcessor (EXIF/resize), TestNameMatcher (fuzzy test-name matching), DateUtils
    ui/
      theme/        Color/Theme/Type/Shape — light/dark Material3 schemes
      main/         MainViewModel + MainUiState (single source of truth)
      components/   Reusable pieces (StepCard, ProviderToggle, ConfidenceBadge, ...)
      screens/      SettingsSection, UploadSection, ReviewSection, HomeScreen
```

The three "step cards" from the web UI (Settings → Upload → Review) are
ported 1:1 as scrollable Compose sections in `HomeScreen`, and the OCR
prompt, test-name fuzzy matcher, date parsing/merging, and Sheets row format
are direct Kotlin ports of `ocr_parser.py` / `sheets_writer.py` / `app.js` so
behavior matches the web app exactly.

## Setup

### 1. Open the project

Open the `android/` folder directly in **Android Studio** (Ladybug/2024.2+
recommended). Android Studio will generate the Gradle wrapper and sync
automatically. JDK 17+ is required.

### 2. API keys

No `.env` file — enter your Gemini / Groq / LlamaParse API key directly in
the app's Settings card. Keys are encrypted at rest via
`EncryptedSharedPreferences` (AES-256-GCM) and never leave the device except
in the direct HTTPS request to that provider.

### 3. Google Sheets access (one-time Cloud Console setup)

The app calls the Sheets API directly with an OAuth access token obtained
via Google Play Services' **Authorization API** — there's no `credentials.json`
and no client secret embedded in the app. You only need to register the app
once:

1. In [Google Cloud Console](https://console.cloud.google.com/), enable the
   **Google Sheets API** on your project.
2. Under **APIs & Services → Credentials → Create Credentials → OAuth client ID**,
   choose **Android**.
3. Set the package name to `com.medocr.app`.
4. Get your signing certificate's SHA-1 and paste it in:
   ```
   ./gradlew signingReport
   ```
   (uses the debug keystore automatically for local builds — repeat this step
   with your release keystore's SHA-1 before shipping a signed build).
5. Save. No further code changes are needed — Play Services resolves the
   registered OAuth client by package name + signing certificate at runtime.

Tap **Connect Account** in Settings to grant the `spreadsheets` scope; the
grant persists across app restarts (Google re-authorizes silently on cold
start).

### 4. Run

Select the `app` run configuration and run on a device/emulator with Google
Play Services (API 26+).

## Feature parity with the web app

- Multi-provider OCR (Gemini / Groq with automatic model fallback / LlamaParse
  with Gemini re-format fallback)
- Multi-image batch analyse with progress bar, per-image error toasts
- Multi-date extraction — patients grouped and merged by date across images
- Review & edit: per-field confidence highlighting (amber = low confidence,
  red = invalid amount), skip/crossed-out toggle, add/delete row, add/remove
  date group
- Fuzzy test-name normalization against the same known-test vocabulary
  (`testName.json` → `TestNameMatcher.KNOWN_TESTS`)
- Append to Sheets with the same row format (`Date | Name AGE/GENDER | Test | Amount`,
  blank separator row per date group, header row auto-created)
- Light / Dark / System theme toggle, persisted across launches
- Camera capture + gallery multi-select (Android Photo Picker)

## Notable differences from the web app

- No server component — this app calls Gemini/Groq/LlamaParse/Sheets
  directly from the device, so there's no "server key configured" concept;
  every user supplies their own key(s) in Settings.
- No PWA/service worker equivalent (native app already installs to the
  home screen).
- Review rows render as stacked field cards per patient instead of a dense
  8-column table — more usable at phone widths; all the same fields and
  validation are present.
