"""
ocr_parser.py
Supports Google Gemini and Groq Vision models for extracting structured
patient data from handwritten medical test list images.

Provider selection is done by the caller via the `provider` argument.
Groq falls back through all available vision models automatically.
"""

import os
import json
import re
import base64
from google import genai
from google.genai import types
from PIL import Image
import io


# ── Groq vision models — tried in order; next model is used if one fails ───────
GROQ_VISION_MODELS = [
    "meta-llama/llama-4-scout-17b-16e-instruct",      # Llama 4 Scout  — fast, good vision
    "meta-llama/llama-4-maverick-17b-128e-instruct",  # Llama 4 Maverick — larger context
    "llama-3.2-90b-vision-preview",                   # Llama 3.2 90B  — high accuracy
    "llama-3.2-11b-vision-preview",                   # Llama 3.2 11B  — lightweight fallback
]


def encode_image_to_base64(image_path: str) -> str:
    """Encode an image file to base64 string."""
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


def preprocess_image(image_path: str) -> bytes:
    """
    Preprocess the image for better OCR accuracy:
    - Auto-rotate based on EXIF
    - Convert to RGB
    - Slightly enhance contrast
    Returns JPEG bytes.
    """
    img = Image.open(image_path)
    
    # Handle EXIF orientation
    try:
        from PIL import ImageOps
        img = ImageOps.exif_transpose(img)
    except Exception:
        pass
    
    # Convert to RGB (handles RGBA, grayscale, etc.)
    img = img.convert("RGB")
    
    # Resize if too large (Vision API works best under 4MB, ~2000px)
    max_dim = 2000
    w, h = img.size
    if max(w, h) > max_dim:
        scale = max_dim / max(w, h)
        img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
    
    # Save to bytes
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=92)
    return buf.getvalue()


OCR_PROMPT = """
You are an expert medical data extraction assistant. I will give you a photo of a handwritten medical test list from a pathology lab. Your job is to extract the structured patient records from this image.

**CRITICAL RULES:**
1. **Multiple dates**: A single image may contain records for MULTIPLE dates. Look for date changes in the page — e.g. a new date heading followed by patient entries. Group patients under their respective dates.
2. **Crossed-out / struck-through text**: If any text has a line drawn through it (strikethrough), extract it but mark `"crossed_out": true`.
3. **Date**: Dates in these images are ALWAYS written in DD/MM/YY format (day/month/2-digit year, e.g. "07/03/26"). Extract the date exactly as written using DD/MM/YY. Do NOT convert to any other format.
4. **Serial numbers**: Each patient entry starts with a circled number or plain number. These separate patients within a date group.
5. **Price format**: Prices are written as "1200P", "2500p", "1100D" — extract just the numeric part. If there's a crossed-out price and a corrected one, use the corrected price.
6. **Name/Age/Gender**: Usually "Richa 30F" or "Sanjay Vid 60M". Name before age+gender letter.
7. **Tests**: Comma-separated medical test abbreviations (e.g., "CBC, TSH, LFT, BUN").
8. **Confidence**: For each field, give confidence (0.0 to 1.0).

**RETURN FORMAT — valid JSON only, no markdown, no explanation:**
```json
{
  "date_groups": [
    {
      "date": "07/03/26",
      "date_confidence": 0.95,
      "patients": [
        {
          "serial": 1,
          "name": "Richa",
          "age": "30",
          "gender": "F",
          "tests": "mut, CEA-129",
          "amount": 1200,
          "crossed_out": false,
          "confidence": {
            "name": 0.9,
            "age": 0.95,
            "gender": 0.95,
            "tests": 0.85,
            "amount": 0.95
          },
          "raw_text": "Richa 30F 1200P mut, CEA-129"
        }
      ]
    },
    {
      "date": "08/03/26",
      "date_confidence": 0.90,
      "patients": [...]
    }
  ]
}
```

If only ONE date is found, still use the `date_groups` array with one element.
If a field cannot be determined, use null. For tests, always return a clean comma-separated string. Now extract all patient data from this image:
"""


def _error_response(msg: str) -> dict:
    """Return a standardised error result dict."""
    return {
        "date_groups": [],
        "date": "",
        "date_confidence": 0.0,
        "patients": [],
        "error": msg,
    }


def _parse_ocr_text(raw_text: str) -> dict:
    """
    Parse the raw JSON text returned by any OCR model into a structured dict.
    Shared by both the Gemini and Groq code paths.
    """
    # Strip markdown code fences if present
    if raw_text.startswith("```"):
        raw_text = re.sub(r"^```(?:json)?\n?", "", raw_text)
        raw_text = re.sub(r"\n?```$", "", raw_text)

    result = json.loads(raw_text)  # raises json.JSONDecodeError on bad input

    if "date_groups" in result:
        date_groups = [
            {
                "date": group.get("date", ""),
                "date_confidence": float(group.get("date_confidence", 0.8)),
                "patients": _clean_patients(group.get("patients", [])),
            }
            for group in result["date_groups"]
        ]
    else:
        # Legacy single-date format — wrap in a group
        date_groups = [{
            "date": result.get("date", ""),
            "date_confidence": float(result.get("date_confidence", 0.8)),
            "patients": _clean_patients(result.get("patients", [])),
        }]

    all_patients = [p for g in date_groups for p in g["patients"]]

    return {
        "date_groups": date_groups,
        "date": date_groups[0]["date"] if date_groups else "",
        "date_confidence": date_groups[0]["date_confidence"] if date_groups else 0.8,
        "patients": all_patients,
        "error": None,
    }


def _clean_patients(patients):
    """Normalize and clean patient records."""
    cleaned = []
    for p in patients:
        # Ensure amount is int or None
        amt = p.get("amount")
        if amt is not None:
            try:
                amt = int(str(amt).replace(",", "").strip())
            except (ValueError, TypeError):
                amt = None
        
        # Clean tests string — uppercase and normalise spacing
        tests = p.get("tests") or ""
        tests = ", ".join([t.strip().upper() for t in tests.split(",") if t.strip()])
        
        # Ensure name is cleaned and uppercased
        name = (p.get("name") or "").strip().upper()
        age = str(p.get("age") or "").strip()
        gender = (p.get("gender") or "").strip().upper()
        
        # Confidence dict
        conf = p.get("confidence", {})
        
        cleaned.append({
            "serial": p.get("serial"),
            "name": name,
            "age": age,
            "gender": gender,
            "tests": tests,
            "amount": amt,
            "crossed_out": bool(p.get("crossed_out", False)),
            "confidence": {
                "name": float(conf.get("name", 0.8)),
                "age": float(conf.get("age", 0.8)),
                "gender": float(conf.get("gender", 0.8)),
                "tests": float(conf.get("tests", 0.8)),
                "amount": float(conf.get("amount", 0.8)),
            },
            "raw_text": p.get("raw_text", "")
        })
    return cleaned


# ─────────────────────────────────────────────────────────────────────────────
# Public API
# ─────────────────────────────────────────────────────────────────────────────

def parse_image(image_path: str, api_key: str, provider: str = "gemini") -> dict:
    """
    Dispatch OCR to the requested provider.

    Parameters
    ----------
    image_path : str   Path to the image file.
    api_key    : str   API key for the chosen provider.
    provider   : str   "gemini" (default), "groq", or "llamaparse".

    Returns
    -------
    dict with keys: date_groups, date, date_confidence, patients, error
    (plus optional model_used for Groq).
    """
    if provider == "groq":
        return _parse_image_groq(image_path, api_key)
    if provider == "llamaparse":
        return _parse_image_llamaparse(image_path, api_key)
    return _parse_image_gemini(image_path, api_key)


# ─────────────────────────────────────────────────────────────────────────────
# Gemini provider
# ─────────────────────────────────────────────────────────────────────────────

def _parse_image_gemini(image_path: str, api_key: str) -> dict:
    """Send image to Gemini Vision and return structured patient data."""
    raw_text = ""
    try:
        image_bytes = preprocess_image(image_path)

        with genai.Client(api_key=api_key) as client:
            response = client.models.generate_content(
                model="gemini-2.0-flash",
                contents=[
                    OCR_PROMPT,
                    types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg"),
                ],
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                ),
            )

        raw_text = (response.text or "").strip()
        return _parse_ocr_text(raw_text)

    except json.JSONDecodeError as e:
        return _error_response(
            f"Failed to parse Gemini response as JSON: {str(e)}. Raw: {raw_text[:300]}"
        )
    except Exception as e:
        return _error_response(f"Gemini OCR error: {str(e)}")


# ─────────────────────────────────────────────────────────────────────────────
# Groq provider  (tries every model in GROQ_VISION_MODELS as fallback)
# ─────────────────────────────────────────────────────────────────────────────

def _parse_image_groq(image_path: str, api_key: str) -> dict:
    """
    Send image to Groq Vision and return structured patient data.
    Iterates through GROQ_VISION_MODELS until one succeeds.
    """
    try:
        from groq import Groq
    except ImportError:
        return _error_response(
            "The 'groq' package is not installed. Run: pip install groq"
        )

    try:
        image_bytes = preprocess_image(image_path)
    except Exception as e:
        return _error_response(f"Image preprocessing failed: {str(e)}")

    b64 = base64.b64encode(image_bytes).decode("utf-8")
    errors = []

    for model in GROQ_VISION_MODELS:
        raw_text = ""
        try:
            client = Groq(api_key=api_key)
            resp = client.chat.completions.create(
                model=model,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": OCR_PROMPT},
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": f"data:image/jpeg;base64,{b64}"
                                },
                            },
                        ],
                    }
                ],
                temperature=0.1,
                max_tokens=4096,
            )
            raw_text = (resp.choices[0].message.content or "").strip()
            result = _parse_ocr_text(raw_text)
            result["model_used"] = model  # expose which model succeeded
            return result

        except json.JSONDecodeError as e:
            errors.append(f"[{model}] JSON parse failed: {str(e)}. Raw: {raw_text[:200]}")
        except Exception as e:
            errors.append(f"[{model}] {str(e)}")
            # continue to the next fallback model

    return _error_response(
        "All Groq models failed.\n" + "\n".join(errors)
    )


# ─────────────────────────────────────────────────────────────────────────────
# LlamaParse provider  (cloud document-parsing API by LlamaIndex)
# ─────────────────────────────────────────────────────────────────────────────

# Correct routes — from llama_cloud_services.parse.base constants (no /v1/ prefix)
_LLAMAPARSE_BASE        = "https://api.cloud.llamaindex.ai"
_LLAMAPARSE_UPLOAD_URL  = f"{_LLAMAPARSE_BASE}/api/parsing/upload"
_LLAMAPARSE_JOB_URL     = f"{_LLAMAPARSE_BASE}/api/parsing/job/{{}}"
_LLAMAPARSE_RESULT_URL  = f"{_LLAMAPARSE_BASE}/api/parsing/job/{{}}/result/markdown"


def _extract_json_from_text(text: str) -> str:
    """
    Try to find and extract a JSON object from free-form text.
    LlamaParse may return JSON wrapped in prose or markdown — this
    locates the outermost { ... } block and returns it.
    """
    # First try: whole text is already JSON / fenced JSON
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:json)?\n?", "", stripped)
        stripped = re.sub(r"\n?```$", "", stripped.strip())

    if stripped.startswith("{"):
        return stripped

    # Second try: find first { and last } in the raw text
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        return text[start:end + 1]

    return stripped  # return as-is; caller will raise JSONDecodeError


def _llamaparse_to_structured(raw_text: str, llamaparse_api_key: str) -> dict:
    """
    Two-step pipeline:
      1. Try to parse raw_text directly as JSON (handles cases where
         LlamaParse followed the parsing_instruction perfectly).
      2. If that fails, send the extracted text to Gemini (using
         GEMINI_API_KEY from env) so it can reformat into our schema.
      3. If Gemini is also unavailable, return a clear error.
    """
    # ── Attempt 1: parse raw text directly ───────────────────────────────────
    candidate = _extract_json_from_text(raw_text)
    try:
        return _parse_ocr_text(candidate)
    except (json.JSONDecodeError, Exception):
        pass

    # ── Attempt 2: Gemini re-format (server key only — no extra key needed) ──
    gemini_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if gemini_key and gemini_key not in ("", "your_gemini_api_key_here"):
        try:
            prompt = (
                OCR_PROMPT
                + "\n\nThe following is the raw OCR text already extracted from "
                "the image. Do NOT describe an image — use only this text:\n\n"
                + raw_text
            )
            with genai.Client(api_key=gemini_key) as client:
                response = client.models.generate_content(
                    model="gemini-2.0-flash",
                    contents=[prompt],
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                    ),
                )
            return _parse_ocr_text((response.text or "").strip())
        except Exception as gemini_err:
            return _error_response(
                f"LlamaParse extracted text but Gemini re-format failed: {gemini_err}. "
                f"LlamaParse raw output: {raw_text[:400]}"
            )

    return _error_response(
        "LlamaParse extracted text but could not parse it as structured JSON. "
        "Set GEMINI_API_KEY on the server (or enter a Gemini key in the UI) to "
        "enable automatic re-formatting. "
        f"LlamaParse raw output: {raw_text[:400]}"
    )


def _parse_image_llamaparse(image_path: str, api_key: str) -> dict:
    """
    Upload image to LlamaParse, poll for completion, then parse the
    extracted text into structured patient data.

    Pipeline:
      image → LlamaParse OCR → raw markdown/text
            → JSON extraction (direct) OR Gemini re-format fallback
            → structured patient dict
    """
    import time
    try:
        import requests as _requests
    except ImportError:
        return _error_response(
            "The 'requests' package is not installed. Run: pip install requests"
        )

    try:
        image_bytes = preprocess_image(image_path)
    except Exception as e:
        return _error_response(f"Image preprocessing failed: {str(e)}")

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Accept": "application/json",
    }

    # ── Step 1: Upload the image (multipart form) ─────────────────────────────
    try:
        upload_resp = _requests.post(
            _LLAMAPARSE_UPLOAD_URL,
            headers=headers,
            files={"file": ("image.jpg", image_bytes, "image/jpeg")},
            data={
                "parsing_instruction": OCR_PROMPT,
                "result_type": "markdown",
                "language": "en",
            },
            timeout=60,
        )
        upload_resp.raise_for_status()
        resp_json = upload_resp.json()
        job_id = resp_json.get("id") or resp_json.get("job_id")
        if not job_id:
            return _error_response(
                f"LlamaParse upload: no job ID in response: {upload_resp.text[:300]}"
            )
    except _requests.exceptions.HTTPError as e:
        body = ""
        try:
            body = e.response.text[:400]
        except Exception:
            pass
        return _error_response(
            f"LlamaParse upload failed (HTTP {e.response.status_code}): {body}"
        )
    except Exception as e:
        return _error_response(f"LlamaParse upload failed: {str(e)}")

    # ── Step 2: Poll until job completes (up to ~2 min) ───────────────────────
    for attempt in range(40):
        time.sleep(3)
        try:
            status_resp = _requests.get(
                _LLAMAPARSE_JOB_URL.format(job_id),
                headers=headers,
                timeout=15,
            )
            status_resp.raise_for_status()
            status_data = status_resp.json()
            status = status_data.get("status", "PENDING").upper()
            if status == "SUCCESS":
                break
            if status in ("ERROR", "CANCELLED", "PARTIAL_SUCCESS"):
                error_detail = status_data.get("error") or status_data.get("message") or str(status_data)
                return _error_response(
                    f"LlamaParse job ended with status '{status}': {error_detail}"
                )
            # PENDING / IN_PROGRESS — keep waiting
        except _requests.exceptions.HTTPError as e:
            return _error_response(
                f"LlamaParse status check failed (HTTP {e.response.status_code}): {e.response.text[:200]}"
            )
        except Exception as e:
            return _error_response(f"LlamaParse status check failed: {str(e)}")
    else:
        return _error_response(
            f"LlamaParse job timed out after {40 * 3} seconds (job_id={job_id})."
        )

    # ── Step 3: Fetch the markdown result ────────────────────────────────────
    raw_text = ""
    try:
        result_resp = _requests.get(
            _LLAMAPARSE_RESULT_URL.format(job_id),
            headers=headers,
            timeout=30,
        )
        result_resp.raise_for_status()
        result_data = result_resp.json()
        raw_text = result_data.get("markdown") or ""
        if not raw_text:
            pages = result_data.get("pages") or []
            raw_text = "\n".join(
                p.get("md") or p.get("text") or "" for p in pages
            )
    except _requests.exceptions.HTTPError as e:
        return _error_response(
            f"LlamaParse result fetch failed (HTTP {e.response.status_code}): {e.response.text[:200]}"
        )
    except Exception as e:
        return _error_response(f"LlamaParse result fetch failed: {str(e)}")

    raw_text = raw_text.strip()
    if not raw_text:
        return _error_response("LlamaParse returned empty text — nothing was extracted from the image.")

    # ── Step 4: Convert extracted text → structured JSON ─────────────────────
    result = _llamaparse_to_structured(raw_text, api_key)
    if not result.get("error"):
        result["model_used"] = "llamaparse"
    return result
