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
    provider   : str   "gemini" (default) or "groq".

    Returns
    -------
    dict with keys: date_groups, date, date_confidence, patients, error
    (plus optional model_used for Groq).
    """
    if provider == "groq":
        return _parse_image_groq(image_path, api_key)
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
