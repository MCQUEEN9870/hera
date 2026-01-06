package com.example.demo.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.example.demo.service.SupabaseService;

/**
 * Specialized controller for vehicle deletion operations
 * This controller handles all vehicle deletion operations with robust error handling
 */
@RestController
@RequestMapping("/api")
public class DeletionController {

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
        System.out.println("DeletionController: Starting deletion for vehicle ID: " + registrationId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
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
                System.out.println("DeletionController: Found vehicle: " + vehicle.get("vehicle_plate_number") + " (ID: " + vehicle.get("id") + ")");
            } catch (Exception e) {
                System.out.println("DeletionController: Could not get vehicle details: " + e.getMessage());
            }
            
            // Step 3: Delete images from Supabase storage first
            try {
                System.out.println("DeletionController: Deleting vehicle images from storage");
                supabaseService.deleteAllVehicleImages(registrationId);
                System.out.println("DeletionController: Successfully deleted images from storage");
            } catch (Exception e) {
                System.err.println("DeletionController: Error deleting images from storage: " + e.getMessage());
                // Continue with deletion even if image deletion fails
            }
            
            // Step 4: Delete from registration_image_folders
            try {
                int folderRows = jdbcTemplate.update(
                    "DELETE FROM registration_image_folders WHERE registration_id = ?",
                    registrationId
                );
                System.out.println("DeletionController: Deleted " + folderRows + " rows from registration_image_folders");
            } catch (Exception e) {
                System.err.println("DeletionController: Error deleting from registration_image_folders: " + e.getMessage());
                // Continue anyway
            }
            
            // Step 5: Clear image URLs to avoid LOB issues
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE registration SET vehicle_image_urls_json = '[]' WHERE id = ?",
                    registrationId
                );
                System.out.println("DeletionController: Cleared image URLs for " + updated + " rows");
            } catch (Exception e) {
                System.err.println("DeletionController: Error clearing image URLs: " + e.getMessage());
                // Continue anyway
            }
            
            // Step 6: Delete the registration
            int regRows = jdbcTemplate.update(
                "DELETE FROM registration WHERE id = ?",
                registrationId
            );
            System.out.println("DeletionController: Deleted " + regRows + " rows from registration");
            
            if (regRows == 0) {
                System.err.println("DeletionController: No rows deleted from registration table");
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
                System.err.println("DeletionController: Vehicle still exists after deletion!");
                response.put("success", false);
                response.put("message", "Failed to delete vehicle - it still exists after deletion attempt");
                return ResponseEntity.status(500).body(response);
            }
            
            // Success response
            response.put("success", true);
            response.put("message", "Vehicle successfully deleted");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("DeletionController: Error in deletion: " + e.getMessage());
            e.printStackTrace();
            
            response.put("success", false);
            response.put("message", "Failed to delete vehicle: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Emergency endpoint for vehicle deletion with foreign key checks disabled
     * This is a last resort method when normal deletion fails
     */
    @DeleteMapping("/vehicles/force-delete/{registrationId}")
    public ResponseEntity<?> forceDeleteVehicle(@PathVariable Long registrationId) {
        System.out.println("DeletionController: Starting FORCE deletion for vehicle ID: " + registrationId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
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
                System.out.println("DeletionController: FORCE deleting vehicle images from storage");
                supabaseService.deleteAllVehicleImages(registrationId);
                System.out.println("DeletionController: Successfully deleted images from storage");
            } catch (Exception e) {
                System.err.println("DeletionController: Error deleting images from storage: " + e.getMessage());
                // Continue with deletion even if image deletion fails
            }
            
            // Step 3: Disable foreign key checks temporarily
            System.out.println("DeletionController: Disabling foreign key checks");
            jdbcTemplate.execute("SET CONSTRAINTS ALL DEFERRED");
            
            // Step 4: Delete from registration_image_folders first
            try {
                int folderRows = jdbcTemplate.update(
                    "DELETE FROM registration_image_folders WHERE registration_id = ?",
                    registrationId
                );
                System.out.println("DeletionController: Deleted " + folderRows + " rows from registration_image_folders");
            } catch (Exception e) {
                System.err.println("DeletionController: Error deleting from registration_image_folders: " + e.getMessage());
                // Continue anyway
            }
            
            // Step 5: Clear image URLs to avoid LOB issues
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE registration SET vehicle_image_urls_json = '[]' WHERE id = ?",
                    registrationId
                );
                System.out.println("DeletionController: Cleared image URLs for " + updated + " rows");
            } catch (Exception e) {
                System.err.println("DeletionController: Error clearing image URLs: " + e.getMessage());
                // Continue anyway
            }
            
            // Step 6: Delete from registration table using raw SQL
            int regRows = jdbcTemplate.update(
                "DELETE FROM registration WHERE id = ?",
                registrationId
            );
            System.out.println("DeletionController: Deleted " + regRows + " rows from registration");
            
            // Step 7: Re-enable foreign key checks
            System.out.println("DeletionController: Re-enabling foreign key checks");
            jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");
            
            // Step 8: Verify deletion
            Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            
            if (remaining != null && remaining > 0) {
                System.err.println("DeletionController: Vehicle still exists after force deletion!");
                response.put("success", false);
                response.put("message", "Failed to delete vehicle - it still exists after deletion attempt");
                return ResponseEntity.status(500).body(response);
            }
            
            // Success response
            response.put("success", true);
            response.put("message", "Vehicle successfully deleted using force delete");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("DeletionController: Error in forced deletion: " + e.getMessage());
            e.printStackTrace();
            
            response.put("success", false);
            response.put("message", "Failed to delete vehicle: " + e.getMessage());
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
            System.out.println("Account deletion requested for user ID: " + userId);
            
            // Create a list to track any deletion errors
            List<String> deletionErrors = new ArrayList<>();
            
            // Find all registrations associated with this user by userId
            List<Registration> userRegistrations = registrationRepository.findByUserId(userId);
            System.out.println("Found " + userRegistrations.size() + " vehicle registrations for user ID: " + userId);
            
            // First handle all potential foreign key constraint issues, in correct order
            
            // 1. First handle any other tables that might have foreign keys to user table
            
            // 2. Delete all registrations associated with this user
            for (Registration registration : userRegistrations) {
                try {
                    Long registrationId = registration.getId();
                    System.out.println("Processing registration ID: " + registrationId);
                    // IMPORTANT: Delete images BEFORE removing folder records so path info is still available
                    // 2.1 Delete vehicle images from storage
                    try {
                        System.out.println("Deleting vehicle images for registration: " + registrationId);
                        supabaseService.deleteAllVehicleImages(registrationId);
                        System.out.println("Successfully deleted vehicle images for registration: " + registrationId);
                    } catch (Exception e) {
                        String errorMsg = "Error deleting vehicle images for registration " + registrationId + ": " + e.getMessage();
                        System.err.println(errorMsg);
                        deletionErrors.add(errorMsg);
                        // Continue with deletion
                    }

                    // 2.2 Delete registration_image_folders entries (after storage cleanup)
                    try {
                        int rowsDeleted = jdbcTemplate.update(
                            "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                            registrationId
                        );
                        System.out.println("Deleted " + rowsDeleted + " rows from registration_image_folders for registration: " + registrationId);
                    } catch (Exception e) {
                        String errorMsg = "Error deleting from registration_image_folders for registration " + registrationId + ": " + e.getMessage();
                        System.err.println(errorMsg);
                        deletionErrors.add(errorMsg);
                        // Continue with deletion
                    }
                    
                    // 2.3 Clear image URLs to avoid LOB issues
                    registration.setVehicleImageUrls(new ArrayList<>());
                    registrationRepository.save(registration);
                    
                    // 2.4 Delete the registration
                    registrationRepository.delete(registration);
                    System.out.println("Successfully deleted registration: " + registrationId);
                    
                } catch (Exception e) {
                    String errorMsg = "Error deleting registration " + registration.getId() + ": " + e.getMessage();
                    System.err.println(errorMsg);
                    e.printStackTrace();
                    deletionErrors.add(errorMsg);
                    
                    // Try direct SQL as fallback
                    try {
                        int rowsDeleted = jdbcTemplate.update("DELETE FROM registration WHERE id = ?", registration.getId());
                        if (rowsDeleted > 0) {
                            System.out.println("Successfully deleted registration using SQL: " + registration.getId());
                        } else {
                            System.err.println("No rows deleted with SQL for registration: " + registration.getId());
                        }
                    } catch (Exception ex) {
                        System.err.println("SQL fallback also failed for registration " + registration.getId() + ": " + ex.getMessage());
                    }
                }
            }
            
            // 3. Delete profile photo if exists
            try {
                if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
                    System.out.println("Deleting profile photo for user ID: " + userId);
                    supabaseService.deleteProfilePhoto(user.getProfilePhotoUrl());
                    System.out.println("Successfully deleted profile photo for user ID: " + userId);
                }
            } catch (Exception e) {
                String errorMsg = "Error deleting profile photo: " + e.getMessage();
                System.err.println(errorMsg);
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
                System.out.println("Successfully deleted user from database: " + userId);
            } catch (Exception e) {
                String errorMsg = "Error deleting user from database: " + e.getMessage();
                System.err.println(errorMsg);
                e.printStackTrace();
                deletionErrors.add(errorMsg);
                
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Failed to delete user: " + e.getMessage());
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
            System.err.println("Error in deleteUser: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete user: " + e.getMessage());
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
        
        System.out.println("ADMIN DELETE: Attempting to delete vehicle with plate number: " + vehiclePlateNumber);
        
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
            System.out.println("ADMIN DELETE: Found vehicle with ID: " + registrationId);
            
            // Delete registration_image_folders entries
            int deletedFolders = jdbcTemplate.update(
                "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                registrationId
            );
            System.out.println("ADMIN DELETE: Deleted " + deletedFolders + " folder records");
            
            // Clear image URLs to avoid LOB issues
            registration.setVehicleImageUrls(new ArrayList<>());
            registrationRepository.save(registration);
            
            // Delete the registration
            registrationRepository.delete(registration);
            System.out.println("ADMIN DELETE: Successfully deleted registration record");
            
            // Clean up storage buckets
            try {
                supabaseService.deleteAllVehicleImages(registrationId);
                System.out.println("ADMIN DELETE: Successfully cleaned up storage buckets");
            } catch (IOException e) {
                System.err.println("ADMIN DELETE: Error cleaning up storage buckets: " + e.getMessage());
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
            System.err.println("ADMIN DELETE: Error deleting vehicle: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting vehicle: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Admin endpoint to delete a vehicle by its ID
     */
    @DeleteMapping("/admin/vehicles/{registrationId}")
    public ResponseEntity<?> deleteVehicleById(@PathVariable Long registrationId) {
        System.out.println("ADMIN DELETE: Attempting to delete vehicle with ID: " + registrationId);
        
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
            System.out.println("ADMIN DELETE: Found vehicle: " + vehiclePlateNumber);
            
            // Delete registration_image_folders entries
            int deletedFolders = jdbcTemplate.update(
                "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                registrationId
            );
            System.out.println("ADMIN DELETE: Deleted " + deletedFolders + " folder records");
            
            // Clear image URLs to avoid LOB issues
            registration.setVehicleImageUrls(new ArrayList<>());
            registrationRepository.save(registration);
            
            // Delete the registration
            registrationRepository.delete(registration);
            System.out.println("ADMIN DELETE: Successfully deleted registration record");
            
            // Clean up storage buckets
            try {
                supabaseService.deleteAllVehicleImages(registrationId);
                System.out.println("ADMIN DELETE: Successfully cleaned up storage buckets");
            } catch (IOException e) {
                System.err.println("ADMIN DELETE: Error cleaning up storage buckets: " + e.getMessage());
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
            System.err.println("ADMIN DELETE: Error deleting vehicle: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting vehicle: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 