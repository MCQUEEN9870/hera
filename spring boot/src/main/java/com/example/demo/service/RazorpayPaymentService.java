package com.example.demo.service;

import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;

@Service
public class RazorpayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentService.class);

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Value("${app.premium.pricing.monthly.inr:59}")
    private int monthlyInr;

    @Value("${app.premium.pricing.quarterly.inr:159}")
    private int quarterlyInr;

    @Value("${app.premium.pricing.half-yearly.inr:299}")
    private int halfYearlyInr;

    @Value("${app.premium.pricing.yearly.inr:499}")
    private int yearlyInr;

    public boolean isConfigured() {
        return razorpayKeyId != null && !razorpayKeyId.isBlank() && razorpayKeySecret != null && !razorpayKeySecret.isBlank();
    }

    public int premiumAmountInPaise(String plan) {
        int inr = premiumAmountInInr(plan);
        return inr * 100;
    }

    public int premiumAmountInInr(String plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Plan is required");
        }
        String normalized = plan.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "monthly" -> monthlyInr;
            case "quarterly" -> quarterlyInr;
            case "half-yearly" -> halfYearlyInr;
            case "yearly" -> yearlyInr;
            default -> throw new IllegalArgumentException("Invalid plan: " + plan);
        };
    }

    /**
     * Verifies:
     * - signature is valid
     * - payment belongs to order
     * - payment is captured
     * - amount matches server pricing for the plan
     * - order notes match expectedContactNumber + plan (set by our create-order endpoint)
     */
    public boolean verifyPremiumPayment(
        String expectedContactNumber,
        String plan,
        String orderId,
        String paymentId,
        String signature
    ) {
        if (!isConfigured()) {
            log.warn("Razorpay verifyPremiumPayment called but keys are not configured");
            return false;
        }
        if (expectedContactNumber == null || expectedContactNumber.isBlank()) {
            return false;
        }
        if (plan == null || plan.isBlank() || orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }

        try {
            final int expectedAmount;
            try {
                expectedAmount = premiumAmountInPaise(plan);
            } catch (IllegalArgumentException ex) {
                log.info("Razorpay premium verify rejected: invalid plan '{}' (maskedContact={})", plan, maskPhone(expectedContactNumber));
                return false;
            }

            // 1) Signature check (cryptographic)
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            boolean signatureOk = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
            if (!signatureOk) {
                log.info("Razorpay premium verify rejected: signature mismatch (orderId={}, paymentId={}, maskedContact={})",
                    safeForLog(orderId), safeForLog(paymentId), maskPhone(expectedContactNumber));
                return false;
            }

            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // 2) Order checks
            Order order = client.orders.fetch(orderId);
            Object orderAmountObj = order.get("amount");
            int orderAmount = orderAmountObj instanceof Number ? ((Number) orderAmountObj).intValue() : -1;
            if (orderAmount != expectedAmount) {
                log.info("Razorpay premium verify rejected: order amount mismatch (orderId={}, orderAmount={}, expectedAmount={}, maskedContact={})",
                    safeForLog(orderId), orderAmount, expectedAmount, maskPhone(expectedContactNumber));
                return false;
            }

            // Bind order to the authenticated user.
            // Prefer notes (set by our create-order endpoint). Fallback to receipt (server-set in controller).
            boolean boundToUser = false;
            String receipt = safeString(order.get("receipt"));
            if (receipt != null && receipt.startsWith("premium_" + expectedContactNumber + "_")) {
                boundToUser = true;
            }

            Object notesObj = order.get("notes");
            if (notesObj instanceof JSONObject notesJson) {
                String orderContact = notesJson.optString("contactNumber", "");
                String orderPlan = notesJson.optString("plan", "");
                if (expectedContactNumber.equals(orderContact) && plan.trim().equalsIgnoreCase(orderPlan)) {
                    boundToUser = true;
                }
            } else if (notesObj instanceof Map<?, ?> notesMap) {
                Object orderContact = notesMap.get("contactNumber");
                Object orderPlan = notesMap.get("plan");
                if (orderContact != null && orderPlan != null
                    && expectedContactNumber.equals(String.valueOf(orderContact))
                    && plan.trim().equalsIgnoreCase(String.valueOf(orderPlan))) {
                    boundToUser = true;
                }
            }

            if (!boundToUser) {
                log.info("Razorpay premium verify rejected: order not bound to user (orderId={}, maskedContact={}, receiptPresent={})",
                    safeForLog(orderId), maskPhone(expectedContactNumber), receipt != null && !receipt.isBlank());
                return false;
            }

            // 3) Payment checks
            Payment payment = null;
            String status = null;
            Boolean capturedFlag = null;
            int paymentAmount = -1;

            // Razorpay can sometimes be eventually-consistent right after checkout.
            // Retry a few times if we see non-captured transient statuses.
            for (int attempt = 0; attempt < 3; attempt++) {
                payment = client.payments.fetch(paymentId);

                String paymentOrderId = safeString(payment.get("order_id"));
                if (paymentOrderId == null || !orderId.equals(paymentOrderId)) {
                    log.info("Razorpay premium verify rejected: payment belongs to different order (orderId={}, paymentOrderId={}, paymentId={}, maskedContact={})",
                        safeForLog(orderId), safeForLog(paymentOrderId), safeForLog(paymentId), maskPhone(expectedContactNumber));
                    return false;
                }

                status = safeString(payment.get("status"));
                Object capturedObj = payment.get("captured");
                capturedFlag = (capturedObj instanceof Boolean) ? (Boolean) capturedObj : null;
                Object payAmountObj = payment.get("amount");
                paymentAmount = payAmountObj instanceof Number ? ((Number) payAmountObj).intValue() : -1;

                boolean captured = (capturedFlag != null && capturedFlag)
                    || (status != null && "captured".equalsIgnoreCase(status));

                if (captured) {
                    break;
                }

                // If it is still processing/authorized, wait a bit then retry.
                if (attempt < 2 && status != null && (
                    "authorized".equalsIgnoreCase(status)
                    || "created".equalsIgnoreCase(status)
                    || "pending".equalsIgnoreCase(status)
                )) {
                    try {
                        Thread.sleep(350L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    continue;
                }

                // Non-transient status: stop.
                break;
            }

            boolean finalCaptured = (capturedFlag != null && capturedFlag)
                || (status != null && "captured".equalsIgnoreCase(status));
            if (!finalCaptured) {
                log.info("Razorpay premium verify rejected: payment not captured yet (orderId={}, paymentId={}, status={}, capturedFlag={}, maskedContact={})",
                    safeForLog(orderId), safeForLog(paymentId), safeForLog(status), capturedFlag, maskPhone(expectedContactNumber));
                return false;
            }
            if (paymentAmount != expectedAmount) {
                log.info("Razorpay premium verify rejected: payment amount mismatch (orderId={}, paymentId={}, paymentAmount={}, expectedAmount={}, maskedContact={})",
                    safeForLog(orderId), safeForLog(paymentId), paymentAmount, expectedAmount, maskPhone(expectedContactNumber));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Razorpay premium verify failed due to exception (orderId={}, paymentId={}, maskedContact={})",
                safeForLog(orderId), safeForLog(paymentId), maskPhone(expectedContactNumber), e);
            return false;
        }
    }

    private static String safeString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static String safeForLog(String value) {
        if (value == null || value.isBlank()) return "";
        String v = value.trim();
        // Show only prefix/suffix to avoid leaking full ids.
        if (v.length() <= 10) return v;
        return v.substring(0, 6) + "…" + v.substring(v.length() - 4);
    }

    private static String maskPhone(String phone) {
        if (phone == null) return "";
        String digits = phone.trim();
        if (digits.length() <= 4) return "****";
        return "****" + digits.substring(digits.length() - 4);
    }
}
