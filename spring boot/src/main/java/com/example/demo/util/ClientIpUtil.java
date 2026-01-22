package com.example.demo.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility to resolve the real client IP when running behind reverse proxies (e.g., Koyeb).
 *
 * Note: IP addresses can be spoofed via headers if the service is exposed directly.
 * In Koyeb deployment, requests typically pass through a trusted proxy that sets these headers.
 */
public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return null;

        // RFC 7239: Forwarded: for=1.2.3.4;proto=https;host=example.com
        String forwarded = safeHeader(request, "Forwarded");
        String fromForwarded = parseForwardedFor(forwarded);
        if (hasText(fromForwarded)) return fromForwarded;

        // De-facto standard: X-Forwarded-For: client, proxy1, proxy2
        String xff = safeHeader(request, "X-Forwarded-For");
        String fromXff = firstCommaSeparatedToken(xff);
        if (hasText(fromXff)) return normalizeIpToken(fromXff);

        String xRealIp = safeHeader(request, "X-Real-IP");
        if (hasText(xRealIp)) return normalizeIpToken(xRealIp);

        try {
            return request.getRemoteAddr();
        } catch (Exception _e) {
            return null;
        }
    }

    private static String parseForwardedFor(String forwarded) {
        if (!hasText(forwarded)) return null;

        // Forwarded can contain multiple entries separated by ','
        // We want the first 'for=' in the first entry.
        String firstEntry = firstCommaSeparatedToken(forwarded);
        if (!hasText(firstEntry)) return null;

        String[] parts = firstEntry.split(";");
        for (String p : parts) {
            if (p == null) continue;
            String part = p.trim();
            if (part.length() < 4) continue;
            if (part.regionMatches(true, 0, "for=", 0, 4)) {
                String v = part.substring(4).trim();
                return normalizeIpToken(v);
            }
        }
        return null;
    }

    private static String firstCommaSeparatedToken(String value) {
        if (!hasText(value)) return null;
        int idx = value.indexOf(',');
        String first = idx >= 0 ? value.substring(0, idx) : value;
        return first != null ? first.trim() : null;
    }

    private static String normalizeIpToken(String raw) {
        if (!hasText(raw)) return null;
        String v = raw.trim();

        // Remove optional quotes
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            v = v.substring(1, v.length() - 1).trim();
        }

        // Forwarded header can include obfuscated identifiers like: for=_hidden
        if (!hasText(v) || v.startsWith("_")) return null;

        // Remove IPv6 brackets: [2001:db8::1]
        if (v.startsWith("[") && v.contains("]")) {
            int end = v.indexOf(']');
            String inside = v.substring(1, end);
            return hasText(inside) ? inside : null;
        }

        // If it's IPv4 with port: 1.2.3.4:12345
        // Only strip port when it clearly looks like IPv4.
        int colon = v.lastIndexOf(':');
        int dot = v.indexOf('.');
        if (dot >= 0 && colon > dot) {
            String maybeIp = v.substring(0, colon);
            if (hasText(maybeIp)) {
                return maybeIp;
            }
        }

        return v;
    }

    private static String safeHeader(HttpServletRequest request, String name) {
        try {
            String v = request.getHeader(name);
            return hasText(v) ? v.trim() : null;
        } catch (Exception _e) {
            return null;
        }
    }

    private static boolean hasText(String v) {
        return v != null && !v.trim().isEmpty();
    }
}
