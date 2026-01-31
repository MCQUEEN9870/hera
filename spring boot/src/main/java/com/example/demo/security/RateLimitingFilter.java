package com.example.demo.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import com.example.demo.util.ClientIpUtil;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ===== Auth / OTP limits (defaults tuned to reduce abuse without breaking normal users) =====
    @Value("${app.ratelimit.auth.login.ip.limit:10}")
    private int loginIpLimit;
    @Value("${app.ratelimit.auth.login.ip.window-seconds:300}")
    private int loginIpWindowSeconds;
    @Value("${app.ratelimit.auth.login.contact.limit:5}")
    private int loginContactLimit;
    @Value("${app.ratelimit.auth.login.contact.window-seconds:300}")
    private int loginContactWindowSeconds;

    @Value("${app.ratelimit.auth.check-user.ip.limit:30}")
    private int checkUserIpLimit;
    @Value("${app.ratelimit.auth.check-user.ip.window-seconds:60}")
    private int checkUserIpWindowSeconds;

    @Value("${app.ratelimit.auth.signup.ip.limit:10}")
    private int signupIpLimit;
    @Value("${app.ratelimit.auth.signup.ip.window-seconds:600}")
    private int signupIpWindowSeconds;
    @Value("${app.ratelimit.auth.signup.contact.limit:3}")
    private int signupContactLimit;
    @Value("${app.ratelimit.auth.signup.contact.window-seconds:600}")
    private int signupContactWindowSeconds;

    @Value("${app.ratelimit.auth.otp.verify.ip.limit:25}")
    private int otpVerifyIpLimit;
    @Value("${app.ratelimit.auth.otp.verify.ip.window-seconds:300}")
    private int otpVerifyIpWindowSeconds;
    @Value("${app.ratelimit.auth.otp.verify.contact.limit:10}")
    private int otpVerifyContactLimit;
    @Value("${app.ratelimit.auth.otp.verify.contact.window-seconds:300}")
    private int otpVerifyContactWindowSeconds;

    @Value("${app.ratelimit.payments.create-order.ip.limit:10}")
    private int createOrderIpLimit;
    @Value("${app.ratelimit.payments.create-order.ip.window-seconds:60}")
    private int createOrderIpWindowSeconds;

    @Value("${app.ratelimit.payments.verify.ip.limit:30}")
    private int verifyPaymentIpLimit;
    @Value("${app.ratelimit.payments.verify.ip.window-seconds:60}")
    private int verifyPaymentIpWindowSeconds;

    @Value("${app.ratelimit.trust.ip.limit:30}")
    private int trustIpLimit;
    @Value("${app.ratelimit.trust.ip.window-seconds:60}")
    private int trustIpWindowSeconds;

    @Value("${app.ratelimit.feedback.ip.limit:10}")
    private int feedbackIpLimit;
    @Value("${app.ratelimit.feedback.ip.window-seconds:300}")
    private int feedbackIpWindowSeconds;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        ContentCachingRequestWrapper wrapped = (request instanceof ContentCachingRequestWrapper)
            ? (ContentCachingRequestWrapper) request
            : new ContentCachingRequestWrapper(request);

        String method = wrapped.getMethod();
        String path = wrapped.getRequestURI();

        RateDecision decision = evaluate(wrapped, method, path);
        if (decision.blocked) {
            writeTooManyRequests(response, decision.retryAfterSeconds);
            return;
        }

        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            // Best-effort cleanup to avoid unbounded memory growth
            cleanupOldEntries();
        }
    }

    private RateDecision evaluate(HttpServletRequest request, String method, String path) {
        // Only apply to specific high-risk endpoints
        if (HttpMethod.POST.matches(method)) {
            // Auth: login + OTP flows
            if (PATH_MATCHER.match("/auth/login", path)) {
                return enforceIpAndContact(request, "auth:login", loginIpLimit, loginIpWindowSeconds, loginContactLimit, loginContactWindowSeconds);
            }
            if (PATH_MATCHER.match("/auth/check-user", path)) {
                return enforceIpOnly(request, "auth:check-user", checkUserIpLimit, checkUserIpWindowSeconds);
            }
            if (PATH_MATCHER.match("/auth/signup", path) || PATH_MATCHER.match("/auth/signup-direct", path)) {
                return enforceIpAndContact(request, "auth:signup", signupIpLimit, signupIpWindowSeconds, signupContactLimit, signupContactWindowSeconds);
            }
            if (PATH_MATCHER.match("/auth/forgot-init", path)) {
                // Same pattern as signup (OTP send)
                return enforceIpAndContact(request, "auth:forgot-init", signupIpLimit, signupIpWindowSeconds, signupContactLimit, signupContactWindowSeconds);
            }

            // Email OTP password reset (Brevo)
            if (PATH_MATCHER.match("/auth/forgot-email-init", path)) {
                // Email OTP send: treat like signup/forgot-init to prevent spamming
                return enforceIpAndContact(request, "auth:forgot-email-init", signupIpLimit, signupIpWindowSeconds, signupContactLimit, signupContactWindowSeconds);
            }
            if (PATH_MATCHER.match("/auth/forgot-email-verify", path) || PATH_MATCHER.match("/auth/forgot-email-complete", path)) {
                // Email OTP verify + complete: treat like OTP verify endpoints
                return enforceIpAndContact(request, "auth:forgot-email-verify", otpVerifyIpLimit, otpVerifyIpWindowSeconds, otpVerifyContactLimit, otpVerifyContactWindowSeconds);
            }
            if (PATH_MATCHER.match("/auth/verify-otp", path) || PATH_MATCHER.match("/auth/forgot-verify", path) || PATH_MATCHER.match("/auth/forgot-complete", path)) {
                return enforceIpAndContact(request, "auth:otp-verify", otpVerifyIpLimit, otpVerifyIpWindowSeconds, otpVerifyContactLimit, otpVerifyContactWindowSeconds);
            }

            // Payments: order creation endpoint (even though it requires auth, add IP throttling)
            if (PATH_MATCHER.match("/api/payments/create-order", path)) {
                return enforceIpOnly(request, "payments:create-order", createOrderIpLimit, createOrderIpWindowSeconds);
            }

            // Payments: signature verification endpoint (cheap but can be abused)
            if (PATH_MATCHER.match("/api/payments/verify", path)) {
                return enforceIpOnly(request, "payments:verify", verifyPaymentIpLimit, verifyPaymentIpWindowSeconds);
            }

            // Trust counter: prevent spam / manipulation
            if (PATH_MATCHER.match("/api/vehicles/update-trust-counter/**", path)) {
                return enforceIpOnly(request, "vehicles:trust", trustIpLimit, trustIpWindowSeconds);
            }

            // Feedback endpoints: prevent spam
            if (PATH_MATCHER.match("/api/save-feedback", path) || PATH_MATCHER.match("/api/save-user-feedback", path)) {
                return enforceIpOnly(request, "feedback:submit", feedbackIpLimit, feedbackIpWindowSeconds);
            }
        }
        return RateDecision.allow();
    }

    private RateDecision enforceIpOnly(HttpServletRequest request, String bucket, int limit, int windowSeconds) {
        String ip = clientIp(request);
        if (!StringUtils.hasText(ip)) {
            ip = "unknown";
        }
        return checkAndConsume(bucket + ":ip:" + ip, limit, windowSeconds);
    }

    private RateDecision enforceIpAndContact(
        HttpServletRequest request,
        String bucket,
        int ipLimit,
        int ipWindowSeconds,
        int contactLimit,
        int contactWindowSeconds
    ) {
        RateDecision ipDecision = enforceIpOnly(request, bucket, ipLimit, ipWindowSeconds);
        if (ipDecision.blocked) {
            return ipDecision;
        }

        Optional<String> contactNumber = extractContactNumber(request);
        if (contactNumber.isPresent()) {
            RateDecision contactDecision = checkAndConsume(bucket + ":contact:" + contactNumber.get(), contactLimit, contactWindowSeconds);
            if (contactDecision.blocked) {
                return contactDecision;
            }
        }
        return RateDecision.allow();
    }

    private RateDecision checkAndConsume(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = Duration.ofSeconds(windowSeconds).toMillis();

        WindowCounter counter = counters.compute(key, (_k, existing) -> {
            if (existing == null) {
                return new WindowCounter(now, 1, now);
            }
            if (now - existing.windowStartMs >= windowMs) {
                existing.windowStartMs = now;
                existing.count = 1;
                existing.lastSeenMs = now;
                return existing;
            }
            existing.count++;
            existing.lastSeenMs = now;
            return existing;
        });

        if (counter.count > limit) {
            long windowEnd = counter.windowStartMs + windowMs;
            long retryAfterMs = Math.max(0, windowEnd - now);
            int retryAfterSeconds = (int) Math.ceil(retryAfterMs / 1000.0);
            return RateDecision.block(retryAfterSeconds);
        }
        return RateDecision.allow();
    }

    private Optional<String> extractContactNumber(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE)) {
            return Optional.empty();
        }

        if (!(request instanceof ContentCachingRequestWrapper wrapped)) {
            return Optional.empty();
        }
        byte[] bytes = wrapped.getContentAsByteArray();
        if (bytes == null || bytes.length == 0) {
            // Body may not be cached yet; nothing we can do safely.
            return Optional.empty();
        }
        try {
            String body = new String(bytes, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(body)) {
                return Optional.empty();
            }
            JsonNode node = OBJECT_MAPPER.readTree(body);
            JsonNode contact = node.get("contactNumber");
            if (contact != null && contact.isTextual()) {
                String v = contact.asText();
                if (StringUtils.hasText(v)) {
                    return Optional.of(v.trim());
                }
            }
        } catch (Exception _e) {
            // ignore
        }
        return Optional.empty();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = ClientIpUtil.getClientIp(request);
        return StringUtils.hasText(ip) ? ip : request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (retryAfterSeconds > 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        }
        response.getWriter().write("{\"message\":\"Too Many Requests\"}");
    }

    private void cleanupOldEntries() {
        // Remove entries not seen for 30 minutes.
        long cutoff = System.currentTimeMillis() - Duration.ofMinutes(30).toMillis();
        if (counters.size() < 10_000) {
            // Keep it cheap on normal traffic.
            return;
        }
        counters.entrySet().removeIf(e -> e.getValue() == null || e.getValue().lastSeenMs < cutoff);
    }

    private static final class WindowCounter {
        volatile long windowStartMs;
        volatile int count;
        volatile long lastSeenMs;

        WindowCounter(long windowStartMs, int count, long lastSeenMs) {
            this.windowStartMs = windowStartMs;
            this.count = count;
            this.lastSeenMs = lastSeenMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(windowStartMs, count, lastSeenMs);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WindowCounter other)) return false;
            return windowStartMs == other.windowStartMs && count == other.count && lastSeenMs == other.lastSeenMs;
        }
    }

    private static final class RateDecision {
        final boolean blocked;
        final int retryAfterSeconds;

        private RateDecision(boolean blocked, int retryAfterSeconds) {
            this.blocked = blocked;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        static RateDecision allow() {
            return new RateDecision(false, 0);
        }

        static RateDecision block(int retryAfterSeconds) {
            return new RateDecision(true, Math.max(0, retryAfterSeconds));
        }
    }
}

