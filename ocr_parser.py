"""
ocr_parser.py
Uses Google Gemini Vision API to extract structured patient data 
from a handwritten medical test list image.
"""

import os
import json
import re
import base64
import google.generativeai as genai
from PIL import Image
import io


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
3. **Date**: Convert dates to D/M/YYYY format if possible.
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
      "date": "7/3/2026",
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
      "date": "8/3/2026",
      "date_confidence": 0.90,
      "patients": [...]
    }
  ]
}
```

If only ONE date is found, still use the `date_groups` array with one element.
If a field cannot be determined, use null. For tests, always return a clean comma-separated string. Now extract all patient data from this image:
"""


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
        
        # Clean tests string
        tests = p.get("tests") or ""
        tests = ", ".join([t.strip() for t in tests.split(",") if t.strip()])
        
        # Ensure name is cleaned
        name = (p.get("name") or "").strip()
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


def parse_image(image_path: str, api_key: str) -> dict:
    """
    Main function: sends image to Gemini Vision and returns structured dict.
    Returns dict with keys: date_groups (list of {date, date_confidence, patients}), error
    Also includes legacy keys 'date', 'date_confidence', 'patients' for backward compat.
    """
    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-2.0-flash")
        
        # Preprocess and load image
        image_bytes = preprocess_image(image_path)
        
        # Send to Gemini
        response = model.generate_content([
            OCR_PROMPT,
            {"mime_type": "image/jpeg", "data": base64.b64encode(image_bytes).decode("utf-8")}
        ])
        
        raw_text = response.text.strip()
        
        # Strip markdown code fences if present
        if raw_text.startswith("```"):
            raw_text = re.sub(r"^```(?:json)?\n?", "", raw_text)
            raw_text = re.sub(r"\n?```$", "", raw_text)
        
        result = json.loads(raw_text)
        
        # Handle both new multi-date format and legacy single-date format
        if "date_groups" in result:
            date_groups = []
            for group in result["date_groups"]:
                date_groups.append({
                    "date": group.get("date", ""),
                    "date_confidence": float(group.get("date_confidence", 0.8)),
                    "patients": _clean_patients(group.get("patients", []))
                })
        else:
            # Legacy single-date format — wrap in a group
            date_groups = [{
                "date": result.get("date", ""),
                "date_confidence": float(result.get("date_confidence", 0.8)),
                "patients": _clean_patients(result.get("patients", []))
            }]
        
        # Also provide flat legacy keys for backward compatibility
        all_patients = []
        for g in date_groups:
            all_patients.extend(g["patients"])
        
        return {
            "date_groups": date_groups,
            "date": date_groups[0]["date"] if date_groups else "",
            "date_confidence": date_groups[0]["date_confidence"] if date_groups else 0.8,
            "patients": all_patients,
            "error": None
        }
    
    except json.JSONDecodeError as e:
        return {
            "date_groups": [],
            "date": "",
            "date_confidence": 0.0,
            "patients": [],
            "error": f"Failed to parse Gemini response as JSON: {str(e)}. Raw response: {raw_text[:300]}"
        }
    except Exception as e:
        return {
            "date_groups": [],
            "date": "",
            "date_confidence": 0.0,
            "patients": [],
            "error": f"OCR Error: {str(e)}"
        }
