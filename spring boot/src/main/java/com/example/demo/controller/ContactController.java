package com.example.demo.controller;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.demo.model.ContactSubmission;
import com.example.demo.repository.ContactSubmissionRepository;
import com.example.demo.service.EmailService;
import com.example.demo.util.ClientIpUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/contact-submissions")
public class ContactController {

    private final ContactSubmissionRepository repository;
    private final EmailService emailService;

    @Value("${mail.admin.to:info@herapherigoods.in}")
    private String adminTo;
    @Value("${captcha.enabled:false}")
    private boolean captchaEnabled;
    @Value("${captcha.secret:}")
    private String captchaSecret;

    // Simple in-memory rate limit: max N requests per IP per windowMs
    private static final long WINDOW_MS = 60_000; // 1 minute
    private static final int MAX_REQUESTS = 5;    // 5 per minute per IP

    private static final Map<String, Window> ipWindows = new ConcurrentHashMap<>();

    public ContactController(ContactSubmissionRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody ContactSubmission submission) {
        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String ip = ClientIpUtil.getClientIp(req);
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        if (!allow(ip)) {
            return ResponseEntity.status(429).body("Too many requests. Please try again in a minute.");
        }

        // Optional CAPTCHA verification (Google reCAPTCHA v3/v2 or hCaptcha)
        if (captchaEnabled) {
            String token = req.getHeader("X-Captcha-Token");
            if (token == null || token.isBlank()) {
                return ResponseEntity.status(400).body("Captcha token missing");
            }
            boolean ok = verifyCaptcha(token, ip);
            if (!ok) {
                return ResponseEntity.status(400).body("Captcha verification failed");
            }
        }

        // Basic server-side validation safeguards (lengths enforced by entity)
        if (submission.getName() == null || submission.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Name is required");
        }
        if (submission.getEmail() == null || submission.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        if (submission.getSubject() == null || submission.getSubject().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Subject is required");
        }
        if (submission.getMessage() == null || submission.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Message is required");
        }

        ContactSubmission saved = repository.save(submission);

        // Fire-and-forget style: try email, but do not block success if email fails.
        try {
            String safePhone = submission.getPhone() == null ? "" : submission.getPhone().trim();
            String userEmail = submission.getEmail() == null ? "" : submission.getEmail().trim();

            String adminSubject = "New Contact Submission: " + submission.getSubject();
            String adminHtml = """
                <div style=\"font-family:Arial,sans-serif;line-height:1.5\">
                  <h2 style=\"margin:0 0 10px 0\">New Contact Submission</h2>
                  <p><b>Name:</b> %s</p>
                  <p><b>Email:</b> %s</p>
                  <p><b>Phone:</b> %s</p>
                  <p><b>Subject:</b> %s</p>
                  <p><b>Message:</b><br/>%s</p>
                  <hr/>
                  <p style=\"color:#666\">IP: %s | Submission ID: %s</p>
                </div>
                """.formatted(
                    escape(submission.getName()),
                    escape(userEmail),
                    escape(safePhone),
                    escape(submission.getSubject()),
                    escape(submission.getMessage()).replace("\n", "<br/>") ,
                    escape(ip),
                    saved.getId()
                );

            // Send admin notification with reply-to pointing to the user.
            emailService.sendHtmlWithReplyTo(adminTo, adminSubject, adminHtml, userEmail);

            // Send acknowledgement to user
            if (!userEmail.isBlank()) {
                String ackSubject = "We received your message - Hera Pheri Goods";
                String ackHtml = """
                    <div style=\"font-family:Arial,sans-serif;line-height:1.6\">
                      <p>Hi %s,</p>
                      <p>Thanks for contacting Hera Pheri Goods. We received your message and will get back to you soon.</p>
                      <p style=\"margin-top:14px\"><b>Your message:</b><br/>%s</p>
                      <hr/>
                      <p style=\"color:#666;font-size:12px\">This is an automated email from %s.</p>
                    </div>
                    """.formatted(
                        escape(submission.getName()),
                        escape(submission.getMessage()).replace("\n", "<br/>") ,
                        "info@herapherigoods.in"
                    );
                emailService.sendHtml(userEmail, ackSubject, ackHtml);
            }
        } catch (Exception e) {
            // Keep API success; log happens inside EmailService.
        }

        return ResponseEntity.ok().body(saved.getId());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static boolean allow(String ip) {
        long now = Instant.now().toEpochMilli();
        Window w = ipWindows.computeIfAbsent(ip, k -> new Window(now, 0));
        synchronized (w) {
            if (now - w.startMs > WINDOW_MS) {
                w.startMs = now;
                w.count = 0;
            }
            if (w.count >= MAX_REQUESTS) {
                return false;
            }
            w.count++;
            return true;
        }
    }

    private static class Window {
        long startMs;
        int count;
        Window(long startMs, int count) { this.startMs = startMs; this.count = count; }
    }

    private boolean verifyCaptcha(String token, String ip) {
        try {
            // Google reCAPTCHA v3 endpoint
            String url = "https://www.google.com/recaptcha/api/siteverify?secret=" + captchaSecret + "&response=" + token + "&remoteip=" + ip;
            RestTemplate rt = new RestTemplate();
            java.util.Map<?,?> resp = rt.postForObject(url, null, java.util.Map.class);
            if (resp == null) return false;
            Object success = resp.get("success");
            if (success instanceof Boolean b) return b;
            return false;
        } catch (IllegalArgumentException | org.springframework.web.client.RestClientException e) {
            return false;
        }
    }
}



