package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${mail.from:}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPlainText(String to, String subject, String text) {
        send(to, subject, text, false, null);
    }

    public void sendHtml(String to, String subject, String html) {
        send(to, subject, html, true, null);
    }

    public void sendHtmlWithReplyTo(String to, String subject, String html, String replyTo) {
        send(to, subject, html, true, replyTo);
    }

    private void send(String to, String subject, String body, boolean isHtml, String replyTo) {
        if (!mailEnabled) {
            log.info("Email disabled; skipping send (to={}, subject={})", maskEmail(to), subject);
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("Email send skipped: recipient missing (subject={})", subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject == null ? "" : subject);

            if (mailFrom != null && !mailFrom.isBlank()) {
                helper.setFrom(mailFrom);
            }
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            helper.setText(body == null ? "" : body, isHtml);
            mailSender.send(message);

            log.info("Email sent (to={}, subject={})", maskEmail(to), subject);
        } catch (Exception e) {
            // Don't leak secrets; log minimal info.
            String smtp = "";
            try {
                if (mailSender instanceof JavaMailSenderImpl impl) {
                    smtp = String.format(" (smtpHost=%s, smtpPort=%s, smtpUser=%s)",
                        safe(impl.getHost()),
                        impl.getPort(),
                        maskEmail(impl.getUsername()));
                }
            } catch (Exception _ignored) {
                // ignore
            }
            log.error("Email send failed (to={}, subject={}): {}{}", maskEmail(to), subject, e.toString(), smtp);
            throw new RuntimeException("Email send failed", e);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String maskEmail(String email) {
        if (email == null) return null;
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.substring(0, 1) + "***" + e.substring(at);
    }
}
