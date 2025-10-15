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

// import com.example.demo.service.SessionService;

import jakarta.servlet.http.HttpServletRequest;

// Temporarily disabled presence endpoints
// @RestController
// @RequestMapping({"/api/presence", "/api/posts/presence"})
// @CrossOrigin(origins = {"https://herapherigoods.in", "https://www.herapherigoods.in", "https://api.herapherigoods.in", "http://localhost:8080", "http://localhost:5500", "http://127.0.0.1:5500"}, 
//                allowCredentials = "true",
//                allowedHeaders = "*",
//                methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class PresenceController {

    // private final SessionService sessionService;

    // public PresenceController(SessionService sessionService) {
    //     this.sessionService = sessionService;
    // }

    // @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // public ResponseEntity<SseEmitter> stream(
    //         @RequestParam(name = "page", defaultValue = "posts") String page,
    //         @RequestParam(name = "sessionId", required = false) String sessionId,
    //         HttpServletRequest request) {
    //     return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    // }

    // @GetMapping(path = "/count")
    // public ResponseEntity<java.util.Map<String, Object>> count(@RequestParam(name = "page", defaultValue = "posts") String page) {
    //     return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    // }

    // @PostMapping(path = "/heartbeat")
    // public ResponseEntity<java.util.Map<String, Object>> heartbeat(
    //         @RequestParam(name = "page", defaultValue = "posts") String page,
    //         @RequestParam(name = "sessionId") String sessionId,
    //         HttpServletRequest request) {
    //     return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    // }

    // @PostMapping(path = "/disconnect")
    // public ResponseEntity<java.util.Map<String, Object>> disconnect(
    //         @RequestParam(name = "sessionId") String sessionId) {
    //     return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    // }

    // @GetMapping(path = "/test")
    // public ResponseEntity<java.util.Map<String, Object>> test() {
    //     return ResponseEntity.ok(java.util.Map.of("success", true, "message", "presence disabled"));
    // }

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