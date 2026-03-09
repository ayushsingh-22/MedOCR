/**
 * app.js - MedOCR Frontend Logic
 * Handles file upload, OCR trigger, review table, and Google Sheets append.
 */

// ── State ───────────────────────────────────────────────────────────────────
let selectedFile = null;
let currentPatients = []; // Array of patient objects in the review table

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

// API key toggle
document.addEventListener('DOMContentLoaded', () => {
  checkAuthStatus();

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

// ── File Upload ──────────────────────────────────────────────────────────────
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
  const file = e.dataTransfer.files[0];
  if (file) setFile(file);
}
function handleFileSelect(e) {
  const file = e.target.files[0];
  if (file) setFile(file);
}

function setFile(file) {
  selectedFile = file;
  const reader = new FileReader();
  reader.onload = (e) => {
    document.getElementById('previewImg').src = e.target.result;
    document.getElementById('previewFileName').textContent = file.name;
    document.getElementById('uploadZone').style.display = 'none';
    document.getElementById('previewSection').style.display = 'flex';
  };
  reader.readAsDataURL(file);
}

function clearUpload() {
  selectedFile = null;
  document.getElementById('fileInput').value = '';
  document.getElementById('uploadZone').style.display = '';
  document.getElementById('previewSection').style.display = 'none';
  document.getElementById('stepReview').style.display = 'none';
}

// ── Analyse ──────────────────────────────────────────────────────────────────
async function analyseImage() {
  const apiKey = document.getElementById('geminiApiKey').value.trim();
  // No frontend block — backend falls back to GEMINI_API_KEY env var if field is empty
  if (!selectedFile) {
    showToast('⚠️ No image selected', 'error');
    return;
  }

  showLoading('Analysing handwriting with Gemini AI...');

  const formData = new FormData();
  formData.append('image', selectedFile);
  formData.append('api_key', apiKey);

  try {
    const res = await fetch('/api/upload', { method: 'POST', body: formData });
    const data = await res.json();
    hideLoading();

    if (!res.ok || data.error) {
      showToast('❌ ' + (data.error || 'OCR failed'), 'error');
      return;
    }

    // Populate review section
    populateReviewSection(data);
    showToast(`✅ Extracted ${data.patients.length} patient entries`, 'success');
  } catch (err) {
    hideLoading();
    showToast('❌ Network error: ' + err.message, 'error');
  }
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
               placeholder="Age" style="width:52px" />
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
               placeholder="Amount" />
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
