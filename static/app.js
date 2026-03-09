/**
 * app.js - MedOCR Frontend Logic
 * Handles file upload, OCR trigger, review table, Google Sheets append,
 * theme management (light/dark/system), and PWA service worker.
 */

// ── State ───────────────────────────────────────────────────────────────────
let selectedFiles = []; // Array of File objects
let currentPatients = []; // Array of patient objects in the review table

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

// ── PWA Service Worker ──────────────────────────────────────────────────────
function registerServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js', { scope: '/' })
      .then((reg) => console.log('SW registered, scope:', reg.scope))
      .catch((err) => console.warn('SW registration failed:', err));
  }
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

    // Show server key hint if env key is configured and user hasn't entered one
    if (serverKeyHint) {
      const userKey = document.getElementById('geminiApiKey').value.trim();
      serverKeyHint.style.display = (data.has_server_gemini_key && !userKey) ? 'block' : 'none';
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

  // API key toggle
  document.getElementById('toggleApiKey').addEventListener('click', () => {
    const input = document.getElementById('geminiApiKey');
    input.type = input.type === 'password' ? 'text' : 'password';
  });

  // Load saved values from localStorage
  const savedKey = localStorage.getItem('medocr_api_key');
  if (savedKey) document.getElementById('geminiApiKey').value = savedKey;
  const savedSheet = localStorage.getItem('medocr_sheet_id');
  if (savedSheet) document.getElementById('sheetId').value = savedSheet;
  const savedSheetName = localStorage.getItem('medocr_sheet_name');
  if (savedSheetName) document.getElementById('sheetName').value = savedSheetName;

  // Auto-save settings to localStorage
  document.getElementById('geminiApiKey').addEventListener('change', e => {
    localStorage.setItem('medocr_api_key', e.target.value);
    // Refresh server-key hint visibility when user types a key
    checkAuthStatus();
  });
  document.getElementById('sheetId').addEventListener('change', e => {
    localStorage.setItem('medocr_sheet_id', e.target.value);
  });
  document.getElementById('sheetName').addEventListener('change', e => {
    localStorage.setItem('medocr_sheet_name', e.target.value);
  });

  // Date field: update preview on change
  document.getElementById('extractedDate').addEventListener('input', updateDatePreview);
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

// ── Analyse (batch multi-image) ──────────────────────────────────────────────
async function analyseImages() {
  const apiKey = document.getElementById('geminiApiKey').value.trim();

  if (selectedFiles.length === 0) {
    showToast('⚠️ No images selected', 'error');
    return;
  }

  const total = selectedFiles.length;
  let allPatients = [];
  let firstDate = '';
  let firstDateConf = 0;
  let successCount = 0;
  let errorCount = 0;

  // Show progress UI
  showBatchProgress(0, total, 'Starting analysis...');

  for (let i = 0; i < total; i++) {
    const file = selectedFiles[i];
    showBatchProgress(i, total, `Analysing image ${i + 1} of ${total}: ${file.name}`);

    const formData = new FormData();
    formData.append('image', file);
    formData.append('api_key', apiKey);

    try {
      const res = await fetch('/api/upload', { method: 'POST', body: formData });
      const data = await res.json();

      if (!res.ok || data.error) {
        errorCount++;
        showToast(`⚠️ Image ${i + 1} failed: ${data.error || 'OCR error'}`, 'error');
        continue;
      }

      // Use date from first successful image
      if (!firstDate && data.date) {
        firstDate = data.date;
        firstDateConf = data.date_confidence || 0;
      }

      // Collect patients with re-numbered serials
      const patients = (data.patients || []).map((p, pIdx) => ({
        ...p,
        serial: allPatients.length + pIdx + 1,
        _source: file.name
      }));
      allPatients = allPatients.concat(patients);
      successCount++;
    } catch (err) {
      errorCount++;
      showToast(`⚠️ Image ${i + 1}: Network error`, 'error');
    }
  }

  // Done — hide progress
  hideBatchProgress();

  if (allPatients.length === 0) {
    showToast('❌ No patient data extracted from any image', 'error');
    return;
  }

  // Populate review with merged results
  populateReviewSection({
    date: firstDate,
    date_confidence: firstDateConf,
    patients: allPatients
  });

  const msg = `✅ Extracted ${allPatients.length} patients from ${successCount} image${successCount !== 1 ? 's' : ''}`;
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

// ── Review Section ───────────────────────────────────────────────────────────
function populateReviewSection(data) {
  // Date
  const dateInput = document.getElementById('extractedDate');
  dateInput.value = data.date || '';
  const dateConf = data.date_confidence || 0;
  const confBadge = document.getElementById('dateConfidenceBadge');
  confBadge.textContent = `${Math.round(dateConf * 100)}% confidence`;
  confBadge.className = 'conf-badge ' + confidenceClass(dateConf);
  updateDatePreview();

  // Patients
  currentPatients = data.patients || [];
  renderTable();

  // Show review section
  document.getElementById('stepReview').style.display = '';
  document.getElementById('stepReview').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderTable() {
  const tbody = document.getElementById('reviewTableBody');
  tbody.innerHTML = '';

  currentPatients.forEach((p, idx) => {
    const row = document.createElement('tr');
    row.id = `row-${idx}`;
    if (p.crossed_out) row.classList.add('crossed-row');

    const conf = p.confidence || {};

    row.innerHTML = `
      <td class="col-num">${p.serial || idx + 1}</td>
      <td class="col-name">
        <input class="cell-input ${confClass(conf.name)}" 
               value="${esc(p.name)}" 
               oninput="updatePatient(${idx}, 'name', this.value)"
               placeholder="Patient name" />
      </td>
      <td class="col-age">
        <input class="cell-input ${confClass(conf.age)}"
               value="${esc(p.age)}"
               oninput="updatePatient(${idx}, 'age', this.value)"
               placeholder="Age" style="width:100%" />
      </td>
      <td class="col-gender">
        <select class="cell-select" onchange="updatePatient(${idx}, 'gender', this.value)">
          ${['M','F','H',''].map(g => `<option value="${g}" ${p.gender === g ? 'selected' : ''}>${g || '–'}</option>`).join('')}
        </select>
      </td>
      <td class="col-tests">
        <input class="cell-input ${confClass(conf.tests)}"
               value="${esc(p.tests)}"
               oninput="updatePatient(${idx}, 'tests', this.value)"
               placeholder="CBC, TSH, LFT..." />
      </td>
      <td class="col-amount">
        <input class="cell-input ${confClass(conf.amount)}"
               id="amt-${idx}"
               value="${p.amount != null ? p.amount : ''}"
               oninput="updateAmount(${idx}, this)"
               placeholder="Amt" />
      </td>
      <td class="col-crossed" style="text-align:center">
        <input type="checkbox" class="skip-toggle" title="Skip this entry"
               ${p.crossed_out ? 'checked' : ''}
               onchange="toggleCrossedOut(${idx}, this.checked)" />
      </td>
      <td class="col-del">
        <button class="del-btn" title="Delete row" onclick="deleteRow(${idx})">🗑</button>
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

function updatePatient(idx, field, value) {
  currentPatients[idx][field] = value;
}

function updateAmount(idx, input) {
  const val = input.value.trim();
  currentPatients[idx].amount = val === '' ? null : val;
  if (val !== '' && isNaN(Number(val))) {
    input.classList.add('invalid');
    input.classList.remove('low-conf');
  } else {
    input.classList.remove('invalid');
  }
}

function toggleCrossedOut(idx, checked) {
  currentPatients[idx].crossed_out = checked;
  const row = document.getElementById(`row-${idx}`);
  row.classList.toggle('crossed-row', checked);
}

function deleteRow(idx) {
  currentPatients.splice(idx, 1);
  renderTable();
}

function addRow() {
  currentPatients.push({
    serial: currentPatients.length + 1,
    name: '', age: '', gender: 'M',
    tests: '', amount: null,
    crossed_out: false,
    confidence: { name: 1, age: 1, gender: 1, tests: 1, amount: 1 }
  });
  renderTable();
  // Focus new row name field
  setTimeout(() => {
    const rows = document.querySelectorAll('#reviewTableBody tr');
    const lastRow = rows[rows.length - 1];
    if (lastRow) lastRow.querySelector('.cell-input')?.focus();
  }, 50);
}

// ── Date Preview ─────────────────────────────────────────────────────────────
async function updateDatePreview() {
  const raw = document.getElementById('extractedDate').value.trim();
  if (!raw) {
    document.getElementById('formattedDatePreview').textContent = '';
    return;
  }
  try {
    const res = await fetch('/api/format-date', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ date: raw })
    });
    const data = await res.json();
    document.getElementById('formattedDatePreview').textContent = '→ ' + data.formatted;
  } catch (e) {
    document.getElementById('formattedDatePreview').textContent = '';
  }
}

// ── Append to Sheets ─────────────────────────────────────────────────────────
async function appendToSheet() {
  const sheetId = document.getElementById('sheetId').value.trim();
  const sheetName = document.getElementById('sheetName').value.trim() || 'Sheet1';
  const date = document.getElementById('extractedDate').value.trim();

  if (!sheetId) {
    showToast('⚠️ Please enter the Google Sheet ID', 'error');
    document.getElementById('sheetId').focus();
    return;
  }
  if (!date) {
    showToast('⚠️ Date is empty — please fill it in', 'error');
    document.getElementById('extractedDate').focus();
    return;
  }

  // Validate amounts
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

  // Filter: only non-crossed-out patients
  const toAppend = currentPatients.filter(p => !p.crossed_out);
  if (toAppend.length === 0) {
    showToast('⚠️ All entries are marked as skipped — nothing to append', 'error');
    return;
  }

  // Normalize amounts
  const patients = toAppend.map(p => ({
    name: p.name,
    age: p.age,
    gender: p.gender,
    tests: p.tests,
    amount: p.amount !== null && p.amount !== '' ? Number(p.amount) : null
  }));

  showLoading('Appending to Google Sheet...');

  try {
    const res = await fetch('/api/append', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sheet_id: sheetId, sheet_name: sheetName, date, patients })
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
