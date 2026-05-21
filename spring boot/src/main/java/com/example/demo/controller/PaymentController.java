package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SecurityUtils;
import com.example.demo.service.PremiumMembershipService;
import com.example.demo.service.RazorpayPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret:}")
    private String razorpayWebhookSecret;

    private final RazorpayPaymentService razorpayPaymentService;

    private final UserRepository userRepository;

    private final PremiumMembershipService premiumMembershipService;

    public PaymentController(
        RazorpayPaymentService razorpayPaymentService,
        UserRepository userRepository,
        PremiumMembershipService premiumMembershipService
    ) {
        this.razorpayPaymentService = razorpayPaymentService;
        this.userRepository = userRepository;
        this.premiumMembershipService = premiumMembershipService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        try {
            if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Razorpay keys not configured on server"));
            }

            String contactNumber = SecurityUtils.currentContactOrNull();
            if (contactNumber == null || contactNumber.isBlank()) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            // Server-authoritative pricing: client must NOT decide amount.
            String plan = (String) body.get("plan");
            if (plan == null || plan.isBlank()) {
                Object notesObj = body.get("notes");
                if (notesObj instanceof Map<?, ?> notesMap) {
                    Object p = notesMap.get("plan");
                    if (p != null) {
                        plan = String.valueOf(p);
                    }
                }
            }
            if (plan == null || plan.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Plan is required"));
            }

            int amountInPaise;
            int priceInr;
            try {
                amountInPaise = razorpayPaymentService.premiumAmountInPaise(plan);
                priceInr = razorpayPaymentService.premiumAmountInInr(plan);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
            }

            // Server-authoritative: currency must be INR for these plans.
            String currency = "INR";
            // IMPORTANT: do not accept client-provided receipt; bind order to authenticated user.
            String receipt = "premium_" + contactNumber + "_" + System.currentTimeMillis();

            // Force secure notes binding: order is tied to the authenticated user + chosen plan
            Map<String, Object> notes = new HashMap<>();
            notes.put("contactNumber", contactNumber);
            notes.put("plan", plan);

            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);
            orderRequest.put("payment_capture", 1);
            if (!notes.isEmpty()) {
                orderRequest.put("notes", new JSONObject(notes));
            }

            Order order = client.orders.create(orderRequest);
            JSONObject res = order.toJson();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("order", res.toMap());
            response.put("keyId", razorpayKeyId);
            response.put("plan", plan);
            response.put("priceInr", priceInr);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Payment create-order failed", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Unable to create order. Please try again later."));
        }
    }

    /**
     * Razorpay webhook fallback for payment.captured.
     * This is intentionally unauthenticated (Razorpay servers call it), but signature-verified.
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> razorpayWebhook(
        @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
        @RequestBody String payload
    ) {
        try {
            if (razorpayWebhookSecret == null || razorpayWebhookSecret.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Webhook secret not configured"));
            }
            if (signature == null || signature.isBlank() || payload == null || payload.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid webhook payload"));
            }

            boolean ok = Utils.verifyWebhookSignature(payload, signature, razorpayWebhookSecret);
            if (!ok) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid webhook signature"));
            }

            JSONObject event = new JSONObject(payload);
            String eventName = event.optString("event", "").trim();
            if (!"payment.captured".equalsIgnoreCase(eventName)) {
                return ResponseEntity.ok(Map.of("success", true));
            }

            JSONObject paymentEntity = event
                .optJSONObject("payload")
                .optJSONObject("payment")
                .optJSONObject("entity");
            if (paymentEntity == null) {
                return ResponseEntity.ok(Map.of("success", true));
            }

            String paymentId = paymentEntity.optString("id", "").trim();
            String orderId = paymentEntity.optString("order_id", "").trim();
            int payloadAmount = paymentEntity.optInt("amount", -1);
            String status = paymentEntity.optString("status", "").trim();

            if (paymentId.isEmpty() || orderId.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", true));
            }
            if (!"captured".equalsIgnoreCase(status)) {
                return ResponseEntity.ok(Map.of("success", true));
            }

            if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
                log.warn("Webhook received but Razorpay keys not configured");
                return ResponseEntity.ok(Map.of("success", true));
            }

            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            Order order = client.orders.fetch(orderId);
            if (order == null) {
                return ResponseEntity.ok(Map.of("success", true));
            }

            String receipt = safeString(order.get("receipt"));
            if (receipt == null || !receipt.startsWith("premium_")) {
                // Not our premium orders; ignore.
                return ResponseEntity.ok(Map.of("success", true));
            }

            String contactFromReceipt = parseContactFromPremiumReceipt(receipt);
            String plan = null;
            String contactFromNotes = null;

            Object notesObj = order.get("notes");
            if (notesObj instanceof JSONObject notesJson) {
                contactFromNotes = safeString(notesJson.optString("contactNumber", ""));
                plan = safeString(notesJson.optString("plan", ""));
            } else if (notesObj instanceof Map<?, ?> notesMap) {
                contactFromNotes = safeString(notesMap.get("contactNumber"));
                plan = safeString(notesMap.get("plan"));
            }

            String contactNumber = (contactFromNotes != null) ? contactFromNotes : contactFromReceipt;
            if (contactFromNotes != null && contactFromReceipt != null && !contactFromNotes.equals(contactFromReceipt)) {
                log.warn("Webhook premium binding mismatch between receipt and notes (orderId={}, maskedReceiptContact={}, maskedNotesContact={})",
                    safeForLog(orderId), maskPhone(contactFromReceipt), maskPhone(contactFromNotes));
                return ResponseEntity.ok(Map.of("success", true));
            }
            if (contactNumber == null || contactNumber.isBlank() || plan == null || plan.isBlank()) {
                log.warn("Webhook premium order missing binding (orderId={}, maskedContactFromReceipt={})",
                    safeForLog(orderId), maskPhone(contactFromReceipt));
                return ResponseEntity.ok(Map.of("success", true));
            }

            int expectedAmount = razorpayPaymentService.premiumAmountInPaise(plan);
            Object orderAmountObj = order.get("amount");
            int orderAmount = orderAmountObj instanceof Number ? ((Number) orderAmountObj).intValue() : -1;
            if (orderAmount != expectedAmount) {
                log.warn("Webhook premium amount mismatch on order (orderId={}, orderAmount={}, expectedAmount={})",
                    safeForLog(orderId), orderAmount, expectedAmount);
                return ResponseEntity.ok(Map.of("success", true));
            }
            if (payloadAmount > 0 && payloadAmount != expectedAmount) {
                log.warn("Webhook premium amount mismatch in event payload (paymentId={}, payloadAmount={}, expectedAmount={})",
                    safeForLog(paymentId), payloadAmount, expectedAmount);
                return ResponseEntity.ok(Map.of("success", true));
            }

            Payment payment = client.payments.fetch(paymentId);
            String paymentOrderId = safeString(payment.get("order_id"));
            if (paymentOrderId == null || !orderId.equals(paymentOrderId)) {
                log.warn("Webhook premium payment belongs to different order (orderId={}, paymentOrderId={}, paymentId={})",
                    safeForLog(orderId), safeForLog(paymentOrderId), safeForLog(paymentId));
                return ResponseEntity.ok(Map.of("success", true));
            }
            String apiStatus = safeString(payment.get("status"));
            Object capturedObj = payment.get("captured");
            boolean captured = (capturedObj instanceof Boolean b && b) || (apiStatus != null && "captured".equalsIgnoreCase(apiStatus));
            if (!captured) {
                log.warn("Webhook premium payment not captured per API (paymentId={}, status={})", safeForLog(paymentId), safeForLog(apiStatus));
                return ResponseEntity.ok(Map.of("success", true));
            }
            Object payAmountObj = payment.get("amount");
            int payAmount = payAmountObj instanceof Number ? ((Number) payAmountObj).intValue() : -1;
            if (payAmount != expectedAmount) {
                log.warn("Webhook premium amount mismatch on payment (paymentId={}, payAmount={}, expectedAmount={})",
                    safeForLog(paymentId), payAmount, expectedAmount);
                return ResponseEntity.ok(Map.of("success", true));
            }

            User user = userRepository.findByContactNumber(contactNumber);
            if (user == null) {
                log.warn("Webhook premium user not found (maskedContact={})", maskPhone(contactNumber));
                return ResponseEntity.ok(Map.of("success", true));
            }

            premiumMembershipService.applyVerifiedPremiumPayment(user, plan, orderId, paymentId, "razorpay_webhook");
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.warn("Razorpay webhook handling failed", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Webhook processing failed"));
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
        if (v.length() <= 10) return v;
        return v.substring(0, 6) + "…" + v.substring(v.length() - 4);
    }

    private static String parseContactFromPremiumReceipt(String receipt) {
        // receipt format: premium_{contact}_{timestamp}
        if (receipt == null) return null;
        String r = receipt.trim();
        if (!r.startsWith("premium_")) return null;
        String rest = r.substring("premium_".length());
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore <= 0) return null;
        String contact = rest.substring(0, lastUnderscore).trim();
        return contact.isEmpty() ? null : contact;
    }

    private static String maskPhone(String phone) {
        if (phone == null) return "";
        String digits = phone.trim();
        if (digits.length() <= 4) return "****";
        return "****" + digits.substring(digits.length() - 4);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> payload) {
        try {
            String orderId = payload.get("razorpay_order_id");
            String paymentId = payload.get("razorpay_payment_id");
            String signature = payload.get("razorpay_signature");
            if (orderId == null || paymentId == null || signature == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid verification payload"));
            }

            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            boolean isValid = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);

            Map<String, Object> response = new HashMap<>();
            response.put("success", isValid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Payment verification failed", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Unable to verify payment. Please try again later."));
        }
    }
}



