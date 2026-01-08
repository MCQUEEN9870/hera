package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.example.demo.security.SecurityUtils;
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

    private final RazorpayPaymentService razorpayPaymentService;

    public PaymentController(RazorpayPaymentService razorpayPaymentService) {
        this.razorpayPaymentService = razorpayPaymentService;
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

            String currency = (String) body.getOrDefault("currency", "INR");
            String receipt = (String) body.getOrDefault("receipt", ("premium_" + contactNumber + "_" + System.currentTimeMillis()));

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



