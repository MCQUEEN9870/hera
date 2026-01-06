package com.example.demo.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.ImageMigrationService;

@RestController
@RequestMapping("/api/migration")
public class ImageMigrationController {

    @Autowired
    private ImageMigrationService migrationService;
    
    /**
     * Migrate all registrations from flat structure to folder structure
     */
    @PostMapping("/images")
    public ResponseEntity<?> migrateAllImages() {
        try {
            List<String> results = migrationService.migrateAllRegistrations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Migration completed");
            response.put("results", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during migration: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Migrate a specific registration from flat structure to folder structure
     */
    @PostMapping("/images/{registrationId}")
    public ResponseEntity<?> migrateRegistrationImages(@PathVariable Long registrationId) {
        try {
            int count = migrationService.migrateRegistrationImages(registrationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Migration completed for registration ID: " + registrationId);
            response.put("migratedCount", count);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during migration: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
} 