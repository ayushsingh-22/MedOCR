/**
 * Service Worker for MedOCR PWA
 * Cache-first for static assets, network-first for API calls.
 */

const CACHE_NAME = 'medocr-v1';
const STATIC_ASSETS = [
  '/',
  '/static/style.css',
  '/static/app.js',
  '/static/icon-192.png',
  '/static/icon-512.png',
  '/static/manifest.json'
];

// Install — cache static shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS))
  );
  self.skipWaiting();
});

// Activate — clean old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch strategy
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // API calls → network-first
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/auth/')) {
    event.respondWith(
      fetch(event.request).catch(() =>
        new Response(JSON.stringify({ error: 'You are offline.' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' }
        })
      )
    );
    return;
  }

  // Static assets → cache-first
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        // Cache new static resources
        if (response.ok && event.request.method === 'GET') {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return response;
      }).catch(() => {
        // Offline fallback for navigation requests
        if (event.request.mode === 'navigate') {
          return new Response(
            `<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>MedOCR — Offline</title>
            <style>
              body{font-family:'Inter',system-ui,sans-serif;display:flex;align-items:center;justify-content:center;
              min-height:100vh;margin:0;background:#080814;color:#f0f0ff;text-align:center;padding:24px}
              .card{background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.09);border-radius:16px;
              padding:40px;max-width:400px}
              h2{margin-bottom:12px;font-size:22px}
              p{color:#8888aa;font-size:15px;line-height:1.6}
              button{margin-top:20px;padding:12px 28px;background:linear-gradient(135deg,#6366f1,#8b5cf6);
              color:#fff;border:none;border-radius:8px;font-size:15px;font-weight:600;cursor:pointer;font-family:inherit}
            </style></head><body>
            <div class="card">
              <h2>📡 You're Offline</h2>
              <p>MedOCR needs an internet connection to analyse images and sync with Google Sheets.</p>
              <button onclick="location.reload()">Try Again</button>
            </div></body></html>`,
            { headers: { 'Content-Type': 'text/html' } }
          );
        }
        return new Response('', { status: 408 });
      });
    })
  );
});
