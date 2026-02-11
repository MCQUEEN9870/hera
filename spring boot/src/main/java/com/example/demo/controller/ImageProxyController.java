package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Lightweight image proxy: keeps pretty URL compatibility but no longer resizes or watermarks.
 * Simply redirects to the public Supabase object. New images are already processed at upload.
 */
@RestController
@RequestMapping("/api/images")
public class ImageProxyController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.bucket.name}")
    private String bucketName;

    @Value("${supabase.profile.bucket.name:profile-photos}")
    private String profileBucketName;

    @GetMapping("/vehicles/{registrationId}/{filename:.+}")
    public ResponseEntity<Void> redirectVehicleImage(
            @PathVariable("registrationId") String registrationId,
            @PathVariable("filename") String filename) {
        try {
            String target = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + registrationId + "/" + filename;
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.LOCATION, target);
            // Cache redirect for a short time (client will cache final image separately)
            headers.add(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
            return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 redirect
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/profile/**")
    public ResponseEntity<Void> redirectProfileImage(HttpServletRequest request) {
        try {
            String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String remaining = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);
            // remaining is the object key inside the profile bucket (may contain slashes)
            if (remaining == null) remaining = "";
            remaining = remaining.replaceAll("^/+", "");
            if (remaining.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            String target = supabaseUrl + "/storage/v1/object/public/" + profileBucketName + "/" + remaining;
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.LOCATION, target);
            headers.add(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
