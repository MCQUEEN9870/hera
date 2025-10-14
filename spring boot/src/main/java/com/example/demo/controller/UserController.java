package com.example.demo.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.Registration;
import com.example.demo.model.User;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.SupabaseService;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private SupabaseService supabaseService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        System.out.println("Received Data: " + user);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User registered successfully!");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Update user's email by contact number
     */
    @PutMapping("/{contactNumber}/email")
    public ResponseEntity<?> updateUserEmail(@PathVariable String contactNumber, @RequestBody Map<String, Object> requestBody) {
        try {
            if (contactNumber == null || contactNumber.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Contact number is required"));
            }
            Object emailObj = requestBody.get("email");
            String email = emailObj == null ? null : String.valueOf(emailObj).trim();
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email is required"));
            }
            // Basic email validation
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please enter a valid email address"));
            }

            User user = userRepository.findByContactNumber(contactNumber);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
            }

            user.setEmail(email);
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Email updated successfully");
            response.put("email", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to update email: " + e.getMessage()));
        }
    }
    
    /**
     * Get user profile by contact number
     */
    @GetMapping("/{contactNumber}")
    public ResponseEntity<?> getUserProfile(@PathVariable String contactNumber) {
        User user = userRepository.findByContactNumber(contactNumber);
        
        if (user == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "User not found");
            return ResponseEntity.status(404).body(errorResponse);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("fullName", user.getFullName());
        userData.put("contactNumber", user.getContactNumber());
        userData.put("email", user.getEmail());
        userData.put("profilePhoto", user.getProfilePhotoUrl());
        userData.put("membership", user.getMembership());
        
        // Include membership details if premium
        if (user.getMembership() != null && user.getMembership().equalsIgnoreCase("Premium")) {
            userData.put("membershipPurchaseTime", user.getMembershipPurchaseTime());
            userData.put("membershipExpireTime", user.getMembershipExpireTime());
        }
        
        // Use actual join date from entity or current date if null
        String joinDate = user.getJoinDate() != null 
            ? user.getJoinDate().toString() 
            : java.time.LocalDateTime.now().toString();
        userData.put("joinDate", joinDate);
        
        response.put("user", userData);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get user vehicles by contact number
     */
    @GetMapping("/{contactNumber}/vehicles")
    public ResponseEntity<?> getUserVehicles(@PathVariable String contactNumber) {
        // Find the user
        User user = userRepository.findByContactNumber(contactNumber);
        
        if (user == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "User not found");
            return ResponseEntity.status(404).body(errorResponse);
        }
        
        // Find all registrations with the user ID
        List<Registration> registrations = registrationRepository.findByUserId(user.getId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        List<Map<String, Object>> vehicleList = new ArrayList<>();
        
        for (Registration reg : registrations) {
            Map<String, Object> vehicle = new HashMap<>();
            vehicle.put("id", reg.getId().toString());
            vehicle.put("number", reg.getVehiclePlateNumber());
            vehicle.put("type", reg.getVehicleType());
            vehicle.put("status", "active"); // Default to active
            vehicle.put("owner", reg.getFullName());
            vehicle.put("userId", reg.getUserId()); // Add the user ID
            
            // Use actual registration date from entity
            String registrationDate = reg.getRegistrationDate() != null 
                ? reg.getRegistrationDate().toString() 
                : java.time.LocalDate.now().toString();
            vehicle.put("registrationDate", registrationDate);
            
            vehicle.put("contact", reg.getContactNumber());
            vehicle.put("whatsapp", reg.getWhatsappNumber());
            vehicle.put("alternateContact", reg.getAlternateContactNumber());
            vehicle.put("location", reg.getCity() + ", " + reg.getState());
            vehicle.put("pincode", reg.getPincode());
            
            // Handle vehicle images
            Map<String, String> photos = new HashMap<>();
            List<String> imageUrls = reg.getVehicleImageUrls();
            
            // Set default image if no images are available
            String defaultImage = "attached_assets/images/default-vehicle.png";
            
            // Map image URLs to the expected format
            photos.put("front", imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage);
            photos.put("side", imageUrls.size() > 1 ? imageUrls.get(1) : (imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage));
            photos.put("back", imageUrls.size() > 2 ? imageUrls.get(2) : (imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage));
            photos.put("loading", imageUrls.size() > 3 ? imageUrls.get(3) : (imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage));
            
            vehicle.put("photos", photos);
            
            // Add service highlights
            Map<String, String> highlights = new HashMap<>();
            highlights.put("highlight1", reg.getHighlight1());
            highlights.put("highlight2", reg.getHighlight2());
            highlights.put("highlight3", reg.getHighlight3());
            highlights.put("highlight4", reg.getHighlight4());
            highlights.put("highlight5", reg.getHighlight5());
            vehicle.put("highlights", highlights);
            
            vehicleList.add(vehicle);
        }
        
        response.put("vehicles", vehicleList);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all registered user locations - to be used for displaying location in feedback carousels
     */
    @GetMapping("/get-user-locations")
    public ResponseEntity<?> getUserLocations() {
        System.out.println("Fetching user locations for feedback carousel");
        
        // Find all registrations to get user locations
        List<Registration> registrations = registrationRepository.findAll();
        
        Map<String, String> userLocations = new HashMap<>();
        
        // For each registration, add the user's location to the map
        // If a user has multiple registrations, the latest one's location will be used
        for (Registration reg : registrations) {
            // Only include registrations with a valid userId
            if (reg.getUserId() != null) {
                String location = reg.getCity();
                if (reg.getState() != null && !reg.getState().isEmpty()) {
                    location += ", " + reg.getState();
                }
                
                // Map the userId to the location
                userLocations.put(reg.getUserId().toString(), location);
            }
        }
        
        return ResponseEntity.ok(userLocations);
    }
    
    /**
     * Get user profile photo
     */
    @GetMapping("/{contactNumber}/profile-photo")
    public ResponseEntity<?> getProfilePhoto(@PathVariable String contactNumber) {
        User user = userRepository.findByContactNumber(contactNumber);
        
        if (user == null || user.getProfilePhotoUrl() == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Profile photo not found");
            return ResponseEntity.status(404).body(response);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("photoUrl", user.getProfilePhotoUrl());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Upload profile photo
     */
    @PostMapping("/{contactNumber}/profile-photo")
    public ResponseEntity<?> uploadProfilePhoto(
            @PathVariable String contactNumber,
            @RequestParam("photo") MultipartFile photo) {
        
        System.out.println("Received profile photo upload request for user: " + contactNumber);
        System.out.println("Photo details - Name: " + photo.getOriginalFilename() + 
                          ", Size: " + photo.getSize() + 
                          " bytes, ContentType: " + photo.getContentType());
        
        // Validate photo size and format
        if (photo.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Empty file received");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        if (photo.getSize() > 10 * 1024 * 1024) { // 10MB limit
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "File too large. Maximum size allowed is 10MB");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Only image files are allowed");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        // Find or create user if needed
        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) {
            System.out.println("User not found. Creating new user for: " + contactNumber);
            user = new User();
            user.setContactNumber(contactNumber);
            // Set default values for required fields
            user.setFullName("User " + contactNumber.substring(Math.max(0, contactNumber.length() - 4)));
        } else {
            System.out.println("Found existing user: " + user.getFullName() + " (" + contactNumber + ")");
            
            // If user already has a profile photo, delete the old one
            if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
                try {
                    System.out.println("Deleting old profile photo: " + user.getProfilePhotoUrl());
                    supabaseService.deleteProfilePhoto(user.getProfilePhotoUrl());
                } catch (Exception e) {
                    System.err.println("Warning: Failed to delete old profile photo: " + e.getMessage());
                    // Continue with upload even if delete fails
                }
            }
        }
        
        try {
            String photoUrl = supabaseService.uploadProfilePhoto(photo);
            user.setProfilePhotoUrl(photoUrl);
            userRepository.save(user);
            
            System.out.println("Profile photo upload successful. URL: " + photoUrl);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("photoUrl", photoUrl);
            response.put("message", "Profile photo uploaded successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // Print full stack trace for debugging
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to upload profile photo: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Delete profile photo
     */
    @DeleteMapping("/{contactNumber}/profile-photo")
    public ResponseEntity<?> deleteProfilePhoto(@PathVariable String contactNumber) {
        System.out.println("Received profile photo delete request for user: " + contactNumber);
        
        User user = userRepository.findByContactNumber(contactNumber);
        
        if (user == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "User not found");
            return ResponseEntity.status(404).body(errorResponse);
        }
        
        // Check if user has a profile photo
        String profilePhotoUrl = user.getProfilePhotoUrl();
        if (profilePhotoUrl == null || profilePhotoUrl.isEmpty()) {
            // No photo to delete, return success
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "No profile photo to delete");
            return ResponseEntity.ok(response);
        }
        
        System.out.println("Attempting to delete profile photo: " + profilePhotoUrl);
        
        try {
            // Delete from Supabase
            supabaseService.deleteProfilePhoto(profilePhotoUrl);
            
            // Update user
            user.setProfilePhotoUrl(null);
            userRepository.save(user);
            
            System.out.println("Profile photo deleted successfully");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile photo deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // Full stack trace for debugging
            System.err.println("Error deleting profile photo: " + e.getMessage());
            
            // Even if Supabase deletion fails, still remove the URL from the user
            try {
                user.setProfilePhotoUrl(null);
                userRepository.save(user);
                System.out.println("Removed profile photo URL from user record despite storage deletion failure");
            } catch (Exception ex) {
                System.err.println("Failed to update user record: " + ex.getMessage());
            }
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete profile photo: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Check if user has reached vehicle registration limit
     */
    @GetMapping("/{contactNumber}/check-vehicle-limit")
    public ResponseEntity<?> checkVehicleLimit(@PathVariable String contactNumber) {
        try {
            // Find the user
            User user = userRepository.findByContactNumber(contactNumber);
            
            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "User not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Find count of user's registered vehicles using userId
            long vehicleCount = registrationRepository.findByUserId(user.getId()).size();
            
            // Define vehicle limits based on membership
            String membership = "Standard"; // Default membership
            int maxVehicles = 3; // Default limit for standard membership
            
            if (user.getMembership() != null && user.getMembership().equalsIgnoreCase("Premium")) {
                membership = "Premium";
                maxVehicles = 5; // Limit for premium membership
            }
            
            boolean hasReachedLimit = vehicleCount >= maxVehicles;
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("contactNumber", contactNumber);
            response.put("userId", user.getId());
            response.put("membership", membership);
            response.put("vehicleCount", vehicleCount);
            response.put("maxVehicles", maxVehicles);
            response.put("hasReachedLimit", hasReachedLimit);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error checking vehicle limit: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Delete user account and all associated data
     */
    @DeleteMapping("/{contactNumber}")
    public ResponseEntity<?> deleteUserAccount(@PathVariable String contactNumber) {
        try {
            // Find the user
            User user = userRepository.findByContactNumber(contactNumber);
            
            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "User not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Log the account deletion request
            System.out.println("Account deletion requested for: " + contactNumber + " (User ID: " + user.getId() + ")");
            
            // Create a list to track any deletion errors
            List<String> deletionErrors = new ArrayList<>();
            
            // Find all registrations associated with this user by userId
            List<Registration> userRegistrations = registrationRepository.findByUserId(user.getId());
            System.out.println("Found " + userRegistrations.size() + " vehicle registrations for user: " + contactNumber);
            
            // Clear the registration_user_id_fkey constraint
            for (Registration registration : userRegistrations) {
                try {
                    // Delete each vehicle registration
                    System.out.println("Deleting vehicle registration: " + registration.getId());
                    
                    // Delete registration_image_folders entries
                    try {
                        int rowsDeleted = jdbcTemplate.update(
                            "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                            registration.getId()
                        );
                        System.out.println("Deleted " + rowsDeleted + " rows from registration_image_folders for registration: " + registration.getId());
                    } catch (Exception e) {
                        String errorMsg = "Error deleting from registration_image_folders for registration " + registration.getId() + ": " + e.getMessage();
                        System.err.println(errorMsg);
                        deletionErrors.add(errorMsg);
                        // Continue with deletion
                    }
                    
                    // Delete vehicle images from storage
                    try {
                        System.out.println("Deleting vehicle images for registration: " + registration.getId());
                        supabaseService.deleteRegistrationFolder(registration.getId());
                        System.out.println("Successfully deleted vehicle images for registration: " + registration.getId());
                    } catch (Exception e) {
                        String errorMsg = "Error deleting vehicle images for registration " + registration.getId() + ": " + e.getMessage();
                        System.err.println(errorMsg);
                        deletionErrors.add(errorMsg);
                        // Continue with deletion
                    }
                    
                    // Clear image URLs to avoid LOB issues
                    registration.setVehicleImageUrls(new ArrayList<>());
                    registrationRepository.save(registration);
                    
                    // Delete the registration
                    registrationRepository.delete(registration);
                    System.out.println("Successfully deleted registration: " + registration.getId());
                    
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
            
            // Delete user's profile photo if exists
            try {
                if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
                    System.out.println("Deleting profile photo for user: " + contactNumber);
                    supabaseService.deleteProfilePhoto(user.getProfilePhotoUrl());
                    System.out.println("Successfully deleted profile photo for user: " + contactNumber);
                }
            } catch (Exception e) {
                String errorMsg = "Error deleting profile photo: " + e.getMessage();
                System.err.println(errorMsg);
                deletionErrors.add(errorMsg);
                // Continue with user deletion
            }
            
            try {
                user.setProfilePhotoUrl(null);
                userRepository.save(user);
                
                // Delete the user record
                System.out.println("Deleting user from database: " + contactNumber);
                userRepository.delete(user);
                System.out.println("Successfully deleted user from database: " + contactNumber);
            } catch (Exception e) {
                String errorMsg = "Error deleting user from database: " + e.getMessage();
                System.err.println(errorMsg);
                e.printStackTrace();
                deletionErrors.add(errorMsg);
                
                if (deletionErrors.size() > 0) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Failed to delete user completely: " + String.join("; ", deletionErrors));
                    return ResponseEntity.status(500).body(errorResponse);
                }
            }
            
            // If there were some errors but we got to this point, the account is deleted but with warnings
            if (deletionErrors.size() > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "User account deleted but with warnings");
                response.put("warnings", deletionErrors);
                return ResponseEntity.ok(response);
            }
            
            // Create success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User account and all associated data successfully deleted");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error in deleteUserAccount: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete user account: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Upgrade user to premium membership
     */
    @PostMapping("/{contactNumber}/upgrade-premium")
    public ResponseEntity<?> upgradeToPremium(
            @PathVariable String contactNumber,
            @RequestBody Map<String, Object> requestBody) {
        try {
            // Find the user
            User user = userRepository.findByContactNumber(contactNumber);
            
            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "User not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Extract plan details from request body
            String plan = (String) requestBody.get("plan");
            String paymentId = (String) requestBody.get("paymentId");
            
            if (plan == null || plan.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Plan is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Calculate membership duration based on plan
            int durationInMonths;
            switch (plan.toLowerCase()) {
                case "monthly":
                    durationInMonths = 1;
                    break;
                case "quarterly":
                    durationInMonths = 3;
                    break;
                case "half-yearly":
                    durationInMonths = 6;
                    break;
                case "yearly":
                    durationInMonths = 12;
                    break;
                default:
                    durationInMonths = 1; // Default to monthly
            }
            
            // Set membership details
            user.setMembership("Premium");
            
            // Calculate purchase and expiry times
            LocalDateTime now = LocalDateTime.now();
            user.setMembershipPurchaseTime(now);
            user.setMembershipExpireTime(now.plusMonths(durationInMonths));
            
            // Save updated user
            userRepository.save(user);
            
            // Also update all registrations for this user to reflect Premium membership
            try {
                List<Registration> userRegistrations = registrationRepository.findByUserId(user.getId());
                if (userRegistrations != null && !userRegistrations.isEmpty()) {
                    for (Registration reg : userRegistrations) {
                        reg.setMembership("Premium");
                    }
                    registrationRepository.saveAll(userRegistrations);
                }
            } catch (Exception syncEx) {
                // Log but do not fail the premium upgrade response
                System.err.println("Warning: Failed to sync registration membership for user " + user.getId() + ": " + syncEx.getMessage());
            }
            
            // Create success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Premium membership activated successfully");
            response.put("plan", plan);
            response.put("purchaseTime", user.getMembershipPurchaseTime());
            response.put("expireTime", user.getMembershipExpireTime());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to upgrade to premium: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
} 