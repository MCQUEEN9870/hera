package com.example.demo.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.model.Registration;
import com.example.demo.model.RegistrationImageFolder;
import com.example.demo.repository.RegistrationImageFolderRepository;
import com.example.demo.repository.RegistrationRepository;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class ImageMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ImageMigrationService.class);

    @Value("${supabase.url}")
    private String supabaseUrl;
    
    @Value("${supabase.key}")
    private String supabaseKey;
    
    @Value("${supabase.bucket.name}")
    private String bucketName;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private RegistrationImageFolderRepository registrationImageFolderRepository;
    
    private final OkHttpClient client = new OkHttpClient();
    
    /**
     * Migrate images for a specific registration from flat structure to folder structure
     * 
     * @param registrationId Registration ID to migrate
     * @return Number of migrated images
     * @throws IOException If migration fails
     */
    public int migrateRegistrationImages(Long registrationId) throws IOException {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IOException("Registration not found with ID: " + registrationId));
        
        // Skip if there are no image URLs
        List<String> existingImageUrls = registration.getVehicleImageUrls();
        if (existingImageUrls == null || existingImageUrls.isEmpty()) {
            return 0;
        }
        
        // Create folder path for this registration
        String folderPath = registrationId.toString();
        int migratedCount = 0;
        
        // Process each image URL
        for (String imageUrl : existingImageUrls) {
            String filename = null;
            try {
                // Extract filename from URL
                filename = extractFilenameFromUrl(imageUrl);
                
                // Download the image content
                byte[] imageContent = downloadImageContent(imageUrl);
                
                if (imageContent != null && imageContent.length > 0) {
                    // Upload to new location with folder structure
                    String newPath = folderPath + "/" + filename;
                    uploadToNewLocation(newPath, imageContent, getMimeType(filename));
                    migratedCount++;
                }
            } catch (Exception e) {
                log.warn("Error migrating image (registrationId={}, filename={}): {}", registrationId, filename, e.toString());
                // Continue with other images even if one fails
            }
        }
        
        if (migratedCount > 0) {
            // Create or update folder record
            RegistrationImageFolder imageFolder = registrationImageFolderRepository
                    .findFirstByRegistrationId(registrationId)
                    .orElse(new RegistrationImageFolder(registrationId, folderPath));
            
            registrationImageFolderRepository.save(imageFolder);
            
            // Instead of clearing image URLs, get the new ones and update the registration
            try {
                List<String> newImageUrls = new ArrayList<>();
                for (String imageUrl : existingImageUrls) {
                    String filename = extractFilenameFromUrl(imageUrl);
                    String newUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + folderPath + "/" + filename;
                    newImageUrls.add(newUrl);
                }
                
                // Update the registration with the new URLs
                registration.setVehicleImageUrls(newImageUrls);
                registrationRepository.save(registration);

                log.info("Updated registration image URLs after migration (registrationId={}, count={})", registrationId, newImageUrls.size());
            } catch (Exception e) {
                log.warn("Error updating registration with new image URLs (registrationId={}): {}", registrationId, e.toString());
            }
        }
        
        return migratedCount;
    }
    
    /**
     * Migrate all registrations from flat structure to folder structure
     * 
     * @return Map of registration IDs to migration status
     */
    public List<String> migrateAllRegistrations() {
        List<String> results = new ArrayList<>();
        List<Registration> registrations = registrationRepository.findAll();
        
        for (Registration registration : registrations) {
            try {
                int migratedCount = migrateRegistrationImages(registration.getId());
                results.add("Registration " + registration.getId() + ": Migrated " + migratedCount + " images");
            } catch (Exception e) {
                log.warn("Migration failed for registration (registrationId={}): {}", registration.getId(), e.toString());
                results.add("Registration " + registration.getId() + ": Failed");
            }
        }
        
        return results;
    }
    
    /**
     * Extract filename from a Supabase URL
     */
    private String extractFilenameFromUrl(String url) {
        // Extract the last part of the URL as the filename
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }
    
    /**
     * Download image content from URL
     */
    private byte[] downloadImageContent(String imageUrl) throws IOException {
        Request request = new Request.Builder()
                .url(imageUrl)
                .get()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download image: " + response.message());
            }
            
            return response.body().bytes();
        }
    }
    
    /**
     * Upload content to new location in Supabase with folder structure
     */
    private void uploadToNewLocation(String path, byte[] content, String contentType) throws IOException {
        RequestBody fileBody = RequestBody.create(MediaType.parse(contentType), content);
        
        Request request = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + path)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .put(fileBody)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to upload image: " + response.message());
            }
        }
    }
    
    /**
     * Get MIME type based on file extension
     */
    private String getMimeType(String filename) {
        if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (filename.toLowerCase().endsWith(".gif")) {
            return "image/gif";
        } else if (filename.toLowerCase().endsWith(".webp")) {
            return "image/webp";
        } else {
            return "application/octet-stream";
        }
    }
} 