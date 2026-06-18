# MedOCR - Handwritten Lab Records to Google Sheets

MedOCR is a Flask web app (also installable as a PWA) that takes a photo of handwritten pathology/lab notes, uses an AI Vision model to extract patient entries, lets you review and edit the result, and appends clean rows to Google Sheets.

## Features

- Upload handwritten list images (`png`, `jpg`, `jpeg`, `webp`, `bmp`, `tiff`) up to 16 MB.
- **Multi-provider OCR** — choose between three AI backends per upload:
  - **Gemini** (`gemini-2.0-flash`) — Google's Vision model (default).
  - **Groq Vision** — runs Llama 4 Scout → Llama 4 Maverick → Llama 3.2 90B → Llama 3.2 11B with automatic model fallback.
  - **LlamaParse** — LlamaIndex cloud document-parsing API.
- **Multi-date extraction** — a single image can contain records spanning multiple dates; entries are grouped automatically.
- Image pre-processing pipeline: EXIF auto-rotate, RGB normalisation, resize to ≤ 2000 px before sending to the model.
- Detects crossed-out entries and lets you skip them before append.
- Editable review table with confidence hints for low-confidence OCR fields.
- Google OAuth login flow for Sheets API access.
- Appends date-grouped rows into your sheet (`Date`, `Name`, `Test`, `Amount`).
- **Progressive Web App (PWA)** — installable on desktop and mobile via the browser's "Add to Home Screen" / "Install" prompt.
- Service worker for offline shell caching.

## Project Structure

| File / Folder | Purpose |
|---|---------|
| `app.py` | Flask server and API routes |
| `ocr_parser.py` | Multi-provider OCR (Gemini, Groq, LlamaParse) and image pre-processing |
| `sheets_writer.py` | Google OAuth + Sheets append logic |
| `templates/index.html` | Main web UI |
| `static/app.js` | Frontend behaviour (upload, review, append, provider selection) |
| `static/style.css` | UI styling |
| `static/sw.js` | Service worker (PWA offline cache) |
| `static/manifest.json` | Web App Manifest (PWA metadata and icons) |
| `Procfile` | Gunicorn start command for Render/Railway |
| `requirements.txt` | Python dependencies |
| `.env.example` | Environment variable template |
| `credentials.json` | Google OAuth client credentials (you provide) |
| `token.json` | OAuth token cache (auto-generated after login) |

## Prerequisites

- Python 3.10+
- At least one AI provider API key (Gemini, Groq, or LlamaParse)
- A Google Cloud project with Google Sheets API enabled

## Setup

### 1. Install dependencies

```powershell
cd "c:\Users\ayush\OneDrive\Desktop\OCR"
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

### 2. Configure environment variables

Create `.env` from `.env.example`:

```powershell
copy .env.example .env
```

Then update `.env` values:

```env
GEMINI_API_KEY=your_gemini_api_key_here
GROQ_API_KEY=your_groq_api_key_here
LLAMAPARSE_API_KEY=your_llamaparse_api_key_here
GOOGLE_SHEET_ID=
FLASK_SECRET_KEY=change-me-to-a-random-secret-string
REDIRECT_URI=http://localhost:5000/auth/callback
```

Notes:
- `GEMINI_API_KEY`, `GROQ_API_KEY`, and `LLAMAPARSE_API_KEY` can all be entered in the UI at runtime — no `.env` entry required.
- Only one provider's key is needed; the others are optional.
- `GOOGLE_SHEET_ID` is optional because you can enter the Sheet ID in the UI.

### 3. Add Google OAuth credentials

1. Open Google Cloud Console.
2. Enable `Google Sheets API`.
3. Create OAuth 2.0 Client ID (Application type: Desktop app).
4. Download the client JSON and save it as `credentials.json` in the project root.

The app uses callback URL:
- `http://localhost:5000/auth/callback`

### 4. Run the app

```powershell
python app.py
```

Open:
- `http://localhost:5000`

## Usage Flow

1. **Settings**
   - Select your OCR provider (Gemini, Groq, or LlamaParse).
   - Enter the corresponding API key.
   - Enter Google Sheet ID and optional Sheet tab name.
   - Click `Connect Account` and complete Google OAuth.

2. **Upload**
   - Drag and drop or select an image.

3. **Analyse**
   - Click `Analyse with AI` — the selected provider processes the image.
   - If multiple dates are detected in the image, results are grouped by date automatically.

4. **Review**
   - Edit name, age, gender, tests, amount as needed.
   - Fields with low OCR confidence are highlighted.
   - Use `Skip` for crossed-out entries.

5. **Append**
   - Click `Append to Google Sheet`.

## PWA — Install as an App

MedOCR ships as a Progressive Web App. When the app is open in Chrome, Edge, or Safari on mobile:

- **Desktop**: click the install icon in the address bar ("Install MedOCR").
- **Mobile**: use the browser menu → "Add to Home Screen".

The installed app opens in standalone mode (no browser chrome) and caches the shell for instant loads.

## Deploy on Render (Recommended)

This project is a Flask backend app, so use a Python host (Render/Railway/Fly.io). Netlify is not suitable for this architecture.

### 1. Push the repo to GitHub

Make sure these are **not** committed:
- `.env`
- `credentials.json`
- `token.json`

### 2. Create a Render Web Service

Use these settings:
- Runtime: `Python`
- Build Command: `pip install -r requirements.txt`
- Start Command: `python -m gunicorn app:app --bind 0.0.0.0:$PORT`

(`Procfile` is already included with the same start command.)

### 3. Configure Environment Variables in Render

Set:
- `FLASK_SECRET_KEY` = strong random string
- `GEMINI_API_KEY` = your Gemini key (if using Gemini provider)
- `GROQ_API_KEY` = your Groq key (if using Groq provider)
- `LLAMAPARSE_API_KEY` = your LlamaParse key (if using LlamaParse provider)
- `GOOGLE_SHEET_ID` = optional default sheet id
- `REDIRECT_URI` = `https://<your-render-domain>/auth/callback`

Optional path overrides:
- `GOOGLE_OAUTH_CREDENTIALS_PATH`
- `TOKEN_PATH`

### 4. Add OAuth credentials file at runtime

The app requires `credentials.json` for Google OAuth. In production, provide it through your deployment process (for example: mounted file, secure secret file, or startup step that writes it to disk).

### 5. Update Google Cloud OAuth redirect URI

In Google Cloud Console, add this exact redirect URI to your OAuth client:
- `https://<your-render-domain>/auth/callback`

### 6. Deploy and test

After deploy:
1. Open your Render URL.
2. Click `Connect Account` and complete Google login.
3. Upload an image and run OCR.
4. Append to your Google Sheet.

## Output Format in Google Sheet

Columns written:

| A | B | C | D |
|---|---|---|---|
| Date | Name | Test | Amount |

Row formatting behavior:
- Date is formatted to `D Mon YYYY` (example: `7 Mar 2026`).
- For a batch, date appears only on the first row.
- Name is formatted as `NAME AGE/GENDER` (example: `RICHA 30/F`).
- Amount is written as numeric where valid.

## API Endpoints (Internal)

- `GET /` → UI page
- `GET /health` → lightweight keep-alive
- `GET /sw.js` → service worker (served with `Cache-Control: no-store`)
- `GET /favicon.ico` → app icon
- `GET /api/auth/status` → auth + credentials status (includes provider key flags)
- `GET /api/auth/login` → start Google OAuth
- `GET /auth/callback` → OAuth callback
- `POST /api/auth/logout` → clear token
- `POST /api/upload` → upload image + OCR parse (form field `provider`: `gemini` | `groq` | `llamaparse`)
- `POST /api/append` → append reviewed rows to sheet (supports flat list or `date_groups`)
- `POST /api/format-date` → utility date formatting

## Troubleshooting

`credentials.json not found`
- Put your downloaded OAuth client file in project root as `credentials.json`.

`Gemini API key is required`
- Enter a key in Settings (Gemini tab), or set `GEMINI_API_KEY` in `.env`.

`Groq API key is required`
- Enter a key in Settings (Groq tab), or set `GROQ_API_KEY` in `.env`.

`LlamaParse API key is required`
- Enter a key in Settings (LlamaParse tab), or set `LLAMAPARSE_API_KEY` in `.env`.

`All Groq models failed`
- Groq automatically tries all available vision models in order. Check your Groq key and quota.

`Not authenticated`
- Click `Connect Account` and complete Google sign-in.

`Amount must be a number`
- Use plain numeric values only (example: `1200`, not `1200P`).

`Google Sheets API error`
- Confirm Sheets API is enabled and your account has edit access to the sheet.

## Security Notes

- Keep `credentials.json`, `token.json`, and `.env` private.
- Do not commit real API keys or OAuth secrets to public repositories.
