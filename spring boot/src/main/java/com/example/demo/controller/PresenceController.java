package com.example.demo.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.demo.service.SessionService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping({"/api/presence", "/api/posts/presence"})
@CrossOrigin(origins = {"https://herapherigoods.in", "https://www.herapherigoods.in", "https://api.herapherigoods.in", "http://localhost:8080", "http://localhost:5500", "http://127.0.0.1:5500"}, 
               allowCredentials = "true",
               allowedHeaders = "*",
               methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class PresenceController {

    private final SessionService sessionService;

    public PresenceController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @RequestParam(name = "page", defaultValue = "posts") String page,
            @RequestParam(name = "sessionId", required = false) String sessionId,
            HttpServletRequest request) {
        
        System.out.println("SSE Stream request received - Page: " + page + ", SessionId: " + sessionId);
        System.out.println("Request origin: " + request.getHeader("Origin"));
        System.out.println("Request user-agent: " + request.getHeader("User-Agent"));
        
        // Generate session ID if not provided
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = generateSessionId(request);
            System.out.println("Generated new session ID: " + sessionId);
        }
        
        SseEmitter emitter = sessionService.add(page, sessionId);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        headers.set("X-Accel-Buffering", "no"); // disable buffering on some proxies (nginx)
        return new ResponseEntity<>(emitter, headers, HttpStatus.OK);
    }

    @GetMapping(path = "/count")
    public ResponseEntity<java.util.Map<String, Object>> count(@RequestParam(name = "page", defaultValue = "posts") String page) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        int count = sessionService.getLiveUserCount(page);
        return new ResponseEntity<>(java.util.Map.of("page", page, "count", count), headers, HttpStatus.OK);
    }

    @PostMapping(path = "/heartbeat")
    public ResponseEntity<java.util.Map<String, Object>> heartbeat(
            @RequestParam(name = "page", defaultValue = "posts") String page,
            @RequestParam(name = "sessionId") String sessionId,
            HttpServletRequest request) {
        
        System.out.println("Heartbeat request received - Page: " + page + ", SessionId: " + sessionId);
        System.out.println("Request origin: " + request.getHeader("Origin"));
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            System.out.println("Heartbeat failed: Session ID is required");
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Session ID is required"));
        }
        
        sessionService.updateSessionHeartbeat(sessionId, page);
        int count = sessionService.getLiveUserCount(page);
        
        System.out.println("Heartbeat processed successfully - Count: " + count);
        
        return ResponseEntity.ok(java.util.Map.of(
            "success", true,
            "page", page,
            "sessionId", sessionId,
            "count", count
        ));
    }

    @PostMapping(path = "/disconnect")
    public ResponseEntity<java.util.Map<String, Object>> disconnect(
            @RequestParam(name = "sessionId") String sessionId) {
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Session ID is required"));
        }
        
        sessionService.removeSession(sessionId);
        
        return ResponseEntity.ok(java.util.Map.of(
            "success", true,
            "message", "Session disconnected"
        ));
    }

    @GetMapping(path = "/test")
    public ResponseEntity<java.util.Map<String, Object>> test() {
        try {
            int count = sessionService.getLiveUserCount("posts");
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Database connection working",
                "count", count,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Generate a unique session ID based on request information
     */
    private String generateSessionId(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        long timestamp = System.currentTimeMillis();
        
        // Create a hash of the combination
        String combined = clientIp + ":" + userAgent + ":" + timestamp;
        int hash = combined.hashCode();
        
        return "session_" + Math.abs(hash) + "_" + timestamp;
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}