"""
app.py
Flask backend for the Medical OCR → Google Sheets tool.
"""

import os
import uuid
import json
from flask import Flask, request, jsonify, session, redirect, render_template, url_for, send_from_directory
from dotenv import load_dotenv
from werkzeug.utils import secure_filename

load_dotenv()

from ocr_parser import parse_image
import sheets_writer as sw

app = Flask(__name__)
app.secret_key = os.environ.get("FLASK_SECRET_KEY", "dev-secret-change-me")
app.config["SESSION_COOKIE_SAMESITE"] = "Lax"
app.config["SESSION_COOKIE_SECURE"] = os.environ.get("SESSION_COOKIE_SECURE", "1") == "1"

UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), "uploads")
ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "webp", "bmp", "tiff"}
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024  # 16MB max upload


def allowed_file(filename):
    return "." in filename and filename.rsplit(".", 1)[1].lower() in ALLOWED_EXTENSIONS


# ─── ROUTES ──────────────────────────────────────────────────────────────────

@app.route("/")
def index():
    return render_template("index.html")


@app.route("/sw.js")
def service_worker():
    """Serve the service worker from root scope.
    MUST be served with Cache-Control: no-store so the browser always
    fetches the latest version from the network on every page load.
    """
    response = send_from_directory(app.static_folder, "sw.js",
                                   mimetype="application/javascript")
    response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate, max-age=0"
    return response



@app.route("/health")
def health():
    """Lightweight keep-alive endpoint."""
    return jsonify({"status": "ok"}), 200


@app.route("/favicon.ico")
def favicon():
    """Serve the app icon as favicon so browsers don't log a 404."""
    return send_from_directory(app.static_folder, "icon-192.png",
                               mimetype="image/png")


@app.route("/api/auth/status")
def auth_status():
    """Check if Google OAuth token exists and is valid."""
    authenticated = sw.is_authenticated()
    has_creds_file = os.path.exists(sw.CREDENTIALS_PATH)
    gemini_env_key = os.environ.get("GEMINI_API_KEY", "")
    has_server_gemini_key = bool(gemini_env_key and gemini_env_key != "your_gemini_api_key_here")
    groq_env_key = os.environ.get("GROQ_API_KEY", "")
    has_server_groq_key = bool(groq_env_key and groq_env_key != "your_groq_api_key_here")
    default_sheet_id = (os.environ.get("Google_Sheet_ID") or os.environ.get("GOOGLE_SHEET_ID") or "").strip()
    return jsonify({
        "authenticated": authenticated,
        "has_credentials_file": has_creds_file,
        "has_server_gemini_key": has_server_gemini_key,
        "has_server_groq_key": has_server_groq_key,
        "default_sheet_id": default_sheet_id
    })


@app.route("/api/auth/login")
def auth_login():
    """Redirect user to Google OAuth consent screen."""
    try:
        url, state, _ = sw.get_auth_url()
        session["oauth_state"] = state
        return redirect(url)
    except FileNotFoundError as e:
        return jsonify({"error": str(e)}), 400


@app.route("/auth/callback")
def auth_callback():
    """Google OAuth callback — exchange code for token."""
    code = request.args.get("code")
    state = request.args.get("state")
    error = request.args.get("error")
    error_description = request.args.get("error_description", "")

    # ── Step 1: Check if Google sent an error instead of a code ──────────────
    if error:
        hint = ""
        if error == "redirect_uri_mismatch":
            hint = (
                f"<p><b>Hint:</b> The redirect URI your app sent "
                f"(<code>{sw.REDIRECT_URI}</code>) does not match any URI "
                f"registered in Google Cloud Console for this OAuth client. "
                f"Add <b>{sw.REDIRECT_URI}</b> to the authorized redirect URIs.</p>"
            )
        return f"""
        <html><head><title>OAuth Error</title>
        <style>
          body {{ font-family: sans-serif; padding: 40px; background: #0f0f1a; color: #fff; }}
          code {{ background: rgba(255,255,255,0.1); padding: 2px 6px; border-radius: 4px; }}
          .red {{ color: #f87171; }}
        </style></head><body>
          <h2 class="red">❌ Google OAuth Error</h2>
          <p><b>Error:</b> <code>{error}</code></p>
          <p><b>Description:</b> {error_description}</p>
          {hint}
          <p>Check that your <code>credentials.json</code> is "Web application" type 
             (not "Desktop app") and that the redirect URI above is listed in 
             Google Cloud Console → APIs &amp; Services → Credentials.</p>
        </body></html>
        """, 400

    # ── Step 2: Check authorization code is present ───────────────────────────
    if not code:
        return f"""
        <html><body style="font-family:sans-serif;padding:40px;background:#0f0f1a;color:#fff">
          <h2 style="color:#f87171">❌ OAuth Error: No authorization code received.</h2>
          <p>Google did not return a code. This usually means the OAuth consent was cancelled.</p>
        </body></html>
        """, 400

    # ── Step 3: Retrieve session state saved during login ───────────────────
    saved_state = session.pop("oauth_state", None) or state

    # ── Step 4: Exchange code for token ──────────────────────────────────────
    try:
        sw.handle_oauth_callback(code, saved_state)
        return f"""
        <html><head><title>Authentication Successful</title>
        <style>
          body {{ font-family: sans-serif; display: flex; align-items: center; 
                 justify-content: center; height: 100vh; margin: 0; 
                 background: #0f0f1a; color: #fff; }}
          .card {{ text-align: center; padding: 40px; background: rgba(255,255,255,0.05);
                  border-radius: 16px; border: 1px solid rgba(255,255,255,0.1); }}
          h2 {{ color: #4ade80; }} p {{ color: #aaa; }}
          code {{ font-size:12px; background:rgba(255,255,255,0.1); padding:2px 6px; border-radius:4px; }}
        </style>
        </head><body>
          <div class="card">
            <h2>✅ Google Account Connected!</h2>
            <p>Redirect URI used: <code>{sw.REDIRECT_URI}</code></p>
            <p>You can now close this tab and return to the app.</p>
            <script>setTimeout(() => {{ window.close(); }}, 2000);</script>
          </div>
        </body></html>
        """
    except Exception as e:
        import traceback
        tb = traceback.format_exc()
        return f"""
        <html><head><title>OAuth Token Error</title>
        <style>
          body {{ font-family: sans-serif; padding: 40px; background: #0f0f1a; color: #fff; }}
          pre {{ background: rgba(255,255,255,0.05); padding: 16px; border-radius: 8px; 
                overflow-x: auto; font-size: 12px; color: #f87171; }}
          code {{ background: rgba(255,255,255,0.1); padding: 2px 6px; border-radius: 4px; }}
        </style></head><body>
          <h2 style="color:#f87171">❌ Token Exchange Failed (Step 4)</h2>
          <p><b>Error:</b> {e}</p>
          <p><b>Redirect URI used:</b> <code>{sw.REDIRECT_URI}</code></p>
          <p>Make sure this URI is listed in Google Cloud Console and your 
             <code>credentials.json</code> is "Web application" type.</p>
          <pre>{tb}</pre>
        </body></html>
        """, 400


@app.route("/api/auth/logout", methods=["POST"])
def auth_logout():
    """Remove saved OAuth token."""
    sw.revoke_auth()
    return jsonify({"success": True})


@app.route("/api/upload", methods=["POST"])
def upload_image():
    """
    Receive an image upload, run OCR + parsing via Gemini, return structured JSON.
    """
    provider = (request.form.get("provider") or "gemini").lower().strip()
    if provider not in ("gemini", "groq"):
        provider = "gemini"

    if provider == "groq":
        api_key = request.form.get("groq_api_key") or os.environ.get("GROQ_API_KEY", "")
        if not api_key or api_key == "your_groq_api_key_here":
            return jsonify({"error": "Groq API key is required. Please enter it in the settings panel."}), 400
    else:
        api_key = request.form.get("api_key") or os.environ.get("GEMINI_API_KEY", "")
        if not api_key or api_key == "your_gemini_api_key_here":
            return jsonify({"error": "Gemini API key is required. Please enter it in the settings panel."}), 400
    
    if "image" not in request.files:
        return jsonify({"error": "No image file provided."}), 400
    
    file = request.files["image"]
    
    if file.filename == "":
        return jsonify({"error": "No file selected."}), 400
    
    if not allowed_file(file.filename):
        return jsonify({"error": f"File type not allowed. Supported: {', '.join(ALLOWED_EXTENSIONS)}"}), 400
    
    # Save uploaded file
    filename = f"{uuid.uuid4().hex}_{secure_filename(file.filename)}"
    filepath = os.path.join(UPLOAD_FOLDER, filename)
    file.save(filepath)
    
    try:
        result = parse_image(filepath, api_key, provider=provider)
        
        if result.get("error"):
            return jsonify({"error": result["error"]}), 500
        
        return jsonify({
            "success": True,
            "date_groups": result.get("date_groups", []),
            "date": result.get("date", ""),
            "date_confidence": result.get("date_confidence", 0.8),
            "patients": result.get("patients", [])
        })
    
    finally:
        # Clean up uploaded file
        try:
            os.remove(filepath)
        except Exception:
            pass


@app.route("/api/append", methods=["POST"])
def append_to_sheet():
    """
    Receive user-reviewed/corrected patient data and append to Google Sheet.
    Body JSON:
    {
      "sheet_id": "...",
      "sheet_name": "Sheet1",  // optional
      "date": "7/3/2026",
      "patients": [
        { "name": "Richa", "age": "30", "gender": "F", "tests": "mut, CEA-129", "amount": 1200 },
        ...
      ]
    }
    """
    if not sw.is_authenticated():
        return jsonify({"error": "Not authenticated. Please connect your Google Account first."}), 401
    
    data = request.get_json()
    if not data:
        return jsonify({"error": "No JSON body provided."}), 400
    
    sheet_id = (data.get("sheet_id") or "").strip()
    if not sheet_id:
        # Fall back to .env default
        sheet_id = (os.environ.get("Google_Sheet_ID") or os.environ.get("GOOGLE_SHEET_ID") or "").strip()
    if not sheet_id:
        return jsonify({"error": "Google Sheet ID is required. Set it in the UI or in the .env file."}), 400
    
    date_str = (data.get("date") or "").strip()
    patients = data.get("patients", [])
    date_groups = data.get("date_groups", None)
    sheet_name = data.get("sheet_name", "Sheet1")
    
    # Collect all patients for validation (from groups or flat list)
    all_patients = []
    if date_groups:
        for g in date_groups:
            all_patients.extend(g.get("patients", []))
    else:
        all_patients = patients
    
    if not all_patients:
        return jsonify({"error": "No patient records to append."}), 400
    
    # Validate amounts
    for i, p in enumerate(all_patients):
        amt = p.get("amount")
        if amt is not None and amt != "":
            try:
                int(float(str(amt)))
            except (ValueError, TypeError):
                return jsonify({"error": f"Patient #{i+1}: Amount must be a number, got '{amt}'."}), 400
    
    result = sw.append_patient_rows(sheet_id, date_str, patients, sheet_name, date_groups=date_groups)
    
    if result["success"]:
        return jsonify({
            "success": True,
            "rows_added": result["rows_added"],
            "updated_range": result.get("updated_range", "")
        })
    else:
        return jsonify({"error": result["error"]}), 500


@app.route("/api/format-date", methods=["POST"])
def format_date_endpoint():
    """Utility: format a date string."""
    data = request.get_json() or {}
    raw = data.get("date", "")
    return jsonify({"formatted": sw.format_date(raw)})


# ─── MAIN ─────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("\n🏥 Medical OCR → Google Sheets Tool")
    print("=" * 40)
    print(f"📂 Project folder: {os.path.dirname(__file__)}")
    print(f"🔑 Gemini API Key: {'✅ Found in .env' if os.environ.get('GEMINI_API_KEY','').startswith('AI') else '⚠️  Not set (enter in UI)'}")
    print(f"🔐 Google OAuth:   {'✅ Found' if os.path.exists(sw.CREDENTIALS_PATH) else '⚠️  credentials.json missing'}")
    print(f"🌐 Opening at:     http://localhost:5000")
    print("=" * 40 + "\n")
    app.run(debug=True, port=5000)
