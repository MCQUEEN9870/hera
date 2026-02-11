package com.example.demo.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
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
import com.example.demo.repository.RegistrationImageFolderRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SecurityUtils;
import com.example.demo.service.SupabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 

@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);

    @Autowired
    private SupabaseService supabaseService;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RegistrationImageFolderRepository registrationImageFolderRepository;

    @Autowired
    private Environment environment;

    private boolean isProdProfile() {
        if (environment == null) return false;
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }

    private static String extractSupabaseObjectPath(String url) {
        if (url == null) return null;
        String s = url.trim();
        if (s.isEmpty()) return null;
        // Strip query params / fragments (signed URLs etc.)
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);

        String markerPublic = "/storage/v1/object/public/";
        String markerObject = "/storage/v1/object/";
        String tail;

        int i = s.indexOf(markerPublic);
        if (i >= 0) {
            tail = s.substring(i + markerPublic.length());
        } else {
            i = s.indexOf(markerObject);
            if (i < 0) return null;
            tail = s.substring(i + markerObject.length());
        }

        String[] parts = tail.split("/", 2);
        if (parts.length < 2) return null;
        String path = parts[1];
        if (path == null || path.isBlank()) return null;
        return path;
    }

    /**
     * Fetch a single registration by ID (used by frontend vehicle card/detail)
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getRegistrationById(@PathVariable("id") Long id) {
        try {
            Optional<Registration> opt = registrationRepository.findById(id);
            if (opt.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Registration not found: " + id);
                return ResponseEntity.status(404).body(err);
            }
            Registration reg = opt.get();
            Map<String, Object> data = new HashMap<>();
            data.put("id", reg.getId());
            data.put("userId", reg.getUserId());
            data.put("owner", reg.getFullName());
            data.put("vehicleType", reg.getVehicleType());
            data.put("vehiclePlateNumber", reg.getVehiclePlateNumber());
            data.put("contact", reg.getContactNumber());
            data.put("whatsapp", reg.getWhatsappNumber());
            data.put("whatsappNumber", reg.getWhatsappNumber());
            data.put("whatsappNo", reg.getWhatsappNumber());
            data.put("alternateContact", reg.getAlternateContactNumber());
            data.put("alternateNumber", reg.getAlternateContactNumber());
            data.put("alternateContactNumber", reg.getAlternateContactNumber());
            data.put("state", reg.getState());
            data.put("city", reg.getCity());
            data.put("pincode", reg.getPincode());
            data.put("registrationDate", reg.getRegistrationDate() != null ? reg.getRegistrationDate().toString() : "");
            // Include membership to enable premium UI on card fetch-by-id
            data.put("membership", reg.getMembership());
            // Safe flags (no URLs) to allow public UI to show document upload/verification badges
            boolean rcUploaded = reg.getRc() != null && !reg.getRc().isBlank();
            boolean dlUploaded = reg.getD_l() != null && !reg.getD_l().isBlank();
            data.put("rcUploaded", rcUploaded);
            data.put("dlUploaded", dlUploaded);

            // Owner profile photo: safe public metadata only (presence + key)
            boolean profilePhotoUploaded = false;
            String profilePhotoKey = null;
            try {
                User u = null;
                if (reg.getUserId() != null) {
                    u = userRepository.findById(reg.getUserId()).orElse(null);
                }
                // Fallback for any legacy rows where userId may be null but contactNumber exists
                if (u == null) {
                    String contact = reg.getContactNumber();
                    if (contact != null && !contact.isBlank()) {
                        u = userRepository.findByContactNumber(contact);
                    }
                }

                if (u != null) {
                    String url = u.getProfilePhotoUrl();
                    profilePhotoUploaded = url != null && !url.isBlank();
                    if (profilePhotoUploaded) {
                        profilePhotoKey = extractSupabaseObjectPath(url);
                    }
                }
            } catch (Exception ignored) {
                // Keep safe defaults
            }
            data.put("profilePhotoUploaded", profilePhotoUploaded);
            if (profilePhotoKey != null && !profilePhotoKey.isBlank()) {
                data.put("profilePhotoKey", profilePhotoKey);
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.warn("Error fetching registration by id={}", id, e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error fetching registration");
            return ResponseEntity.status(500).body(err);
        }
    }

    @PostMapping
    public ResponseEntity<?> handleRegistration(
            @RequestParam("fullName") String fullName,
            @RequestParam("vehicleType") String vehicleType,
            @RequestParam("contactNumber") String contactNumber,
            @RequestParam(value = "whatsappNumber", required = false) String whatsappNumber,
            @RequestParam(value = "vehiclePlateNumber", required = false) String vehiclePlateNumber,
            @RequestParam("state") String state,
            @RequestParam("city") String city,
            @RequestParam("pincode") String pincode,
            @RequestParam(value = "alternateContactNumber", required = false) String alternateContactNumber,
            @RequestParam("vehicleImages") MultipartFile[] vehicleImages
    ) {
        try {
            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null || !currentContact.equals(contactNumber)) {
                return SecurityUtils.forbidden("Forbidden");
            }
            // Find the user by contactNumber
            User user = userRepository.findByContactNumber(currentContact);
            
            // If user doesn't exist, registration shouldn't proceed
            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "User not found with the provided contact number. Please ensure you're logged in.");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Create and save registration to database (without image URLs)
            Registration registration = new Registration();
            registration.setFullName(fullName);
            registration.setVehicleType(vehicleType);
            registration.setContactNumber(contactNumber);
            registration.setWhatsappNumber(whatsappNumber != null ? whatsappNumber : "");
            registration.setAlternateContactNumber(alternateContactNumber);
            
            // For manual carts, use a placeholder if vehiclePlateNumber is null or empty
            if ((vehiclePlateNumber == null || vehiclePlateNumber.isEmpty()) && 
                vehicleType != null && vehicleType.toLowerCase().contains("manual")) {
                registration.setVehiclePlateNumber("MANUAL-CART-" + System.currentTimeMillis());
            } else if (vehiclePlateNumber != null) {
            registration.setVehiclePlateNumber(vehiclePlateNumber);
            } else {
                // If not a manual cart and no plate number provided, return an error
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Vehicle plate number is required for non-manual vehicles.");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            registration.setState(state);
            registration.setCity(city);
            registration.setPincode(pincode);
            registration.setUserId(user.getId()); // Set the user ID
            
            // Ensure registration inherits current user's membership
            if (user.getMembership() != null && !user.getMembership().isEmpty()) {
                registration.setMembership(user.getMembership());
            }

            // Save to local database first to get the ID
            Registration savedRegistration = registrationRepository.save(registration);
            
            // Now upload images to Supabase storage in a folder named after registration ID
            // The uploadImagesToFolder method now returns a Map with folderPath and imageUrls
            Map<String, Object> uploadResult = supabaseService.uploadImagesToFolder(Arrays.asList(vehicleImages), savedRegistration.getId());
            
            // Extract folder path and image URLs from the result
            String folderPath = (String) uploadResult.get("folderPath");
            @SuppressWarnings("unchecked")
            List<String> imageUrls = (List<String>) uploadResult.get("imageUrls");
            
            // Update the registration with image URLs for backward compatibility
            if (imageUrls != null && !imageUrls.isEmpty()) {
                savedRegistration.setVehicleImageUrls(imageUrls);
                registrationRepository.save(savedRegistration);
                log.debug("Saved image URLs to registration (registrationId={}, count={})", savedRegistration.getId(), imageUrls.size());
            }
            
            // Additionally save to Supabase if needed
            try {
                supabaseService.saveRegistration(registration);
            } catch (Exception e) {
                log.warn("Failed to save registration to Supabase (registrationId={})", savedRegistration.getId(), e);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle registered successfully!");
            response.put("id", savedRegistration.getId());
            response.put("userId", savedRegistration.getUserId());
            response.put("imageFolderPath", folderPath);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.warn("Error processing registration", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error processing registration");
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @GetMapping
    public ResponseEntity<List<Registration>> getAllRegistrations() {
        String currentContact = SecurityUtils.currentContactOrNull();
        if (currentContact == null) {
            return ResponseEntity.status(403).body(List.of());
        }
        User currentUser = userRepository.findByContactNumber(currentContact);
        if (currentUser == null) {
            return ResponseEntity.status(403).body(List.of());
        }
        return ResponseEntity.ok(registrationRepository.findByUserId(currentUser.getId()));
    }
    
    @GetMapping("/{id}/entity")
    public ResponseEntity<?> getRegistration(@PathVariable Long id) {
        return registrationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Add a PUT method to update registration
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRegistration(@PathVariable Long id, @RequestBody Map<String, Object> updateData) {
        try {
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Vehicle registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }

            // Get the existing registration
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            
            // Update only the fields that can be changed
            if (updateData.containsKey("owner")) {
                registration.setFullName(updateData.get("owner").toString());
            }
            
            if (updateData.containsKey("contact")) {
                registration.setContactNumber(updateData.get("contact").toString());
            }
            
            if (updateData.containsKey("whatsapp")) {
                Object whatsapp = updateData.get("whatsapp");
                registration.setWhatsappNumber(whatsapp != null ? whatsapp.toString() : "");
            }
            
            if (updateData.containsKey("alternateContact")) {
                registration.setAlternateContactNumber(updateData.get("alternateContact").toString());
            }
            
            // Add code to handle location update
            if (updateData.containsKey("location")) {
                String location = updateData.get("location").toString();
                registration.setCity(location.trim());
            }
            
            // Save the updated registration
            registrationRepository.save(registration);
            
            // Update in Supabase if needed
            try {
                supabaseService.updateRegistration(registration);
            } catch (Exception e) {
                log.warn("Failed to update registration in Supabase (registrationId={})", registration.getId(), e);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle registration updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error updating registration", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error updating registration");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // Add a DELETE method to delete registration by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRegistration(@PathVariable Long id) {
        try {
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Vehicle registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }

            log.debug("Starting deletion of registration (registrationId={})", id);
            
            // Get registration to delete images from storage
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            List<String> imageUrls = registration.getVehicleImageUrls();
            if (imageUrls != null && !imageUrls.isEmpty()) {
                log.debug("Found image URLs in registration (registrationId={}, count={})", id, imageUrls.size());
            } else {
                log.debug("No image URLs found in registration (registrationId={})", id);
            }
            
            // First, delete any folder records from registration_image_folders
            // This is important to avoid foreign key constraint violations
            try {
                registrationImageFolderRepository.deleteByRegistrationId(id);
                log.debug("Deleted folder records (registrationId={})", id);
            } catch (Exception e) {
                log.warn("Failed to delete folder records (registrationId={})", id, e);
                // Continue with deletion process
            }
            
            // Delete images from Supabase storage - try folder deletion first
            // Try to delete the folder, but continue even if it fails
            try {
                // Delete images from storage bucket using folder structure
                supabaseService.deleteRegistrationFolder(id);
                log.debug("Deleted storage folder (registrationId={})", id);
            } catch (Exception e) {
                // Log but continue since we still want to delete individual URLs
                log.warn("Failed to delete images from folder (registrationId={})", id, e);
                // Continue with individual file deletion
            }
            
            // Also try to delete any image URLs that might be stored directly
            // This is especially important if folder deletion failed
            if (imageUrls != null && !imageUrls.isEmpty()) {
                int successCount = 0;
                
                for (String imageUrl : imageUrls) {
                    try {
                        supabaseService.deleteImage(imageUrl);
                        successCount++;
                    } catch (Exception e) {
                        log.debug("Failed to delete an image URL during registration deletion (registrationId={})", id);
                        // Continue with other deletions even if one fails
                    }
                }

                log.debug("Deleted {} out of {} image URLs (registrationId={})", successCount, imageUrls.size(), id);
            }
            
            // Also try a direct database cleanup approach for the folder entry
            try {
                // This ensures that any orphaned folder entries are removed
                registrationImageFolderRepository.deleteByRegistrationId(id);
                log.debug("Direct DB cleanup completed (registrationId={})", id);
            } catch (Exception e) {
                log.warn("Failed direct SQL cleanup (registrationId={})", id, e);
            }
            
            // Now delete the registration itself from the database
            try {
                registrationRepository.deleteById(id);
                log.debug("Deleted registration record (registrationId={})", id);
                
                // Extra verification - check if the registration is actually gone
                boolean stillExists = registrationRepository.existsById(id);
                if (stillExists) {
                    log.warn("Registration still exists after deletion attempt (registrationId={})", id);
                    
                    // One more attempt with a direct SQL approach
                    try {
                        registrationRepository.deleteById(id);
                        
                        // Check again
                        stillExists = registrationRepository.existsById(id);
                        if (stillExists) {
                            log.warn("Final attempt failed; registration record persists (registrationId={})", id);
                        } else {
                            log.debug("Final deletion attempt successful (registrationId={})", id);
                        }
                    } catch (Exception e) {
                        log.warn("Final deletion attempt exception (registrationId={})", id, e);
                    }
                } else {
                    log.debug("Verified registration record deleted from database (registrationId={})", id);
                }
            } catch (Exception e) {
                log.warn("Failed to delete registration from database (registrationId={})", id, e);
                
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Error deleting registration from database");
                return ResponseEntity.status(500).body(errorResponse);
            }

            log.debug("Successfully completed deletion process (registrationId={})", id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle registration deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Unhandled exception in deletion process (registrationId={})", id, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error deleting registration");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Migrate old image URL data from ElementCollection to embedded JSON format
     * This is needed to maintain compatibility after model changes
     */
    @GetMapping("/migrate-urls")
    public ResponseEntity<?> migrateImageUrls() {
        try {
            // Hard safety: never allow a state-mutating migration endpoint in production
            if (isProdProfile()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Not found");
                return ResponseEntity.status(404).body(errorResponse);
            }

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }

            // Get all registrations
            List<Registration> registrations = registrationRepository.findAll();
            int migratedCount = 0;

            log.info("Starting registration image URL migration (total={})", registrations.size());
            
            for (Registration registration : registrations) {
                // Check if vehicleImageUrls is not empty but vehicleImageUrlsJson is empty
                // This indicates it needs migration
                List<String> urls = registration.getVehicleImageUrls();
                if ((urls != null && !urls.isEmpty()) && 
                    (registration.getVehicleImageUrlsJson() == null || registration.getVehicleImageUrlsJson().isEmpty())) {
                    
                    // The setter for vehicleImageUrls will update vehicleImageUrlsJson
                    registration.setVehicleImageUrls(urls);
                    registrationRepository.save(registration);
                    migratedCount++;

                    log.debug("Migrated registration image URLs (registrationId={}, urlCount={})", registration.getId(), urls.size());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Migration completed successfully");
            response.put("migratedCount", migratedCount);
            response.put("totalCount", registrations.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error during registration image URL migration", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during migration");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Upload RC document for a registration
     * 
     * @param id The registration ID
     * @param document The RC document file
     * @return Response with success status and document URL
     */
    @PostMapping("/{id}/rc")
    public ResponseEntity<?> uploadRcDocument(@PathVariable Long id, @RequestParam("document") MultipartFile document) {
        try {
            // Validate file
            if (document == null || document.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Empty file received");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (document.getSize() > 5 * 1024 * 1024) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "File too large. Maximum size allowed is 5MB");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            String contentType = document.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Only image files are allowed");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            
            // Check if there's an existing RC document that needs to be deleted
            if (registration.getRc() != null && !registration.getRc().isEmpty()) {
                try {
                    // Delete the existing document
                    supabaseService.deleteDocument(registration.getRc());
                } catch (Exception e) {
                    log.warn("Failed to delete existing RC document from storage (registrationId={})", id, e);
                    // Continue with upload even if deletion fails
                }
            }
            
            // Upload the new document
            String documentUrl = supabaseService.uploadRcDocument(document, id);
            
            // Update the registration with the new URL
            registration.setRc(documentUrl);
            registrationRepository.save(registration);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RC document uploaded successfully");
            response.put("documentUrl", documentUrl);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error uploading RC document (registrationId={})", id, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error uploading RC document");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Upload driving license document for a registration
     * 
     * @param id The registration ID
     * @param document The driving license document file
     * @return Response with success status and document URL
     */
    @PostMapping("/{id}/dl")
    public ResponseEntity<?> uploadDrivingLicenseDocument(@PathVariable Long id, @RequestParam("document") MultipartFile document) {
        try {
            // Validate file
            if (document == null || document.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Empty file received");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (document.getSize() > 5 * 1024 * 1024) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "File too large. Maximum size allowed is 5MB");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            String contentType = document.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Only image files are allowed");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            
            // Check if there's an existing DL document that needs to be deleted
            if (registration.getD_l() != null && !registration.getD_l().isEmpty()) {
                try {
                    // Delete the existing document
                    supabaseService.deleteDocument(registration.getD_l());
                } catch (Exception e) {
                    log.warn("Failed to delete existing DL document from storage (registrationId={})", id, e);
                    // Continue with upload even if deletion fails
                }
            }
            
            // Upload the new document
            String documentUrl = supabaseService.uploadDlDocument(document, id);
            
            // Update the registration with the new URL
            registration.setD_l(documentUrl);
            registrationRepository.save(registration);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Driving license document uploaded successfully");
            response.put("documentUrl", documentUrl);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error uploading driving license document (registrationId={})", id, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error uploading driving license document");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Get document status for a registration
     * 
     * @param id The registration ID
     * @return Response with document URLs and status
     */
    @GetMapping("/{id}/documents")
    public ResponseEntity<?> getDocumentStatus(@PathVariable Long id) {
        try {
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            
            // Create response with document URLs and status
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            
            Map<String, Object> documents = new HashMap<>();
            
            // RC document
            Map<String, Object> rcDocument = new HashMap<>();
            rcDocument.put("url", registration.getRc());
            rcDocument.put("status", registration.getRc() != null && !registration.getRc().isEmpty() ? "uploaded" : "not_uploaded");
            documents.put("rc", rcDocument);
            
            // Driving license document
            Map<String, Object> dlDocument = new HashMap<>();
            dlDocument.put("url", registration.getD_l());
            dlDocument.put("status", registration.getD_l() != null && !registration.getD_l().isEmpty() ? "uploaded" : "not_uploaded");
            documents.put("dl", dlDocument);
            
            response.put("documents", documents);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error getting document status (registrationId={})", id, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error getting document status");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Delete RC document for a registration
     * 
     * @param id The registration ID
     * @return Response with success status
     */
    @DeleteMapping("/{id}/rc/delete")
    public ResponseEntity<?> deleteRcDocument(@PathVariable Long id) {
        try {
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            
            // Check if there's an RC document to delete
            if (registration.getRc() == null || registration.getRc().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "No RC document found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Delete the document from storage
            try {
                supabaseService.deleteDocument(registration.getRc());
            } catch (Exception e) {
                log.warn("Failed to delete RC document from storage (registrationId={})", id, e);
                // Continue with database update even if storage deletion fails
            }
            
            // Update the registration to remove the document URL
            registration.setRc(null);
            registrationRepository.save(registration);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RC document deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error deleting RC document (registrationId={})", id, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error deleting RC document");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Update membership status for a registration
     * 
     * @param id The registration ID
     * @param membershipData The membership data containing the new membership status
     * @return Response with success status
     */
    @PutMapping("/{id}/membership")
    public ResponseEntity<?> updateMembershipStatus(@PathVariable Long id, @RequestBody Map<String, String> membershipData) {
        try {
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            
            // Update membership status
            String membership = membershipData.get("membership");
            if (membership == null || (!membership.equals("Premium") && !membership.equals("Standard"))) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid membership status. Must be 'Premium' or 'Standard'");
                return ResponseEntity.status(400).body(errorResponse);
            }
            
            registration.setMembership(membership);
            registrationRepository.save(registration);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Membership status updated successfully to " + membership);
            response.put("membership", membership);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error updating membership status (registrationId={})", id, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error updating membership status");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Search registrations by ID or owner name
     * 
     * @param term The search term
     * @return List of matching registrations
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchRegistrations(@RequestParam String term) {
        try {
            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null) {
                return SecurityUtils.forbidden("Forbidden");
            }

            List<Registration> results = new ArrayList<>();
            List<Registration> myRegs = registrationRepository.findByUserId(currentUser.getId());

            if (term != null && term.matches("\\d+")) {
                Long id = Long.valueOf(term);
                for (Registration r : myRegs) {
                    if (r.getId() != null && r.getId().equals(id)) {
                        results.add(r);
                        break;
                    }
                }
            } else {
                String t = term == null ? "" : term.toLowerCase();
                for (Registration r : myRegs) {
                    String name = r.getFullName() == null ? "" : r.getFullName();
                    if (name.toLowerCase().contains(t)) {
                        results.add(r);
                    }
                }
            }

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.warn("Error searching registrations", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error searching registrations");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Delete driving license document for a registration
     * 
     * @param id The registration ID
     * @return Response with success status
     */
    @DeleteMapping("/{id}/dl/delete")
    public ResponseEntity<?> deleteDrivingLicenseDocument(@PathVariable Long id) {
        try {
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();

            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null || registration.getUserId() == null || !registration.getUserId().equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }
            
            // Check if there's a DL document to delete
            if (registration.getD_l() == null || registration.getD_l().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "No driving license document found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Delete the document from storage
            try {
                supabaseService.deleteDocument(registration.getD_l());
            } catch (Exception e) {
                log.warn("Failed to delete DL document from storage (registrationId={})", id, e);
                // Continue with database update even if storage deletion fails
            }
            
            // Update the registration to remove the document URL
            registration.setD_l(null);
            registrationRepository.save(registration);
            
            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Driving license document deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error deleting driving license document (registrationId={})", id, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error deleting driving license document");
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Sync registration membership with user membership.
     * If userId/contactNumber provided, sync only that user's registrations; otherwise sync all.
     */
    @PostMapping("/sync-membership")
    public ResponseEntity<?> syncRegistrationMembership(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "contactNumber", required = false) String contactNumber) {
        try {
            String currentContact = SecurityUtils.currentContactOrNull();
            if (currentContact == null) {
                return SecurityUtils.forbidden("Forbidden");
            }
            User currentUser = userRepository.findByContactNumber(currentContact);
            if (currentUser == null) {
                return SecurityUtils.forbidden("Forbidden");
            }

            // Resolve userId from contactNumber if needed
            if (userId == null && contactNumber != null && !contactNumber.isEmpty()) {
                User user = userRepository.findByContactNumber(contactNumber);
                if (user != null) {
                    userId = user.getId();
                }
            }

            // Only allow syncing the authenticated user's registrations
            if (userId == null) {
                userId = currentUser.getId();
            }
            if (!userId.equals(currentUser.getId())) {
                return SecurityUtils.forbidden("Forbidden");
            }

            int updatedCount = 0;
            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("message", "User not found for provided identifier");
                    return ResponseEntity.status(404).body(error);
                }

                List<Registration> regs = registrationRepository.findByUserId(userId);
                if (regs != null && !regs.isEmpty()) {
                    for (Registration r : regs) {
                        r.setMembership(user.getMembership() != null ? user.getMembership() : "Standard");
                    }
                    registrationRepository.saveAll(regs);
                    updatedCount = regs.size();
                }
            } else {
                // Sync all registrations
                List<Registration> allRegs = registrationRepository.findAll();
                for (Registration r : allRegs) {
                    if (r.getUserId() != null) {
                        User u = userRepository.findById(r.getUserId()).orElse(null);
                        if (u != null) {
                            r.setMembership(u.getMembership() != null ? u.getMembership() : "Standard");
                            updatedCount++;
                        }
                    }
                }
                registrationRepository.saveAll(allRegs);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("updated", updatedCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to sync registration membership", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to sync membership");
            return ResponseEntity.status(500).body(error);
        }
    }
}
