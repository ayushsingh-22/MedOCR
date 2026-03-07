"""
sheets_writer.py
Handles Google OAuth and appending rows to a Google Sheet.
"""

import os
import json
from datetime import datetime
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import Flow
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError


SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]
TOKEN_PATH = os.path.join(os.path.dirname(__file__), "token.json")
CREDENTIALS_PATH = os.path.join(os.path.dirname(__file__), "credentials.json")
REDIRECT_URI = "http://localhost:5000/auth/callback"

MONTH_NAMES = {
    1: "Jan", 2: "Feb", 3: "Mar", 4: "Apr",
    5: "May", 6: "Jun", 7: "Jul", 8: "Aug",
    9: "Sep", 10: "Oct", 11: "Nov", 12: "Dec"
}


def format_date(date_str: str) -> str:
    """
    Convert date string like '7/3/2026', '07/03/2026', '7/3/26'
    to '7 Mar 2026' format.
    Returns original string if parsing fails.
    """
    if not date_str:
        return date_str
    
    parts = date_str.replace("-", "/").split("/")
    try:
        day = int(parts[0])
        month = int(parts[1])
        year_raw = int(parts[2]) if len(parts) > 2 else datetime.now().year
        year = year_raw if year_raw > 100 else 2000 + year_raw
        month_name = MONTH_NAMES.get(month, str(month))
        return f"{day} {month_name} {year}"
    except (ValueError, IndexError):
        return date_str


def format_name_age_gender(name: str, age: str, gender: str) -> str:
    """
    Combine name + age/gender into 'RICHA 30/F' format.
    """
    name_part = (name or "").upper().strip()
    age_part = (str(age) or "").strip()
    gender_part = (gender or "").upper().strip()
    
    if age_part and gender_part:
        return f"{name_part} {age_part}/{gender_part}"
    elif age_part:
        return f"{name_part} {age_part}"
    else:
        return name_part


def get_credentials():
    """Load existing OAuth credentials from token.json."""
    if not os.path.exists(TOKEN_PATH):
        return None
    try:
        creds = Credentials.from_authorized_user_file(TOKEN_PATH, SCOPES)
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
            with open(TOKEN_PATH, "w") as f:
                f.write(creds.to_json())
        return creds if creds and creds.valid else None
    except Exception:
        return None


def is_authenticated() -> bool:
    """Check if the user has valid OAuth credentials."""
    return get_credentials() is not None


def get_auth_url() -> tuple:
    """Generate Google OAuth authorization URL.
    Returns (auth_url, state, code_verifier) — all three must be kept together.
    The state and code_verifier must be stored in the session and passed back to
    handle_oauth_callback() so it can reconstruct the same Flow with PKCE.
    """
    if not os.path.exists(CREDENTIALS_PATH):
        raise FileNotFoundError(
            "credentials.json not found. Please download it from Google Cloud Console "
            "(APIs & Services → Credentials → OAuth 2.0 Client IDs) and place it in "
            f"the same folder as this app: {os.path.dirname(__file__)}"
        )

    flow = Flow.from_client_secrets_file(
        CREDENTIALS_PATH,
        scopes=SCOPES,
        redirect_uri=REDIRECT_URI
    )
    auth_url, state = flow.authorization_url(
        access_type="offline",
        include_granted_scopes="true",
        prompt="consent"
    )
    # The library auto-generates a PKCE code_verifier — we must persist it
    code_verifier = flow.code_verifier
    return auth_url, state, code_verifier


def handle_oauth_callback(code: str, state: str, code_verifier: str = None) -> bool:
    """Exchange authorization code for credentials and save to token.json.
    'state' must be the same value returned by get_auth_url().
    'code_verifier' must be the PKCE verifier from the same get_auth_url() call.
    """
    flow = Flow.from_client_secrets_file(
        CREDENTIALS_PATH,
        scopes=SCOPES,
        state=state,
        redirect_uri=REDIRECT_URI
    )
    # Restore the PKCE code_verifier so token exchange matches the challenge
    if code_verifier:
        flow.code_verifier = code_verifier
    flow.fetch_token(code=code)
    creds = flow.credentials
    with open(TOKEN_PATH, "w") as f:
        f.write(creds.to_json())
    return True


def ensure_header_row(service, spreadsheet_id: str, sheet_name: str = "Sheet1"):
    """
    Check if the first row has the correct headers.
    If the sheet is empty, write the header row.
    """
    range_name = f"{sheet_name}!A1:D1"
    result = service.spreadsheets().values().get(
        spreadsheetId=spreadsheet_id,
        range=range_name
    ).execute()
    
    existing = result.get("values", [])
    
    headers = ["Date", "Name", "Test", "Amount"]
    
    if not existing or existing[0] != headers:
        if not existing:
            # Sheet is empty — write headers
            service.spreadsheets().values().update(
                spreadsheetId=spreadsheet_id,
                range=range_name,
                valueInputOption="RAW",
                body={"values": [headers]}
            ).execute()
        # If row 1 exists but is wrong, we don't overwrite — just proceed


def append_patient_rows(
    spreadsheet_id: str,
    date_str: str,
    patients: list,
    sheet_name: str = "Sheet1"
) -> dict:
    """
    Append formatted patient rows to the Google Sheet.
    
    patients: list of dicts with keys: name, age, gender, tests, amount
    
    Format:
    - First patient in group: [formatted_date, "NAME AGE/GENDER", tests, amount]
    - Subsequent patients:    ["",             "NAME AGE/GENDER", tests, amount]
    
    Returns dict with keys: success, rows_added, error
    """
    creds = get_credentials()
    if not creds:
        return {"success": False, "rows_added": 0, "error": "Not authenticated. Please connect your Google Account first."}
    
    try:
        service = build("sheets", "v4", credentials=creds)
        
        ensure_header_row(service, spreadsheet_id, sheet_name)
        
        formatted_date = format_date(date_str)
        rows = []
        
        for i, patient in enumerate(patients):
            name_col = format_name_age_gender(
                patient.get("name", ""),
                patient.get("age", ""),
                patient.get("gender", "")
            )
            tests_col = (patient.get("tests") or "").strip()
            amount_val = patient.get("amount")
            
            # Amount: use numeric value for Sheets (not string)
            try:
                amount_col = int(amount_val) if amount_val is not None else ""
            except (ValueError, TypeError):
                amount_col = amount_val or ""
            
            date_col = formatted_date if i == 0 else ""
            
            rows.append([date_col, name_col, tests_col, amount_col])
        
        if not rows:
            return {"success": False, "rows_added": 0, "error": "No patient rows to append."}
        
        # Find the next empty row
        range_name = f"{sheet_name}!A:D"
        result = service.spreadsheets().values().append(
            spreadsheetId=spreadsheet_id,
            range=range_name,
            valueInputOption="USER_ENTERED",
            insertDataOption="INSERT_ROWS",
            body={"values": rows}
        ).execute()
        
        updated_range = result.get("updates", {}).get("updatedRange", "")
        rows_added = result.get("updates", {}).get("updatedRows", len(rows))
        
        return {
            "success": True,
            "rows_added": rows_added,
            "updated_range": updated_range,
            "error": None
        }
    
    except HttpError as e:
        return {"success": False, "rows_added": 0, "error": f"Google Sheets API error: {e.reason}"}
    except Exception as e:
        return {"success": False, "rows_added": 0, "error": f"Unexpected error: {str(e)}"}


def revoke_auth():
    """Remove saved credentials."""
    if os.path.exists(TOKEN_PATH):
        os.remove(TOKEN_PATH)
    return True
