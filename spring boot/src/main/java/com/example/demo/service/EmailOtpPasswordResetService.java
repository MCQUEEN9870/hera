package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

@Service
public class EmailOtpPasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpPasswordResetService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository userRepository;
    private final NotificationEmailService notificationEmailService;

    @Value("${security.password-reset.email-otp.enabled:true}")
    private boolean enabled;

    @Value("${security.password-reset.email-otp.ttl-seconds:60}")
    private int otpTtlSeconds;

    @Value("${security.password-reset.email-otp.reset-token-ttl-minutes:10}")
    private int resetTokenTtlMinutes;

    @Value("${security.password-reset.email-otp.pepper:CHANGE_ME_IN_PROD}")
    private String pepper;

    @Value("${security.password-reset.email-otp.max-attempts:15}")
    private int maxAttempts;

    public EmailOtpPasswordResetService(UserRepository userRepository, NotificationEmailService notificationEmailService) {
        this.userRepository = userRepository;
        this.notificationEmailService = notificationEmailService;
    }

    public Map<String, Object> init(String contactNumber, String emailRaw) {
        if (!enabled) return Map.of("ok", false, "message", "Email OTP is disabled");
        if (contactNumber == null || !contactNumber.matches("^[6-9]\\d{9}$")) {
            return Map.of("ok", false, "message", "Please enter a valid 10-digit mobile number.");
        }

        String email = normalizeEmail(emailRaw);
        if (email.isEmpty() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return Map.of("ok", false, "message", "Please enter a valid email address.");
        }

        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) {
            // Keep response consistent with existing flow
            return Map.of("ok", false, "message", "User not found.");
        }

        int attempts = user.getEmailOtpAttempts() == null ? 0 : user.getEmailOtpAttempts();
        if (attempts >= Math.max(5, maxAttempts)) {
            return Map.of("ok", false, "message", "Too many attempts. Please try again later.");
        }

        String code = String.format("%06d", RNG.nextInt(1_000_000));
        String codeHash = sha256Hex("EMAIL_OTP:" + pepper + ":" + contactNumber + ":" + email + ":" + code);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(Math.max(30, otpTtlSeconds));

        boolean sent = notificationEmailService.trySendPasswordResetEmailOtp(email, user.getFullName(), code, otpTtlSeconds);
        if (!sent) {
            log.warn("Email OTP send failed (maskedContact={}, to={})", maskPhone(contactNumber), maskEmail(email));
            return Map.of("ok", false, "message", "Failed to send OTP to email. Please try again.");
        }

        // Persist state on user table only AFTER send success
        user.setOtpEmail(email);
        user.setEmailOtpCodeHash(codeHash);
        user.setEmailOtpExpiresAt(expiresAt);
        user.setEmailOtpAttempts(attempts + 1); // counts sends/resends (and later verify attempts)
        user.setEmailOtpVerifiedAt(null);
        user.setEmailResetTokenHash(null);
        user.setEmailResetTokenExpiresAt(null);
        userRepository.save(user);

        return Map.of("ok", true, "message", "OTP sent to email");
    }

    public Map<String, Object> verify(String contactNumber, String emailRaw, String codeRaw) {
        if (!enabled) return Map.of("ok", false, "message", "Email OTP is disabled");
        if (contactNumber == null || !contactNumber.matches("^[6-9]\\d{9}$")) {
            return Map.of("ok", false, "message", "Invalid request");
        }

        String email = normalizeEmail(emailRaw);
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (email.isEmpty() || !code.matches("^\\d{6}$")) {
            return Map.of("ok", false, "message", "Invalid email or code");
        }

        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) return Map.of("ok", false, "message", "Invalid or expired code");

        // Verify email matches last requested email
        if (user.getOtpEmail() == null || !user.getOtpEmail().equalsIgnoreCase(email)) {
            return Map.of("ok", false, "message", "Invalid or expired code");
        }

        Integer attempts = user.getEmailOtpAttempts() == null ? 0 : user.getEmailOtpAttempts();
        int max = Math.max(5, maxAttempts);
        if (attempts >= max) return Map.of("ok", false, "message", "Too many attempts. Please resend.");

        // Count every verify attempt as well (helps audit/abuse detection)
        attempts = attempts + 1;
        user.setEmailOtpAttempts(attempts);
        userRepository.save(user);

        if (user.getEmailOtpExpiresAt() == null || user.getEmailOtpExpiresAt().isBefore(LocalDateTime.now())) {
            return Map.of("ok", false, "message", "Code expired. Please resend.");
        }

        String expectedHash = user.getEmailOtpCodeHash();
        String actualHash = sha256Hex("EMAIL_OTP:" + pepper + ":" + contactNumber + ":" + email + ":" + code);
        if (expectedHash == null || !constantTimeEquals(expectedHash, actualHash)) {
            return Map.of("ok", false, "message", "Invalid or expired code");
        }

        String resetToken = generateResetToken();
        String resetTokenHash = sha256Hex("RESET_TOKEN:" + pepper + ":" + contactNumber + ":" + email + ":" + resetToken);
        LocalDateTime resetExpiresAt = LocalDateTime.now().plusMinutes(Math.max(2, resetTokenTtlMinutes));

        user.setEmailOtpVerifiedAt(LocalDateTime.now());
        user.setEmailResetTokenHash(resetTokenHash);
        user.setEmailResetTokenExpiresAt(resetExpiresAt);
        userRepository.save(user);

        return Map.of("ok", true, "resetToken", resetToken);
    }

    public Map<String, Object> complete(String contactNumber, String emailRaw, String resetTokenRaw, String newPassword) {
        if (!enabled) return Map.of("ok", false, "message", "Email OTP is disabled");
        if (contactNumber == null || !contactNumber.matches("^[6-9]\\d{9}$")) {
            return Map.of("ok", false, "message", "Invalid request");
        }

        String email = normalizeEmail(emailRaw);
        String resetToken = resetTokenRaw == null ? "" : resetTokenRaw.trim();
        String pw = newPassword == null ? "" : newPassword.trim();
        if (email.isEmpty() || resetToken.isEmpty() || pw.length() < 4) {
            return Map.of("ok", false, "message", "Invalid request");
        }

        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) return Map.of("ok", false, "message", "Invalid or expired reset token");

        if (user.getOtpEmail() == null || !user.getOtpEmail().equalsIgnoreCase(email)) {
            return Map.of("ok", false, "message", "Invalid or expired reset token");
        }

        if (user.getEmailResetTokenExpiresAt() == null || user.getEmailResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            return Map.of("ok", false, "message", "Reset token expired. Please resend OTP.");
        }

        String expectedHash = user.getEmailResetTokenHash();
        String actualHash = sha256Hex("RESET_TOKEN:" + pepper + ":" + contactNumber + ":" + email + ":" + resetToken);
        if (expectedHash == null || !constantTimeEquals(expectedHash, actualHash)) {
            return Map.of("ok", false, "message", "Invalid or expired reset token");
        }

        try {
            user.setPasswordHash(new BCryptPasswordEncoder().encode(pw));
        } catch (Exception e) {
            return Map.of("ok", false, "message", "Failed to set password");
        }

        // Clear email OTP state
        user.setEmailOtpCodeHash(null);
        user.setEmailOtpExpiresAt(null);
        user.setEmailOtpAttempts(0);
        user.setEmailResetTokenHash(null);
        user.setEmailResetTokenExpiresAt(null);
        userRepository.save(user);

        return Map.of("ok", true, "message", "Password reset successful");
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String maskPhone(String phone) {
        if (phone == null) return "<null>";
        String normalized = phone.trim();
        if (normalized.length() <= 4) return "****";
        return "******" + normalized.substring(normalized.length() - 4);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "<null>";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String generateResetToken() {
        byte[] bytes = new byte[24];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failure", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
