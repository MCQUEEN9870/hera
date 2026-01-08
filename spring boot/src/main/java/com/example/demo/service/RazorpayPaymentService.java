package com.example.demo.service;

import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;

@Service
public class RazorpayPaymentService {

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
            return false;
        }
        if (expectedContactNumber == null || expectedContactNumber.isBlank()) {
            return false;
        }
        if (plan == null || plan.isBlank() || orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }

        try {
            // 1) Signature check (cryptographic)
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            boolean signatureOk = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
            if (!signatureOk) {
                return false;
            }

            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // 2) Order checks
            Order order = client.orders.fetch(orderId);
            int expectedAmount = premiumAmountInPaise(plan);

            Object orderAmountObj = order.get("amount");
            int orderAmount = orderAmountObj instanceof Number ? ((Number) orderAmountObj).intValue() : -1;
            if (orderAmount != expectedAmount) {
                return false;
            }

            Object notesObj = order.get("notes");
            if (notesObj instanceof JSONObject notesJson) {
                String orderContact = notesJson.optString("contactNumber", "");
                String orderPlan = notesJson.optString("plan", "");
                if (!expectedContactNumber.equals(orderContact)) {
                    return false;
                }
                if (!plan.trim().equalsIgnoreCase(orderPlan)) {
                    return false;
                }
            } else if (notesObj instanceof Map<?, ?> notesMap) {
                Object orderContact = notesMap.get("contactNumber");
                Object orderPlan = notesMap.get("plan");
                if (orderContact == null || !expectedContactNumber.equals(String.valueOf(orderContact))) {
                    return false;
                }
                if (orderPlan == null || !plan.trim().equalsIgnoreCase(String.valueOf(orderPlan))) {
                    return false;
                }
            } else {
                // Notes missing/unexpected: refuse, because it breaks our user binding.
                return false;
            }

            // 3) Payment checks
            Payment payment = client.payments.fetch(paymentId);
            String paymentOrderId = String.valueOf(payment.get("order_id"));
            if (!orderId.equals(paymentOrderId)) {
                return false;
            }
            String status = String.valueOf(payment.get("status"));
            if (!"captured".equalsIgnoreCase(status)) {
                return false;
            }
            Object payAmountObj = payment.get("amount");
            int paymentAmount = payAmountObj instanceof Number ? ((Number) payAmountObj).intValue() : -1;
            return paymentAmount == expectedAmount;
        } catch (Exception e) {
            return false;
        }
    }
}
