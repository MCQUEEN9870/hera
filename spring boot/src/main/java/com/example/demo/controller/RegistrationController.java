package com.example.demo.controller;

import java.io.IOException;
import java.util.ArrayList;
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
import com.example.demo.service.SupabaseService;
 

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/registration")
public class RegistrationController {

    @Autowired
    private SupabaseService supabaseService;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RegistrationImageFolderRepository registrationImageFolderRepository;

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
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error fetching registration: " + e.getMessage());
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
            // Find the user by contactNumber
            User user = userRepository.findByContactNumber(contactNumber);
            
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
                
                // Log the URLs being saved
                System.out.println("Saved image URLs to registration: " + savedRegistration.getId());
                for (String url : imageUrls) {
                    System.out.println(" - " + url);
                }
            }
            
            // Additionally save to Supabase if needed
            try {
                supabaseService.saveRegistration(registration);
            } catch (Exception e) {
                System.err.println("Failed to save to Supabase: " + e.getMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle registered successfully!");
            response.put("id", savedRegistration.getId());
            response.put("userId", savedRegistration.getUserId());
            response.put("imageFolderPath", folderPath);
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error processing registration: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @GetMapping
    public ResponseEntity<List<Registration>> getAllRegistrations() {
        return ResponseEntity.ok(registrationRepository.findAll());
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
                System.err.println("Failed to update in Supabase: " + e.getMessage());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle registration updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error updating registration: " + e.getMessage());
            
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

            System.out.println("DELETE REQUEST: Starting deletion of registration ID: " + id);
            
            // Get registration to delete images from storage
            Registration registration = registrationRepository.findById(id).get();
            List<String> imageUrls = registration.getVehicleImageUrls();
            
            // Log all the image URLs being deleted for debugging
            if (imageUrls != null && !imageUrls.isEmpty()) {
                System.out.println("DELETE INFO: Found " + imageUrls.size() + " image URLs in registration " + id);
                for (String url : imageUrls) {
                    System.out.println("DELETE INFO: Image URL found: " + url);
                }
            } else {
                System.out.println("DELETE INFO: No image URLs found in registration " + id);
            }
            
            // First, delete any folder records from registration_image_folders
            // This is important to avoid foreign key constraint violations
            try {
                System.out.println("DELETE DB: Deleting folder records for registration " + id);
                registrationImageFolderRepository.deleteByRegistrationId(id);
                System.out.println("DELETE DB: Successfully deleted folder records");
            } catch (Exception e) {
                System.err.println("DELETE ERROR: Failed to delete folder records: " + e.getMessage());
                e.printStackTrace();
                // Continue with deletion process
            }
            
            // Delete images from Supabase storage - try folder deletion first
            // Try to delete the folder, but continue even if it fails
            try {
                // Delete images from storage bucket using folder structure
                System.out.println("DELETE STORAGE: Deleting folder for registration " + id);
                supabaseService.deleteRegistrationFolder(id);
                System.out.println("DELETE STORAGE: Successfully deleted folder");
            } catch (Exception e) {
                // Log but continue since we still want to delete individual URLs
                System.err.println("DELETE ERROR: Failed to delete images from folder: " + e.getMessage());
                e.printStackTrace();
                // Continue with individual file deletion
            }
            
            // Also try to delete any image URLs that might be stored directly
            // This is especially important if folder deletion failed
            if (imageUrls != null && !imageUrls.isEmpty()) {
                System.out.println("DELETE URLS: Deleting " + imageUrls.size() + " individual image URLs");
                int successCount = 0;
                
                for (String imageUrl : imageUrls) {
                    try {
                        System.out.println("DELETE URL: Attempting to delete image: " + imageUrl);
                        supabaseService.deleteImage(imageUrl);
                        System.out.println("DELETE URL: Successfully deleted image: " + imageUrl);
                        successCount++;
                    } catch (Exception e) {
                        System.err.println("DELETE ERROR: Failed to delete image URL: " + imageUrl + " - " + e.getMessage());
                        // Continue with other deletions even if one fails
                    }
                }
                
                System.out.println("DELETE URLS: Successfully deleted " + successCount + " out of " + imageUrls.size() + " image URLs");
            }
            
            // Also try a direct database cleanup approach for the folder entry
            try {
                System.out.println("DELETE DB DIRECT: Running direct SQL cleanup for registration folder entries");
                // This ensures that any orphaned folder entries are removed
                registrationImageFolderRepository.deleteByRegistrationId(id);
                System.out.println("DELETE DB DIRECT: Direct SQL cleanup completed");
            } catch (Exception e) {
                System.err.println("DELETE ERROR: Failed direct SQL cleanup: " + e.getMessage());
            }
            
            // Now delete the registration itself from the database
            try {
                System.out.println("DELETE DB: Deleting registration record " + id);
                registrationRepository.deleteById(id);
                System.out.println("DELETE DB: Successfully deleted registration record");
                
                // Extra verification - check if the registration is actually gone
                boolean stillExists = registrationRepository.existsById(id);
                if (stillExists) {
                    System.err.println("DELETE WARNING: Registration still exists in database after deletion!");
                    
                    // One more attempt with a direct SQL approach
                    try {
                        System.out.println("DELETE DB FINAL: Making one final attempt to remove registration record");
                        registrationRepository.deleteById(id);
                        
                        // Check again
                        stillExists = registrationRepository.existsById(id);
                        if (stillExists) {
                            System.err.println("DELETE ERROR: Final attempt failed. Registration record persists in database.");
                        } else {
                            System.out.println("DELETE DB FINAL: Final deletion attempt successful");
                        }
                    } catch (Exception e) {
                        System.err.println("DELETE ERROR: Final deletion attempt exception: " + e.getMessage());
                    }
                } else {
                    System.out.println("DELETE DB VERIFY: Verified registration record is deleted from database");
                }
            } catch (Exception e) {
                System.err.println("DELETE ERROR: Failed to delete registration from database: " + e.getMessage());
                e.printStackTrace();
                
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Error deleting registration from database: " + e.getMessage());
                return ResponseEntity.status(500).body(errorResponse);
            }
            
            System.out.println("DELETE COMPLETE: Successfully completed deletion process for registration " + id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle registration deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("DELETE FATAL ERROR: Unhandled exception in deletion process: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error deleting registration: " + e.getMessage());
            
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
            // Get all registrations
            List<Registration> registrations = registrationRepository.findAll();
            int migratedCount = 0;
            
            System.out.println("Starting migration of " + registrations.size() + " registrations...");
            
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
                    
                    System.out.println("Migrated registration ID " + registration.getId() + 
                                      " with " + urls.size() + " URLs");
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Migration completed successfully");
            response.put("migratedCount", migratedCount);
            response.put("totalCount", registrations.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during migration: " + e.getMessage());
            
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
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();
            
            // Check if there's an existing RC document that needs to be deleted
            if (registration.getRc() != null && !registration.getRc().isEmpty()) {
                try {
                    // Delete the existing document
                    supabaseService.deleteDocument(registration.getRc());
                } catch (Exception e) {
                    System.err.println("Failed to delete existing RC document: " + e.getMessage());
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
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error uploading RC document: " + e.getMessage());
            
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
            // Check if registration exists
            if (!registrationRepository.existsById(id)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Registration not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            // Get the registration
            Registration registration = registrationRepository.findById(id).get();
            
            // Check if there's an existing DL document that needs to be deleted
            if (registration.getD_l() != null && !registration.getD_l().isEmpty()) {
                try {
                    // Delete the existing document
                    supabaseService.deleteDocument(registration.getD_l());
                } catch (Exception e) {
                    System.err.println("Failed to delete existing driving license document: " + e.getMessage());
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
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error uploading driving license document: " + e.getMessage());
            
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
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error getting document status: " + e.getMessage());
            
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
                System.err.println("Failed to delete RC document from storage: " + e.getMessage());
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
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error deleting RC document: " + e.getMessage());
            
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
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error updating membership status: " + e.getMessage());
            
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
            List<Registration> results = new ArrayList<>();
            
            // Try to parse term as Long for ID search
            if (term.matches("\\d+")) {
                // Term is a number, search by ID
                registrationRepository.findById(Long.valueOf(term)).ifPresent(results::add);
            } else {
                // Term is not a number, search by name
                results.addAll(registrationRepository.findByFullNameContainingIgnoreCase(term));
            }
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error searching registrations: " + e.getMessage());
            
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
                System.err.println("Failed to delete driving license document from storage: " + e.getMessage());
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
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error deleting driving license document: " + e.getMessage());
            
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
            // Resolve userId from contactNumber if needed
            if (userId == null && contactNumber != null && !contactNumber.isEmpty()) {
                User user = userRepository.findByContactNumber(contactNumber);
                if (user != null) {
                    userId = user.getId();
                }
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
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to sync membership: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
