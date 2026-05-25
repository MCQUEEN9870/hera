// Environment detection and API base URL management
// This file automatically detects the environment and sets appropriate URLs

// Detect if we're running locally or on live frontend
function isLocalEnvironment() {
    // Check if we're running from file:// protocol (local development)
    if (location.protocol === 'file:') return true;
    
    // Check if we're running on localhost
    if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') return true;
    
    // Check if we're running on LAN IP (local network)
    if (location.hostname.match(/^192\.168\.|^10\.|^172\.(1[6-9]|2[0-9]|3[0-1])\./)) return true;
    
    // If none of the above, assume we're on live frontend
    return false;
}

// Function to get the appropriate URL extension
function getUrlExtension() {
    return isLocalEnvironment() ? '.html' : '';
}

// Function to build URLs that work in both environments
function buildUrl(path) {
    const extension = getUrlExtension();
    // Remove leading slash if present
    const cleanPath = path.startsWith('/') ? path.substring(1) : path;

    // Preserve query/hash while normalizing the base path
    const hashIndex = cleanPath.indexOf('#');
    const queryIndex = cleanPath.indexOf('?');
    const cutIndex = (queryIndex === -1)
        ? hashIndex
        : (hashIndex === -1 ? queryIndex : Math.min(queryIndex, hashIndex));

    const basePath = (cutIndex === -1) ? cleanPath : cleanPath.substring(0, cutIndex);
    const suffix = (cutIndex === -1) ? '' : cleanPath.substring(cutIndex);
    const lowerBase = (basePath || '').toLowerCase();

    // Canonical public slug is /find-vehicles on live.
    // Local file:// development still uses vehicles.html.
    let mappedBase = basePath;
    if (isLocalEnvironment()) {
        if (lowerBase === 'find-vehicles') mappedBase = 'vehicles';
    } else {
        if (lowerBase === 'vehicles') mappedBase = 'find-vehicles';
    }

    // Add .html extension for local, none for live
    return extension ? `${mappedBase}${extension}${suffix}` : `${mappedBase}${suffix}`;
}

function normalizeApiBase(url) {
    if (!url || typeof url !== 'string') return '';
    return url.trim().replace(/\/+$/, '');
}

const TRUSTED_API_BASES_PROD = new Set([
    'https://api.herapherigoods.in'
]);

function computeDefaultApiBase() {
    if (location.protocol === 'file:') {
        return 'http://localhost:8080';
    }
    if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
        return 'http://localhost:8080';
    }
    if (location.hostname.match(/^192\.168\.|^10\.|^172\.(1[6-9]|2[0-9]|3[0-1])\./)) {
        return `http://${location.hostname}:8080`;
    }
    return 'https://api.herapherigoods.in';
}

function isTrustedApiBaseForThisEnv(candidate) {
    const c = normalizeApiBase(candidate);
    if (!c) return false;
    // On live frontend, DO NOT allow overrides (prevents token exfiltration via ?api=...)
    if (!isLocalEnvironment()) {
        return TRUSTED_API_BASES_PROD.has(c);
    }
    // In local dev, allow localhost/lan http(s) bases.
    try {
        const u = new URL(c);
        const isHttp = u.protocol === 'http:' || u.protocol === 'https:';
        if (!isHttp) return false;
        const host = (u.hostname || '').toLowerCase();
        if (host === 'localhost' || host === '127.0.0.1') return true;
        if (host.match(/^192\.168\.|^10\.|^172\.(1[6-9]|2[0-9]|3[0-1])\./)) return true;
        return false;
    } catch (_e) {
        return false;
    }
}

// Set API base URL based on environment (initial guess)
let base = computeDefaultApiBase();

// Allow override ONLY in local environment
if (isLocalEnvironment()) {
    const urlParams = new URLSearchParams(location.search);
    const apiOverride = urlParams.get('api');
    if (apiOverride) {
        const candidate = (apiOverride === 'local') ? 'http://localhost:8080' : apiOverride;
        if (typeof candidate === 'string' && candidate.startsWith('http') && isTrustedApiBaseForThisEnv(candidate)) {
            base = normalizeApiBase(candidate);
        }
    }

    const storedApi = localStorage.getItem('apiBaseUrl');
    if (storedApi && isTrustedApiBaseForThisEnv(storedApi)) {
        base = normalizeApiBase(storedApi);
    }
}

// Set global API base URL
window.API_BASE_URL = normalizeApiBase(base);
console.info('[env] API base in use:', window.API_BASE_URL, '(origin:', location.origin || location.protocol + '//' + location.host, ')');

// Helper functions for URL management
window.setApiBase = function(url) {
    const next = normalizeApiBase(url);
    if (!isLocalEnvironment()) {
        console.warn('[env] Refusing to set API base on live frontend');
        return;
    }
    if (!isTrustedApiBaseForThisEnv(next)) {
        console.warn('[env] Refusing untrusted API base:', url);
        return;
    }
    localStorage.setItem('apiBaseUrl', next);
    window.API_BASE_URL = next;
    console.info('[env] API base updated to:', next);
};

window.clearApiBase = function() {
    localStorage.removeItem('apiBaseUrl');
    location.reload();
};

// Token storage helper
// Persist token in localStorage so login survives browser close (up to JWT expiry).
// Auto-clears token + login flags if the JWT is expired/invalid.
(function initAuthToken(){
    function b64UrlToString(input){
        if (!input) return '';
        // base64url -> base64
        let s = String(input).replace(/-/g, '+').replace(/_/g, '/');
        // pad
        while (s.length % 4) s += '=';
        try { return atob(s); } catch (_e) { return ''; }
    }

    function getJwtExpSeconds(token){
        try {
            const parts = String(token || '').split('.');
            if (parts.length !== 3) return 0;
            const payloadJson = b64UrlToString(parts[1]);
            if (!payloadJson) return 0;
            const payload = JSON.parse(payloadJson);
            const exp = Number(payload && payload.exp);
            return Number.isFinite(exp) ? exp : 0;
        } catch (_e) {
            return 0;
        }
    }

    function isJwtExpired(token){
        const exp = getJwtExpSeconds(token);
        if (!exp) return true;
        const nowSec = Math.floor(Date.now() / 1000);
        return nowSec >= exp;
    }

    function clearLoginFlags(){
        try { localStorage.removeItem('isLoggedIn'); } catch (_e) {}
        try { localStorage.removeItem('userPhone'); } catch (_e) {}
        try { localStorage.removeItem('userMembership'); } catch (_e) {}
        try { localStorage.removeItem('lastSelectedVehicleId'); } catch (_e) {}
    }

    function clearTokenOnly(){
        try { sessionStorage.removeItem('authToken'); } catch (_e) {}
        try { localStorage.removeItem('authToken'); } catch (_e) {}
    }

    window.AuthToken = {
        get: function(){
            // 1) sessionStorage (current tab)
            try {
                const s = sessionStorage.getItem('authToken');
                if (s) {
                    if (isJwtExpired(s)) {
                        clearTokenOnly();
                        clearLoginFlags();
                        return '';
                    }
                    return s;
                }
            } catch (_e) {}

            // 2) localStorage (persistent)
            try {
                const l = localStorage.getItem('authToken') || '';
                if (!l) {
                    // Stale UI state: logged-in flags exist but token is missing.
                    try {
                        if (localStorage.getItem('isLoggedIn') === 'true' || localStorage.getItem('userPhone')) {
                            clearLoginFlags();
                        }
                    } catch (_e0) {}
                    return '';
                }
                if (isJwtExpired(l)) {
                    clearTokenOnly();
                    clearLoginFlags();
                    return '';
                }
                // Rehydrate into sessionStorage for this tab
                try { sessionStorage.setItem('authToken', l); } catch (_e2) {}
                return l;
            } catch (_e) {
                return '';
            }
        },
        set: function(token){
            const t = (token || '').toString();
            if (!t) {
                clearTokenOnly();
                return;
            }
            try { sessionStorage.setItem('authToken', t); } catch (_e) {}
            try { localStorage.setItem('authToken', t); } catch (_e) {}
        },
        clear: function(){
            clearTokenOnly();
        }
    };

    // Run once on load so expired tokens don't leave UI stuck in a "logged in" state.
    try { window.AuthToken.get(); } catch (_e) {}
})();

// Export the URL building function globally
window.buildUrl = buildUrl;
window.isLocalEnvironment = isLocalEnvironment;

// Try to auto-detect a working API base on live by probing candidates
(async function autoDetectApiBase() {
    if (isLocalEnvironment()) return; // skip in local
    // Only consider API subdomain and current base; avoid picking the site origin by mistake
    const candidates = [
        window.API_BASE_URL,
        'https://api.herapherigoods.in'
    ].filter(Boolean);

    const check = async (b) => {
        const controller = new AbortController();
        const t = setTimeout(() => controller.abort(), 3000);
        try {
            // Probe a stable public endpoint that should exist
            const urls = [
                b.replace(/\/$/, '') + '/api/posts'
            ];
            
            for (const url of urls) {
                try {
                    const resp = await fetch(url, { method: 'GET', headers: { 'Accept': 'application/json' }, signal: controller.signal });
                    const ct = resp.headers.get('content-type') || '';
                    // Accept only JSON responses; HTML means it's the frontend site, not the API
                    if (resp && resp.ok && ct.includes('application/json')) {
                        clearTimeout(t);
                        return true;
                    }
                } catch (_e) {
                    // Try next URL
                }
            }
            clearTimeout(t);
            return false;
        } catch (_e) {
            clearTimeout(t);
            return false;
        }
    };

    for (const c of candidates) {
        if (await check(c)) {
            if (c !== window.API_BASE_URL) {
                window.setApiBase(c);
            }
            break;
        }
    }
})();

// Attach JWT auth token to API calls (if present)
(function patchFetchForAuth() {
    try {
        if (!window.fetch || window.__AUTH_FETCH_PATCHED__) return;
        window.__AUTH_FETCH_PATCHED__ = true;

        const originalFetch = window.fetch.bind(window);
        const base = (window.API_BASE_URL || '').replace(/\/+$/, '');
        const allowedOrigins = new Set();
        try {
            if (base) allowedOrigins.add(new URL(base).origin);
        } catch (_e) {}
        // Extra safety: on live frontend, only ever attach tokens to the production API origin.
        if (!isLocalEnvironment()) {
            allowedOrigins.clear();
            allowedOrigins.add('https://api.herapherigoods.in');
        }

        window.fetch = function(input, init) {
            try {
                const token = (window.AuthToken && typeof window.AuthToken.get === 'function')
                    ? window.AuthToken.get()
                    : (localStorage.getItem('authToken') || '');

                if (token && base) {
                    const url = (typeof input === 'string')
                        ? input
                        : (input && typeof input.url === 'string' ? input.url : '');

                    // Never attach tokens to untrusted origins
                    try {
                        const parsed = new URL(url, location.href);
                        if (!allowedOrigins.has(parsed.origin)) {
                            return originalFetch(input, init);
                        }
                    } catch (_e) {
                        // If URL can't be parsed, fail safe: don't attach token.
                        return originalFetch(input, init);
                    }

                    const isApiCall = url === base || url.startsWith(base + '/');
                    if (isApiCall) {
                        const nextInit = init ? { ...init } : {};
                        const headers = new Headers(nextInit.headers || (input && input.headers) || undefined);
                        if (!headers.has('Authorization')) {
                            headers.set('Authorization', 'Bearer ' + token);
                        }
                        nextInit.headers = headers;

                        if (typeof input === 'string') {
                            return originalFetch(input, nextInit);
                        }
                        // If it's a Request, clone it with merged headers
                        const req = new Request(input, nextInit);
                        return originalFetch(req);
                    }
                }
            } catch (_e) {
                // Fall through to default fetch
            }
            return originalFetch(input, init);
        };
    } catch (_e) {
        // no-op
    }
})();



