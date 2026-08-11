package com.medocr.app.util

/**
 * Fuzzy-corrects OCR'd lab-test abbreviations against a known vocabulary.
 * Direct Kotlin port of the matching logic in the web app's `static/app.js`
 * (`levenshtein` / `matchSingleTest` / `normalizeTests`).
 */
object TestNameMatcher {

    val KNOWN_TESTS = listOf(
        "ACE", "ADA", "AEC", "AFB", "AFP", "ALP", "AMMONIA", "AMYLASE", "ANA",
        "ANAEMIA PROFILE", "ANTI CCP", "APTT", "ASCI", "ASMA", "ASO", "BIL", "BIO",
        "BLOOD C/S", "BLOOD GROUP", "BLOOD PROFILE", "BODY PROFILE", "BSF", "BSR",
        "BUN", "C-PEPTIDE", "CA", "CBC", "CBNAAT", "CL", "CORTISOL", "COVID ANTIBODY",
        "COVID ANTIGEN", "CREATININE", "CRP", "D-DIMER", "DENGUE SEROLOGY", "ECG",
        "FERRITIN", "FOLIC ACID", "FT3", "FT4", "GENERAL BODY PROFILE", "GENEXPERT",
        "H. PYLORI", "HAV", "HB", "HBA1C", "HBSAG", "HCV", "HIV", "HYDATID SEROLOGY",
        "IGA", "IGE", "IGG", "IGM", "ILB", "INPT", "INR", "INSULIN F", "IRON",
        "IRON STUDIES", "K", "KETONE BODIES", "KFT", "LFT", "LIPASE", "LIPID PROFILE",
        "LIVER FLUID", "M.CELL", "MG", "MP ANTI", "MXT", "NA", "NS1", "OCCULT BLOOD",
        "OT", "PP", "PRC", "PRL", "PROTEIN ELECTROPHORESIS", "PSA", "PT",
        "PUS FOR CYTOLOGY", "RA FACTOR", "RA FACTOR QUANTITATIVE", "SERUM", "SGOT",
        "SGPT", "SNP", "SPUTUM C/S", "SPUTUM FOR AFB & C/S", "STOOL", "STOOL R/E",
        "T.DOT", "TESTOSTERONE", "TFT", "TICK TYPHUS IGM", "TOTAL PROTEIN", "TROP T",
        "TSH", "TTG", "TYPHI IGM", "UACR", "UREA", "URIC ACID", "URINE C/S",
        "URINE R/E", "VDRL", "VIT B12", "VIT D3", "WIDAL",
    )

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[m][n]
    }

    /** Returns the canonical test name for [token], or null if no known test is close enough. */
    fun matchSingleTest(token: String): String? {
        val t = token.trim().uppercase()
        if (t.isEmpty()) return null
        if (t in KNOWN_TESTS) return t

        var bestMatch: String? = null
        var bestScore = -1.0

        for (known in KNOWN_TESTS) {
            val maxLen = maxOf(t.length, known.length)
            var score = if (maxLen == 0) 1.0 else 1.0 - levenshtein(t, known).toDouble() / maxLen

            // Guard: require >=3 chars in both directions so short test names
            // ("K", "CA", "HB", "NA", "OT" …) don't fire on unrelated tokens.
            score = when {
                known.startsWith(t) || t.startsWith(known) -> maxOf(score, 0.75)
                t.length >= 3 && known.contains(t) -> maxOf(score, 0.75)
                known.length >= 3 && t.contains(known) -> maxOf(score, 0.75)
                else -> score
            }

            if (score > bestScore) {
                bestScore = score
                bestMatch = known
            }
        }

        return if (bestScore >= 0.65) bestMatch else null
    }

    /**
     * Normalizes a raw, comma/slash separated OCR "tests" string to canonical
     * names, deduplicated, in a clean comma-separated string. Unmatched tokens
     * are dropped.
     */
    fun normalizeTests(rawTests: String?): String {
        if (rawTests.isNullOrBlank()) return rawTests ?: ""

        val tokens = rawTests.split(Regex("[,/\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val seen = LinkedHashSet<String>()
        for (token in tokens) {
            matchSingleTest(token)?.let { seen.add(it) }
        }
        return seen.joinToString(", ")
    }
}
