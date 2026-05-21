package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.example.demo.model.User;

@Service
public class PremiumPurchaseEmailService {

    private static final Logger log = LoggerFactory.getLogger(PremiumPurchaseEmailService.class);

    private final EmailService emailService;

    @Value("${mail.premium.purchase.enabled:true}")
    private boolean enabled;

    @Value("${mail.premium.purchase.template:email-templates/index.html}")
    private String templatePath;

    /**
     * Base URL for email images, e.g.
     * - https://herapherigoods.in/attached_assets/email/premium
     * - https://<project>.supabase.co/storage/v1/object/public/<bucket>/premium
     */
    @Value("${mail.premium.purchase.img-base:}")
    private String imgBase;

    public PremiumPurchaseEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public boolean trySendPremiumPurchaseEmail(User user) {
        if (!enabled) return false;
        if (user == null) return false;

        String to = safeText(user.getEmail());
        if (to.isBlank()) return false;

        String template;
        try {
            template = loadTemplate(templatePath);
        } catch (Exception e) {
            log.warn("Premium purchase email template load failed (path={}): {}", templatePath, e.toString());
            return false;
        }

        String name = safeText(user.getFullName());
        String plan = prettyPlan(user.getMembershipPlan());
        if (plan.isBlank()) plan = "Premium";

        String amountInr = user.getMembershipAmountPaidInr() == null ? "-" : String.valueOf(user.getMembershipAmountPaidInr());
        String paymentId = safeText(user.getMembershipPaymentId());
        if (paymentId.isBlank()) paymentId = "-";

        String purchaseTime = formatTime(user.getMembershipPurchaseTime());
        String expiryTime = formatTime(user.getMembershipExpireTime());

        String rendered = template;
        rendered = rendered.replace("{{IMG_BASE}}", escUrlBase(imgBase));
        rendered = rendered.replace("{{USER_NAME}}", esc(name.isBlank() ? "there" : name));
        rendered = rendered.replace("{{PLAN_NAME}}", esc(plan));
        rendered = rendered.replace("{{AMOUNT_INR}}", esc(amountInr));
        rendered = rendered.replace("{{PAYMENT_ID}}", esc(paymentId));
        rendered = rendered.replace("{{PURCHASE_TIME}}", esc(purchaseTime));
        rendered = rendered.replace("{{EXPIRY_TIME}}", esc(expiryTime));
        rendered = rendered.replace("{{PAYMENT_STATUS}}", "Captured");

        // If imgBase is empty, images won't load. Still allow sending the email.
        if (safeText(imgBase).isBlank()) {
            log.warn("Premium purchase email img-base is not set (mail.premium.purchase.img-base). Images may not render in email.");
        }

        String subject = "Herapherigoods Premium activated";

        try {
            boolean sent = emailService.sendHtml(to, subject, rendered);
            if (!sent) {
                log.warn("Premium purchase email suppressed by config (to={}, subject={})", maskEmail(to), subject);
            }
            return sent;
        } catch (Exception e) {
            log.warn("Premium purchase email failed (to={}, subject={}): {}", maskEmail(to), subject, e.toString());
            return false;
        }
    }

    private static String loadTemplate(String classpathPath) throws IOException {
        String path = safeText(classpathPath);
        if (path.isBlank()) {
            throw new IllegalArgumentException("template path is blank");
        }
        ClassPathResource res = new ClassPathResource(path);
        byte[] bytes = res.getInputStream().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String formatTime(LocalDateTime t) {
        if (t == null) return "";
        // Keep it simple & readable; DB stores LocalDateTime already.
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
        try {
            return t.format(fmt);
        } catch (Exception e) {
            return t.toString();
        }
    }

    private static String prettyPlan(String rawPlan) {
        String p = safeText(rawPlan).toLowerCase(Locale.ROOT);
        return switch (p) {
            case "monthly" -> "Monthly";
            case "quarterly" -> "Quarterly";
            case "half-yearly" -> "Half-yearly";
            case "yearly" -> "Yearly";
            case "" -> "";
            default -> rawPlan;
        };
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

    private static String escUrlBase(String base) {
        // We escape like text, but avoid trimming slashes incorrectly.
        return esc(safeText(base).replaceAll("/+\\z", ""));
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "<null>";
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.substring(0, 1) + "***" + e.substring(at);
    }
}
