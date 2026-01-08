package com.example.demo.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Registration;
import com.example.demo.model.User;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SecurityUtils;
import com.example.demo.service.SupabaseService;

/**
 * Specialized controller for vehicle deletion operations
 * This controller handles all vehicle deletion operations with robust error handling
 */
@RestController
@RequestMapping("/api")
public class DeletionController {

    private static final Logger log = LoggerFactory.getLogger(DeletionController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private SupabaseService supabaseService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    /**
     * Main endpoint for vehicle deletion
     * Uses direct SQL with proper error handling
     */
    @DeleteMapping("/vehicles/delete/{registrationId}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long registrationId) {
        log.debug("Vehicle deletion requested (registrationId={})", registrationId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            Registration regOwnerCheck = registrationRepository.findById(registrationId).orElse(null);
            if (regOwnerCheck == null) {
                response.put("success", false);
                response.put("message", "Vehicle not found with ID: " + registrationId);
                return ResponseEntity.status(404).body(response);
            }
            if (regOwnerCheck.getUserId() == null || !regOwnerCheck.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }

            // Step 1: Check if vehicle exists
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            
            if (count == null || count == 0) {
                response.put("success", false);
                response.put("message", "Vehicle not found with ID: " + registrationId);
                return ResponseEntity.status(404).body(response);
            }
            
            // Step 2: Get vehicle details for logging
            try {
                Map<String, Object> vehicle = jdbcTemplate.queryForMap(
                    "SELECT id, vehicle_plate_number FROM registration WHERE id = ?",
                    registrationId
                );
                log.debug("Vehicle found for deletion (registrationId={})", vehicle.get("id"));
            } catch (Exception e) {
                log.debug("Could not fetch vehicle details for logging (registrationId={}): {}", registrationId, e.toString());
            }
            
            // Step 3: Delete images from Supabase storage first
            try {
                log.debug("Deleting vehicle images from storage (registrationId={})", registrationId);
                supabaseService.deleteAllVehicleImages(registrationId);
                log.debug("Deleted vehicle images from storage (registrationId={})", registrationId);
            } catch (Exception e) {
                log.warn("Error deleting vehicle images from storage (registrationId={}): {}", registrationId, e.toString());
                // Continue with deletion even if image deletion fails
            }
            
            // Step 4: Delete from registration_image_folders
            try {
                int folderRows = jdbcTemplate.update(
                    "DELETE FROM registration_image_folders WHERE registration_id = ?",
                    registrationId
                );
                log.debug("Deleted registration_image_folders rows (registrationId={}, rows={})", registrationId, folderRows);
            } catch (Exception e) {
                log.warn("Error deleting registration_image_folders rows (registrationId={}): {}", registrationId, e.toString());
                // Continue anyway
            }
            
            // Step 5: Clear image URLs to avoid LOB issues
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE registration SET vehicle_image_urls_json = '[]' WHERE id = ?",
                    registrationId
                );
                log.debug("Cleared image URLs JSON (registrationId={}, rows={})", registrationId, updated);
            } catch (Exception e) {
                log.warn("Error clearing image URLs JSON (registrationId={}): {}", registrationId, e.toString());
                // Continue anyway
            }
            
            // Step 6: Delete the registration
            int regRows = jdbcTemplate.update(
                "DELETE FROM registration WHERE id = ?",
                registrationId
            );
            log.debug("Deleted registration row (registrationId={}, rows={})", registrationId, regRows);
            
            if (regRows == 0) {
                log.warn("No rows deleted from registration table (registrationId={})", registrationId);
                response.put("success", false);
                response.put("message", "Failed to delete vehicle - no rows affected");
                return ResponseEntity.status(500).body(response);
            }
            
            // Step 7: Verify deletion
            Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            
            if (remaining != null && remaining > 0) {
                log.warn("Vehicle still exists after deletion attempt (registrationId={})", registrationId);
                response.put("success", false);
                response.put("message", "Failed to delete vehicle - it still exists after deletion attempt");
                return ResponseEntity.status(500).body(response);
            }
            
            // Success response
            response.put("success", true);
            response.put("message", "Vehicle successfully deleted");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error deleting vehicle (registrationId={})", registrationId, e);
            
            response.put("success", false);
            response.put("message", "Failed to delete vehicle");
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Emergency endpoint for vehicle deletion with foreign key checks disabled
     * This is a last resort method when normal deletion fails
     */
    @DeleteMapping("/vehicles/force-delete/{registrationId}")
    public ResponseEntity<?> forceDeleteVehicle(@PathVariable Long registrationId) {
        log.warn("FORCE vehicle deletion requested (registrationId={})", registrationId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            Registration regOwnerCheck = registrationRepository.findById(registrationId).orElse(null);
            if (regOwnerCheck == null) {
                response.put("success", false);
                response.put("message", "Vehicle not found with ID: " + registrationId);
                return ResponseEntity.status(404).body(response);
            }
            if (regOwnerCheck.getUserId() == null || !regOwnerCheck.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }

            // Step 1: Check if vehicle exists
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            
            if (count == null || count == 0) {
                response.put("success", false);
                response.put("message", "Vehicle not found with ID: " + registrationId);
                return ResponseEntity.status(404).body(response);
            }
            
            // Step 2: Delete images from Supabase storage first
            try {
                log.debug("FORCE deleting vehicle images from storage (registrationId={})", registrationId);
                supabaseService.deleteAllVehicleImages(registrationId);
                log.debug("FORCE deleted vehicle images from storage (registrationId={})", registrationId);
            } catch (Exception e) {
                log.warn("Error deleting vehicle images from storage during FORCE delete (registrationId={}): {}", registrationId, e.toString());
                // Continue with deletion even if image deletion fails
            }
            
            // Step 3: Disable foreign key checks temporarily
            log.debug("Disabling foreign key checks (registrationId={})", registrationId);
            jdbcTemplate.execute("SET CONSTRAINTS ALL DEFERRED");
            
            // Step 4: Delete from registration_image_folders first
            try {
                int folderRows = jdbcTemplate.update(
                    "DELETE FROM registration_image_folders WHERE registration_id = ?",
                    registrationId
                );
                log.debug("Deleted registration_image_folders rows (registrationId={}, rows={})", registrationId, folderRows);
            } catch (Exception e) {
                log.warn("Error deleting registration_image_folders rows (registrationId={}): {}", registrationId, e.toString());
                // Continue anyway
            }
            
            // Step 5: Clear image URLs to avoid LOB issues
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE registration SET vehicle_image_urls_json = '[]' WHERE id = ?",
                    registrationId
                );
                log.debug("Cleared image URLs JSON (registrationId={}, rows={})", registrationId, updated);
            } catch (Exception e) {
                log.warn("Error clearing image URLs JSON (registrationId={}): {}", registrationId, e.toString());
                // Continue anyway
            }
            
            // Step 6: Delete from registration table using raw SQL
            int regRows = jdbcTemplate.update(
                "DELETE FROM registration WHERE id = ?",
                registrationId
            );
            log.debug("Deleted registration row (registrationId={}, rows={})", registrationId, regRows);
            
            // Step 7: Re-enable foreign key checks
            log.debug("Re-enabling foreign key checks (registrationId={})", registrationId);
            jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");
            
            // Step 8: Verify deletion
            Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            
            if (remaining != null && remaining > 0) {
                log.warn("Vehicle still exists after FORCE deletion attempt (registrationId={})", registrationId);
                response.put("success", false);
                response.put("message", "Failed to delete vehicle - it still exists after deletion attempt");
                return ResponseEntity.status(500).body(response);
            }
            
            // Success response
            response.put("success", true);
            response.put("message", "Vehicle successfully deleted using force delete");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in FORCE vehicle deletion (registrationId={})", registrationId, e);
            
            response.put("success", false);
            response.put("message", "Failed to delete vehicle");
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * DELETE /api/deleteUser/{userId}
     * Deletes user account and all associated data
     * 
     * @param userId The ID of the user to delete
     * @return Response with success/error message
     */
    @DeleteMapping("/deleteUser/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || userId == null || !userId.equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }

            // Find the user
            Optional<User> optionalUser = userRepository.findById(userId);
            
            if (!optionalUser.isPresent()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "User not found with ID: " + userId);
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            User user = optionalUser.get();
            
            // Log the account deletion request
            log.info("Account deletion requested (userId={})", userId);
            
            // Create a list to track any deletion errors
            List<String> deletionErrors = new ArrayList<>();
            
            // Find all registrations associated with this user by userId
            List<Registration> userRegistrations = registrationRepository.findByUserId(userId);
            log.debug("Found registrations for deletion (userId={}, count={})", userId, userRegistrations.size());
            
            // First handle all potential foreign key constraint issues, in correct order
            
            // 1. First handle any other tables that might have foreign keys to user table
            
            // 2. Delete all registrations associated with this user
            for (Registration registration : userRegistrations) {
                try {
                    Long registrationId = registration.getId();
                    log.debug("Processing registration deletion (registrationId={})", registrationId);
                    // IMPORTANT: Delete images BEFORE removing folder records so path info is still available
                    // 2.1 Delete vehicle images from storage
                    try {
                        log.debug("Deleting vehicle images for registration (registrationId={})", registrationId);
                        supabaseService.deleteAllVehicleImages(registrationId);
                        log.debug("Deleted vehicle images for registration (registrationId={})", registrationId);
                    } catch (Exception e) {
                        String errorMsg = "Error deleting vehicle images for registration " + registrationId + ": " + e.getMessage();
                        log.warn(errorMsg);
                        deletionErrors.add(errorMsg);
                        // Continue with deletion
                    }

                    // 2.2 Delete registration_image_folders entries (after storage cleanup)
                    try {
                        int rowsDeleted = jdbcTemplate.update(
                            "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                            registrationId
                        );
                        log.debug("Deleted registration_image_folders rows (registrationId={}, rows={})", registrationId, rowsDeleted);
                    } catch (Exception e) {
                        String errorMsg = "Error deleting from registration_image_folders for registration " + registrationId + ": " + e.getMessage();
                        log.warn(errorMsg);
                        deletionErrors.add(errorMsg);
                        // Continue with deletion
                    }
                    
                    // 2.3 Clear image URLs to avoid LOB issues
                    registration.setVehicleImageUrls(new ArrayList<>());
                    registrationRepository.save(registration);
                    
                    // 2.4 Delete the registration
                    registrationRepository.delete(registration);
                    log.debug("Deleted registration record (registrationId={})", registrationId);
                    
                } catch (Exception e) {
                    String errorMsg = "Error deleting registration " + registration.getId() + ": " + e.getMessage();
                    log.warn(errorMsg, e);
                    deletionErrors.add(errorMsg);
                    
                    // Try direct SQL as fallback
                    try {
                        int rowsDeleted = jdbcTemplate.update("DELETE FROM registration WHERE id = ?", registration.getId());
                        if (rowsDeleted > 0) {
                            log.debug("Deleted registration using SQL fallback (registrationId={})", registration.getId());
                        } else {
                            log.warn("No rows deleted with SQL fallback (registrationId={})", registration.getId());
                        }
                    } catch (Exception ex) {
                        log.warn("SQL fallback failed (registrationId={}): {}", registration.getId(), ex.toString());
                    }
                }
            }
            
            // 3. Delete profile photo if exists
            try {
                if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
                    log.debug("Deleting profile photo for user (userId={})", userId);
                    supabaseService.deleteProfilePhoto(user.getProfilePhotoUrl());
                    log.debug("Deleted profile photo for user (userId={})", userId);
                }
            } catch (Exception e) {
                String errorMsg = "Error deleting profile photo: " + e.getMessage();
                log.warn(errorMsg);
                deletionErrors.add(errorMsg);
                // Continue with user deletion
            }
            
            // 5. Delete the user record
            try {
                // Clear profile photo URL to avoid LOB issues
                user.setProfilePhotoUrl(null);
                userRepository.save(user);
                
                // Delete the user record
                userRepository.delete(user);
                log.info("Deleted user from database (userId={})", userId);
            } catch (Exception e) {
                String errorMsg = "Error deleting user from database: " + e.getMessage();
                log.error(errorMsg, e);
                deletionErrors.add(errorMsg);
                
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Failed to delete user");
                errorResponse.put("errors", deletionErrors);
                return ResponseEntity.status(500).body(errorResponse);
            }
            
            // If there were some errors but we got to this point, the account is deleted but with warnings
            if (deletionErrors.size() > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "User account deleted but with warnings");
                response.put("warnings", deletionErrors);
                return ResponseEntity.ok(response);
            }
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User account and all associated data successfully deleted");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in deleteUser (userId={})", userId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete user");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Admin endpoint to delete a vehicle by its registration number
     * This is a more direct method than the user-facing endpoints
     */
    @PostMapping("/admin/delete-vehicle")
    public ResponseEntity<?> deleteVehicleByRegistrationNumber(@RequestBody Map<String, String> request) {
        String vehiclePlateNumber = request.get("vehiclePlateNumber");
        
        if (vehiclePlateNumber == null || vehiclePlateNumber.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Vehicle plate number is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        log.info("ADMIN DELETE: Attempting to delete vehicle by plate number");
        
        try {
            // Find the registration by vehicle plate number
            Registration registration = registrationRepository.findByVehiclePlateNumber(vehiclePlateNumber);
            
            if (registration == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Vehicle not found with plate number: " + vehiclePlateNumber);
                return ResponseEntity.notFound().build();
            }
            
            Long registrationId = registration.getId();
            log.info("ADMIN DELETE: Found vehicle (registrationId={})", registrationId);
            
            // Delete registration_image_folders entries
            int deletedFolders = jdbcTemplate.update(
                "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                registrationId
            );
            log.debug("ADMIN DELETE: Deleted folder records (registrationId={}, rows={})", registrationId, deletedFolders);
            
            // Clear image URLs to avoid LOB issues
            registration.setVehicleImageUrls(new ArrayList<>());
            registrationRepository.save(registration);
            
            // Delete the registration
            registrationRepository.delete(registration);
            log.info("ADMIN DELETE: Deleted registration record (registrationId={})", registrationId);
            
            // Clean up storage buckets
            try {
                supabaseService.deleteAllVehicleImages(registrationId);
                log.debug("ADMIN DELETE: Cleaned up storage (registrationId={})", registrationId);
            } catch (IOException e) {
                log.warn("ADMIN DELETE: Error cleaning up storage (registrationId={}): {}", registrationId, e.toString());
                // Continue with response as the database record is deleted
            }
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle successfully deleted");
            response.put("vehiclePlateNumber", vehiclePlateNumber);
            response.put("registrationId", registrationId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("ADMIN DELETE: Error deleting vehicle", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting vehicle");
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Admin endpoint to delete a vehicle by its ID
     */
    @DeleteMapping("/admin/vehicles/{registrationId}")
    public ResponseEntity<?> deleteVehicleById(@PathVariable Long registrationId) {
        log.info("ADMIN DELETE: Attempting to delete vehicle (registrationId={})", registrationId);
        
        try {
            // Find the registration
            Registration registration = registrationRepository.findById(registrationId)
                .orElse(null);
            
            if (registration == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Vehicle not found with ID: " + registrationId);
                return ResponseEntity.notFound().build();
            }
            
            String vehiclePlateNumber = registration.getVehiclePlateNumber();
            log.debug("ADMIN DELETE: Found vehicle (registrationId={})", registrationId);
            
            // Delete registration_image_folders entries
            int deletedFolders = jdbcTemplate.update(
                "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                registrationId
            );
            log.debug("ADMIN DELETE: Deleted folder records (registrationId={}, rows={})", registrationId, deletedFolders);
            
            // Clear image URLs to avoid LOB issues
            registration.setVehicleImageUrls(new ArrayList<>());
            registrationRepository.save(registration);
            
            // Delete the registration
            registrationRepository.delete(registration);
            log.info("ADMIN DELETE: Deleted registration record (registrationId={})", registrationId);
            
            // Clean up storage buckets
            try {
                supabaseService.deleteAllVehicleImages(registrationId);
                log.debug("ADMIN DELETE: Cleaned up storage (registrationId={})", registrationId);
            } catch (IOException e) {
                log.warn("ADMIN DELETE: Error cleaning up storage (registrationId={}): {}", registrationId, e.toString());
                // Continue with response as the database record is deleted
            }
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle successfully deleted");
            response.put("vehiclePlateNumber", vehiclePlateNumber);
            response.put("registrationId", registrationId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("ADMIN DELETE: Error deleting vehicle (registrationId={})", registrationId, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting vehicle");
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 