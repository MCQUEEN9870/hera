package com.example.demo.security;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * @return authenticated contactNumber (JWT subject) or null
     */
    public static String currentContactOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;

        Object principal = authentication.getPrincipal();
        if (principal == null) return null;

        String s = principal instanceof String ? (String) principal : String.valueOf(principal);
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if ("anonymousUser".equalsIgnoreCase(s)) return null;

        return s;
    }

    public static boolean isCurrentContact(String contactNumber) {
        String current = currentContactOrNull();
        return current != null && current.equals(contactNumber);
    }

    public static ResponseEntity<Map<String, Object>> forbidden(String message) {
        return ResponseEntity.status(403)
            .body(Map.of(
                "success", false,
                "message", (message == null || message.isBlank()) ? "Forbidden" : message
            ));
    }
}
