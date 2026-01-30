package com.example.demo.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);


    private enum Provider {
        AUTO,
        BREVO,
        SMTP
    }

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${mail.enabled:false}")
    private boolean enabled;

    /**
     * Supported: auto | brevo | smtp
     */
    @Value("${mail.provider:auto}")
    private String provider;

    // Brevo
    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.api.base-url:https://api.brevo.com/v3}")
    private String brevoBaseUrl;

    @Value("${mail.from.email:info@herapherigoods.in}")
    private String fromEmail;

    @Value("${mail.from.name:Herapherigoods Team}")
    private String fromName;

    // SMTP
    @Value("${mail.from:}")
    private String smtpFrom;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean sendPlainText(String to, String subject, String text) {
        return send(to, subject, text, false, null);
    }

    public boolean sendHtml(String to, String subject, String html) {
        return send(to, subject, html, true, null);
    }

    public boolean sendHtmlWithReplyTo(String to, String subject, String html, String replyTo) {
        return send(to, subject, html, true, replyTo);
    }

    private boolean send(String to, String subject, String body, boolean isHtml, String replyTo) {
        if (!enabled) {
            log.info("Email disabled; skipping send (to={}, subject={})", maskEmail(to), subject);
            return false;
        }
        if (to == null || to.isBlank()) {
            log.warn("Email send skipped: recipient missing (subject={})", subject);
            return false;
        }

        Provider selected = parseProvider(provider);
        if (selected == Provider.AUTO) {
            selected = autoSelectProvider();
        }

        return switch (selected) {
            case BREVO -> sendViaBrevo(to, subject, body, isHtml, replyTo);
            case SMTP -> sendViaSmtp(to, subject, body, isHtml, replyTo);
            case AUTO -> throw new IllegalStateException("Unexpected provider state");
        };
    }

    private Provider parseProvider(String raw) {
        if (raw == null || raw.isBlank()) return Provider.AUTO;
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "auto" -> Provider.AUTO;
            case "brevo" -> Provider.BREVO;
            case "smtp" -> Provider.SMTP;
            default -> throw new IllegalArgumentException("Unknown mail.provider: " + raw);
        };
    }

    private Provider autoSelectProvider() {
        if (brevoApiKey != null && !brevoApiKey.isBlank()) return Provider.BREVO;
        JavaMailSender smtp = mailSenderProvider.getIfAvailable();
        if (smtp != null) return Provider.SMTP;
        throw new IllegalStateException("No email provider configured: set BREVO_API_KEY or configure spring.mail.* and set mail.provider");
    }

    private boolean sendViaBrevo(String toEmail, String subject, String body, boolean isHtml, String replyTo) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException("brevo.api.key missing");
        }

        RestTemplate rt = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);
        headers.set("accept", "application/json");

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of(
            "email", safeText(fromEmail, "info@herapherigoods.in"),
            "name", safeText(fromName, "Herapherigoods Team")
        ));
        payload.put("to", List.of(Map.of("email", toEmail)));
        payload.put("subject", subject == null ? "" : subject);

        if (isHtml) {
            payload.put("htmlContent", body == null ? "" : body);
        } else {
            payload.put("textContent", body == null ? "" : body);
        }

        if (replyTo != null && !replyTo.isBlank()) {
            payload.put("replyTo", Map.of("email", replyTo.trim()));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        String url = (brevoBaseUrl == null || brevoBaseUrl.isBlank()) ? "https://api.brevo.com/v3" : brevoBaseUrl.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        url = url + "/smtp/email";

        try {
            ResponseEntity<String> resp = rt.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Brevo send failed: status=" + resp.getStatusCode() + ", body=" + resp.getBody());
            }
            log.info("Brevo send ok (to={}, subject={})", maskEmail(toEmail), subject);
            return true;
        } catch (HttpStatusCodeException ex) {
            String respBody = ex.getResponseBodyAsString();
            throw new IllegalStateException("Brevo send failed: status=" + ex.getStatusCode() + ", body=" + respBody, ex);
        }
    }

    private boolean sendViaSmtp(String to, String subject, String body, boolean isHtml, String replyTo) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("SMTP mail sender not configured (missing JavaMailSender / spring.mail.*)");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject == null ? "" : subject);

            String from = (smtpFrom != null && !smtpFrom.isBlank()) ? smtpFrom : fromEmail;
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            helper.setText(body == null ? "" : body, isHtml);
            mailSender.send(message);

            log.info("SMTP send ok (to={}, subject={})", maskEmail(to), subject);
            return true;
        } catch (Exception e) {
            String smtpMeta = "";
            try {
                if (mailSender instanceof JavaMailSenderImpl impl) {
                    smtpMeta = String.format(" (smtpHost=%s, smtpPort=%s, smtpUser=%s)",
                        safe(impl.getHost()),
                        impl.getPort(),
                        maskEmail(impl.getUsername()));
                }
            } catch (Exception _ignored) {
                // ignore
            }
            log.error("SMTP send failed (to={}, subject={}): {}{}", maskEmail(to), subject, e.toString(), smtpMeta);
            throw new RuntimeException("Email send failed", e);
        }
    }

    private static String safeText(String value, String fallback) {
        if (value == null) return fallback;
        String v = value.trim();
        return v.isBlank() ? fallback : v;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "<null>";
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.substring(0, 1) + "***" + e.substring(at);
    }
}
