package com.example.demo.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class Fast2SMSService {

    // Repurposed to use 2factor.in SMS API
    // Simple SMS API: GET https://2factor.in/API/V1/{API_KEY}/SMS/{phone}/{message}

    @Value("${twofactor.api.key:ba5ce1cc-7aaa-11f0-a562-0200cd936042}")
    private String twoFactorApiKey;

    @Value("${sms.enabled:true}")
    private boolean smsEnabled;

    @Value("${sms.sender.id:HPIGDS}")
    private String senderId;

    @Value("${twofactor.template.login:userloginotp}")
    private String loginTemplate;

    @Value("${twofactor.template.signup:usersignup}")
    private String signupTemplate;

    @Value("${twofactor.autogen.template-only:true}")
    private boolean autogenTemplateOnly;

    private static final String TWOFACTOR_SMS_URL_TEMPLATE = "https://2factor.in/API/V1/%s/SMS/%s/%s";
    private static final String TWOFACTOR_SMS_WITH_OTP_URL_TEMPLATE = "https://2factor.in/API/V1/%s/SMS/%s/%s/%s";
    private static final String TWOFACTOR_TSMS_URL = "https://2factor.in/API/V1/%s/ADDON_SERVICES/SEND/TSMS";
    private static final String TWOFACTOR_AUTOGEN_URL = "https://2factor.in/API/V1/%s/SMS/%s/%s/%s"; // .../{AUTOGEN|AUTOGEN3}/{TEMPLATE}
    private static final String TWOFACTOR_AUTOGEN_NO_TEMPLATE_URL = "https://2factor.in/API/V1/%s/SMS/%s/%s"; // .../{AUTOGEN|AUTOGEN3}
    private static final String TWOFACTOR_VERIFY_URL = "https://2factor.in/API/V1/%s/SMS/VERIFY/%s/%s"; // .../{session_id}/{otp}

    private String normalizeIndianNumber(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() >= 12) {
            return digits;
        }
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits; // fallback
    }

    private void sendViaTwoFactor(String phoneNumber, String message) {
        if (!smsEnabled) {
            System.out.println("[SMS disabled] Would send to " + phoneNumber + ": " + message);
            return;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();

            String normalized = normalizeIndianNumber(phoneNumber);
            String encodedMsg = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
            String url = String.format(TWOFACTOR_SMS_URL_TEMPLATE, twoFactorApiKey, normalized, encodedMsg);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            System.out.println("2Factor SMS Response: " + response.getStatusCode() + " - " + response.getBody());
        } catch (RestClientException e) {
            System.err.println("Failed to send SMS via 2Factor API: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while sending SMS: " + e.getMessage());
        }
    }

    public void sendOtpSms(String phoneNumber, String otp) {
        try {
            if (!smsEnabled) {
                System.out.println("[SMS disabled] Would send OTP to " + phoneNumber + ": " + otp);
                return;
            }
            RestTemplate restTemplate = new RestTemplate();
            String normalized = normalizeIndianNumber(phoneNumber);
            String message = "Your OTP is: " + otp + " (Herapheri Goods)";
            String encodedMsg = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
            String url = String.format(TWOFACTOR_SMS_WITH_OTP_URL_TEMPLATE, twoFactorApiKey, normalized, encodedMsg, otp);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            System.out.println("2Factor SMS OTP Response: " + response.getStatusCode() + " - " + response.getBody());
        } catch (RestClientException e) {
            System.err.println("Failed to send OTP via 2Factor API: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while sending OTP: " + e.getMessage());
        }
    }

    // Template-based OTP (TSMS): uses approved template and sender id
    public void sendOtpViaTemplate(String phoneNumber, String otp, String senderId, String templateName) {
        try {
            if (!smsEnabled) {
                System.out.println("[SMS disabled] Would send OTP (TSMS) to " + phoneNumber + ": " + otp + " via template " + templateName);
                return;
            }
            RestTemplate restTemplate = new RestTemplate();
            String normalized = normalizeIndianNumber(phoneNumber);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("From", senderId);
            payload.put("To", normalized);
            payload.put("TemplateName", templateName);
            payload.put("VAR1", otp); // maps to XXXX in template text

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> request = new org.springframework.http.HttpEntity<>(payload, headers);

            String url = String.format(TWOFACTOR_TSMS_URL, twoFactorApiKey);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            System.out.println("2Factor TSMS Response: " + response.getStatusCode() + " - " + response.getBody());
        } catch (RestClientException e) {
            System.err.println("Failed to send OTP via 2Factor TSMS API: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while sending OTP (TSMS): " + e.getMessage());
        }
    }

    public void sendLoginOtp(String phoneNumber, String otp) {
        sendOtpViaTemplate(phoneNumber, otp, senderId, loginTemplate);
    }

    public void sendSignupOtp(String phoneNumber, String otp) {
        sendOtpViaTemplate(phoneNumber, otp, senderId, signupTemplate);
    }

    // --- AUTOGEN FLOW ---
    public String sendAutogenOtp(String phoneNumber, String templateName, boolean fourDigit) {
        try {
            if (!smsEnabled) {
                System.out.println("[SMS disabled] Would AUTOGEN send to " + phoneNumber + " via template " + templateName);
                return null;
            }
            RestTemplate restTemplate = new RestTemplate();
            String normalized = normalizeIndianNumber(phoneNumber);
            String mode = fourDigit ? "AUTOGEN3" : "AUTOGEN";
            String url = String.format(TWOFACTOR_AUTOGEN_URL, twoFactorApiKey, normalized, mode, templateName);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            System.out.println("2Factor AUTOGEN Response: " + response.getStatusCode() + " - " + response.getBody());
            if (response.getBody() != null) {
                JSONObject obj = new JSONObject(response.getBody());
                if ("Success".equalsIgnoreCase(obj.optString("Status"))) {
                    return obj.optString("Details", null); // session_id
                }
                System.err.println("AUTOGEN with template failed: " + obj.optString("Details"));
            }
            if (!autogenTemplateOnly) {
                // Fallback: try AUTOGEN without template to isolate template/header issues
                String urlNoTpl = String.format(TWOFACTOR_AUTOGEN_NO_TEMPLATE_URL, twoFactorApiKey, normalized, mode);
                ResponseEntity<String> fallback = restTemplate.getForEntity(urlNoTpl, String.class);
                System.out.println("2Factor AUTOGEN (no-template) Response: " + fallback.getStatusCode() + " - " + fallback.getBody());
                if (fallback.getBody() != null) {
                    JSONObject obj2 = new JSONObject(fallback.getBody());
                    if ("Success".equalsIgnoreCase(obj2.optString("Status"))) {
                        return obj2.optString("Details", null);
                    }
                }
            } else {
                System.out.println("Template-only AUTOGEN enforced. Skipping no-template fallback.");
            }
        } catch (RestClientException e) {
            System.err.println("Failed to send AUTOGEN via 2Factor API: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while AUTOGEN: " + e.getMessage());
        }
        return null;
    }

    public boolean verifyAutogenOtp(String sessionId, String otp) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = String.format(TWOFACTOR_VERIFY_URL, twoFactorApiKey, sessionId, otp);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            System.out.println("2Factor VERIFY Response: " + response.getStatusCode() + " - " + response.getBody());
            if (response.getBody() != null) {
                JSONObject obj = new JSONObject(response.getBody());
                return "Success".equalsIgnoreCase(obj.optString("Status"));
            }
        } catch (RestClientException e) {
            System.err.println("Failed to verify OTP via 2Factor API: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while OTP verify: " + e.getMessage());
        }
        return false;
    }

    public void sendSMS(String contactNumber, String message) {
        sendViaTwoFactor(contactNumber, message);
    }
}
