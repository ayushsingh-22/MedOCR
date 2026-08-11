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

## Run it — nothing to configure first

1. Open the `android/` folder directly in **Android Studio** (Ladybug/2024.2+
   recommended, JDK 17+). Let Gradle sync — no placeholder values, API keys,
   or `local.properties` entries are required for the project to build.
2. Plug in a phone with **USB debugging** enabled (Settings → About phone →
   tap "Build number" 7× to unlock Developer options → enable USB debugging),
   or start any emulator — it'll appear in Android Studio's device dropdown.
3. Click the green **▶ Run** button. The debug build installs and launches
   straight away. Repeat on as many devices as you like — there's nothing
   device-specific to set up.
4. In the app: pick a provider in **Settings**, paste in your own Gemini /
   Groq / LlamaParse API key (get-key links are inline), then **Upload** and
   **Analyse**. This whole flow — scanning, OCR, and reviewing/editing
   results — works immediately, with no Google Cloud setup at all.

API keys are entered in-app (no `.env` file) and stored encrypted on-device
via `EncryptedSharedPreferences` (AES-256-GCM) — each test device holds its
own key.

## Optional: enabling "Append to Google Sheet"

Every screen works out of the box except the final **Append to Google
Sheet** button, which needs a one-time Google Cloud Console registration
(not a hosted service — see "Do I need to host anything?" below):

1. In [Google Cloud Console](https://console.cloud.google.com/), enable the
   **Google Sheets API** on your project.
2. Under **APIs & Services → Credentials → Create Credentials → OAuth client ID**,
   choose **Android**.
3. Set the package name to `com.medocr.app` (the app's `applicationId` — it's
   the same for every debug build installed via the Run button, so this is a
   one-time step covering every test device you install on from this machine).
4. Get your signing certificate's SHA-1. Once the Gradle wrapper exists
   (Android Studio creates it on first sync), run:
   ```
   ./gradlew signingReport
   ```
   and copy the SHA-1 under `Variant: debug`. Paste it into the OAuth client.
   (Repeat with your release keystore's SHA-1 before shipping a signed build.)
5. Save. No code changes needed — Play Services resolves the registered
   OAuth client by package name + signing certificate at runtime.

Tap **Connect Account** in Settings to grant the `spreadsheets` scope; the
grant persists across app restarts. If this step isn't done yet, tapping
Connect shows a clear in-app message telling you so — it won't crash or
block anything else.

## Do I need to host anything (e.g. on Render)?

**No.** The Android app never talks to the Flask backend — it calls
Gemini/Groq/LlamaParse and the Google Sheets API **directly from the
device**, using whatever API key you enter in Settings and the on-device
Google Sheets authorization above. Render hosting is only used for the
separate web/PWA version of MedOCR; it has no bearing on the Android app.

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
