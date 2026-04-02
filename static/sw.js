/**
 * Service Worker for MedOCR PWA — v3
 * Strategy:
 *   - API/auth calls  → network-only (never cache)
 *   - Static assets   → stale-while-revalidate (serve cached, update in bg)
 *   - Navigation      → network-first with offline fallback
 */

const CACHE_NAME = 'medocr-v3'; // ← bump this whenever static assets change

const PRECACHE_ASSETS = [
  '/',
  '/static/style.css',
  '/static/app.js',
  '/static/icon-192.png',
  '/static/icon-512.png',
  // NOTE: do NOT precache /sw.js or /static/manifest.json —
  // they must always be fetched fresh from the network.
];

// ── Install: precache shell ──────────────────────────────────────────────────
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_ASSETS))
  );
  // Activate immediately — don't wait for old tabs to close
  self.skipWaiting();
});

// ── Activate: delete old caches ──────────────────────────────────────────────
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  // Take control of all existing clients immediately
  self.clients.claim();
});

// ── Message: handle SKIP_WAITING from app.js ─────────────────────────────────
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

// ── Fetch strategy ───────────────────────────────────────────────────────────
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // 1. API and auth routes → network-only, never cache
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/auth/')) {
    event.respondWith(
      fetch(event.request).catch(() =>
        new Response(JSON.stringify({ error: 'You are offline.' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        })
      )
    );
    return;
  }

  // 2. sw.js and manifest.json → always network (never cache)
  if (url.pathname === '/sw.js' || url.pathname === '/static/manifest.json') {
    event.respondWith(fetch(event.request));
    return;
  }

  // 3. Static assets → stale-while-revalidate
  //    Serve from cache instantly, then fetch fresh copy in background.
  event.respondWith(
    caches.open(CACHE_NAME).then(async (cache) => {
      const cached = await cache.match(event.request);

      // Kick off a network fetch in the background to update the cache
      const networkFetch = fetch(event.request)
        .then((networkResponse) => {
          if (networkResponse.ok && event.request.method === 'GET') {
            cache.put(event.request, networkResponse.clone());
          }
          return networkResponse;
        })
        .catch(() => null);

      // Return cached immediately if available, otherwise wait for network
      if (cached) {
        // Still revalidate in background (fire-and-forget)
        // eslint-disable-next-line no-unused-expressions
        networkFetch;
        return cached;
      }

      // No cached version — wait for network
      const networkResponse = await networkFetch;
      if (networkResponse) return networkResponse;

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
    })
  );
});
