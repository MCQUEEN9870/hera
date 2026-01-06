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

// Set API base URL based on environment (initial guess)
let base;
if (location.protocol === 'file:') {
    base = 'http://localhost:8080';
} else if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
    base = 'http://localhost:8080';
} else if (location.hostname.match(/^192\.168\.|^10\.|^172\.(1[6-9]|2[0-9]|3[0-1])\./)) {
    base = `http://${location.hostname}:8080`;
} else {
    base = 'https://api.herapherigoods.in';
}

// Check for URL parameter override
const urlParams = new URLSearchParams(location.search);
const apiOverride = urlParams.get('api');
if (apiOverride) {
    if (apiOverride === 'local') {
        base = 'http://localhost:8080';
    } else if (apiOverride.startsWith('http')) {
        base = apiOverride;
    }
}

// Check for localStorage override
const storedApi = localStorage.getItem('apiBaseUrl');
if (storedApi) {
    base = storedApi;
}

// Set global API base URL
window.API_BASE_URL = base;
console.info('[env] API base in use:', window.API_BASE_URL, '(origin:', location.origin || location.protocol + '//' + location.host, ')');

// Helper functions for URL management
window.setApiBase = function(url) {
    localStorage.setItem('apiBaseUrl', url);
    window.API_BASE_URL = url;
    console.info('[env] API base updated to:', url);
};

window.clearApiBase = function() {
    localStorage.removeItem('apiBaseUrl');
    location.reload();
};

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

        window.fetch = function(input, init) {
            try {
                const token = localStorage.getItem('authToken');
                if (token && base) {
                    const url = (typeof input === 'string')
                        ? input
                        : (input && typeof input.url === 'string' ? input.url : '');

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



