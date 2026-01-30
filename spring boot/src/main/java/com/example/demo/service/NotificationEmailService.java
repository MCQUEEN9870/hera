package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailService.class);

    private final EmailService emailService;

    @Value("${mail.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${app.site.name:Herapherigoods}")
    private String siteName;

    @Value("${app.site.url:https://www.herapherigoods.in}")
    private String siteUrl;

    @Value("${app.site.logo.url:https://www.herapherigoods.in/attached_assets/images/1200x630.jpg}")
    private String logoUrl;

    public NotificationEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public boolean trySendPasswordResetEmailOtp(String email, String fullName, String code6, int ttlSeconds) {
        if (!notificationsEnabled) return false;
        if (email == null || email.isBlank()) return false;
        String code = safeText(code6);
        if (code.length() != 6) return false;

        String subject = "Your " + safeText(siteName) + " password reset code";
        String name = safeText(fullName);
        String ttl = ttlSeconds <= 0 ? "60" : String.valueOf(ttlSeconds);

                // NOTE: Most email clients block JavaScript, so a true "Copy" button cannot reliably
                // write to clipboard. We make the code easily selectable (tap/hold to copy).
                String body = """
                        <p style=\"margin:0 0 12px 0\">Hi %s,</p>
                        <p style=\"margin:0 0 12px 0\">Use this code to reset your password:</p>
                        <div style=\"background:#f6f7f9;padding:14px;border-radius:12px;text-align:center;margin:0 0 10px 0\">
                            <span style=\"font-size:28px;letter-spacing:6px;font-weight:800;display:inline-block;user-select:all;-webkit-user-select:all;-ms-user-select:all\">%s</span>
                        </div>
                        <p style=\"margin:0 0 12px 0;color:#475569;font-size:12px\">Tip: Tap and hold the code to copy.</p>
                        <p style=\"margin:0 0 12px 0;color:#444\">This code will expire in <b>%s seconds</b>.</p>
                        <p style=\"margin:0;color:#666;font-size:12px\">If you didn’t request this, you can ignore this email. Your account remains safe.</p>
                        """.formatted(name.isBlank() ? "there" : esc(name), esc(code), esc(ttl));

        return sendHtmlSafeResult(email, subject, wrap("Password Reset", body));
    }

    private boolean sendHtmlSafeResult(String to, String subject, String html) {
        try {
            boolean sent = emailService.sendHtml(to, subject, html);
            if (!sent) {
                log.warn("Email suppressed by config (to={}, subject={})", maskEmail(to), subject);
            }
            return sent;
        } catch (Exception e) {
            log.warn("Notification email failed (to={}, subject={}): {}", maskEmail(to), subject, e.toString());
            return false;
        }
    }

    private String wrap(String title, String innerHtml) {
        String safeTitle = esc(safeText(title));
        String logo = (logoUrl == null || logoUrl.isBlank())
            ? ""
            : ("<div style=\"text-align:center;margin-bottom:10px\">" +
               "<img src=\"" + esc(logoUrl) + "\" alt=\"" + esc(siteName) + "\" style=\"max-width:200px;width:200px;height:auto;display:inline-block\" />" +
               "</div>");

        return """
            <div style=\"font-family:Arial,sans-serif;line-height:1.6;background:#f6f7f9;padding:18px\">
              <div style=\"max-width:620px;margin:0 auto;background:#ffffff;border-radius:16px;padding:18px\">
                %s
                <h2 style=\"margin:0 0 10px 0;text-align:center\">%s</h2>
                %s
                <hr style=\"border:none;border-top:1px solid #eee;margin:16px 0\" />
                <p style=\"margin:0;color:#777;font-size:12px\">%s</p>
              </div>
            </div>
            """.formatted(logo, safeTitle, innerHtml, esc(siteUrl));
    }

    private static String safeText(String s) {
        return s == null ? "" : s.trim();
    }

    private static String esc(String s) {
        if (s == null) return "";
        String out = s;
        out = out.replace("&", "&amp;");
        out = out.replace("<", "&lt;");
        out = out.replace(">", "&gt;");
        out = out.replace("\"", "&quot;");
        out = out.replace("'", "&#39;");
        return out;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "<null>";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
