package com.example.demo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Objects;
import java.time.LocalDate;
import java.time.Duration;

import org.springframework.http.CacheControl;

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

import com.example.demo.model.Registration;
import com.example.demo.repository.RegistrationImageFolderRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.service.SupabaseService;

@RestController
@RequestMapping("/api")
public class VehicleController {

    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private SupabaseService supabaseService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private RegistrationImageFolderRepository registrationImageFolderRepository;
    
    @Autowired
    private DeletionController deletionController;
    
    @GetMapping("/vehicles/check")
    public ResponseEntity<?> checkVehicleExists(@RequestParam("vehicleNumber") String vehicleNumber) {
        // Always return false to allow duplicate vehicle plate numbers
        boolean exists = false;
        
        Map<String, Object> response = new HashMap<>();
        response.put("exists", exists);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/vehicles/search")
        public ResponseEntity<?> searchVehicles(
            @RequestParam(value = "type", required = false) String vehicleType,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "pincode", required = false) String pincode,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
            
        System.out.println("Searching vehicles with: type=" + vehicleType + ", state=" + state + 
                           ", city=" + city + ", pincode=" + pincode);
        
        // Get all registrations
        List<Registration> allRegistrations = registrationRepository.findAll();
        
        // Apply filters based on search parameters - Only filter by vehicle type and pincode
        List<Registration> filteredRegistrations = allRegistrations.stream()
            .filter(reg -> vehicleType == null || vehicleType.isEmpty() || reg.getVehicleType().equals(vehicleType))
            // Ignoring state and city filters as requested
            .filter(reg -> pincode == null || pincode.isEmpty() || reg.getPincode().startsWith(pincode))
            .collect(Collectors.toList());

        // Premium-first then oldest-first sorting
        Comparator<Registration> premiumThenDateAsc = Comparator
                .comparing((Registration r) -> {
                    String membership = r.getMembership();
                    return membership != null && membership.equalsIgnoreCase("premium") ? 0 : 1; // premium first
                })
            .thenComparing(Registration::getRegistrationDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Registration::getId); // tie-breaker for stability

        filteredRegistrations = filteredRegistrations.stream()
            .sorted(premiumThenDateAsc)
                .collect(Collectors.toList());

        // Pagination calculations (1-based page in request)
        int cappedSize = Math.min(Math.max(size, 1), 50); // enforce upper bound
        int totalItems = filteredRegistrations.size();
        int totalPages = (int) Math.ceil(totalItems / (double) cappedSize);
        int currentPage = Math.min(Math.max(page, 1), Math.max(totalPages, 1));
        int fromIndex = (currentPage - 1) * cappedSize;
        int toIndex = Math.min(fromIndex + cappedSize, totalItems);
        List<Registration> pageSlice = fromIndex < totalItems ? filteredRegistrations.subList(fromIndex, toIndex) : List.of();
            
        System.out.println("Found " + filteredRegistrations.size() + " matching vehicles");
        
        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        List<Map<String, Object>> vehicleList = new ArrayList<>();
        
        for (Registration reg : pageSlice) {
            Map<String, Object> vehicle = new HashMap<>();
            vehicle.put("id", reg.getId());
            vehicle.put("userId", reg.getUserId());
            vehicle.put("name", reg.getFullName() + "'s " + reg.getVehicleType());
            vehicle.put("type", reg.getVehicleType());
            vehicle.put("images", reg.getVehicleImageUrls());
            vehicle.put("locationState", reg.getState());
            vehicle.put("locationCity", reg.getCity());
            vehicle.put("locationPincode", reg.getPincode());
            vehicle.put("ownerName", reg.getFullName());
            vehicle.put("ownerPhone", reg.getContactNumber());
            // Include membership for premium/standard styling on frontend
            vehicle.put("membership", reg.getMembership());
            
            // Use the actual registration date if available, otherwise format today's date
            String registrationDate = reg.getRegistrationDate() != null 
                ? reg.getRegistrationDate().toString() 
                : java.time.LocalDate.now().toString();
            vehicle.put("registrationDate", registrationDate);
            
            vehicle.put("capacity", "Standard capacity"); // Add these fields to Registration entity later
            vehicle.put("dimensions", "Standard dimensions");
            vehicle.put("registrationNumber", reg.getVehiclePlateNumber());
            vehicle.put("availability", "Available Now");
            
            // Remove description handling code that used VehicleDescription
            // Add empty description instead
            vehicle.put("description", "");
            
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
        response.put("page", currentPage);
        response.put("pageSize", cappedSize);
        response.put("totalPages", totalPages);
        response.put("totalItems", totalItems);

        // Build a weak ETag using count + newest registration date for cache validation
        LocalDate newestDate = filteredRegistrations.stream()
            .map(Registration::getRegistrationDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.MIN);
        String eTag = "W/\"search-" + filteredRegistrations.size() + "-" + newestDate + "\"";

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .eTag(eTag)
            .body(response);
    }
    
    @PostMapping("/vehicles/{vehicleId}/highlights")
    public ResponseEntity<?> updateVehicleHighlights(
            @PathVariable("vehicleId") Long vehicleId,
            @RequestBody Map<String, String> highlightsData) {
        
        System.out.println("Updating highlights for vehicle ID: " + vehicleId);
        System.out.println("Highlights data: " + highlightsData);
        
        // Find the vehicle registration
        Optional<Registration> optionalRegistration = registrationRepository.findById(vehicleId);
        
        if (!optionalRegistration.isPresent()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Vehicle not found with ID: " + vehicleId);
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        Registration registration = optionalRegistration.get();
        
        // Update highlights
        registration.setHighlight1(highlightsData.get("highlight1"));
        registration.setHighlight2(highlightsData.get("highlight2"));
        registration.setHighlight3(highlightsData.get("highlight3"));
        registration.setHighlight4(highlightsData.get("highlight4"));
        registration.setHighlight5(highlightsData.get("highlight5"));
        
        // Save the updated registration
        Registration updatedRegistration = registrationRepository.save(registration);
        
        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Vehicle highlights updated successfully");
        
        Map<String, String> updatedHighlights = new HashMap<>();
        updatedHighlights.put("highlight1", updatedRegistration.getHighlight1());
        updatedHighlights.put("highlight2", updatedRegistration.getHighlight2());
        updatedHighlights.put("highlight3", updatedRegistration.getHighlight3());
        updatedHighlights.put("highlight4", updatedRegistration.getHighlight4());
        updatedHighlights.put("highlight5", updatedRegistration.getHighlight5());
        
        response.put("highlights", updatedHighlights);
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/vehicles/{vehicleId}")
    public ResponseEntity<?> updateVehicle(
            @PathVariable("vehicleId") Long vehicleId,
            @RequestBody Map<String, String> updateData) {
        
        System.out.println("Updating vehicle with ID: " + vehicleId);
        System.out.println("Update data: " + updateData);
        
        // Find the vehicle registration
        Optional<Registration> optionalRegistration = registrationRepository.findById(vehicleId);
        
        if (!optionalRegistration.isPresent()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Vehicle not found with ID: " + vehicleId);
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        Registration registration = optionalRegistration.get();
        
        // Update fields from the request
        if (updateData.containsKey("owner")) {
            registration.setFullName(updateData.get("owner"));
        }
        
        if (updateData.containsKey("contact")) {
            registration.setContactNumber(updateData.get("contact"));
        }
        
        if (updateData.containsKey("whatsapp")) {
            registration.setWhatsappNumber(updateData.get("whatsapp"));
        }
        
        if (updateData.containsKey("alternateContact")) {
            registration.setAlternateContactNumber(updateData.get("alternateContact"));
        }
        
        if (updateData.containsKey("location")) {
            String location = updateData.get("location");
            if (location != null && !location.isEmpty()) {
                // Split the location to get city and state
                String[] parts = location.split(",");
                if (parts.length >= 2) {
                    // Last part is usually the state
                    String state = parts[parts.length - 1].trim();
                    // The rest can be considered as city
                    StringBuilder cityBuilder = new StringBuilder();
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (i > 0) {
                            cityBuilder.append(", ");
                        }
                        cityBuilder.append(parts[i].trim());
                    }
                    String city = cityBuilder.toString();
                    
                    registration.setState(state);
                    registration.setCity(city);
                    
                    // Log the parsed city and state for debugging
                    System.out.println("Updated location - City: " + city + ", State: " + state);
                } else {
                    // If only one part provided, assume it's the city and keep existing state
                    String city = parts[0].trim();
                    registration.setCity(city);
                    System.out.println("Updated location - City only: " + city + ", keeping State: " + registration.getState());
                }
            }
        }
        
        // Update highlights with logging
        System.out.println("Processing highlights data:");
        if (updateData.containsKey("highlight1")) {
            String value = updateData.get("highlight1");
            System.out.println("Setting highlight1: " + value);
            registration.setHighlight1(value);
        }
        
        if (updateData.containsKey("highlight2")) {
            String value = updateData.get("highlight2");
            System.out.println("Setting highlight2: " + value);
            registration.setHighlight2(value);
        }
        
        if (updateData.containsKey("highlight3")) {
            String value = updateData.get("highlight3");
            System.out.println("Setting highlight3: " + value);
            registration.setHighlight3(value);
        }
        
        if (updateData.containsKey("highlight4")) {
            String value = updateData.get("highlight4");
            System.out.println("Setting highlight4: " + value);
            registration.setHighlight4(value);
        }
        
        if (updateData.containsKey("highlight5")) {
            String value = updateData.get("highlight5");
            System.out.println("Setting highlight5: " + value);
            registration.setHighlight5(value);
        }
        
        // Save the updated registration
        System.out.println("Saving updated registration to database...");
        try {
            Registration updatedRegistration = registrationRepository.save(registration);
            System.out.println("Successfully saved registration in database. ID: " + updatedRegistration.getId());
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vehicle updated successfully");
            
            Map<String, Object> vehicleData = new HashMap<>();
            vehicleData.put("id", updatedRegistration.getId());
            vehicleData.put("owner", updatedRegistration.getFullName());
            vehicleData.put("type", updatedRegistration.getVehicleType());
            vehicleData.put("number", updatedRegistration.getVehiclePlateNumber());
            vehicleData.put("contact", updatedRegistration.getContactNumber());
            vehicleData.put("whatsapp", updatedRegistration.getWhatsappNumber());
            vehicleData.put("alternateContact", updatedRegistration.getAlternateContactNumber());
            vehicleData.put("location", updatedRegistration.getCity() + ", " + updatedRegistration.getState());
            vehicleData.put("pincode", updatedRegistration.getPincode());
            
            // Include the registration date
            String formattedRegistrationDate = updatedRegistration.getRegistrationDate() != null 
                ? updatedRegistration.getRegistrationDate().toString() 
                : "";
            vehicleData.put("registrationDate", formattedRegistrationDate);
            
            // Add highlights
            Map<String, String> highlights = new HashMap<>();
            highlights.put("highlight1", updatedRegistration.getHighlight1());
            highlights.put("highlight2", updatedRegistration.getHighlight2());
            highlights.put("highlight3", updatedRegistration.getHighlight3());
            highlights.put("highlight4", updatedRegistration.getHighlight4());
            highlights.put("highlight5", updatedRegistration.getHighlight5());
            vehicleData.put("highlights", highlights);
            
            response.put("vehicle", vehicleData);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error saving registration: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to update vehicle: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<?> getVehicleById(@PathVariable("vehicleId") Long vehicleId) {
        System.out.println("Getting vehicle with ID: " + vehicleId);
        
        // Find the vehicle registration
        Optional<Registration> optionalRegistration = registrationRepository.findById(vehicleId);
        
        if (!optionalRegistration.isPresent()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Vehicle not found with ID: " + vehicleId);
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        Registration registration = optionalRegistration.get();
        
        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        Map<String, Object> vehicleData = new HashMap<>();
        vehicleData.put("id", registration.getId());
        vehicleData.put("userId", registration.getUserId());
        vehicleData.put("owner", registration.getFullName());
        vehicleData.put("type", registration.getVehicleType());
        vehicleData.put("number", registration.getVehiclePlateNumber());
        vehicleData.put("contact", registration.getContactNumber());
        vehicleData.put("whatsapp", registration.getWhatsappNumber());
        vehicleData.put("alternateContact", registration.getAlternateContactNumber());
        vehicleData.put("location", registration.getCity() + ", " + registration.getState());
        vehicleData.put("pincode", registration.getPincode());
        // Include membership for premium/standard styling on frontend
        vehicleData.put("membership", registration.getMembership());
        
        // Include the registration date
        String formattedRegistrationDate = registration.getRegistrationDate() != null 
            ? registration.getRegistrationDate().toString() 
            : "";
        vehicleData.put("registrationDate", formattedRegistrationDate);
        
        // Add vehicle images
        vehicleData.put("photos", getVehiclePhotos(registration));
        
        // Add highlights
        Map<String, String> highlights = new HashMap<>();
        highlights.put("highlight1", registration.getHighlight1());
        highlights.put("highlight2", registration.getHighlight2());
        highlights.put("highlight3", registration.getHighlight3());
        highlights.put("highlight4", registration.getHighlight4());
        highlights.put("highlight5", registration.getHighlight5());
        vehicleData.put("highlights", highlights);
        
        response.put("vehicle", vehicleData);

        LocalDate regDate = registration.getRegistrationDate() != null ? registration.getRegistrationDate() : LocalDate.MIN;
        String eTag = "W/\"veh-" + registration.getId() + "-" + regDate + "\"";

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(120)).cachePublic())
            .eTag(eTag)
            .body(response);
    }
    
    // Helper method to format vehicle photos
    private Map<String, String> getVehiclePhotos(Registration registration) {
        Map<String, String> photos = new HashMap<>();
        List<String> imageUrls = registration.getVehicleImageUrls();
        
        // Set default image if no images are available
        String defaultImage = "attached_assets/images/default-vehicle.png";
        
        // Map image URLs to the expected format
        photos.put("front", imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage);
        photos.put("side", imageUrls.size() > 1 ? imageUrls.get(1) : (imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage));
        photos.put("back", imageUrls.size() > 2 ? imageUrls.get(2) : (imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage));
        photos.put("loading", imageUrls.size() > 3 ? imageUrls.get(3) : (imageUrls.size() > 0 ? imageUrls.get(0) : defaultImage));
        
        return photos;
    }
    
    /**
     * Get vehicles by user ID
     */
    @GetMapping("/vehicles/user/{userId}")
    public ResponseEntity<?> getVehiclesByUserId(@PathVariable("userId") Long userId) {
        System.out.println("Fetching vehicles for user ID: " + userId);
        
        // Find the vehicles for this user
        List<Registration> registrations = registrationRepository.findByUserId(userId);
        
        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        List<Map<String, Object>> vehicleList = new ArrayList<>();
        
        for (Registration reg : registrations) {
            Map<String, Object> vehicle = new HashMap<>();
            vehicle.put("id", reg.getId());
            vehicle.put("userId", reg.getUserId());
            vehicle.put("name", reg.getFullName() + "'s " + reg.getVehicleType());
            vehicle.put("type", reg.getVehicleType());
            vehicle.put("images", reg.getVehicleImageUrls());
            vehicle.put("locationState", reg.getState());
            vehicle.put("locationCity", reg.getCity());
            vehicle.put("locationPincode", reg.getPincode());
            vehicle.put("ownerName", reg.getFullName());
            vehicle.put("ownerPhone", reg.getContactNumber());
            
            // Use the actual registration date if available, otherwise format today's date
            String registrationDate = reg.getRegistrationDate() != null 
                ? reg.getRegistrationDate().toString() 
                : java.time.LocalDate.now().toString();
            vehicle.put("registrationDate", registrationDate);
            
            vehicle.put("capacity", "Standard capacity");
            vehicle.put("dimensions", "Standard dimensions");
            vehicle.put("registrationNumber", reg.getVehiclePlateNumber());
            vehicle.put("availability", "Available Now");
            vehicle.put("description", "");
            
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
        response.put("count", vehicleList.size());

        LocalDate newestDate = registrations.stream()
            .map(Registration::getRegistrationDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.MIN);
        String eTag = "W/\"user-" + userId + "-" + registrations.size() + "-" + newestDate + "\"";

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
            .eTag(eTag)
            .body(response);
    }
    
    /**
     * Delete a vehicle and archive its images (endpoint for /api/registration/{registrationId})
     * This is the endpoint that the frontend actually calls
     */
    @DeleteMapping("/registration/{registrationId}")
    public ResponseEntity<?> deleteRegistration(@PathVariable Long registrationId) {
        System.out.println("VehicleController: Forwarding to DeletionController for ID: " + registrationId);
        
        // Forward to the DeletionController's deleteVehicle method
        try {
            return deletionController.deleteVehicle(registrationId);
        } catch (Exception e) {
            System.err.println("VehicleController: Error forwarding to DeletionController: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete vehicle: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Delete a vehicle and archive its images (endpoint for /api/vehicles/{registrationId})
     * This is for compatibility with any older code that might use this endpoint
     */
    @DeleteMapping("/vehicles/{registrationId}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long registrationId) {
        System.out.println("VehicleController: Redirecting from /api/vehicles endpoint to DeletionController: " + registrationId);
        // Call the DeletionController
        return deleteRegistration(registrationId);
    }
    
    /**
     * Debug endpoint to inspect database schema and foreign key constraints
     */
    @GetMapping("/debug/database-schema")
    public ResponseEntity<?> inspectDatabaseSchema() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get registration_image_folders table schema
            List<Map<String, Object>> imageFoldersSchema = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'registration_image_folders'"
            );
            result.put("registration_image_folders_schema", imageFoldersSchema);
            
            // Get registration table schema
            List<Map<String, Object>> registrationSchema = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'registration'"
            );
            result.put("registration_schema", registrationSchema);
            
            // Get foreign key constraints
            List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, tc.table_name, kcu.column_name, " +
                "ccu.table_name AS foreign_table_name, ccu.column_name AS foreign_column_name " +
                "FROM information_schema.table_constraints AS tc " +
                "JOIN information_schema.key_column_usage AS kcu ON tc.constraint_name = kcu.constraint_name " +
                "JOIN information_schema.constraint_column_usage AS ccu ON ccu.constraint_name = tc.constraint_name " +
                "WHERE constraint_type = 'FOREIGN KEY' AND " +
                "(tc.table_name = 'registration_image_folders' OR tc.table_name = 'registration')"
            );
            result.put("foreign_keys", foreignKeys);
            
            // Check if there are any triggers on these tables
            List<Map<String, Object>> triggers = jdbcTemplate.queryForList(
                "SELECT trigger_name, event_manipulation, action_statement " +
                "FROM information_schema.triggers " +
                "WHERE event_object_table IN ('registration_image_folders', 'registration')"
            );
            result.put("triggers", triggers);
            
            // Get sample data counts
            Integer registrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration", Integer.class);
            result.put("registration_count", registrationCount);
            
            Integer imageFoldersCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration_image_folders", Integer.class);
            result.put("image_folders_count", imageFoldersCount);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Debug endpoint to test direct SQL deletion for a specific vehicle
     */
    @DeleteMapping("/debug/direct-delete/{registrationId}")
    public ResponseEntity<?> debugDirectDelete(@PathVariable Long registrationId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Step 1: Check if the vehicle exists
            Integer vehicleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            
            result.put("vehicle_exists", vehicleCount > 0);
            
            if (vehicleCount == 0) {
                result.put("message", "Vehicle not found with ID: " + registrationId);
                return ResponseEntity.ok(result);
            }
            
            // Step 2: Check for related image folders
            Integer folderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration_image_folders WHERE registration_id = ?", 
                Integer.class, 
                registrationId
            );
            result.put("image_folders_count", folderCount);
            
            // Step 3: Get all foreign keys pointing to this registration
            List<Map<String, Object>> referencingTables = jdbcTemplate.queryForList(
                "SELECT tc.table_name, kcu.column_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name " +
                "WHERE constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'registration' AND ccu.column_name = 'id'"
            );
            result.put("referencing_tables", referencingTables);
            
            // Step 4: Try to delete image folders first
            try {
                int deletedFolders = jdbcTemplate.update(
                    "DELETE FROM registration_image_folders WHERE registration_id = ?", 
                    registrationId
                );
                result.put("deleted_folders", deletedFolders);
            } catch (Exception e) {
                result.put("folder_deletion_error", e.getMessage());
            }
            
            // Step 5: Try to delete the registration
            try {
                int deletedRegistration = jdbcTemplate.update(
                    "DELETE FROM registration WHERE id = ?", 
                    registrationId
                );
                result.put("deleted_registration", deletedRegistration);
            } catch (Exception e) {
                result.put("registration_deletion_error", e.getMessage());
            }
            
            // Step 6: Check if deletion was successful
            Integer remainingVehicleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            result.put("vehicle_deleted", remainingVehicleCount == 0);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * New direct deletion endpoint that bypasses all Spring Data JPA
     * This is a last resort method for vehicle deletion
     */
    @DeleteMapping("/force-delete-vehicle/{registrationId}")
    public ResponseEntity<?> forceDeleteVehicle(@PathVariable Long registrationId) {
        System.out.println("FORCE DELETE: Starting forced deletion process for vehicle ID: " + registrationId);
        
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
                System.out.println("FORCE DELETE: Found vehicle: " + vehicle.get("vehicle_plate_number") + " (ID: " + vehicle.get("id") + ")");
            } catch (Exception e) {
                System.out.println("FORCE DELETE: Could not get vehicle details: " + e.getMessage());
            }
            
            // Step 3: Disable foreign key checks temporarily
            System.out.println("FORCE DELETE: Disabling foreign key checks");
            jdbcTemplate.execute("SET CONSTRAINTS ALL DEFERRED");
            
            // Step 4: Delete from registration_image_folders first
            try {
                int folderRows = jdbcTemplate.update(
                    "DELETE FROM registration_image_folders WHERE registration_id = ?",
                    registrationId
                );
                System.out.println("FORCE DELETE: Deleted " + folderRows + " rows from registration_image_folders");
            } catch (Exception e) {
                System.err.println("FORCE DELETE: Error deleting from registration_image_folders: " + e.getMessage());
                // Continue anyway
            }
            
            // Step 5: Clear image URLs to avoid LOB issues
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE registration SET vehicle_image_urls_json = '[]' WHERE id = ?",
                    registrationId
                );
                System.out.println("FORCE DELETE: Cleared image URLs for " + updated + " rows");
            } catch (Exception e) {
                System.err.println("FORCE DELETE: Error clearing image URLs: " + e.getMessage());
                // Continue anyway
            }
            
            // Step 6: Delete from registration table using raw SQL
            int regRows = jdbcTemplate.update(
                "DELETE FROM registration WHERE id = ?",
                registrationId
            );
            System.out.println("FORCE DELETE: Deleted " + regRows + " rows from registration");
            
            // Step 7: Re-enable foreign key checks
            System.out.println("FORCE DELETE: Re-enabling foreign key checks");
            jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");
            
            // Step 8: Verify deletion
            Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE id = ?", 
                Integer.class, 
                registrationId
            );
            
            if (remaining != null && remaining > 0) {
                System.err.println("FORCE DELETE: Vehicle still exists after deletion!");
                response.put("success", false);
                response.put("message", "Failed to delete vehicle - it still exists after deletion attempt");
                return ResponseEntity.status(500).body(response);
            }
            
            // Step 9: Try to clean up storage (optional)
            try {
                supabaseService.deleteAllVehicleImages(registrationId);
                System.out.println("FORCE DELETE: Cleaned up storage");
            } catch (Exception e) {
                System.err.println("FORCE DELETE: Error cleaning up storage: " + e.getMessage());
                // Continue as storage cleanup is optional
            }
            
            // Success response
            response.put("success", true);
            response.put("message", "Vehicle successfully deleted using force delete");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("FORCE DELETE: Error in forced deletion: " + e.getMessage());
            e.printStackTrace();
            
            response.put("success", false);
            response.put("message", "Failed to delete vehicle: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
} 