package com.example.demo.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.Registration;
import com.example.demo.model.User;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SecurityUtils;
import com.example.demo.service.DeletionAuditService;
import com.example.demo.service.RazorpayPaymentService;
import com.example.demo.service.SupabaseService;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private static String maskPhone(String phone) {
        if (phone == null) return null;
        String s = phone.trim();
        if (s.length() <= 4) return "****";
        String last4 = s.substring(s.length() - 4);
        return "******" + last4;
    }

    @Autowired
    private SupabaseService supabaseService;
    @Autowired
    private DeletionAuditService deletionAuditService;
    
    @Autowired
    private UserRepository userRepository;

    @Value("${app.admin.token:}")
    private String adminToken;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RazorpayPaymentService razorpayPaymentService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        // Endpoint kept for backward-compat; avoid logging full payload.
        log.debug("/api/users/register called");
        
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
            if (!SecurityUtils.isCurrentContact(contactNumber)) {
                return SecurityUtils.forbidden("Forbidden");
            }
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
            log.warn("Failed to update email (maskedContact={})", maskPhone(contactNumber), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to update email"));
        }
    }
    
    /**
     * Get user profile by contact number
     */
    @GetMapping("/{contactNumber}")
    public ResponseEntity<?> getUserProfile(@PathVariable String contactNumber) {
        if (!SecurityUtils.isCurrentContact(contactNumber)) {
            return SecurityUtils.forbidden("Forbidden");
        }
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
        if (!SecurityUtils.isCurrentContact(contactNumber)) {
            return SecurityUtils.forbidden("Forbidden");
        }
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
    public ResponseEntity<?> getUserLocations(
        @RequestHeader(value = "X-Admin-Token", required = false) String token
    ) {
        if (adminToken == null || adminToken.isBlank()) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        if (token == null || token.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        log.debug("Fetching user locations for feedback carousel");
        
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
        if (!SecurityUtils.isCurrentContact(contactNumber)) {
            return SecurityUtils.forbidden("Forbidden");
        }
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

        if (!SecurityUtils.isCurrentContact(contactNumber)) {
            return SecurityUtils.forbidden("Forbidden");
        }
        
        log.debug("Received profile photo upload request (maskedContact={}, sizeBytes={}, contentType={})",
            maskPhone(contactNumber),
            photo != null ? photo.getSize() : null,
            photo != null ? photo.getContentType() : null);
        
        // Validate photo size and format
        if (photo.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Empty file received");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        if (photo.getSize() > 5 * 1024 * 1024) { // 5MB limit
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "File too large. Maximum size allowed is 5MB");
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
            log.info("User not found during profile photo upload; creating minimal user (maskedContact={})", maskPhone(contactNumber));
            user = new User();
            user.setContactNumber(contactNumber);
            // Set default values for required fields
            user.setFullName("User " + contactNumber.substring(Math.max(0, contactNumber.length() - 4)));
        } else {
            log.debug("Found existing user for profile photo upload (maskedContact={})", maskPhone(contactNumber));
            
            // If user already has a profile photo, delete the old one
            if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
                try {
                    supabaseService.deleteProfilePhoto(user.getProfilePhotoUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old profile photo from storage (maskedContact={})", maskPhone(contactNumber), e);
                    // Continue with upload even if delete fails
                }
            }
        }
        
        try {
            String photoUrl = supabaseService.uploadProfilePhoto(photo);
            user.setProfilePhotoUrl(photoUrl);
            userRepository.save(user);

            log.info("Profile photo upload successful (maskedContact={})", maskPhone(contactNumber));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("photoUrl", photoUrl);
            response.put("message", "Profile photo uploaded successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to upload profile photo (maskedContact={})", maskPhone(contactNumber), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to upload profile photo");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Delete profile photo
     */
    @DeleteMapping("/{contactNumber}/profile-photo")
    public ResponseEntity<?> deleteProfilePhoto(@PathVariable String contactNumber) {
        log.debug("Received profile photo delete request (maskedContact={})", maskPhone(contactNumber));

        if (!SecurityUtils.isCurrentContact(contactNumber)) {
            return SecurityUtils.forbidden("Forbidden");
        }
        
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
        
        try {
            // Delete from Supabase
            supabaseService.deleteProfilePhoto(profilePhotoUrl);
            
            // Update user
            user.setProfilePhotoUrl(null);
            userRepository.save(user);
            
            log.info("Profile photo deleted successfully (maskedContact={})", maskPhone(contactNumber));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile photo deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error deleting profile photo from storage (maskedContact={})", maskPhone(contactNumber), e);
            
            // Even if Supabase deletion fails, still remove the URL from the user
            try {
                user.setProfilePhotoUrl(null);
                userRepository.save(user);
                log.warn("Removed profile photo URL from user record despite storage deletion failure (maskedContact={})", maskPhone(contactNumber));
            } catch (Exception ex) {
                log.warn("Failed to update user record while removing profile photo URL (maskedContact={})", maskPhone(contactNumber), ex);
            }
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete profile photo");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Check if user has reached vehicle registration limit
     */
    @GetMapping("/{contactNumber}/check-vehicle-limit")
    public ResponseEntity<?> checkVehicleLimit(@PathVariable String contactNumber) {
        try {
            if (!SecurityUtils.isCurrentContact(contactNumber)) {
                return SecurityUtils.forbidden("Forbidden");
            }
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
            log.warn("Error checking vehicle limit (maskedContact={})", maskPhone(contactNumber), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error checking vehicle limit");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Delete user account and all associated data
     */
    @DeleteMapping("/{contactNumber}")
    public ResponseEntity<?> deleteUserAccount(@PathVariable String contactNumber, HttpServletRequest request) {
        try {
            if (!SecurityUtils.isCurrentContact(contactNumber)) {
                return SecurityUtils.forbidden("Forbidden");
            }
            // Find the user
            User user = userRepository.findByContactNumber(contactNumber);
            
            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "User not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            log.info("Account deletion requested (maskedContact={}, userId={})", maskPhone(contactNumber), user.getId());

            // Archive evidence first (profile + each vehicle images + RC/DL) so admin can still inspect after deletion.
            Map<String, Object> profileArchival = null;
            List<Map<String, Object>> vehiclesArchival = new ArrayList<>();
            boolean archiveOk = true;
            try {
                profileArchival = supabaseService.archiveAndDeleteProfileEvidence(user.getId(), user.getProfilePhotoUrl());
                Object ok = profileArchival != null ? profileArchival.get("success") : null;
                if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                    archiveOk = false;
                }
            } catch (Exception e) {
                archiveOk = false;
                profileArchival = Map.of("success", false, "errors", List.of("profile-archive-exception: " + e.getMessage()));
            }

            List<Registration> regsForAudit = registrationRepository.findByUserId(user.getId());
            for (Registration r : regsForAudit) {
                try {
                    Map<String, Object> ar = supabaseService.archiveAndDeleteRegistrationEvidence(user.getId(), r);
                    vehiclesArchival.add(Map.of(
                        "registrationId", r.getId(),
                        "result", ar
                    ));
                    Object ok = ar != null ? ar.get("success") : null;
                    if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                        archiveOk = false;
                    }
                } catch (Exception e) {
                    archiveOk = false;
                    vehiclesArchival.add(Map.of(
                        "registrationId", r.getId(),
                        "result", Map.of("success", false, "errors", List.of("vehicle-archive-exception: " + e.getMessage()))
                    ));
                }
            }

            // Audit snapshot (do not block deletion if audit fails)
            try {
                String actor = SecurityUtils.currentContactOrNull();
                Map<String, Object> payload = new HashMap<>();
                payload.put("action", "ACCOUNT_DELETE");
                payload.put("archiveOk", archiveOk);

                Map<String, Object> userSnapshot = new HashMap<>();
                userSnapshot.put("id", user.getId());
                userSnapshot.put("contactNumber", user.getContactNumber());
                userSnapshot.put("fullName", user.getFullName());
                userSnapshot.put("email", user.getEmail());
                userSnapshot.put("membership", user.getMembership());
                userSnapshot.put("membershipPurchaseTime", user.getMembershipPurchaseTime() != null ? user.getMembershipPurchaseTime().toString() : null);
                userSnapshot.put("membershipExpireTime", user.getMembershipExpireTime() != null ? user.getMembershipExpireTime().toString() : null);
                userSnapshot.put("joinDate", user.getJoinDate() != null ? user.getJoinDate().toString() : null);
                userSnapshot.put("verified", user.getVerified());
                userSnapshot.put("profilePhotoUrl", user.getProfilePhotoUrl());
                payload.put("user", userSnapshot);

                List<Map<String, Object>> vehiclesSnapshot = new ArrayList<>();
                for (Registration r : regsForAudit) {
                    Map<String, Object> v = new HashMap<>();
                    v.put("registrationId", r.getId());
                    v.put("userId", r.getUserId());
                    v.put("fullName", r.getFullName());
                    v.put("contactNumber", r.getContactNumber());
                    v.put("whatsappNumber", r.getWhatsappNumber());
                    v.put("alternateContactNumber", r.getAlternateContactNumber());
                    v.put("vehicleType", r.getVehicleType());
                    v.put("vehiclePlateNumber", r.getVehiclePlateNumber());
                    v.put("state", r.getState());
                    v.put("city", r.getCity());
                    v.put("pincode", r.getPincode());
                    v.put("registrationDate", r.getRegistrationDate() != null ? r.getRegistrationDate().toString() : null);
                    v.put("membership", r.getMembership());
                    v.put("rc", r.getRc());
                    v.put("d_l", r.getD_l());
                    v.put("vehicleImageUrls", r.getVehicleImageUrls());

                    // Folder paths recorded in DB (for manual reconciliation)
                    try {
                        List<Map<String, Object>> folderRows = jdbcTemplate.queryForList(
                            "SELECT folder_path FROM registration_image_folders WHERE registration_id = ?",
                            r.getId()
                        );
                        List<String> folderPaths = new ArrayList<>();
                        for (Map<String, Object> fr : folderRows) {
                            Object fp = fr.get("folder_path");
                            if (fp != null) {
                                String s = fp.toString();
                                if (!s.isBlank()) folderPaths.add(s);
                            }
                        }
                        v.put("folderPathsFromDb", folderPaths);
                    } catch (Exception _e) {
                        v.put("folderPathsFromDb", List.of());
                    }

                    Map<String, Object> storage = new HashMap<>();
                    storage.put("bucket", "vehicle-images");
                    storage.put("prefixCandidates", List.of(
                        String.valueOf(r.getId()),
                        r.getId() + "/",
                        r.getId() + "/.hidden_folder/"
                    ));
                    v.put("storage", storage);
                    vehiclesSnapshot.add(v);
                }
                payload.put("vehicles", vehiclesSnapshot);

                payload.put("archival", Map.of(
                    "profile", profileArchival,
                    "vehicles", vehiclesArchival
                ));

                deletionAuditService.record(
                    "ACCOUNT_DELETE",
                    actor,
                    user.getContactNumber(),
                    user.getId(),
                    null,
                    payload,
                    request
                );
            } catch (Exception e) {
                log.debug("Account delete: audit snapshot failed (maskedContact={}, userId={}): {}", maskPhone(contactNumber), user.getId(), e.toString());
            }

            if (!archiveOk) {
                return ResponseEntity.status(503).body(Map.of(
                    "success", false,
                    "message", "Temporary issue while deleting. Please try again shortly.",
                    "archival", Map.of("profile", profileArchival, "vehicles", vehiclesArchival)
                ));
            }
            
            // Create a list to track any deletion errors
            List<String> deletionErrors = new ArrayList<>();
            
            // Find all registrations associated with this user by userId
            List<Registration> userRegistrations = regsForAudit;
            log.info("Found registrations for account deletion (maskedContact={}, count={})", maskPhone(contactNumber), userRegistrations.size());
            
            // Clear the registration_user_id_fkey constraint
            for (Registration registration : userRegistrations) {
                try {
                    // Delete each vehicle registration
                    Long registrationId = registration.getId();
                    log.info("Deleting vehicle registration (registrationId={})", registrationId);
                    
                    // Delete registration_image_folders entries
                    try {
                        int rowsDeleted = jdbcTemplate.update(
                            "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                            registration.getId()
                        );
                        log.debug("Deleted registration_image_folders rows (registrationId={}, rowsDeleted={})", registrationId, rowsDeleted);
                    } catch (Exception e) {
                        log.warn("Error deleting from registration_image_folders (registrationId={})", registrationId, e);
                        deletionErrors.add("Some image-folder records could not be deleted for registrationId=" + registrationId);
                        // Continue with deletion
                    }
                    
                    // Images/docs already archived+deleted from source bucket above (success enforced).
                    
                    // Clear image URLs to avoid LOB issues
                    registration.setVehicleImageUrls(new ArrayList<>());
                    registrationRepository.save(registration);
                    
                    // Delete the registration
                    registrationRepository.delete(registration);
                    log.info("Deleted registration record (registrationId={})", registrationId);
                    
                } catch (Exception e) {
                    Long registrationId = registration.getId();
                    log.warn("Error deleting registration (registrationId={})", registrationId, e);
                    deletionErrors.add("Some registration records could not be deleted for registrationId=" + registrationId);
                    
                    // Try direct SQL as fallback
                    try {
                        int rowsDeleted = jdbcTemplate.update("DELETE FROM registration WHERE id = ?", registration.getId());
                        if (rowsDeleted > 0) {
                            log.info("Deleted registration using SQL fallback (registrationId={})", registrationId);
                        } else {
                            log.warn("SQL fallback deleted no rows (registrationId={})", registrationId);
                        }
                    } catch (Exception ex) {
                        log.warn("SQL fallback also failed (registrationId={})", registrationId, ex);
                    }
                }
            }
            
            // Profile photo already archived+deleted from source bucket above (success enforced).
            
            try {
                user.setProfilePhotoUrl(null);
                userRepository.save(user);
                
                // Delete the user record
                userRepository.delete(user);
                log.info("Deleted user from database (maskedContact={}, userId={})", maskPhone(contactNumber), user.getId());
            } catch (Exception e) {
                log.warn("Error deleting user from database (maskedContact={}, userId={})", maskPhone(contactNumber), user.getId(), e);
                deletionErrors.add("User record could not be deleted from database");
                
                if (deletionErrors.size() > 0) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Failed to delete user completely");
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
            log.error("Error in deleteUserAccount (maskedContact={})", maskPhone(contactNumber), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete user account");
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
            if (!SecurityUtils.isCurrentContact(contactNumber)) {
                return SecurityUtils.forbidden("Forbidden");
            }
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
            String orderId = (String) requestBody.get("razorpay_order_id");
            String signature = (String) requestBody.get("razorpay_signature");
            
            if (plan == null || plan.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Plan is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Require verified Razorpay payment before upgrading.
            // (Client-side checks are not enough; attackers can call this endpoint directly.)
            if (orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank() || signature == null || signature.isBlank()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Payment verification fields are required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (!razorpayPaymentService.verifyPremiumPayment(contactNumber, plan, orderId, paymentId, signature)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Payment could not be verified");
                return ResponseEntity.status(402).body(errorResponse);
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
                log.warn("Failed to sync registration membership after premium upgrade (userId={})", user.getId(), syncEx);
            }
            
            // Create success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Premium membership activated successfully");
            response.put("plan", plan);
            try {
                response.put("price", razorpayPaymentService.premiumAmountInInr(plan));
            } catch (Exception ignored) {
                // no-op
            }
            response.put("purchaseTime", user.getMembershipPurchaseTime());
            response.put("expireTime", user.getMembershipExpireTime());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to upgrade to premium (maskedContact={})", maskPhone(contactNumber), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to upgrade to premium");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
} 