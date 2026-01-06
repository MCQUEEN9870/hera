package com.example.demo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class JwtService {

    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DEC = Base64.getUrlDecoder();

    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.jwt.secret:}")
    private String configuredSecret;

    @Value("${app.jwt.ttl-seconds:2592000}")
    private long ttlSeconds;

    private volatile byte[] secretBytes;

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    private boolean isProdProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) return true;
        }
        return false;
    }

    private byte[] getSecretBytes() {
        if (secretBytes != null) return secretBytes;
        synchronized (this) {
            if (secretBytes != null) return secretBytes;

            boolean isProd = isProdProfile();
            if (configuredSecret != null && !configuredSecret.isBlank()) {
                secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
                return secretBytes;
            }

            if (isProd) {
                throw new IllegalStateException("JWT secret missing in production. Set 'app.jwt.secret' (env: JWT_SECRET).");
            }

            // Dev fallback: ephemeral secret (tokens invalidate on restart)
            byte[] tmp = new byte[32];
            new SecureRandom().nextBytes(tmp);
            secretBytes = tmp;
            return secretBytes;
        }
    }

    public String issueTokenForContact(String contactNumber) {
        if (contactNumber == null || contactNumber.isBlank()) {
            throw new IllegalArgumentException("contactNumber is required");
        }

        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofSeconds(Math.max(60, ttlSeconds)));

        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
            "sub", contactNumber,
            "iat", now.getEpochSecond(),
            "exp", exp.getEpochSecond()
        );

        byte[] headerJson;
        byte[] payloadJson;
        try {
            headerJson = objectMapper.writeValueAsBytes(header);
            payloadJson = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JWT", e);
        }

        String h = B64_URL.encodeToString(headerJson);
        String p = B64_URL.encodeToString(payloadJson);
        String signingInput = h + "." + p;
        String sig = B64_URL.encodeToString(hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), getSecretBytes()));
        return signingInput + "." + sig;
    }

    /**
     * @return contactNumber (subject) if valid, otherwise null
     */
    public String verifyAndGetSubjectOrNull(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSig = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), getSecretBytes());

        byte[] providedSig;
        try {
            providedSig = B64_URL_DEC.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (!MessageDigest.isEqual(expectedSig, providedSig)) return null;

        byte[] payloadBytes;
        try {
            payloadBytes = B64_URL_DEC.decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }

        Object sub = payload.get("sub");
        Object exp = payload.get("exp");
        if (!(sub instanceof String) || ((String) sub).isBlank()) return null;

        long expSec = toLong(exp);
        if (expSec <= 0) return null;
        if (Instant.now().getEpochSecond() > expSec) return null;

        return (String) sub;
    }

    private static long toLong(Object v) {
        if (v == null) return -1;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return -1;
        }
    }

    private static byte[] hmacSha256(byte[] data, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}
