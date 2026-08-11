package com.medocr.app.data.remote

/** Shared vision prompt sent to every OCR provider — keep in lockstep with the schema [OcrJsonParser] expects. */
object OcrPrompt {
    const val TEXT = """
You are an expert medical data extraction assistant. I will give you a photo of a handwritten medical test list from a pathology lab. Your job is to extract the structured patient records from this image.

**CRITICAL RULES:**
1. **Multiple dates**: A single image may contain records for MULTIPLE dates. Look for date changes in the page — e.g. a new date heading followed by patient entries. Group patients under their respective dates.
2. **Crossed-out / struck-through text**: If any text has a line drawn through it (strikethrough), extract it but mark "crossed_out": true.
3. **Date**: Dates in these images are ALWAYS written in DD/MM/YY format (day/month/2-digit year, e.g. "07/03/26"). Extract the date exactly as written using DD/MM/YY. Do NOT convert to any other format.
4. **Serial numbers**: Each patient entry starts with a circled number or plain number. These separate patients within a date group.
5. **Price format**: Prices are written as "1200P", "2500p", "1100D" — extract just the numeric part. If there's a crossed-out price and a corrected one, use the corrected price.
6. **Name/Age/Gender**: Usually "Richa 30F" or "Sanjay Vid 60M". Name before age+gender letter.
7. **Tests**: Comma-separated medical test abbreviations (e.g., "CBC, TSH, LFT, BUN").
8. **Confidence**: For each field, give confidence (0.0 to 1.0).

**RETURN FORMAT — valid JSON only, no markdown, no explanation:**
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
          "confidence": { "name": 0.9, "age": 0.95, "gender": 0.95, "tests": 0.85, "amount": 0.95 },
          "raw_text": "Richa 30F 1200P mut, CEA-129"
        }
      ]
    }
  ]
}

If only ONE date is found, still use the date_groups array with one element.
If a field cannot be determined, use null. For tests, always return a clean comma-separated string. Now extract all patient data from this image:
"""
}
