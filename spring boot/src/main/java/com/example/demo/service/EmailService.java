package com.example.demo.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${mail.enabled:true}")
    private boolean enabled;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.api.base-url:https://api.brevo.com/v3}")
    private String brevoBaseUrl;

    @Value("${mail.from.email:info@herapherigoods.in}")
    private String fromEmail;

    @Value("${mail.from.name:Herapherigoods Team}")
    private String fromName;

    /**
     * @return true if the email was sent to Brevo successfully; false if email sending is disabled.
     */
    public boolean sendHtml(String toEmail, String subject, String html) {
        if (!enabled) {
            log.warn("[mail disabled] Not sending email to {}", maskEmail(toEmail));
            return false;
        }
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException("brevo.api.key missing");
        }
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("toEmail required");
        }

        RestTemplate rt = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);
        headers.set("accept", "application/json");

        Map<String, Object> payload = Map.of(
            "sender", Map.of("email", fromEmail, "name", fromName),
            "to", java.util.List.of(Map.of("email", toEmail)),
            "subject", subject == null ? "" : subject,
            "htmlContent", html == null ? "" : html
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        String url = (brevoBaseUrl == null || brevoBaseUrl.isBlank())
            ? "https://api.brevo.com/v3"
            : brevoBaseUrl.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        url = url + "/smtp/email";

        try {
            ResponseEntity<String> resp = rt.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Brevo send failed: status=" + resp.getStatusCode() + ", body=" + resp.getBody());
            }
            log.info("Brevo send ok (to={})", maskEmail(toEmail));
            return true;
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            throw new IllegalStateException("Brevo send failed: status=" + ex.getStatusCode() + ", body=" + body, ex);
        }
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "<null>";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
