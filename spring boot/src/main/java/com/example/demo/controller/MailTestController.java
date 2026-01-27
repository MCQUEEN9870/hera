package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.EmailService;

import jakarta.validation.constraints.Email;

@RestController
@RequestMapping("/api/mail")
public class MailTestController {

    private final EmailService emailService;

    @Value("${mail.admin.to:info@herapherigoods.in}")
    private String defaultTo;

    @Value("${mail.test.token:}")
    private String mailTestToken;

    public MailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTest(
        @RequestHeader(value = "X-Mail-Test-Token", required = false) String token,
        @RequestBody(required = false) MailTestRequest body
    ) {
        // Extra guard even though endpoint is JWT-protected by SecurityConfig.
        if (mailTestToken == null || mailTestToken.isBlank()) {
            return ResponseEntity.status(403).body("Mail test disabled: set MAIL_TEST_TOKEN (mail.test.token)");
        }
        if (token == null || token.isBlank() || !mailTestToken.equals(token)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        String to = (body != null && body.to != null && !body.to.isBlank()) ? body.to.trim() : defaultTo;
        String subject = (body != null && body.subject != null && !body.subject.isBlank())
            ? body.subject.trim()
            : "SMTP Test - Hera Pheri Goods";

        String html = """
            <div style=\"font-family:Arial,sans-serif;line-height:1.6\">
              <h2 style=\"margin:0 0 8px 0\">SMTP Test Email</h2>
              <p>If you received this, SMTP authentication + delivery is working.</p>
              <p style=\"color:#666;font-size:12px\">Sent by /api/mail/test</p>
            </div>
            """;

        emailService.sendHtml(to, subject, html);
        return ResponseEntity.ok("sent");
    }

    private static class MailTestRequest {
        @Email
        public String to;
        public String subject;
    }
}
