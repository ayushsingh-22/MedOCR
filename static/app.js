/**
 * app.js - MedOCR Frontend Logic
 * Handles file upload, OCR trigger, review table, Google Sheets append,
 * theme management (light/dark/system), and PWA service worker.
 */

// ── State ───────────────────────────────────────────────────────────────────
let selectedFiles = []; // Array of File objects
let currentPatients = []; // Array of patient objects in the review table

// ── Known Test List (from testName.json) ─────────────────────────────────────
const KNOWN_TESTS = [
  'ACE','ADA','AEC','AFB','AFP','ALP','AMMONIA','AMYLASE','ANA',
  'ANAEMIA PROFILE','ANTI CCP','APTT','ASCI','ASMA','ASO','BIL','BIO',
  'BLOOD C/S','BLOOD GROUP','BLOOD PROFILE','BODY PROFILE','BSF','BSR',
  'BUN','C-PEPTIDE','CA','CBC','CBNAAT','CL','CORTISOL','COVID ANTIBODY',
  'COVID ANTIGEN','CREATININE','CRP','D-DIMER','DENGUE SEROLOGY','ECG',
  'FERRITIN','FOLIC ACID','FT3','FT4','GENERAL BODY PROFILE','GENEXPERT',
  'H. PYLORI','HAV','HB','HBA1C','HBSAG','HCV','HIV','HYDATID SEROLOGY',
  'IGA','IGE','IGG','IGM','ILB','INPT','INR','INSULIN F','IRON',
  'IRON STUDIES','K','KETONE BODIES','KFT','LFT','LIPASE','LIPID PROFILE',
  'LIVER FLUID','M.CELL','MG','MP ANTI','MXT','NA','NS1','OCCULT BLOOD',
  'OT','PP','PRC','PRL','PROTEIN ELECTROPHORESIS','PSA','PT',
  'PUS FOR CYTOLOGY','RA FACTOR','RA FACTOR QUANTITATIVE','SERUM','SGOT',
  'SGPT','SNP','SPUTUM C/S','SPUTUM FOR AFB & C/S','STOOL','STOOL R/E',
  'T.DOT','TESTOSTERONE','TFT','TICK TYPHUS IGM','TOTAL PROTEIN','TROP T',
  'TSH','TTG','TYPHI IGM','UACR','UREA','URIC ACID','URINE C/S',
  'URINE R/E','VDRL','VIT B12','VIT D3','WIDAL'
];

// ── Test Name Fuzzy Matching ──────────────────────────────────────────────────

/** Compute Levenshtein distance between two strings. */
function levenshtein(a, b) {
  const m = a.length, n = b.length;
  const dp = Array.from({ length: m + 1 }, (_, i) => {
    const row = [];
    for (let j = 0; j <= n; j++) row[j] = i === 0 ? j : (j === 0 ? i : 0);
    return row;
  });
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1]
        ? dp[i - 1][j - 1]
        : 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]);
    }
  }
  return dp[m][n];
}

/**
 * Fuzzy-match a single OCR token against KNOWN_TESTS.
 * Returns the canonical test name if a good match is found, or null if no
 * known test is close enough (caller should drop the token).
 */
function matchSingleTest(token) {
  const t = token.trim().toUpperCase();
  if (!t) return null;

  // Exact match
  if (KNOWN_TESTS.includes(t)) return t;

  let bestMatch = null;
  let bestScore = -1;

  for (const known of KNOWN_TESTS) {
    const maxLen = Math.max(t.length, known.length);
    let score = maxLen === 0 ? 1 : 1 - levenshtein(t, known) / maxLen;

    // Boost for prefix / substring containment.
    // Guard: require at least 3 chars in both directions to avoid short
    // test names ("K", "CA", "HB", "NA", "OT" …) firing on unrelated tokens.
    if (known.startsWith(t) || t.startsWith(known)) {
      score = Math.max(score, 0.75);
    } else if (t.length >= 3 && known.includes(t)) {
      score = Math.max(score, 0.75);
    } else if (known.length >= 3 && t.includes(known)) {
      score = Math.max(score, 0.75);
    }

    if (score > bestScore) {
      bestScore = score;
      bestMatch = known;
    }
  }

  // Only accept if similarity is high enough; otherwise signal "no match"
  return bestScore >= 0.65 ? bestMatch : null;
}

/**
 * Normalize an OCR-extracted tests string to canonical names from KNOWN_TESTS.
 * Splits on commas / slashes, fuzzy-matches each token, deduplicates, and
 * returns a clean comma-separated string.
 * Tokens that don't match any known test are silently dropped.
 */
function normalizeTests(rawTests) {
  if (!rawTests || !rawTests.trim()) return rawTests || '';

  const tokens = rawTests.split(/[,\/\n]+/).map(t => t.trim()).filter(Boolean);
  const seen = new Set();
  const result = [];

  for (const token of tokens) {
    const matched = matchSingleTest(token);
    if (matched !== null && !seen.has(matched)) {
      seen.add(matched);
      result.push(matched);
    }
  }

  return result.join(', ');
}

// ── Theme Management ────────────────────────────────────────────────────────
const THEME_KEY = 'medocr_theme';
const THEME_ICONS = { light: '☀️', dark: '🌙', system: '💻' };
const THEME_TITLES = { light: 'Light mode — click for dark', dark: 'Dark mode — click for system', system: 'System default — click for light' };

function initTheme() {
  const stored = localStorage.getItem(THEME_KEY);
  // First-time user: no stored value → use 'system'
  const mode = stored || 'system';
  applyTheme(mode);
  updateThemeToggleUI(mode);

  // Listen for OS preference changes (only relevant in system mode)
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (getCurrentThemeMode() === 'system') {
      applyTheme('system');
    }
    // Also update theme-color meta tag
    updateThemeColorMeta();
  });
}

function getCurrentThemeMode() {
  return localStorage.getItem(THEME_KEY) || 'system';
}

function applyTheme(mode) {
  const root = document.documentElement;
  if (mode === 'light') {
    root.setAttribute('data-theme', 'light');
  } else if (mode === 'dark') {
    root.setAttribute('data-theme', 'dark');
  } else {
    // System: remove data-theme so CSS @media takes over
    root.removeAttribute('data-theme');
  }
  localStorage.setItem(THEME_KEY, mode);
  updateThemeColorMeta();
}

function updateThemeToggleUI(mode) {
  const icon = document.getElementById('themeIcon');
  const btn = document.getElementById('themeToggle');
  if (icon) icon.textContent = THEME_ICONS[mode] || '💻';
  if (btn) btn.title = THEME_TITLES[mode] || 'Toggle theme';
}

function cycleTheme() {
  const current = getCurrentThemeMode();
  const next = current === 'system' ? 'light' : current === 'light' ? 'dark' : 'system';
  applyTheme(next);
  updateThemeToggleUI(next);
  showToast(`Theme: ${next === 'system' ? 'System default' : next.charAt(0).toUpperCase() + next.slice(1)}`, 'info');
}

function updateThemeColorMeta() {
  const meta = document.querySelector('meta[name="theme-color"]');
  if (!meta) return;
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark' ||
    (!document.documentElement.hasAttribute('data-theme') && window.matchMedia('(prefers-color-scheme: dark)').matches);
  meta.setAttribute('content', isDark ? '#080814' : '#6366f1');
}

// ── PWA Service Worker ────────────────────────────────────────────
function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return;

  // When a new SW takes control, reload once so the page runs under the new SW.
  let refreshing = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!refreshing) { refreshing = true; location.reload(); }
  });

  navigator.serviceWorker
    .register('/sw.js', {
      scope: '/',
      // Always fetch sw.js from network, never from HTTP cache.
      updateViaCache: 'none',
    })
    .then((reg) => {
      console.log('SW registered, scope:', reg.scope);
      reg.update();

      // Helper: tell a SW to skip waiting and activate immediately
      const skipWaiting = (worker) => worker.postMessage({ type: 'SKIP_WAITING' });

      // If there is already a waiting SW (e.g. previous update was deferred),
      // activate it right now — don't wait for tab close.
      if (reg.waiting) skipWaiting(reg.waiting);

      reg.addEventListener('updatefound', () => {
        const newWorker = reg.installing;
        if (!newWorker) return;
        newWorker.addEventListener('statechange', () => {
          if (newWorker.state === 'installed') {
            // New SW installed — activate immediately regardless of open tabs
            skipWaiting(newWorker);
          }
        });
      });
    })
    .catch((err) => console.warn('SW registration failed:', err));
}

// ── PWA Install Prompt ──────────────────────────────────────────────────────
let deferredInstallPrompt = null;

function setupInstallPrompt() {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredInstallPrompt = e;
    // Show custom install banner
    const banner = document.getElementById('installPrompt');
    if (banner) banner.classList.add('show');
  });

  const btnInstall = document.getElementById('btnInstall');
  const btnDismiss = document.getElementById('btnDismissInstall');

  if (btnInstall) {
    btnInstall.addEventListener('click', async () => {
      if (deferredInstallPrompt) {
        deferredInstallPrompt.prompt();
        const result = await deferredInstallPrompt.userChoice;
        if (result.outcome === 'accepted') {
          showToast('🎉 MedOCR installed!', 'success');
        }
        deferredInstallPrompt = null;
      }
      const banner = document.getElementById('installPrompt');
      if (banner) banner.classList.remove('show');
    });
  }

  if (btnDismiss) {
    btnDismiss.addEventListener('click', () => {
      const banner = document.getElementById('installPrompt');
      if (banner) banner.classList.remove('show');
      deferredInstallPrompt = null;
    });
  }

  // Hide prompt if app is already installed
  window.addEventListener('appinstalled', () => {
    const banner = document.getElementById('installPrompt');
    if (banner) banner.classList.remove('show');
    deferredInstallPrompt = null;
  });
}

// ── Auth ────────────────────────────────────────────────────────────────────
async function checkAuthStatus() {
  try {
    const res = await fetch('/api/auth/status');
    const data = await res.json();

    const chip = document.getElementById('authChip');
    const chipText = document.getElementById('authChipText');
    const btnAuth = document.getElementById('btnGoogleAuth');
    const btnLogout = document.getElementById('btnLogout');
    const credsHint = document.getElementById('credsHint');
    const serverKeyHint = document.getElementById('serverKeyHint');

    // Show server key hints based on active provider
    if (serverKeyHint) {
      const userGeminiKey = document.getElementById('geminiApiKey').value.trim();
      serverKeyHint.style.display = (data.has_server_gemini_key && !userGeminiKey) ? 'block' : 'none';
    }
    const serverGroqKeyHint = document.getElementById('serverGroqKeyHint');
    if (serverGroqKeyHint) {
      const userGroqKey = document.getElementById('groqApiKey').value.trim();
      serverGroqKeyHint.style.display = (data.has_server_groq_key && !userGroqKey) ? 'block' : 'none';
    }
    const serverLlamaParseKeyHint = document.getElementById('serverLlamaParseKeyHint');
    if (serverLlamaParseKeyHint) {
      const userLlamaParseKey = document.getElementById('llamaParseApiKey').value.trim();
      serverLlamaParseKeyHint.style.display = (data.has_server_llamaparse_key && !userLlamaParseKey) ? 'block' : 'none';
    }

    if (!data.has_credentials_file) {
      chip.className = 'auth-chip auth-chip--disconnected';
      chipText.textContent = 'credentials.json missing';
      credsHint.style.display = 'block';
      btnAuth.disabled = true;
    } else if (data.authenticated) {
      chip.className = 'auth-chip auth-chip--connected';
      chipText.textContent = 'Connected';
      btnAuth.style.display = 'none';
      btnLogout.style.display = '';
    } else {
      chip.className = 'auth-chip auth-chip--disconnected';
      chipText.textContent = 'Not connected';
      btnAuth.style.display = '';
      btnLogout.style.display = 'none';
    }

    // Pre-fill Google Sheet ID from .env if user hasn't set one
    const sheetInput = document.getElementById('sheetId');
    const sheetHint = document.getElementById('serverSheetHint');
    if (data.default_sheet_id) {
      if (!sheetInput.value.trim()) {
        sheetInput.value = data.default_sheet_id;
      }
      if (sheetHint) sheetHint.style.display = 'block';
    } else {
      if (sheetHint) sheetHint.style.display = 'none';
    }
  } catch (e) {
    console.error('Auth check failed:', e);
  }
}

function handleGoogleAuth() {
  window.open('/api/auth/login', '_blank', 'width=500,height=700');
  // Poll for auth completion
  const poll = setInterval(async () => {
    const res = await fetch('/api/auth/status');
    const data = await res.json();
    if (data.authenticated) {
      clearInterval(poll);
      checkAuthStatus();
      showToast('✅ Google Account connected!', 'success');
    }
  }, 2000);
  setTimeout(() => clearInterval(poll), 120000); // stop after 2 min
}

async function handleLogout() {
  await fetch('/api/auth/logout', { method: 'POST' });
  checkAuthStatus();
  showToast('Disconnected from Google', 'info');
}

// ── Provider toggle ──────────────────────────────────────────────────────────
function updateProviderUI() {
  const provider = document.querySelector('input[name="ocrProvider"]:checked')?.value || 'gemini';
  document.getElementById('fieldGeminiKey').style.display    = provider === 'gemini'      ? '' : 'none';
  document.getElementById('fieldGroqKey').style.display      = provider === 'groq'        ? '' : 'none';
  document.getElementById('fieldLlamaParseKey').style.display = provider === 'llamaparse' ? '' : 'none';
  const label = document.getElementById('btnAnalyseLabel');
  if (label) {
    if (provider === 'groq')        label.textContent = 'Analyse with Groq AI';
    else if (provider === 'llamaparse') label.textContent = 'Analyse with LlamaParse';
    else                            label.textContent = 'Analyse with Gemini AI';
  }
  localStorage.setItem('medocr_provider', provider);
  // Refresh key hints
  checkAuthStatus();
}

// ── DOMContentLoaded ────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  // Init theme
  initTheme();

  // Theme toggle
  const themeBtn = document.getElementById('themeToggle');
  if (themeBtn) themeBtn.addEventListener('click', cycleTheme);

  // Register service worker
  registerServiceWorker();

  // PWA install prompt
  setupInstallPrompt();

  // Auth check
  checkAuthStatus();

  // API key toggles
  const toggleApiKeyBtn = document.getElementById('toggleApiKey');
  if (toggleApiKeyBtn) toggleApiKeyBtn.addEventListener('click', () => {
    const input = document.getElementById('geminiApiKey');
    input.type = input.type === 'password' ? 'text' : 'password';
  });
  const toggleGroqKeyBtn = document.getElementById('toggleGroqKey');
  if (toggleGroqKeyBtn) toggleGroqKeyBtn.addEventListener('click', () => {
    const input = document.getElementById('groqApiKey');
    input.type = input.type === 'password' ? 'text' : 'password';
  });
  const toggleLlamaParseKeyBtn = document.getElementById('toggleLlamaParseKey');
  if (toggleLlamaParseKeyBtn) toggleLlamaParseKeyBtn.addEventListener('click', () => {
    const input = document.getElementById('llamaParseApiKey');
    input.type = input.type === 'password' ? 'text' : 'password';
  });

  // Load saved values from localStorage
  const savedKey = localStorage.getItem('medocr_api_key');
  if (savedKey) document.getElementById('geminiApiKey').value = savedKey;
  const savedGroqKey = localStorage.getItem('medocr_groq_api_key');
  if (savedGroqKey) document.getElementById('groqApiKey').value = savedGroqKey;
  const savedLlamaParseKey = localStorage.getItem('medocr_llamaparse_api_key');
  if (savedLlamaParseKey) document.getElementById('llamaParseApiKey').value = savedLlamaParseKey;
  const savedProvider = localStorage.getItem('medocr_provider');
  if (savedProvider) {
    const radio = document.querySelector(`input[name="ocrProvider"][value="${savedProvider}"]`);
    if (radio) radio.checked = true;
  }
  updateProviderUI();

  const savedSheet = localStorage.getItem('medocr_sheet_id');
  if (savedSheet) document.getElementById('sheetId').value = savedSheet;
  const savedSheetName = localStorage.getItem('medocr_sheet_name');
  if (savedSheetName) document.getElementById('sheetName').value = savedSheetName;

  // Auto-save settings to localStorage
  document.getElementById('geminiApiKey').addEventListener('change', e => {
    localStorage.setItem('medocr_api_key', e.target.value);
    checkAuthStatus();
  });
  document.getElementById('groqApiKey').addEventListener('change', e => {
    localStorage.setItem('medocr_groq_api_key', e.target.value);
    checkAuthStatus();
  });
  document.getElementById('llamaParseApiKey').addEventListener('change', e => {
    localStorage.setItem('medocr_llamaparse_api_key', e.target.value);
    checkAuthStatus();
  });
  document.getElementById('sheetId').addEventListener('change', e => {
    localStorage.setItem('medocr_sheet_id', e.target.value);
  });
  document.getElementById('sheetName').addEventListener('change', e => {
    localStorage.setItem('medocr_sheet_name', e.target.value);
  });
});

// ── File Upload (multi-image) ────────────────────────────────────────────────
function handleDragOver(e) {
  e.preventDefault();
  document.getElementById('uploadZone').classList.add('drag-over');
}
function handleDragLeave() {
  document.getElementById('uploadZone').classList.remove('drag-over');
}
function handleDrop(e) {
  e.preventDefault();
  document.getElementById('uploadZone').classList.remove('drag-over');
  const files = Array.from(e.dataTransfer.files).filter(f => f.type.startsWith('image/'));
  if (files.length) addFiles(files);
}
function handleFileSelect(e) {
  const files = Array.from(e.target.files);
  if (files.length) addFiles(files);
  // Reset input so same file can be re-selected
  e.target.value = '';
}

function addFiles(newFiles) {
  selectedFiles = selectedFiles.concat(newFiles);
  renderGallery();
  document.getElementById('uploadZone').style.display = 'none';
  document.getElementById('previewSection').style.display = 'flex';
}

function removeFile(index) {
  selectedFiles.splice(index, 1);
  if (selectedFiles.length === 0) {
    clearUpload();
  } else {
    renderGallery();
  }
}

function renderGallery() {
  const gallery = document.getElementById('previewGallery');
  gallery.innerHTML = '';
  selectedFiles.forEach((file, idx) => {
    const thumb = document.createElement('div');
    thumb.className = 'preview-thumb';
    const img = document.createElement('img');
    img.alt = file.name;
    // Create thumbnail URL
    const url = URL.createObjectURL(file);
    img.src = url;
    img.onload = () => URL.revokeObjectURL(url);

    const removeBtn = document.createElement('button');
    removeBtn.className = 'thumb-remove';
    removeBtn.textContent = '✕';
    removeBtn.title = 'Remove';
    removeBtn.onclick = (e) => { e.stopPropagation(); removeFile(idx); };

    const name = document.createElement('div');
    name.className = 'thumb-name';
    name.textContent = file.name;

    thumb.appendChild(img);
    thumb.appendChild(removeBtn);
    thumb.appendChild(name);
    gallery.appendChild(thumb);
  });

  document.getElementById('previewCount').textContent =
    `${selectedFiles.length} image${selectedFiles.length !== 1 ? 's' : ''} selected`;
}

function clearUpload() {
  selectedFiles = [];
  document.getElementById('fileInput').value = '';
  document.getElementById('uploadZone').style.display = '';
  document.getElementById('previewSection').style.display = 'none';
  document.getElementById('previewGallery').innerHTML = '';
  document.getElementById('stepReview').style.display = 'none';
}

// ── Date Group Utilities ──────────────────────────────────────────────────────

/**
 * Parse a date string in DD/MM/YY format (e.g. '07/03/26')
 * into a numeric timestamp for sorting (returns Infinity if unparseable).
 */
function parseDateForSort(dateStr) {
  if (!dateStr) return Infinity;

  // Dates are always DD/MM/YY — normalise separators
  const parts = dateStr.trim().replace(/[-.\s]/g, '/').split('/');
  if (parts.length < 2) return Infinity;

  try {
    const day   = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1;
    let year    = parts.length > 2 ? parseInt(parts[2], 10) : new Date().getFullYear() % 100;
    if (year < 100) year += 2000;

    const d = new Date(year, month, day);
    return isNaN(d.getTime()) ? Infinity : d.getTime();
  } catch {
    return Infinity;
  }
}

/**
 * Normalise a DD/MM/YY date string to a canonical D/M/YYYY key for deduplication.
 */
function normaliseDateKey(dateStr) {
  if (!dateStr) return '';
  // Dates are always DD/MM/YY
  const parts = dateStr.trim().replace(/[-.\s]/g, '/').split('/');
  if (parts.length < 2) return dateStr.trim();

  const day   = parseInt(parts[0], 10);
  const month = parseInt(parts[1], 10);
  let year    = parts.length > 2 ? parseInt(parts[2], 10) : new Date().getFullYear() % 100;
  if (year < 100) year += 2000;

  if (isNaN(day) || isNaN(month) || isNaN(year)) return dateStr.trim();
  return `${day}/${month}/${year}`; // canonical: no leading zeros
}

/**
 * Given a raw array of date_groups (possibly from multiple images),
 * merge groups that share the same date and sort them chronologically.
 * Keeps the highest date_confidence seen for each merged group.
 */
function mergeAndSortDateGroups(groups) {
  const map = new Map(); // normalisedDate -> group object

  for (const g of groups) {
    const key = normaliseDateKey(g.date);
    if (map.has(key)) {
      // Merge patients into existing group
      const existing = map.get(key);
      existing.patients = existing.patients.concat(g.patients || []);
      // Keep the best confidence value seen
      if ((g.date_confidence || 0) > (existing.date_confidence || 0)) {
        existing.date_confidence = g.date_confidence;
      }
    } else {
      // New date — clone so we don't mutate the original
      map.set(key, {
        date: g.date || '',
        date_confidence: g.date_confidence || 0,
        patients: [...(g.patients || [])]
      });
    }
  }

  // Sort chronologically (earliest first)
  return Array.from(map.values()).sort(
    (a, b) => parseDateForSort(a.date) - parseDateForSort(b.date)
  );
}

// ── Analyse (batch multi-image) ──────────────────────────────────────────────
async function analyseImages() {
  const provider = document.querySelector('input[name="ocrProvider"]:checked')?.value || 'gemini';
  let apiKey;
  if (provider === 'groq') {
    apiKey = document.getElementById('groqApiKey').value.trim();
  } else if (provider === 'llamaparse') {
    apiKey = document.getElementById('llamaParseApiKey').value.trim();
  } else {
    apiKey = document.getElementById('geminiApiKey').value.trim();
  }

  if (selectedFiles.length === 0) {
    showToast('⚠️ No images selected', 'error');
    return;
  }

  const total = selectedFiles.length;
  let allDateGroups = []; // Accumulate date_groups from all images
  let successCount = 0;
  let errorCount = 0;

  // Show progress UI
  showBatchProgress(0, total, 'Starting analysis...');

  for (let i = 0; i < total; i++) {
    const file = selectedFiles[i];
    showBatchProgress(i, total, `Analysing image ${i + 1} of ${total}: ${file.name}`);

    const formData = new FormData();
    formData.append('image', file);
    formData.append('provider', provider);
    if (provider === 'groq') {
      formData.append('groq_api_key', apiKey);
    } else if (provider === 'llamaparse') {
      formData.append('llamaparse_api_key', apiKey);
    } else {
      formData.append('api_key', apiKey);
    }

    try {
      const res = await fetch('/api/upload', { method: 'POST', body: formData });
      const data = await res.json();

      if (!res.ok || data.error) {
        errorCount++;
        showToast(`⚠️ Image ${i + 1} failed: ${data.error || 'OCR error'}`, 'error');
        continue;
      }

      // Collect date groups from this image
      if (data.date_groups && data.date_groups.length > 0) {
        allDateGroups = allDateGroups.concat(data.date_groups);
      } else {
        // Legacy fallback: wrap in a single group
        allDateGroups.push({
          date: data.date || '',
          date_confidence: data.date_confidence || 0,
          patients: data.patients || []
        });
      }
      successCount++;
    } catch (err) {
      errorCount++;
      showToast(`⚠️ Image ${i + 1}: Network error`, 'error');
    }
  }

  // Done — hide progress
  hideBatchProgress();

  // Merge groups with the same date and sort chronologically
  const mergedGroups = mergeAndSortDateGroups(allDateGroups);

  const totalPatients = mergedGroups.reduce((sum, g) => sum + g.patients.length, 0);
  if (totalPatients === 0) {
    showToast('❌ No patient data extracted from any image', 'error');
    return;
  }

  // Populate review with sorted, merged date groups
  populateReviewSection({ date_groups: mergedGroups });

  const msg = `✅ Extracted ${totalPatients} patients from ${successCount} image${successCount !== 1 ? 's' : ''}`;
  showToast(errorCount ? `${msg} (${errorCount} failed)` : msg, 'success');
}

function showBatchProgress(current, total, text) {
  // Remove loading overlay if shown
  hideLoading();
  // Find or create progress UI inside the upload card
  let progressEl = document.getElementById('batchProgress');
  if (!progressEl) {
    progressEl = document.createElement('div');
    progressEl.id = 'batchProgress';
    progressEl.className = 'batch-progress';
    progressEl.innerHTML = `
      <div class="batch-progress-bar"><div class="batch-progress-fill" id="batchProgressFill"></div></div>
      <div class="batch-progress-text" id="batchProgressText"></div>
    `;
    const previewSection = document.getElementById('previewSection');
    previewSection.appendChild(progressEl);
  }
  progressEl.style.display = 'block';
  const pct = total > 0 ? Math.round(((current + 0.5) / total) * 100) : 0;
  document.getElementById('batchProgressFill').style.width = pct + '%';
  document.getElementById('batchProgressText').textContent = text;
  // Disable analyse button during processing
  document.getElementById('btnAnalyse').disabled = true;
}

function hideBatchProgress() {
  const progressEl = document.getElementById('batchProgress');
  if (progressEl) progressEl.style.display = 'none';
  document.getElementById('btnAnalyse').disabled = false;
}

// ── Review Section (date-grouped) ────────────────────────────────────────────
let currentDateGroups = []; // Array of { date, date_confidence, patients[] }

/** Apply normalizeTests to every patient in a list of date groups. */
function applyTestNormalization(groups) {
  return groups.map(g => ({
    ...g,
    patients: (g.patients || []).map(p => ({
      ...p,
      tests: normalizeTests(p.tests || '')
    }))
  }));
}

function populateReviewSection(data) {
  // Build date groups from response
  let rawGroups;
  if (data.date_groups && data.date_groups.length > 0) {
    rawGroups = data.date_groups.map(g => ({
      date: g.date || '',
      date_confidence: g.date_confidence || 0,
      patients: g.patients || []
    }));
  } else {
    // Legacy: single date + flat patients
    rawGroups = [{
      date: data.date || '',
      date_confidence: data.date_confidence || 0,
      patients: data.patients || []
    }];
  }

  // Normalize test names against KNOWN_TESTS before rendering
  rawGroups = applyTestNormalization(rawGroups);

  // Always merge same-date groups and sort chronologically before display
  currentDateGroups = mergeAndSortDateGroups(rawGroups);

  renderDateGroups();

  // Show review section
  document.getElementById('stepReview').style.display = '';
  document.getElementById('stepReview').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderDateGroups() {
  const container = document.getElementById('dateGroupsContainer');
  container.innerHTML = '';

  currentDateGroups.forEach((group, gIdx) => {
    const section = document.createElement('div');
    section.className = 'date-group';
    section.id = `date-group-${gIdx}`;

    const dateConf = group.date_confidence || 0;
    const confCls = confidenceClass(dateConf);

    section.innerHTML = `
      <div class="date-group-header">
        <div class="date-row">
          <label class="field-label" style="margin-bottom:0">📅 Date ${currentDateGroups.length > 1 ? (gIdx + 1) : ''}</label>
          <input type="text" class="text-input date-input" id="groupDate-${gIdx}"
                 value="${esc(group.date)}" placeholder="e.g. 7/3/2026"
                 oninput="updateGroupDate(${gIdx}, this.value)" />
          <span class="conf-badge ${confCls}">${Math.round(dateConf * 100)}%</span>
          ${currentDateGroups.length > 1 ? `<button class="btn btn-ghost btn-sm" onclick="removeGroup(${gIdx})" title="Remove group">✕</button>` : ''}
        </div>
      </div>
      <div class="table-wrapper">
        <table class="review-table">
          <thead>
            <tr>
              <th class="col-num">#</th>
              <th class="col-name">Name</th>
              <th class="col-age">Age</th>
              <th class="col-gender">Gen</th>
              <th class="col-tests">Tests</th>
              <th class="col-amount">Amt</th>
              <th class="col-crossed">Skip</th>
              <th class="col-del"></th>
            </tr>
          </thead>
          <tbody id="groupBody-${gIdx}">
          </tbody>
        </table>
      </div>
      <div style="margin-top:8px; display:flex; gap:8px; flex-wrap:wrap;">
        <button class="btn btn-ghost btn-sm" onclick="addRowToGroup(${gIdx})">+ Add Row</button>
      </div>
    `;

    container.appendChild(section);

    // Render patients for this group
    renderGroupTable(gIdx);
  });
}

function renderGroupTable(gIdx) {
  const group = currentDateGroups[gIdx];
  const tbody = document.getElementById(`groupBody-${gIdx}`);
  if (!tbody) return;
  tbody.innerHTML = '';

  group.patients.forEach((p, pIdx) => {
    const row = document.createElement('tr');
    row.id = `row-${gIdx}-${pIdx}`;
    if (p.crossed_out) row.classList.add('crossed-row');

    const conf = p.confidence || {};

    row.innerHTML = `
      <td class="col-num">${p.serial || pIdx + 1}</td>
      <td class="col-name">
        <input class="cell-input ${confClass(conf.name)}" 
               value="${esc((p.name || '').toUpperCase())}" 
               oninput="updateGroupPatient(${gIdx}, ${pIdx}, 'name', this.value.toUpperCase())"
               placeholder="PATIENT NAME" />
      </td>
      <td class="col-age">
        <input class="cell-input ${confClass(conf.age)}"
               value="${esc(p.age)}"
               oninput="updateGroupPatient(${gIdx}, ${pIdx}, 'age', this.value)"
               placeholder="Age" style="width:100%" />
      </td>
      <td class="col-gender">
        <select class="cell-select" onchange="updateGroupPatient(${gIdx}, ${pIdx}, 'gender', this.value)">
          ${['M','F','H',''].map(g => `<option value="${g}" ${p.gender === g ? 'selected' : ''}>${g || '–'}</option>`).join('')}
        </select>
      </td>
      <td class="col-tests">
        <input class="cell-input ${confClass(conf.tests)}"
               value="${esc((p.tests || '').toUpperCase())}"
               oninput="updateGroupPatient(${gIdx}, ${pIdx}, 'tests', this.value.toUpperCase())"
               placeholder="CBC, TSH, LFT..." />
      </td>
      <td class="col-amount">
        <input class="cell-input ${confClass(conf.amount)}"
               id="amt-${gIdx}-${pIdx}"
               value="${p.amount != null ? p.amount : ''}"
               oninput="updateGroupAmount(${gIdx}, ${pIdx}, this)"
               placeholder="Amt" />
      </td>
      <td class="col-crossed" style="text-align:center">
        <input type="checkbox" class="skip-toggle" title="Skip this entry"
               ${p.crossed_out ? 'checked' : ''}
               onchange="toggleGroupCrossedOut(${gIdx}, ${pIdx}, this.checked)" />
      </td>
      <td class="col-del">
        <button class="del-btn" title="Delete row" onclick="deleteGroupRow(${gIdx}, ${pIdx})">🗑</button>
      </td>
    `;
    tbody.appendChild(row);
  });
}

function esc(str) {
  return (str || '').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
function confClass(val) {
  if (val === undefined || val === null) return '';
  return val < 0.6 ? 'low-conf' : '';
}
function confidenceClass(val) {
  if (val >= 0.85) return 'good';
  if (val >= 0.65) return 'medium';
  return 'low';
}

function updateGroupDate(gIdx, value) {
  currentDateGroups[gIdx].date = value;
}

function updateGroupPatient(gIdx, pIdx, field, value) {
  currentDateGroups[gIdx].patients[pIdx][field] = value;
}

function updateGroupAmount(gIdx, pIdx, input) {
  const val = input.value.trim();
  currentDateGroups[gIdx].patients[pIdx].amount = val === '' ? null : val;
  if (val !== '' && isNaN(Number(val))) {
    input.classList.add('invalid');
    input.classList.remove('low-conf');
  } else {
    input.classList.remove('invalid');
  }
}

function toggleGroupCrossedOut(gIdx, pIdx, checked) {
  currentDateGroups[gIdx].patients[pIdx].crossed_out = checked;
  const row = document.getElementById(`row-${gIdx}-${pIdx}`);
  if (row) row.classList.toggle('crossed-row', checked);
}

function deleteGroupRow(gIdx, pIdx) {
  currentDateGroups[gIdx].patients.splice(pIdx, 1);
  // Remove group if empty
  if (currentDateGroups[gIdx].patients.length === 0 && currentDateGroups.length > 1) {
    currentDateGroups.splice(gIdx, 1);
    renderDateGroups();
  } else {
    renderGroupTable(gIdx);
  }
}

function addRowToGroup(gIdx) {
  const group = currentDateGroups[gIdx];
  group.patients.push({
    serial: group.patients.length + 1,
    name: '', age: '', gender: 'M',
    tests: '', amount: null,
    crossed_out: false,
    confidence: { name: 1, age: 1, gender: 1, tests: 1, amount: 1 }
  });
  renderGroupTable(gIdx);
  setTimeout(() => {
    const tbody = document.getElementById(`groupBody-${gIdx}`);
    const rows = tbody ? tbody.querySelectorAll('tr') : [];
    const lastRow = rows[rows.length - 1];
    if (lastRow) lastRow.querySelector('.cell-input')?.focus();
  }, 50);
}

function removeGroup(gIdx) {
  if (currentDateGroups.length <= 1) return;
  currentDateGroups.splice(gIdx, 1);
  renderDateGroups();
}

// Legacy compat — keep addRow working from the HTML header button
function addRow() {
  if (currentDateGroups.length === 0) {
    currentDateGroups.push({
      date: '', date_confidence: 0, patients: []
    });
    renderDateGroups();
  }
  addRowToGroup(currentDateGroups.length - 1);
}

// ── Append to Sheets ─────────────────────────────────────────────────────────
async function appendToSheet() {
  const sheetId = document.getElementById('sheetId').value.trim();
  const sheetName = document.getElementById('sheetName').value.trim() || 'Sheet1';

  if (!sheetId) {
    showToast('⚠️ Please enter the Google Sheet ID', 'error');
    document.getElementById('sheetId').focus();
    return;
  }

  // Validate all groups have dates
  for (let i = 0; i < currentDateGroups.length; i++) {
    if (!currentDateGroups[i].date.trim()) {
      showToast(`⚠️ Date ${i + 1} is empty — please fill it in`, 'error');
      const dateInput = document.getElementById(`groupDate-${i}`);
      if (dateInput) dateInput.focus();
      return;
    }
  }

  // Validate amounts across all groups
  const amtInputs = document.querySelectorAll('[id^="amt-"]');
  let hasInvalid = false;
  amtInputs.forEach(input => {
    if (input.value.trim() !== '' && isNaN(Number(input.value))) {
      input.classList.add('invalid');
      hasInvalid = true;
    }
  });
  if (hasInvalid) {
    showToast('❌ Fix invalid Amount fields (highlighted in red)', 'error');
    return;
  }

  // Build date_groups for API — sort oldest→newest, filter out crossed-out patients
  const dateGroups = [...currentDateGroups]
    .sort((a, b) => parseDateForSort(a.date) - parseDateForSort(b.date))
    .map(g => ({
      date: g.date,
      patients: g.patients
        .filter(p => !p.crossed_out)
        .map(p => ({
          name: p.name,
          age: p.age,
          gender: p.gender,
          tests: p.tests,
          amount: p.amount !== null && p.amount !== '' ? Number(p.amount) : null
        }))
    }))
    .filter(g => g.patients.length > 0);

  if (dateGroups.length === 0) {
    showToast('⚠️ All entries are marked as skipped — nothing to append', 'error');
    return;
  }

  showLoading('Appending to Google Sheet...');

  try {
    const res = await fetch('/api/append', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sheet_id: sheetId, sheet_name: sheetName, date_groups: dateGroups })
    });
    const data = await res.json();
    hideLoading();

    if (!res.ok || data.error) {
      showToast('❌ ' + (data.error || 'Append failed'), 'error');
      return;
    }

    showToast(`✅ ${data.rows_added} rows added to Sheet!`, 'success');
  } catch (err) {
    hideLoading();
    showToast('❌ Network error: ' + err.message, 'error');
  }
}

// ── Utilities ────────────────────────────────────────────────────────────────
let toastTimeout = null;
function showToast(message, type = 'info') {
  const toast = document.getElementById('toast');
  const toastMsg = document.getElementById('toastMessage');
  const toastIcon = document.getElementById('toastIcon');

  const icons = { success: '✅', error: '❌', info: 'ℹ️' };
  toastIcon.textContent = icons[type] || '';
  toastMsg.textContent = message;
  toast.className = `toast toast-${type}`;

  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    toast.classList.add('toast-hidden');
  }, 4000);
}

function showLoading(text = 'Please wait...') {
  document.getElementById('loadingText').textContent = text;
  document.getElementById('loadingOverlay').classList.remove('hidden');
}
function hideLoading() {
  document.getElementById('loadingOverlay').classList.add('hidden');
}
