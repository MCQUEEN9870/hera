package com.example.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.AffineTransform;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.Registration;
import com.example.demo.model.RegistrationImageFolder;
import com.example.demo.model.User;
import com.example.demo.repository.RegistrationImageFolderRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SupabaseService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseService.class);

    @Value("${supabase.url}")
    private String supabaseUrl;
    
    @Value("${supabase.key}")
    private String supabaseKey;
    
    @Value("${supabase.bucket.name}")
    private String bucketName;
    
    @Value("${supabase.profile.bucket.name:profile-photos}")
    private String profileBucketName;

    @Value("${supabase.deleted.images.bucket.name:deleted-images}")
    private String deletedImagesBucketName;
    
    @Value("${supabase.deleted.profiles.bucket.name:deleted-profiles}")
    private String deletedProfilesBucketName;

    // Single private bucket for all deleted-user evidence (vehicles + rc/dl + profile, etc.)
    @Value("${supabase.deleted.evidence.bucket.name:deleted-evidence}")
    private String deletedEvidenceBucketName;

    // Bucket names for RC and Driving License documents
    @Value("${supabase.rc.bucket.name:rc}")
    private String rcBucketName;
    
    @Value("${supabase.dl.bucket.name:dl}")
    private String dlBucketName;

    // Dedicated bucket for post images (defaults to 'post-images', or falls back to main bucket)
    @Value("${supabase.posts.bucket.name:post-images}")
    private String postsBucketName;

    @Value("${supabase.max.retries:3}")
    private int maxRetries;
    
    @Value("${supabase.retry.backoff.ms:1000}")
    private long retryBackoffMs;

    @Autowired
    private RegistrationImageFolderRepository registrationImageFolderRepository;

    @Autowired
    private RegistrationRepository registrationRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        log.info("Initializing SupabaseService...");

        // Ensure all required buckets exist (best-effort). Creating buckets requires a service-role key.
        ensureBucketExistsSafe(bucketName, true);
        ensureBucketExistsSafe(profileBucketName, true);
        ensureBucketExistsSafe(deletedImagesBucketName, true);
        ensureBucketExistsSafe(deletedProfilesBucketName, true);
        ensureBucketExistsSafe(deletedEvidenceBucketName, false);
        // Best-effort: force private for evidence bucket (must not be publicly readable)
        try { ensureBucketPrivate(deletedEvidenceBucketName); } catch (Exception ignore) {}
        ensureBucketExistsSafe(rcBucketName, true);
        ensureBucketExistsSafe(dlBucketName, true);

        // Ensure posts bucket
        if (postsBucketName == null || postsBucketName.isBlank()) {
            postsBucketName = bucketName;
            log.debug("Posts bucket not configured; defaulting to main bucket");
        } else {
            ensureBucketExistsSafe(postsBucketName, true);
            log.debug("Using dedicated posts bucket");
        }
        // Best-effort: ensure posts bucket is public so public URLs work
        try { ensureBucketPublic(postsBucketName); } catch (Exception ignore) {}

        log.info("Supabase bucket verification completed");
    }

    private void ensureBucketExistsSafe(String bucket, boolean isPublic) {
        try {
            ensureBucketExists(bucket, isPublic);
        } catch (Exception e) {
            // Common cause: using anon key instead of service-role key.
            log.warn("Could not ensure Supabase bucket exists (bucket={}, public={}): {}", bucket, isPublic, e.getMessage());
        }
    }

    private void ensureBucketPublic(String bucket) throws IOException {
        String json = "{\"public\": true}";
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
        Request request = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/bucket/" + bucket)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .patch(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.debug("Could not set bucket public via PATCH (status {}) — will try PUT", response.code());
                Request requestPut = new Request.Builder()
                        .url(supabaseUrl + "/storage/v1/bucket/" + bucket)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .put(body)
                        .build();
                try (Response resp2 = client.newCall(requestPut).execute()) {
                    if (!resp2.isSuccessful()) {
                        log.debug("Bucket public update failed (status {}): {}", resp2.code(), resp2.message());
                    }
                }
            }
        }
    }

    private void ensureBucketPrivate(String bucket) throws IOException {
        String json = "{\"public\": false}";
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
        Request request = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/bucket/" + bucket)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .patch(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.debug("Could not set bucket private via PATCH (status {}) — will try PUT", response.code());
                Request requestPut = new Request.Builder()
                        .url(supabaseUrl + "/storage/v1/bucket/" + bucket)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .put(body)
                        .build();
                try (Response resp2 = client.newCall(requestPut).execute()) {
                    if (!resp2.isSuccessful()) {
                        log.debug("Bucket private update failed (status {}): {}", resp2.code(), resp2.message());
                    }
                }
            }
        }
    }

    /** Upload a single post image to posts bucket and return its public URL */
    public String uploadPostImage(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IOException("Empty image file");
        }
        String targetBucket = (postsBucketName == null || postsBucketName.isBlank()) ? bucketName : postsBucketName;
        String original = image.getOriginalFilename() != null ? image.getOriginalFilename() : "image.jpg";
        String filename = "post_" + UUID.randomUUID() + "_" + original.replaceAll("[^A-Za-z0-9._-]", "_");
        RequestBody fileBody = RequestBody.create(MediaType.parse(image.getContentType()), image.getBytes());
        Request request = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/object/" + targetBucket + "/" + filename)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .put(fileBody)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Post image upload failed: " + response.code() + " " + response.message());
            }
            return supabaseUrl + "/storage/v1/object/public/" + targetBucket + "/" + filename;
        }
    }

    /** Delete a single post image by its public URL */
    public void deletePostImageByUrl(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isBlank()) return;
        // Convert public URL to delete URL
        String deleteUrl = imageUrl.replace("/storage/v1/object/public/", "/storage/v1/object/");
        Request request = new Request.Builder()
                .url(deleteUrl)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IOException("Failed to delete post image: " + response.code() + " " + response.message());
            }
        }
    }
    
    /**
     * Retry an operation with exponential backoff
     * 
     * @param <T> The return type of the operation
     * @param operation The operation to retry
     * @param operationName Name of the operation for logging
     * @return The result of the operation
     * @throws IOException If all retries fail
     */
    private <T> T retryWithBackoff(SupabaseOperation<T> operation, String operationName) throws IOException {
        int retries = 0;
        Exception lastException = null;
        
        while (retries <= maxRetries) {
            try {
                if (retries > 0) {
                    log.debug("RETRY: Attempt {} for operation: {}", retries, operationName);
                }
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                log.debug("RETRY ERROR: Failed attempt {} for {}: {}", retries, operationName, e.getMessage());
                
                if (retries >= maxRetries) {
                    break;
                }
                
                // Exponential backoff
                long backoffTime = retryBackoffMs * (long)Math.pow(2, retries);
                log.debug("RETRY: Waiting {}ms before retry...", backoffTime);
                
                try {
                    Thread.sleep(backoffTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted: " + ie.getMessage(), ie);
                }
                
                retries++;
            }
        }
        
        throw new IOException("All " + maxRetries + " retry attempts failed for " + operationName + ": " + 
                             (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }
    
    /**
     * Functional interface for operations that can be retried
     */
    @FunctionalInterface
    private interface SupabaseOperation<T> {
        T execute() throws Exception;
    }
    
    private void ensureBucketExists(String bucket) throws IOException {
        ensureBucketExists(bucket, true);
    }

    private void ensureBucketExists(String bucket, boolean isPublic) throws IOException {
        if (bucket == null || bucket.isBlank()) {
            throw new IOException("Bucket name is blank");
        }
        retryWithBackoff(() -> {
            // First check if bucket exists
            Request checkRequest = new Request.Builder()
                    .url(supabaseUrl + "/storage/v1/bucket/" + bucket)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .get()
                    .build();
                    
            try (Response response = client.newCall(checkRequest).execute()) {
                if (!response.isSuccessful() && response.code() == 404) {
                    // Bucket doesn't exist, create it
                    // Supabase expects id + name; using both makes creation reliable across API versions.
                    String bucketConfig = "{\"id\": \"" + bucket + "\", \"name\": \"" + bucket + "\", \"public\": " + (isPublic ? "true" : "false") + "}";
                    RequestBody body = RequestBody.create(MediaType.parse("application/json"), bucketConfig);
                    
                    Request createRequest = new Request.Builder()
                            .url(supabaseUrl + "/storage/v1/bucket")
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + supabaseKey)
                            .addHeader("Content-Type", "application/json")
                            .post(body)
                            .build();
                    
                    try (Response createResponse = client.newCall(createRequest).execute()) {
                        if (!createResponse.isSuccessful()) {
                            String resp = createResponse.body() != null ? createResponse.body().string() : "";
                            throw new IOException("Failed to create bucket " + bucket + " (status=" + createResponse.code() + "): " + createResponse.message() + (resp.isBlank() ? "" : (" :: " + resp)));
                        }
                        log.debug("Created bucket in Supabase storage");
                    }
                }
            }
            return null;
        }, "ensureBucketExists:" + bucket);
    }

    /**
     * Upload images with retry mechanism
     */
    public List<String> uploadImages(List<MultipartFile> images) throws IOException {
        return retryWithBackoff(() -> {
            List<String> imageUrls = new ArrayList<>();

            for (MultipartFile image : images) {
                if (image.isEmpty()) {
                    continue;
                }
                
                String filename = UUID.randomUUID() + "_" + image.getOriginalFilename();

                RequestBody fileBody = RequestBody.create(MediaType.parse(image.getContentType()), image.getBytes());

                Request request = new Request.Builder()
                        .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filename)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .put(fileBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Image upload failed: " + response.message());
                    }

                    String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + filename;
                    imageUrls.add(publicUrl);
                }
            }

            return imageUrls;
        }, "uploadImages");
    }
    
    /**
     * Upload profile photo without contact number parameter
     */
    public String uploadProfilePhoto(MultipartFile photo) throws IOException {
        if (photo.isEmpty()) {
            throw new IOException("Empty image file");
        }
        
        // Create a unique filename for the profile photo
        String filename = "profile_" + UUID.randomUUID() + "_" + photo.getOriginalFilename();
        
        RequestBody fileBody = RequestBody.create(MediaType.parse(photo.getContentType()), photo.getBytes());

        log.debug("Attempting to upload profile photo to Supabase");
        
        try {
            // First ensure bucket exists
            ensureBucketExists(profileBucketName);
            
            // Construct the URL for uploading to the bucket
            String uploadUrl = supabaseUrl + "/storage/v1/object/" + profileBucketName + "/" + filename;
            
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .put(fileBody)
                    .build();

            // Execute the request with proper error handling
            Response response = null;
            String responseBody = null;
            
            try {
                response = client.newCall(request).execute();
                responseBody = response.body() != null ? response.body().string() : "No response body";
                
                if (!response.isSuccessful()) {
                    String errorMessage = "Profile photo upload failed with status " + response.code() + 
                                         ": " + response.message() + ". Response: " + responseBody;
                    log.warn("Profile photo upload failed with status {}: {}", response.code(), response.message());
                    throw new IOException(errorMessage);
                }
                
                // Generate the public URL
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + profileBucketName + "/" + filename;
                log.debug("Successfully uploaded profile photo");
                
                // Verify the file was uploaded by attempting to access it
                Request verifyRequest = new Request.Builder()
                        .url(publicUrl)
                        .head()
                        .build();
                        
                try (Response verifyResponse = client.newCall(verifyRequest).execute()) {
                    if (!verifyResponse.isSuccessful()) {
                        log.debug("Uploaded file verification failed with status {}", verifyResponse.code());
                    } else {
                        log.debug("Verified uploaded file is accessible");
                    }
                }
                
                return publicUrl;
            } catch (IOException e) {
                log.warn("Network error during profile photo upload: {}", e.getMessage(), e);
                throw new IOException("Failed to upload profile photo: " + e.getMessage(), e);
            } finally {
                if (response != null && response.body() != null) {
                    response.close();
                }
            }
        } catch (IOException e) {
            log.warn("Error uploading profile photo: {}", e.getMessage(), e);
            throw new IOException("Failed to upload profile photo: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete profile photo by URL
     */
    public void deleteProfilePhoto(String photoUrl) throws IOException {
        if (photoUrl == null || photoUrl.isEmpty()) {
            log.debug("No profile photo URL provided for deletion");
            return; // Nothing to delete
        }
        
        try {
            // Extract filename from URL
            String filename;
            
            try {
                // URL format: supabaseUrl + "/storage/v1/object/public/" + profileBucketName + "/" + filename
                if (photoUrl.contains("/storage/v1/object/public/")) {
                    String urlPath = photoUrl.substring(photoUrl.indexOf("/storage/v1/object/public/") + 
                                                      "/storage/v1/object/public/".length());
                    String[] parts = urlPath.split("/");
                    
                    if (parts.length >= 2) {
                        // bucketName should be parts[0], filename should be parts[1]
                        filename = parts[1];
                        
                        for (int i = 2; i < parts.length; i++) {
                            filename += "/" + parts[i]; // Handle potential path components
                        }
                    } else {
                        // Fallback - just get the last part of the URL
                        filename = photoUrl.substring(photoUrl.lastIndexOf("/") + 1);
                    }
                } else {
                    // Fallback for unexpected URL format
                    filename = photoUrl.substring(photoUrl.lastIndexOf("/") + 1);
                }
            } catch (Exception e) {
                log.debug("Error parsing profile photo URL: {}", e.toString());
                filename = photoUrl.substring(photoUrl.lastIndexOf("/") + 1);
            }
            
            log.debug("Attempting to delete profile photo from Supabase");
            
            // Construct the URL for deleting from the bucket
            String deleteUrl = supabaseUrl + "/storage/v1/object/" + profileBucketName + "/" + filename;
            
            Request request = new Request.Builder()
                    .url(deleteUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .delete()
                    .build();

            Response response = null;
            try {
                response = client.newCall(request).execute();

                if (!response.isSuccessful() && response.code() != 404) { // Ignore 404 not found
                    log.warn("Failed to delete profile photo (status={}): {}", response.code(), response.message());
                    throw new IOException("Failed to delete profile photo");
                }
                
                log.debug("Deleted profile photo from Supabase (or already missing)");
            } catch (IOException e) {
                log.warn("Network error during profile photo deletion: {}", e.toString());
                throw new IOException("Failed to delete profile photo: " + e.getMessage(), e);
            } finally {
                if (response != null && response.body() != null) {
                    response.close();
                }
            }
        } catch (Exception e) {
            log.warn("Error deleting profile photo: {}", e.toString());
            throw new IOException("Failed to delete profile photo: " + e.getMessage(), e);
        }
    }
    
    public String getUserProfilePhotoUrl(String contactNumber) throws IOException {
        // Check if users table exists first
        try {
            Request request = new Request.Builder()
                    .url(supabaseUrl + "/rest/v1/users?contact_number=eq." + contactNumber + "&select=profile_photo_url")
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .get()
                    .build();
            
            String responseBody;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 404) {
                        log.debug("Users table might not exist yet (Supabase returned 404)");
                        return null;
                    }
                    throw new IOException("Failed to get user profile: " + response.message());
                }
                
                responseBody = response.body() != null ? response.body().string() : "[]";
            }
            
            if (responseBody.equals("[]")) {
                return null;
            }
            
            // Parse response to get photo URL
            try {
                List<Object> users = objectMapper.readValue(responseBody, List.class);
                if (users.isEmpty()) {
                    return null;
                }
                
                @SuppressWarnings("unchecked")
                Object photoUrl = ((java.util.Map<String, Object>) users.get(0)).get("profile_photo_url");
                return photoUrl != null ? photoUrl.toString() : null;
                
            } catch (Exception e) {
                throw new IOException("Failed to parse user profile data: " + e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Error getting profile photo URL: {}", e.toString());
            return null;
        }
    }
    
    private void updateUserProfilePhoto(String contactNumber, String photoUrl) throws IOException {
        try {
            // First check if the user exists
            Request checkRequest = new Request.Builder()
                    .url(supabaseUrl + "/rest/v1/users?contact_number=eq." + contactNumber)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .head()
                    .build();
            
            boolean userExists;
            try (Response checkResponse = client.newCall(checkRequest).execute()) {
                userExists = checkResponse.isSuccessful() && checkResponse.code() != 404;
            }
            
            if (!userExists) {
                // Insert new user record
                String userJson = "{\"contact_number\": \"" + contactNumber + "\", \"profile_photo_url\": " + 
                        (photoUrl != null ? "\"" + photoUrl + "\"" : "null") + "}";
                
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), userJson);
                
                Request insertRequest = new Request.Builder()
                        .url(supabaseUrl + "/rest/v1/users")
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(body)
                        .build();
                
                try (Response insertResponse = client.newCall(insertRequest).execute()) {
                    if (!insertResponse.isSuccessful()) {
                        log.debug("Failed to create user record (status={}): {}", insertResponse.code(), insertResponse.message());
                        // If we get a 404, it might be because the table doesn't exist
                        if (insertResponse.code() == 404 || insertResponse.code() == 400) {
                            log.debug("Users table may not exist; will retry after next app restart");
                        }
                    }
                }
            } else {
                // User exists, update it
                // Create JSON with photo URL
                String json;
                if (photoUrl != null) {
                    json = "{\"profile_photo_url\": \"" + photoUrl + "\"}";
                } else {
                    json = "{\"profile_photo_url\": null}";
                }
                
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
                
                // Update user record
                Request request = new Request.Builder()
                        .url(supabaseUrl + "/rest/v1/users?contact_number=eq." + contactNumber)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .patch(body)
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Failed to update user profile: " + response.message());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error updating profile photo: {}", e.toString());
        }
    }
    
    public void deleteUser(String contactNumber) throws IOException {
        // First try to delete the profile photo if it exists
        try {
            deleteProfilePhoto(getUserProfilePhotoUrl(contactNumber));
        } catch (Exception e) {
            // Log but continue with user deletion
            log.warn("Failed to delete profile photo during user deletion: {}", e.toString());
        }
        
        // Delete the user record
        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/users?contact_number=eq." + contactNumber)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .delete()
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) { // Ignore 404 not found
                throw new IOException("Failed to delete user: " + response.message());
            }
        }
    }

    public void saveRegistration(Registration registration) throws IOException {
        log.debug("Saving registration to Supabase (registrationId={})", registration != null ? registration.getId() : null);
        
        // Create custom JSON that ensures proper structure for Supabase
        String json = createCustomRegistrationJson(registration);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/registration")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(body)
                .build();

        String responseBody = null;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.body() != null) {
                    responseBody = response.body().string();
                }
                log.warn("Registration save failed (registrationId={}, status={}, message={})", registration.getId(), response.code(), response.message());
                if (responseBody != null && !responseBody.isBlank()) {
                    log.debug("Registration save error body length={}", responseBody.length());
                }
                throw new IOException("Data save failed");
            } else {
                log.debug("Registration saved successfully to Supabase (registrationId={})", registration.getId());
            }
        }
    }

    /**
     * Create a custom JSON representation of the registration for Supabase
     */
    private String createCustomRegistrationJson(Registration registration) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        
        // Add registration fields
        jsonBuilder.append("\"id\": ").append(registration.getId() != null ? registration.getId() : "null").append(",");
        jsonBuilder.append("\"user_id\": ").append(registration.getUserId() != null ? registration.getUserId() : "null").append(",");
        jsonBuilder.append("\"full_name\": \"").append(escapeJsonString(registration.getFullName())).append("\",");
        jsonBuilder.append("\"vehicle_type\": \"").append(escapeJsonString(registration.getVehicleType())).append("\",");
        jsonBuilder.append("\"contact_number\": \"").append(escapeJsonString(registration.getContactNumber())).append("\",");
        jsonBuilder.append("\"whatsapp_number\": \"").append(escapeJsonString(registration.getWhatsappNumber())).append("\",");
        
        // Handle nullable fields
        if (registration.getAlternateContactNumber() != null) {
            jsonBuilder.append("\"alternate_contact_number\": \"").append(escapeJsonString(registration.getAlternateContactNumber())).append("\",");
        } else {
            jsonBuilder.append("\"alternate_contact_number\": null,");
        }
        
        jsonBuilder.append("\"vehicle_plate_number\": \"").append(escapeJsonString(registration.getVehiclePlateNumber())).append("\",");
        jsonBuilder.append("\"state\": \"").append(escapeJsonString(registration.getState())).append("\",");
        jsonBuilder.append("\"city\": \"").append(escapeJsonString(registration.getCity())).append("\",");
        jsonBuilder.append("\"pincode\": \"").append(escapeJsonString(registration.getPincode())).append("\",");
        
        // Add highlight fields
        if (registration.getHighlight1() != null) {
            jsonBuilder.append("\"highlight1\": \"").append(escapeJsonString(registration.getHighlight1())).append("\",");
        } else {
            jsonBuilder.append("\"highlight1\": null,");
        }
        
        if (registration.getHighlight2() != null) {
            jsonBuilder.append("\"highlight2\": \"").append(escapeJsonString(registration.getHighlight2())).append("\",");
        } else {
            jsonBuilder.append("\"highlight2\": null,");
        }
        
        if (registration.getHighlight3() != null) {
            jsonBuilder.append("\"highlight3\": \"").append(escapeJsonString(registration.getHighlight3())).append("\",");
        } else {
            jsonBuilder.append("\"highlight3\": null,");
        }
        
        if (registration.getHighlight4() != null) {
            jsonBuilder.append("\"highlight4\": \"").append(escapeJsonString(registration.getHighlight4())).append("\",");
        } else {
            jsonBuilder.append("\"highlight4\": null,");
        }
        
        if (registration.getHighlight5() != null) {
            jsonBuilder.append("\"highlight5\": \"").append(escapeJsonString(registration.getHighlight5())).append("\",");
        } else {
            jsonBuilder.append("\"highlight5\": null,");
        }
        
        // Handle image URLs as JSON array - Notice no comma after this since it's the last field
        jsonBuilder.append("\"vehicle_image_urls\": [");
        List<String> imageUrls = registration.getVehicleImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            for (int i = 0; i < imageUrls.size(); i++) {
                if (i > 0) {
                    jsonBuilder.append(",");
                }
                jsonBuilder.append("\"").append(escapeJsonString(imageUrls.get(i))).append("\"");
            }
        }
        jsonBuilder.append("]");
        
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    /**
     * Escape special characters in JSON strings
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
    
    /**
     * Update an existing registration in Supabase
     */
    public void updateRegistration(Registration registration) throws IOException {
        log.debug("Updating registration in Supabase (registrationId={})", registration != null ? registration.getId() : null);
        
        // Use custom JSON here too
        String json = createCustomRegistrationJson(registration);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/registration?id=eq." + registration.getId())
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : null;
                log.warn("Registration update failed (registrationId={}, status={}, message={})", registration.getId(), response.code(), response.message());
                if (respBody != null && !respBody.isBlank()) {
                    log.debug("Registration update error body length={}", respBody.length());
                }
                throw new IOException("Registration update failed");
            }
            log.debug("Registration updated successfully in Supabase (registrationId={})", registration.getId());
        }
    }

    /**
     * Delete image from Supabase storage by URL
     * 
     * @param imageUrl The URL of the image to delete
     * @throws IOException If deletion fails
     */
    public void deleteImage(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            log.debug("Delete image requested with empty URL; skipping");
            return; // Nothing to delete
        }
        log.debug("Attempting to delete image from Supabase by URL");
        
        // Extract the filename from the URL
        // Supabase URLs are typically in the format:
        // https://{supabase-project}.supabase.co/storage/v1/object/public/{bucket}/{path}
        String filename = null;
        try {
            // Parse URL to extract the path
            URI uri = new URI(imageUrl);
            String path = uri.getPath();
            
            // Extract the part after /storage/v1/object/public/{bucket}/
            if (path.contains("/public/")) {
                filename = path.substring(path.indexOf("/public/") + "/public/".length());
            } else if (path.contains("/object/")) {
                filename = path.substring(path.indexOf("/object/") + "/object/".length());
            } else {
                // Fallback: just use the last part of the path
                String[] parts = path.split("/");
                filename = parts.length > 0 ? parts[parts.length - 1] : null;
            }
            
            // If bucket name is in the path, remove it from the filename
            if (filename != null && filename.startsWith(bucketName + "/")) {
                filename = filename.substring(bucketName.length() + 1);
            }
        } catch (Exception e) {
            log.debug("Failed to parse image URL for deletion: {}", e.toString());
            // Fallback extraction method
            try {
                // Simple string parsing as fallback
                String[] parts = imageUrl.split("/");
                filename = parts[parts.length - 1];
                String secondLast = parts[parts.length - 2];
                if (secondLast != null && !secondLast.equals("public")) {
                    filename = secondLast + "/" + filename;
                }
            } catch (Exception ex) {
                log.warn("Failed fallback image URL parsing: {}", ex.toString());
                throw new IOException("Could not extract filename from URL");
            }
        }
        
        if (filename == null || filename.trim().isEmpty()) {
            log.warn("Could not extract filename from image URL");
            throw new IOException("Could not extract filename from URL");
        }
        
        // Try direct deletion with full URL path first
        try {
            String deleteUrl = null;
            
            // Try to construct a delete URL based on our URL structure knowledge
            if (imageUrl.contains("/storage/v1/object/public/")) {
                // Replace 'public' with actual bucket in the URL for deletion
                deleteUrl = imageUrl.replace("/storage/v1/object/public/", "/storage/v1/object/");
            } else {
                // Fallback to standard delete URL
                deleteUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filename;
            }
            
            Request request = new Request.Builder()
                    .url(deleteUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .delete()
                    .build();
    
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() && response.code() != 404) { // Ignore 404 errors
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.warn("Failed to delete image (status={}): {}", response.code(), response.message());
                    if (errorBody != null && !errorBody.isBlank()) {
                        log.debug("Delete image error body length={}", errorBody.length());
                    }
                    throw new IOException("Failed to delete image");
                }
                log.debug("Image deleted successfully (or already missing)");
                
                // If it was successful (or 404 which is fine), we're done
                return;
            }
        } catch (Exception e) {
            // If the first attempt failed, log it and try the second method
            log.debug("First delete attempt failed; trying fallback: {}", e.toString());
            // Continue to try the second method
        }
        
        // Second attempt - try with bucket name and filename
        try {
            String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filename;
            log.debug("Second delete attempt (standard bucket+filename URL)");
            
            Request request = new Request.Builder()
                    .url(deleteUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .delete()
                    .build();
    
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() && response.code() != 404) { // Ignore 404 errors
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.warn("Second delete attempt failed (status={}): {}", response.code(), response.message());
                    if (errorBody != null && !errorBody.isBlank()) {
                        log.debug("Second delete attempt error body length={}", errorBody.length());
                    }
                    throw new IOException("Failed to delete image");
                }
                log.debug("Image deleted successfully via second attempt (or already missing)");
            }
        } catch (Exception e) {
            log.warn("All deletion attempts failed: {}", e.toString());
            throw new IOException("Failed to delete image", e);
        }
    }

    /**
     * Delete a registration from Supabase by ID
     */
    public void deleteRegistration(Long registrationId) throws IOException {
        log.debug("Deleting registration from Supabase (registrationId={})", registrationId);
        
        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/registration?id=eq." + registrationId)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .delete()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) { // Ignore 404 not found
                String body = response.body() != null ? response.body().string() : null;
                log.warn("Failed to delete registration (registrationId={}, status={}): {}", registrationId, response.code(), response.message());
                if (body != null && !body.isBlank()) {
                    log.debug("Delete registration error body length={}", body.length());
                }
                throw new IOException("Failed to delete registration");
            }
            log.debug("Registration deleted successfully from Supabase (registrationId={})", registrationId);
        }
    }

    /**
     * Upload images to Supabase storage inside a folder named after the registration ID
     * 
     * @param images List of images to upload
     * @param registrationId Registration ID to use as folder name
     * @return Map containing the folderPath and imageUrls
     * @throws IOException If upload fails
     */
    public Map<String, Object> uploadImagesToFolder(List<MultipartFile> images, Long registrationId) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IOException("No images provided to upload");
        }
        
        if (registrationId == null) {
            throw new IOException("Registration ID cannot be null");
        }
        
        // Create folder path using registration ID
        String folderPath = registrationId.toString();
        
        // Ensure the folder exists
        ensureFolderExists(folderPath);
        
        // Keep track of uploaded image URLs
        List<String> uploadedImageUrls = new ArrayList<>();
        
        // Upload each image to the specified folder WITH resize + watermark + WebP conversion
        int index = 0;
        for (MultipartFile image : images) {
            if (image.isEmpty()) {
                index++;
                continue;
            }

            boolean isFront = (index == 0); // treat first image as front (thumbnail size)
            index++;

            // Process image (resize + watermark + convert to WebP/JPEG fallback)
            byte[] processedBytes = processAndConvertImage(image, isFront);
            String extension = processedBytes == null ? "jpg" : "webp"; // assume webp if successful
            if (processedBytes == null) {
                // Fallback to original bytes if processing failed
                processedBytes = image.getBytes();
            }

            String originalName = image.getOriginalFilename() != null ? image.getOriginalFilename() : "image";
            String baseName = originalName.replaceAll("[^A-Za-z0-9_-]", "_");
            String filename = UUID.randomUUID() + (isFront ? "_front" : "") + "_" + baseName + "." + extension;
            String fullPath = folderPath + "/" + filename;

            MediaType mediaType = extension.equals("webp") ? MediaType.parse("image/webp") : MediaType.parse(image.getContentType());
            RequestBody fileBody = RequestBody.create(mediaType, processedBytes);

            Request request = new Request.Builder()
                    .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fullPath)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .put(fileBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Image upload failed: " + response.message());
                }
                String imageUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fullPath;
                log.debug("Uploaded processed image for registration (registrationId={})", registrationId);
                uploadedImageUrls.add(imageUrl);
            }
        }
        
        // Create or update the registration image folder record
        RegistrationImageFolder imageFolder = registrationImageFolderRepository
                .findFirstByRegistrationId(registrationId)
                .orElse(new RegistrationImageFolder(registrationId, folderPath));
        
        // Save the folder information to the database
        registrationImageFolderRepository.save(imageFolder);
        
        log.debug("Uploaded {} images for registration (registrationId={})", uploadedImageUrls.size(), registrationId);
        
        // Create result map with both folder path and image URLs
        Map<String, Object> result = new HashMap<>();
        result.put("folderPath", folderPath);
        result.put("imageUrls", uploadedImageUrls);
        
        return result;
    }

    /**
     * Resize + watermark + convert image to WebP (front image: 360px width, others: 1200px max width).
     * Falls back to original bytes if any step fails.
     */
    private byte[] processAndConvertImage(MultipartFile image, boolean isFront) {
        try (InputStream in = image.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) return null;
            int targetWidth = isFront ? 360 : 1200;
            int w = src.getWidth();
            int h = src.getHeight();
            int newW = w;
            int newH = h;
            if (w > targetWidth) {
                newW = targetWidth;
                newH = (int) Math.round((targetWidth / (double) w) * h);
            }
            BufferedImage canvas = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, newW, newH, null);
            // Watermark (diagonal tiled HPG)
            String mark = "HPG";
            float alpha = 0.12f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(Color.WHITE);
            int fontSize = Math.max(24, (int) (newW * 0.12));
            g.setFont(new Font("Arial", Font.BOLD, fontSize));
            AffineTransform orig = g.getTransform();
            g.rotate(Math.toRadians(-30), newW / 2.0, newH / 2.0);
            int step = (int) (fontSize * 3.0);
            for (int y = -newH; y < newH * 2; y += step) {
                for (int x = -newW; x < newW * 2; x += step) {
                    g.drawString(mark, x, y);
                }
            }
            g.setTransform(orig);
            g.dispose();
            // Try WebP writer
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionType(param.getCompressionType());
                        param.setCompressionQuality(0.82f);
                    }
                    writer.write(null, new IIOImage(canvas, null, null), param);
                } finally {
                    writer.dispose();
                }
                return out.toByteArray();
            }
            // Fallback to JPEG if WebP not available
            Iterator<ImageWriter> jpgWriters = ImageIO.getImageWritersByFormatName("jpg");
            if (jpgWriters.hasNext()) {
                ImageWriter writer = jpgWriters.next();
                try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(0.82f);
                    }
                    writer.write(null, new IIOImage(canvas, null, null), param);
                } finally {
                    writer.dispose();
                }
                return out.toByteArray();
            }
            // Last resort: PNG
            ImageIO.write(canvas, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.debug("Image processing failed; using original bytes: {}", e.toString());
            return null;
        }
    }

    /**
     * Get the full public URL for an image in the storage bucket
     * 
     * @param folderPath The folder path (registration ID)
     * @param filename The filename
     * @return Full public URL
     */
    public String getPublicUrl(String folderPath, String filename) {
        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + folderPath + "/" + filename;
    }
    
    /**
     * Get all image URLs for a specific registration
     * 
     * @param registrationId The registration ID
     * @return List of image URLs
     * @throws IOException If retrieval fails
     */
    public List<String> getRegistrationImages(Long registrationId) throws IOException {
        // Create folder path using registration ID
        String folderPath = registrationId.toString();

        log.debug("Fetching images for registration (registrationId={})", registrationId);
        
        // Build request to list objects in the folder
        Request request = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/object/list/" + bucketName + "?prefix=" + folderPath + "/")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .get()
                .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Failed to list images for registration (registrationId={}, status={}): {}", registrationId, response.code(), response.message());
                throw new IOException("Failed to list images: " + response.message());
            }
            
            // Parse the response to get filenames
            List<String> imageUrls = new ArrayList<>();
            String responseBody = response.body().string();

            log.debug("Storage list returned payload length={} (registrationId={})", responseBody != null ? responseBody.length() : 0, registrationId);
            
            // Using jackson to parse the JSON response
            List<Map<String, Object>> files = objectMapper.readValue(responseBody, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            
            log.debug("Found {} storage objects (registrationId={})", files.size(), registrationId);
            
            for (Map<String, Object> file : files) {
                String name = (String) file.get("name");
                
                // Skip hidden folder marker files
                if (name.endsWith("/.hidden_folder") || name.endsWith("/.folder")) {
                    log.debug("Skipping hidden folder marker object (registrationId={})", registrationId);
                    continue;
                }
                
                // IMPORTANT: Generate direct public URL for the image using a consistent format
                // Format: {supabaseUrl}/storage/v1/object/public/{bucketName}/{name}
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + name;
                
                imageUrls.add(publicUrl);
            }
            
            // Test if URLs are accessible
            if (!imageUrls.isEmpty()) {
                try {
                    Request testRequest = new Request.Builder()
                            .url(imageUrls.get(0))
                            .head() // Only request headers, not the full image
                            .build();
                    
                    try (Response testResponse = client.newCall(testRequest).execute()) {
                        log.debug("Image URL accessibility test (registrationId={}, status={})", registrationId, testResponse.code());
                        if (!testResponse.isSuccessful()) {
                            log.debug("Primary image URL format not accessible; trying alternative (registrationId={})", registrationId);
                            
                            // Try an alternative URL format if the first one fails
                            String altUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + 
                                folderPath + "/" + imageUrls.get(0).substring(imageUrls.get(0).lastIndexOf('/') + 1);
                            
                            Request altTestRequest = new Request.Builder()
                                .url(altUrl)
                                .head()
                                .build();
                                
                            try (Response altTestResponse = client.newCall(altTestRequest).execute()) {
                                log.debug("Alternative URL test result (registrationId={}, status={})", registrationId, altTestResponse.code());
                                    
                                if (altTestResponse.isSuccessful()) {
                                    log.debug("Alternative URL format works; updating all URLs (registrationId={})", registrationId);
                                    // Update all URLs to use this format
                                    List<String> updatedUrls = new ArrayList<>();
                                    for (String url : imageUrls) {
                                        String updatedUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + 
                                            folderPath + "/" + url.substring(url.lastIndexOf('/') + 1);
                                        updatedUrls.add(updatedUrl);
                                    }
                                    imageUrls = updatedUrls;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error testing image URL for registration (registrationId={}): {}", registrationId, e.toString());
                }
            }

            log.debug("Returning {} image URLs for registration (registrationId={})", imageUrls.size(), registrationId);
            
            return imageUrls;
        }
    }
    
    /**
     * Direct method to delete all images for a vehicle
     * This method uses multiple approaches to ensure all images are deleted
     * 
     * @param registrationId The registration ID
     * @throws IOException If deletion fails
     */
    public void deleteAllVehicleImages(Long registrationId) throws IOException {
        log.debug("Starting comprehensive vehicle image deletion (registrationId={})", registrationId);
        
        // Track if we found any folders to delete
        boolean foundFolders = false;
        
        // APPROACH 1: Try to get folder paths from database
        try {
            List<RegistrationImageFolder> folders = registrationImageFolderRepository.findByRegistrationId(registrationId);

            if (!folders.isEmpty()) {
                foundFolders = true;
                log.debug("Found {} folder records in DB (registrationId={})", folders.size(), registrationId);

                for (RegistrationImageFolder folder : folders) {
                    String folderPath = folder.getFolderPath(); // e.g. "123" (we actually stored images as 123/filename)
                    log.debug("Deleting folder contents from storage using DB-recorded folderPath (registrationId={})", registrationId);

                    // Delete using both prefix variants to cover Supabase list API expectations
                    boolean deletedBase = deleteFilesWithPrefix(folderPath);       // may miss files if API expects trailing slash
                    boolean deletedSlash = deleteFilesWithPrefix(folderPath + "/"); // explicit folder prefix form used during upload
                    if (deletedBase || deletedSlash) {
                        log.debug("Folder contents removed (registrationId={})", registrationId);
                    } else {
                        log.debug("No files matched for folder path variants (registrationId={})", registrationId);
                    }
                }
            } else {
                log.debug("No folders found in DB; trying direct ID-based prefixes (registrationId={})", registrationId);
                // Attempt direct deletion using registrationId base folder assumptions
                String idPath = registrationId.toString();
                boolean idDeletedBase = deleteFilesWithPrefix(idPath);
                boolean idDeletedSlash = deleteFilesWithPrefix(idPath + "/");
                if (idDeletedBase || idDeletedSlash) {
                    foundFolders = true;
                    log.debug("Removed files using direct registration ID prefixes (registrationId={})", registrationId);
                }
            }
        } catch (Exception e) {
            log.debug("Error finding folders in database (registrationId={}): {}", registrationId, e.toString());
        }
        
        // APPROACH 2: Try common folder patterns
        try {
            // Common patterns for vehicle image folders
            String[] folderPatterns = {
                "vehicles/" + registrationId,
                "vehicles/" + registrationId + "/",
                "vehicle/" + registrationId,
                "vehicle/" + registrationId + "/",
                "registration/" + registrationId,
                "registration/" + registrationId + "/"
            };
            
            for (String pattern : folderPatterns) {
                boolean deleted = deleteFilesWithPrefix(pattern);
                if (deleted) {
                    foundFolders = true;
                }
            }
        } catch (Exception e) {
            log.debug("Error deleting with common patterns (registrationId={}): {}", registrationId, e.toString());
        }
        
        // APPROACH 3: Search for files containing the registration ID
        try {
            searchAndDeleteFilesByRegistrationId(registrationId);
        } catch (Exception e) {
            log.debug("Error in search and delete (registrationId={}): {}", registrationId, e.toString());
        }
        
        // Clean up database records regardless of success
        try {
            int deleted = jdbcTemplate.update("DELETE FROM registration_image_folders WHERE registration_id = ?", registrationId);
            log.debug("Removed {} folder records from database (registrationId={})", deleted, registrationId);
        } catch (Exception e) {
            log.warn("Failed to remove folder records from database (registrationId={}): {}", registrationId, e.toString());
        }
        
        if (!foundFolders) {
            log.debug("No folders were found for deletion; images may still exist in storage (registrationId={})", registrationId);
        }
    }
    
    /**
     * Delete all files with a specific prefix from the storage bucket
     * 
     * @param prefix The prefix to match files against
     * @return true if any files were deleted, false otherwise
     * @throws IOException If deletion fails
     */
    private boolean deleteFilesWithPrefix(String prefix) throws IOException {
        log.debug("Deleting files with prefix (normalized) in Supabase storage");
        // Normalise prefix: ensure trailing slash so Supabase list treats it as folder
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        try {
            int deleted = deleteFilesWithPrefixRecursive(normalizedPrefix, 0, new java.util.HashSet<>());
            if (deleted > 0) {
                log.debug("Deleted {} files for prefix", deleted);
            }
            return deleted > 0;
        } catch (Exception e) {
            log.debug("Exception during deletion for prefix: {}", e.toString());
            return false;
        }
    }

    private int deleteFilesWithPrefixRecursive(String normalizedPrefix, int depth, java.util.Set<String> visited) throws IOException {
        // Prevent runaway recursion and loops
        if (depth > 12) return 0;
        if (normalizedPrefix == null || normalizedPrefix.isBlank()) return 0;
        if (!normalizedPrefix.endsWith("/")) normalizedPrefix = normalizedPrefix + "/";
        if (!visited.add(normalizedPrefix)) return 0;

        JsonNode entries = listObjectsWithPrefix(normalizedPrefix);
        if (entries == null || !entries.isArray() || entries.size() == 0) return 0;

        int deletedCount = 0;

        for (JsonNode entry : entries) {
            if (entry == null || entry.get("name") == null) continue;
            String name = entry.get("name").asText();
            if (name == null || name.isBlank()) continue;

            boolean isFolder = entry.has("id") && entry.get("id").isNull();
            String fullPath;
            if (name.startsWith(normalizedPrefix)) {
                fullPath = name;
            } else {
                fullPath = normalizedPrefix + name;
            }

            if (isFolder || name.endsWith("/")) {
                String childPrefix = fullPath.endsWith("/") ? fullPath : fullPath + "/";
                deletedCount += deleteFilesWithPrefixRecursive(childPrefix, depth + 1, visited);
                continue;
            }

            Request deleteRequest = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fullPath)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .delete()
                .build();
            try (Response deleteResponse = client.newCall(deleteRequest).execute()) {
                if (!deleteResponse.isSuccessful() && deleteResponse.code() != 404) {
                    log.debug("Failed delete (status={}) for a file under prefix", deleteResponse.code());
                } else {
                    deletedCount++;
                }
            }
        }

        return deletedCount;
    }

    private JsonNode listObjectsWithPrefix(String normalizedPrefix) throws IOException {
        return listObjectsWithPrefix(bucketName, normalizedPrefix);
    }

    private JsonNode listObjectsWithPrefix(String bucket, String normalizedPrefix) throws IOException {
        // Supabase Storage list API expects POST with JSON body. It returns *children* of prefix (not recursive).
        String listUrl = supabaseUrl + "/storage/v1/object/list/" + bucket;
        String jsonBody = "{\"prefix\":\"" + normalizedPrefix + "\",\"limit\":1000,\"offset\":0,\"sortBy\":{\"column\":\"name\",\"order\":\"asc\"}}";
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonBody);
        Request listRequest = new Request.Builder()
            .url(listUrl)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer " + supabaseKey)
            .post(body)
            .build();

        try (Response response = client.newCall(listRequest).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "[]";
                return objectMapper.readTree(responseBody);
            }
            log.debug("Primary list POST failed (status={}); attempting legacy GET fallback", response.code());
        }

        // Fallback legacy GET (in case POST blocked)
        Request legacyList = new Request.Builder()
            .url(supabaseUrl + "/storage/v1/object/list/" + bucket + "?prefix=" + normalizedPrefix)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer " + supabaseKey)
            .get()
            .build();
        try (Response legacyResp = client.newCall(legacyList).execute()) {
            if (!legacyResp.isSuccessful()) {
                log.debug("Legacy GET list failed (status={}) for prefix", legacyResp.code());
                return objectMapper.readTree("[]");
            }
            String respBody = legacyResp.body() != null ? legacyResp.body().string() : "[]";
            return objectMapper.readTree(respBody);
        }
    }
    
    /**
     * Search for and delete any files that contain the registration ID in their path
     * 
     * @param registrationId The registration ID to search for
     * @throws IOException If deletion fails
     */
    private void searchAndDeleteFilesByRegistrationId(Long registrationId) throws IOException {
        log.debug("Searching for files containing registration ID (registrationId={})", registrationId);
        
        try {
            // List all files in the bucket
            Request listRequest = new Request.Builder()
                    .url(supabaseUrl + "/storage/v1/object/list/" + bucketName)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .get()
                    .build();
            
            int filesDeleted = 0;
            
            try (Response response = client.newCall(listRequest).execute()) {
                if (!response.isSuccessful()) {
                    log.debug("Search delete: failed to list files (status={}): {}", response.code(), response.message());
                    return;
                }
                
                // Parse the response to get file paths
                String responseBody = response.body().string();
                JsonNode fileList = objectMapper.readTree(responseBody);
                
                log.debug("Search delete: checking {} files (registrationId={})", fileList.size(), registrationId);
                
                // Find and delete files containing the registration ID
                String registrationIdStr = registrationId.toString();
                for (JsonNode file : fileList) {
                    String filePath = file.get("name").asText();
                    
                    // Check if the file path contains the registration ID
                    if (filePath.contains(registrationIdStr)) {
                        log.debug("Search delete: found matching file under registrationId (registrationId={})", registrationId);
                        
                        Request deleteRequest = new Request.Builder()
                                .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath)
                                .addHeader("apikey", supabaseKey)
                                .addHeader("Authorization", "Bearer " + supabaseKey)
                                .delete()
                                .build();
                        
                        try (Response deleteResponse = client.newCall(deleteRequest).execute()) {
                            if (!deleteResponse.isSuccessful()) {
                                log.debug("Search delete: failed to delete a file (status={}): {}", deleteResponse.code(), deleteResponse.message());
                            } else {
                                filesDeleted++;
                            }
                        }
                    }
                }
            }

            log.debug("Search delete: deleted {} files containing registrationId (registrationId={})", filesDeleted, registrationId);
            
        } catch (Exception e) {
            log.debug("Search delete: error searching and deleting files (registrationId={}): {}", registrationId, e.toString());
        }
    }
    
    /**
     * Delete all images in a registration folder
     * This method is now a wrapper around the more comprehensive deleteAllVehicleImages method
     * 
     * @param registrationId The registration ID
     * @throws IOException If deletion fails
     */
    public void deleteRegistrationFolder(Long registrationId) throws IOException {
        log.debug("Deleting registration folder via enhanced deletion (registrationId={})", registrationId);
        deleteAllVehicleImages(registrationId);
    }

    /**
     * Archive a vehicle's images to the deleted-images bucket
     * This method is now disabled since archiving is no longer needed
     */
    public List<String> archiveVehicleImages(Long registrationId) throws IOException {
        // Simply delete the images directly without archiving
        log.debug("Vehicle delete: deleting vehicle images (registrationId={})", registrationId);
        deleteRegistrationFolder(registrationId);
        return new ArrayList<>();
    }

    /**
     * Archive user data
     * This method is now disabled since archiving is no longer needed
     */
    @Transactional
    public void archiveUserData(Long userId) throws IOException {
        log.debug("User delete: deleting user data (userId={})", userId);
        
        // Find the user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IOException("User not found with ID: " + userId));
        
        // Delete profile photo if exists
        if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
            log.debug("User delete: deleting profile photo (userId={})", userId);
            deleteProfilePhoto(user.getProfilePhotoUrl());
        }
        
        // Find all registrations associated with this user
        List<Registration> registrations = registrationRepository.findByUserId(userId);
        
        // Delete all registrations and their images
        for (Registration registration : registrations) {
            log.debug("User delete: deleting vehicle registration (registrationId={})", registration.getId());
            deleteRegistrationFolder(registration.getId());
        }
    }
    
    /**
     * This method is no longer needed since we don't use DeletedVehicleRepository
     */
    public Object getDeletedVehicleRepository() {
        throw new UnsupportedOperationException("Archiving functionality has been disabled");
    }

    /**
     * Create a folder in Supabase storage if it doesn't exist
     * 
     * @param folderPath Folder path to create
     * @throws IOException If folder creation fails
     */
    public void ensureFolderExists(String folderPath) throws IOException {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IOException("Folder path cannot be empty");
        }

        log.debug("Ensuring folder marker exists in Supabase storage");
        
        // In Supabase Storage, folders don't technically exist as separate entities
        // They're implicitly created when you upload a file with a path
        // So to "create" a folder, we need to upload an empty file with a special name
        
        // Create a marker file to establish the folder with a name that will be hidden
        // Use a unique marker file name that includes the folder path to make it easier to identify
        String markerFileName = folderPath + "/.hidden_folder_" + folderPath.replace("/", "_");
        RequestBody emptyBody = RequestBody.create(MediaType.parse("application/octet-stream"), new byte[0]);
        
        Request request = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + markerFileName)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .put(emptyBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Failed to create folder marker (status={}): {}", response.code(), response.message());
                throw new IOException("Failed to create folder: " + response.message());
            }
            log.debug("Successfully created folder marker");
        }
    }

    /**
     * Upload a document to the RC bucket
     * 
     * @param document The document file to upload
     * @param registrationId The ID of the registration to associate with the document
     * @return The URL of the uploaded document
     * @throws IOException If the upload fails
     */
    public String uploadRcDocument(MultipartFile document, Long registrationId) throws IOException {
        return uploadDocumentToBucket(document, registrationId, rcBucketName, "rc");
    }

    // ----------------------------
    // Deleted evidence (archive-before-delete)
    // ----------------------------

    private static final class ParsedSupabaseObject {
        final String bucket;
        final String path;
        final String filename;

        ParsedSupabaseObject(String bucket, String path) {
            this.bucket = bucket;
            this.path = path;
            int idx = path != null ? path.lastIndexOf('/') : -1;
            this.filename = (idx >= 0 && idx + 1 < path.length()) ? path.substring(idx + 1) : path;
        }
    }

    private ParsedSupabaseObject parseSupabaseObjectUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String markerPublic = "/storage/v1/object/public/";
            String markerObject = "/storage/v1/object/";
            String tail;

            int i = url.indexOf(markerPublic);
            if (i >= 0) {
                tail = url.substring(i + markerPublic.length());
            } else {
                i = url.indexOf(markerObject);
                if (i < 0) return null;
                tail = url.substring(i + markerObject.length());
            }

            String[] parts = tail.split("/", 2);
            if (parts.length < 2) return null;
            String bucket = parts[0];
            String path = parts[1];
            if (bucket == null || bucket.isBlank() || path == null || path.isBlank()) return null;
            return new ParsedSupabaseObject(bucket, path);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] downloadObjectBytes(String bucket, String path) throws IOException {
        Request request = new Request.Builder()
            .url(supabaseUrl + "/storage/v1/object/" + bucket + "/" + path)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer " + supabaseKey)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 404) return null;
            if (!response.isSuccessful()) {
                throw new IOException("Download failed (status=" + response.code() + ")");
            }
            return response.body() != null ? response.body().bytes() : null;
        }
    }

    private void uploadObjectBytes(String bucket, String path, byte[] bytes, String contentType) throws IOException {
        MediaType mt = MediaType.parse((contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType);
        RequestBody body = RequestBody.create(mt, bytes != null ? bytes : new byte[0]);
        Request request = new Request.Builder()
            .url(supabaseUrl + "/storage/v1/object/" + bucket + "/" + path)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer " + supabaseKey)
            .put(body)
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed (status=" + response.code() + ")");
            }
        }
    }

    private void deleteObject(String bucket, String path) throws IOException {
        Request request = new Request.Builder()
            .url(supabaseUrl + "/storage/v1/object/" + bucket + "/" + path)
            .addHeader("apikey", supabaseKey)
            .addHeader("Authorization", "Bearer " + supabaseKey)
            .delete()
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IOException("Delete failed (status=" + response.code() + ")");
            }
        }
    }

    private int collectKeysRecursive(String bucket, String normalizedPrefix, int depth, Set<String> visited, Set<String> keys) throws IOException {
        if (depth > 12) return 0;
        if (normalizedPrefix == null || normalizedPrefix.isBlank()) return 0;
        if (!normalizedPrefix.endsWith("/")) normalizedPrefix = normalizedPrefix + "/";
        if (!visited.add(bucket + "::" + normalizedPrefix)) return 0;

        JsonNode entries = listObjectsWithPrefix(bucket, normalizedPrefix);
        if (entries == null || !entries.isArray() || entries.size() == 0) return 0;

        int found = 0;
        for (JsonNode entry : entries) {
            if (entry == null || entry.get("name") == null) continue;
            String name = entry.get("name").asText();
            if (name == null || name.isBlank()) continue;

            boolean isFolder = entry.has("id") && entry.get("id").isNull();
            String fullPath = name.startsWith(normalizedPrefix) ? name : (normalizedPrefix + name);

            if (isFolder || name.endsWith("/")) {
                String childPrefix = fullPath.endsWith("/") ? fullPath : fullPath + "/";
                found += collectKeysRecursive(bucket, childPrefix, depth + 1, visited, keys);
                continue;
            }

            if (keys.add(fullPath)) {
                found++;
            }
        }

        return found;
    }

    private Set<String> collectVehicleImageKeys(Long registrationId) {
        Set<String> keys = new HashSet<>();
        if (registrationId == null) return keys;

        Set<String> visited = new HashSet<>();
        List<String> prefixes = new ArrayList<>();

        try {
            List<RegistrationImageFolder> folders = registrationImageFolderRepository.findByRegistrationId(registrationId);
            for (RegistrationImageFolder folder : folders) {
                if (folder == null) continue;
                String fp = folder.getFolderPath();
                if (fp != null && !fp.isBlank()) {
                    prefixes.add(fp);
                    prefixes.add(fp + "/");
                }
            }
        } catch (Exception _e) {
            // best-effort
        }

        String id = registrationId.toString();
        prefixes.add(id);
        prefixes.add(id + "/");
        prefixes.add("vehicles/" + id);
        prefixes.add("vehicles/" + id + "/");
        prefixes.add("vehicle/" + id);
        prefixes.add("vehicle/" + id + "/");
        prefixes.add("registration/" + id);
        prefixes.add("registration/" + id + "/");

        for (String p : prefixes) {
            String normalized = p.endsWith("/") ? p : p + "/";
            try {
                collectKeysRecursive(bucketName, normalized, 0, visited, keys);
            } catch (Exception _e) {
                // best-effort
            }
        }

        return keys;
    }

    private static String safeRelPathForRegistration(Long registrationId, String sourceKey) {
        if (sourceKey == null) return "";
        String key = sourceKey.startsWith("/") ? sourceKey.substring(1) : sourceKey;
        String rid = registrationId != null ? registrationId.toString() : null;
        if (rid != null) {
            String p1 = rid + "/";
            String p2 = "vehicles/" + rid + "/";
            String p3 = "vehicle/" + rid + "/";
            String p4 = "registration/" + rid + "/";
            if (key.startsWith(p1)) return key.substring(p1.length());
            if (key.startsWith(p2)) return key.substring(p2.length());
            if (key.startsWith(p3)) return key.substring(p3.length());
            if (key.startsWith(p4)) return key.substring(p4.length());
        }
        int idx = key.lastIndexOf('/');
        return (idx >= 0 && idx + 1 < key.length()) ? key.substring(idx + 1) : key;
    }

    public Map<String, Object> archiveAndDeleteRegistrationEvidence(Long userId, Registration registration) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> archived = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> pendingDeletes = new ArrayList<>();

        if (userId == null || registration == null || registration.getId() == null) {
            result.put("archivedObjects", archived);
            result.put("errors", errors);
            result.put("success", false);
            return result;
        }

        String userFolder = "user_" + userId;
        Long registrationId = registration.getId();

        // 1) Vehicle images (from main vehicle-images bucket)
        Set<String> keys = collectVehicleImageKeys(registrationId);
        for (String srcKey : keys) {
            String rel = safeRelPathForRegistration(registrationId, srcKey);
            String destKey = userFolder + "/vehicles/" + registrationId + "/" + rel;
            Map<String, Object> row = new HashMap<>();
            row.put("type", "vehicle-image");
            row.put("sourceBucket", bucketName);
            row.put("sourcePath", srcKey);
            row.put("archiveBucket", deletedEvidenceBucketName);
            row.put("archivePath", destKey);

            try {
                byte[] bytes = retryWithBackoff(() -> downloadObjectBytes(bucketName, srcKey), "download:" + srcKey);
                if (bytes == null) {
                    row.put("status", "missing");
                } else {
                    retryWithBackoff(() -> { uploadObjectBytes(deletedEvidenceBucketName, destKey, bytes, "application/octet-stream"); return null; }, "upload:" + destKey);
                    row.put("status", "copied");
                    pendingDeletes.add(Map.of("bucket", bucketName, "path", srcKey));
                }
            } catch (Exception e) {
                row.put("status", "error");
                row.put("error", e.getMessage());
                errors.add("vehicle-image: " + srcKey + " -> " + e.getMessage());
            }
            archived.add(row);
        }

        // 2) RC and DL documents (based on URLs stored on registration)
        try {
            String rcUrl = registration.getRc();
            ParsedSupabaseObject rcObj = parseSupabaseObjectUrl(rcUrl);
            if (rcObj != null) {
                String destKey = userFolder + "/docs/rc/" + registrationId + "/" + rcObj.filename;
                Map<String, Object> row = new HashMap<>();
                row.put("type", "rc");
                row.put("sourceUrl", rcUrl);
                row.put("sourceBucket", rcObj.bucket);
                row.put("sourcePath", rcObj.path);
                row.put("archiveBucket", deletedEvidenceBucketName);
                row.put("archivePath", destKey);
                try {
                    byte[] bytes = retryWithBackoff(() -> downloadObjectBytes(rcObj.bucket, rcObj.path), "download:rc:" + registrationId);
                    if (bytes != null) {
                        retryWithBackoff(() -> { uploadObjectBytes(deletedEvidenceBucketName, destKey, bytes, "application/octet-stream"); return null; }, "upload:rc:" + registrationId);
                        row.put("status", "copied");
                        pendingDeletes.add(Map.of("bucket", rcObj.bucket, "path", rcObj.path));
                    } else {
                        row.put("status", "missing");
                    }
                } catch (Exception e) {
                    row.put("status", "error");
                    row.put("error", e.getMessage());
                    errors.add("rc: " + e.getMessage());
                }
                archived.add(row);
            }
        } catch (Exception _e) {
            // best-effort
        }

        try {
            String dlUrl = registration.getD_l();
            ParsedSupabaseObject dlObj = parseSupabaseObjectUrl(dlUrl);
            if (dlObj != null) {
                String destKey = userFolder + "/docs/dl/" + registrationId + "/" + dlObj.filename;
                Map<String, Object> row = new HashMap<>();
                row.put("type", "dl");
                row.put("sourceUrl", dlUrl);
                row.put("sourceBucket", dlObj.bucket);
                row.put("sourcePath", dlObj.path);
                row.put("archiveBucket", deletedEvidenceBucketName);
                row.put("archivePath", destKey);
                try {
                    byte[] bytes = retryWithBackoff(() -> downloadObjectBytes(dlObj.bucket, dlObj.path), "download:dl:" + registrationId);
                    if (bytes != null) {
                        retryWithBackoff(() -> { uploadObjectBytes(deletedEvidenceBucketName, destKey, bytes, "application/octet-stream"); return null; }, "upload:dl:" + registrationId);
                        row.put("status", "copied");
                        pendingDeletes.add(Map.of("bucket", dlObj.bucket, "path", dlObj.path));
                    } else {
                        row.put("status", "missing");
                    }
                } catch (Exception e) {
                    row.put("status", "error");
                    row.put("error", e.getMessage());
                    errors.add("dl: " + e.getMessage());
                }
                archived.add(row);
            }
        } catch (Exception _e) {
            // best-effort
        }

        boolean allCopied = errors.isEmpty();
        if (allCopied) {
            for (Map<String, String> del : pendingDeletes) {
                try {
                    String b = del.get("bucket");
                    String p = del.get("path");
                    retryWithBackoff(() -> { deleteObject(b, p); return null; }, "delete:" + b + ":" + p);
                } catch (Exception e) {
                    // If deletion fails at this stage, report but keep copies.
                    errors.add("delete-source: " + e.getMessage());
                }
            }
        }

        result.put("success", errors.isEmpty());
        result.put("archivedObjects", archived);
        result.put("errors", errors);
        result.put("archiveBucket", deletedEvidenceBucketName);
        result.put("archiveUserFolder", userFolder);
        return result;
    }

    public Map<String, Object> archiveAndDeleteProfileEvidence(Long userId, String profilePhotoUrl) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> archived = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> pendingDeletes = new ArrayList<>();

        if (userId == null || profilePhotoUrl == null || profilePhotoUrl.isBlank()) {
            result.put("success", true);
            result.put("archivedObjects", archived);
            result.put("errors", errors);
            return result;
        }

        String userFolder = "user_" + userId;
        ParsedSupabaseObject obj = parseSupabaseObjectUrl(profilePhotoUrl);
        if (obj == null) {
            result.put("success", false);
            result.put("archivedObjects", archived);
            result.put("errors", List.of("Could not parse profilePhotoUrl"));
            return result;
        }

        String destKey = userFolder + "/profile/" + obj.filename;
        Map<String, Object> row = new HashMap<>();
        row.put("type", "profile-photo");
        row.put("sourceUrl", profilePhotoUrl);
        row.put("sourceBucket", obj.bucket);
        row.put("sourcePath", obj.path);
        row.put("archiveBucket", deletedEvidenceBucketName);
        row.put("archivePath", destKey);

        try {
            byte[] bytes = retryWithBackoff(() -> downloadObjectBytes(obj.bucket, obj.path), "download:profile:" + userId);
            if (bytes != null) {
                retryWithBackoff(() -> { uploadObjectBytes(deletedEvidenceBucketName, destKey, bytes, "application/octet-stream"); return null; }, "upload:profile:" + userId);
                row.put("status", "copied");
                pendingDeletes.add(Map.of("bucket", obj.bucket, "path", obj.path));
            } else {
                row.put("status", "missing");
            }
        } catch (Exception e) {
            row.put("status", "error");
            row.put("error", e.getMessage());
            errors.add(e.getMessage());
        }

        archived.add(row);

        if (errors.isEmpty()) {
            for (Map<String, String> del : pendingDeletes) {
                try {
                    String b = del.get("bucket");
                    String p = del.get("path");
                    retryWithBackoff(() -> { deleteObject(b, p); return null; }, "delete:" + b + ":" + p);
                } catch (Exception e) {
                    errors.add("delete-source: " + e.getMessage());
                }
            }
        }

        result.put("success", errors.isEmpty());
        result.put("archivedObjects", archived);
        result.put("errors", errors);
        result.put("archiveBucket", deletedEvidenceBucketName);
        result.put("archiveUserFolder", userFolder);
        return result;
    }
    
    /**
     * Upload a document to the DL bucket
     * 
     * @param document The document file to upload
     * @param registrationId The ID of the registration to associate with the document
     * @return The URL of the uploaded document
     * @throws IOException If the upload fails
     */
    public String uploadDlDocument(MultipartFile document, Long registrationId) throws IOException {
        return uploadDocumentToBucket(document, registrationId, dlBucketName, "dl");
    }
    
    /**
     * Helper method to upload a document to a specific bucket
     * 
     * @param document The document file to upload
     * @param registrationId The ID of the registration to associate with the document
     * @param bucketName The name of the bucket to upload to
     * @param documentType The type of document (for logging and filename generation)
     * @return The URL of the uploaded document
     * @throws IOException If the upload fails
     */
    private String uploadDocumentToBucket(MultipartFile document, Long registrationId, String bucketName, String documentType) throws IOException {
        if (document.isEmpty()) {
            throw new IOException("Empty document file");
        }
        
        return retryWithBackoff(() -> {
            // Create a unique filename for the document - use registrationId and document type only
            // This prevents duplicate uploads by using a more consistent filename
            String originalFilename = document.getOriginalFilename();
            String fileExtension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String filename = documentType + "_" + registrationId + fileExtension;
            
            log.debug("Uploading {} document (registrationId={})", documentType, registrationId);
            
            // Create request body from file
            RequestBody fileBody = RequestBody.create(MediaType.parse(document.getContentType()), document.getBytes());
            
            // Build request to upload file
            Request request = new Request.Builder()
                    .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filename)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .put(fileBody)
                    .build();
            
            // Execute request
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException(documentType + " document upload failed: " + response.message());
                }
                
                // Return public URL of uploaded document
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + filename;
                log.debug("Successfully uploaded {} document (registrationId={})", documentType, registrationId);
                return publicUrl;
            }
        }, "upload" + documentType.toUpperCase() + "Document");
    }
    
    /**
     * Delete a document from either RC or DL bucket
     * 
     * @param documentUrl The URL of the document to delete
     * @throws IOException If the deletion fails
     */
    public void deleteDocument(String documentUrl) throws IOException {
        if (documentUrl == null || documentUrl.isEmpty()) {
            return; // Nothing to delete
        }
        
        // Extract bucket name and file path from URL
        String bucketName;
        String filePath;
        
        if (documentUrl.contains("/rc/")) {
            bucketName = rcBucketName;
            filePath = documentUrl.substring(documentUrl.indexOf("/rc/") + 4);
        } else if (documentUrl.contains("/dl/")) {
            bucketName = dlBucketName;
            filePath = documentUrl.substring(documentUrl.indexOf("/dl/") + 4);
        } else {
            throw new IOException("Cannot determine bucket from URL");
        }
        
        deleteFile(bucketName, filePath);
    }
    
    /**
     * Helper method to delete a file from a bucket
     * 
     * @param bucketName The name of the bucket
     * @param filePath The path of the file within the bucket
     * @throws IOException If the deletion fails
     */
    private void deleteFile(String bucketName, String filePath) throws IOException {
        retryWithBackoff(() -> {
            log.debug("Deleting file from bucket {}", bucketName);
            
            Request request = new Request.Builder()
                    .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .delete()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() && response.code() != 404) {
                    throw new IOException("File deletion failed: " + response.message());
                }

                log.debug("File deleted successfully (or already missing)");
                return null;
            }
        }, "deleteFile");
    }
}
