package com.example.demo.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.Registration;
import com.example.demo.model.RegistrationImageFolder;
import com.example.demo.repository.RegistrationImageFolderRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.service.SupabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/registration-images")
public class RegistrationImageController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationImageController.class);

    @Autowired
    private SupabaseService supabaseService;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private RegistrationImageFolderRepository registrationImageFolderRepository;
    
    /**
     * Upload images for a specific registration ID using the new folder structure
     */
    @PostMapping("/{registrationId}")
    public ResponseEntity<?> uploadImagesToFolder(
            @PathVariable Long registrationId,
            @RequestParam("images") MultipartFile[] images
    ) {
        try {
            // Check if registration exists
            Optional<Registration> registration = registrationRepository.findById(registrationId);
            if (!registration.isPresent()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found with ID: " + registrationId);
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Upload images to Supabase storage in a folder named after registration ID
            Map<String, Object> uploadResult = supabaseService.uploadImagesToFolder(Arrays.asList(images), registrationId);
            
            // Extract folder path and image URLs from the result
            String folderPath = (String) uploadResult.get("folderPath");
            @SuppressWarnings("unchecked")
            List<String> imageUrls = (List<String>) uploadResult.get("imageUrls");
            
            // Update the registration with the image URLs for backward compatibility
            Registration registrationEntity = registration.get();
            if (imageUrls != null && !imageUrls.isEmpty()) {
                registrationEntity.setVehicleImageUrls(imageUrls);
                registrationRepository.save(registrationEntity);
                log.debug("Saved image URLs to registration via API (registrationId={}, count={})", registrationId, imageUrls.size());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Images uploaded successfully");
            response.put("registrationId", registrationId);
            response.put("folderPath", folderPath);
            response.put("imageUrls", imageUrls);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.warn("Error uploading images (registrationId={})", registrationId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error uploading images");
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get all images for a specific registration ID
     */
    @GetMapping("/{registrationId}")
    public ResponseEntity<?> getRegistrationImages(@PathVariable Long registrationId) {
        try {
            // Check if registration exists
            Optional<Registration> registration = registrationRepository.findById(registrationId);
            if (!registration.isPresent()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found with ID: " + registrationId);
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // First check if the registration has direct image URLs
            Registration reg = registration.get();
            List<String> imageUrls = reg.getVehicleImageUrls();
            
            if (imageUrls != null && !imageUrls.isEmpty()) {
                log.debug("Found direct image URLs in registration (registrationId={}, count={})", registrationId, imageUrls.size());
                
                // Filter out any hidden folder markers
                imageUrls = imageUrls.stream()
                    .filter(url -> !url.endsWith(".hidden_folder") && !url.endsWith(".folder"))
                    .toList();
                
                log.debug("After filtering, returning direct image URLs (registrationId={}, count={})", registrationId, imageUrls.size());
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("registrationId", registrationId);
                response.put("folderPath", registrationId.toString());
                response.put("imageUrls", imageUrls);
                
                return ResponseEntity.ok()
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Content-Type")
                        .body(response);
            } else {
                log.debug("No direct image URLs found in registration entity; checking folder (registrationId={})", registrationId);
            }
            
            // If no direct URLs, check for folder
            Optional<RegistrationImageFolder> folderInfo = 
                    registrationImageFolderRepository.findFirstByRegistrationId(registrationId);
            
            if (!folderInfo.isPresent()) {
                log.debug("No folder found for registration (registrationId={})", registrationId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("registrationId", registrationId);
                response.put("folderPath", null);
                response.put("imageUrls", List.of());
                return ResponseEntity.ok()
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Content-Type")
                        .body(response);
            }
            log.debug("Found folder for registration (registrationId={})", registrationId);
            
            // Get all image URLs for the registration from Supabase
            try {
                imageUrls = supabaseService.getRegistrationImages(registrationId);
                
                // Filter out any hidden folder markers just to be safe
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    imageUrls = imageUrls.stream()
                        .filter(url -> !url.endsWith(".hidden_folder") && !url.endsWith(".folder"))
                        .toList();
                    log.debug("Filtered storage image URLs (registrationId={}, count={})", registrationId, imageUrls.size());
                }
                
                // Update the registration with these URLs for future use
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    reg.setVehicleImageUrls(imageUrls);
                    registrationRepository.save(reg);
                    log.debug("Updated registration with storage image URLs (registrationId={}, count={})", registrationId, imageUrls.size());
                }
                log.debug("Retrieved image URLs from storage (registrationId={}, count={})", registrationId, imageUrls != null ? imageUrls.size() : 0);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("registrationId", registrationId);
                response.put("folderPath", folderInfo.get().getFolderPath());
                response.put("imageUrls", imageUrls != null ? imageUrls : List.of());
                
                return ResponseEntity.ok()
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Content-Type")
                        .body(response);
            } catch (Exception e) {
                log.warn("Error fetching images from Supabase (registrationId={})", registrationId, e);
                // Return the direct URLs we found initially
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("registrationId", registrationId);
                response.put("folderPath", folderInfo.get().getFolderPath());
                response.put("imageUrls", imageUrls != null ? imageUrls : List.of());
                response.put("error", "Error fetching from storage");
                
                return ResponseEntity.ok()
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Content-Type")
                        .body(response);
            }
        } catch (Exception e) {
            log.warn("Error retrieving images (registrationId={})", registrationId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error retrieving images");
            
            return ResponseEntity.badRequest()
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type")
                    .body(errorResponse);
        }
    }
    
    /**
     * Delete all images for a specific registration ID
     */
    @DeleteMapping("/{registrationId}")
    public ResponseEntity<?> deleteRegistrationImages(@PathVariable Long registrationId) {
        try {
            // Check if registration exists
            Optional<Registration> registration = registrationRepository.findById(registrationId);
            if (!registration.isPresent()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found with ID: " + registrationId);
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Delete all images in the folder
            supabaseService.deleteRegistrationFolder(registrationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All images deleted successfully");
            response.put("registrationId", registrationId);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.warn("Error deleting registration images (registrationId={})", registrationId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error deleting images");
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
} 