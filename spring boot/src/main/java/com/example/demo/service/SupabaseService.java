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

@Service
public class SupabaseService {

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
        System.out.println("Initializing SupabaseService...");
        System.out.println("Supabase URL: " + supabaseUrl);
        System.out.println("Bucket Name: " + bucketName);
        System.out.println("Profile Bucket Name: " + profileBucketName);
        System.out.println("Deleted Images Bucket Name: " + deletedImagesBucketName);
        System.out.println("Deleted Profiles Bucket Name: " + deletedProfilesBucketName);
        System.out.println("RC Bucket Name: " + rcBucketName);
        System.out.println("DL Bucket Name: " + dlBucketName);
        
        // Ensure all required buckets exist
        try {
            ensureBucketExists(bucketName);
            ensureBucketExists(profileBucketName);
            ensureBucketExists(deletedImagesBucketName);
            ensureBucketExists(deletedProfilesBucketName);
            ensureBucketExists(rcBucketName);
            ensureBucketExists(dlBucketName);
            // Ensure posts bucket
            if (postsBucketName == null || postsBucketName.isBlank()) {
                postsBucketName = bucketName;
                System.out.println("Posts bucket not configured; defaulting to main bucket: " + postsBucketName);
            } else {
                ensureBucketExists(postsBucketName);
                System.out.println("Using dedicated posts bucket: " + postsBucketName);
            }
            // Best-effort: ensure posts bucket is public so public URLs work
            try { ensureBucketPublic(postsBucketName); } catch (Exception ignore) {}
            System.out.println("All required buckets exist or have been created.");
        } catch (IOException e) {
            System.err.println("Error ensuring buckets exist: " + e.getMessage());
            e.printStackTrace();
            // Continue initialization despite bucket creation failures
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
                System.out.println("Could not set bucket public via PATCH (status " + response.code() + ") — will try PUT");
                Request requestPut = new Request.Builder()
                        .url(supabaseUrl + "/storage/v1/bucket/" + bucket)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .put(body)
                        .build();
                try (Response resp2 = client.newCall(requestPut).execute()) {
                    if (!resp2.isSuccessful()) {
                        System.out.println("Bucket public update failed (status " + resp2.code() + "): " + resp2.message());
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
                    System.out.println("RETRY: Attempt " + retries + " for operation: " + operationName);
                }
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                System.err.println("RETRY ERROR: Failed attempt " + retries + " for " + operationName + ": " + e.getMessage());
                
                if (retries >= maxRetries) {
                    break;
                }
                
                // Exponential backoff
                long backoffTime = retryBackoffMs * (long)Math.pow(2, retries);
                System.out.println("RETRY: Waiting " + backoffTime + "ms before retry...");
                
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
                    String bucketConfig = "{\"name\": \"" + bucket + "\", \"public\": true}";
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
                            throw new IOException("Failed to create bucket " + bucket + ": " + createResponse.message());
                        }
                        System.out.println("Created bucket: " + bucket);
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

        // Add logging to diagnose issues
        System.out.println("Attempting to upload profile photo to Supabase. Bucket: " + profileBucketName);
        System.out.println("File size: " + photo.getSize() + " bytes, Content type: " + photo.getContentType());
        
        try {
            // First ensure bucket exists
            ensureBucketExists(profileBucketName);
            
            // Construct the URL for uploading to the bucket
            String uploadUrl = supabaseUrl + "/storage/v1/object/" + profileBucketName + "/" + filename;
            System.out.println("Uploading to URL: " + uploadUrl);
            
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
                    System.err.println(errorMessage);
                    throw new IOException(errorMessage);
                }
                
                // Generate the public URL
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + profileBucketName + "/" + filename;
                System.out.println("Successfully uploaded profile photo: " + publicUrl);
                
                // Verify the file was uploaded by attempting to access it
                Request verifyRequest = new Request.Builder()
                        .url(publicUrl)
                        .head()
                        .build();
                        
                try (Response verifyResponse = client.newCall(verifyRequest).execute()) {
                    if (!verifyResponse.isSuccessful()) {
                        System.out.println("Warning: Uploaded file verification failed with status " + 
                                          verifyResponse.code() + ". This may be due to Supabase caching.");
                    } else {
                        System.out.println("Verified uploaded file is accessible");
                    }
                }
                
                return publicUrl;
            } catch (IOException e) {
                System.err.println("Network error during profile photo upload: " + e.getMessage());
                e.printStackTrace();
                throw new IOException("Failed to upload profile photo: " + e.getMessage(), e);
            } finally {
                if (response != null && response.body() != null) {
                    response.close();
                }
            }
        } catch (IOException e) {
            System.err.println("Error uploading profile photo: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to upload profile photo: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete profile photo by URL
     */
    public void deleteProfilePhoto(String photoUrl) throws IOException {
        if (photoUrl == null || photoUrl.isEmpty()) {
            System.out.println("No profile photo URL provided for deletion");
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
                System.err.println("Error parsing profile photo URL: " + e.getMessage());
                filename = photoUrl.substring(photoUrl.lastIndexOf("/") + 1);
            }
            
            System.out.println("Attempting to delete profile photo: " + filename + " from bucket: " + profileBucketName);
            
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
                    String responseBody = response.body() != null ? response.body().string() : "No response body";
                    String errorMessage = "Failed to delete profile photo: " + response.message() +
                                         ". Response: " + responseBody;
                    System.err.println(errorMessage);
                    throw new IOException(errorMessage);
                }
                
                System.out.println("Successfully deleted profile photo: " + filename);
            } catch (IOException e) {
                System.err.println("Network error during profile photo deletion: " + e.getMessage());
                throw new IOException("Failed to delete profile photo: " + e.getMessage(), e);
            } finally {
                if (response != null && response.body() != null) {
                    response.close();
                }
            }
        } catch (Exception e) {
            System.err.println("Error deleting profile photo: " + e.getMessage());
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
                        System.out.println("Users table might not exist yet");
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
            System.err.println("Error getting profile photo URL: " + e.getMessage());
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
                        System.out.println("Failed to create user record: " + insertResponse.code() + " " + insertResponse.message());
                        // If we get a 404, it might be because the table doesn't exist
                        if (insertResponse.code() == 404 || insertResponse.code() == 400) {
                            System.out.println("Users table may not exist. Will retry after next app restart.");
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
            System.err.println("Error updating profile photo: " + e.getMessage());
        }
    }
    
    public void deleteUser(String contactNumber) throws IOException {
        // First try to delete the profile photo if it exists
        try {
            deleteProfilePhoto(getUserProfilePhotoUrl(contactNumber));
        } catch (Exception e) {
            // Log but continue with user deletion
            System.err.println("Failed to delete profile photo: " + e.getMessage());
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
        // Add logging
        System.out.println("Saving registration to Supabase: " + registration.getId());
        
        // Create custom JSON that ensures proper structure for Supabase
        String json = createCustomRegistrationJson(registration);

        // Log the JSON being sent to Supabase
        System.out.println("Registration JSON: " + json);

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
                String errorMessage = "Data save failed: " + response.message();
                if (responseBody != null) {
                    errorMessage += " - " + responseBody;
                }
                System.err.println(errorMessage);
                throw new IOException(errorMessage);
            } else {
                System.out.println("Registration saved successfully to Supabase");
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
        // Add logging
        System.out.println("Updating registration in Supabase with ID: " + registration.getId());
        
        // Use custom JSON here too
        String json = createCustomRegistrationJson(registration);
        
        // Log the JSON being sent to Supabase
        System.out.println("Update Registration JSON: " + json);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/registration?id=eq." + registration.getId())
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(body)
                .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            String errorMessage = "Registration update failed: " + response.message();
            if (response.body() != null) {
                errorMessage += " - " + response.body().string();
            }
            System.err.println(errorMessage);
            throw new IOException(errorMessage);
        } else {
            System.out.println("Registration updated successfully in Supabase");
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
            System.out.println("DELETE IMAGE: Empty URL provided, nothing to delete");
            return; // Nothing to delete
        }
        
        System.out.println("DELETE IMAGE: Attempting to delete image from URL: " + imageUrl);
        
        // Extract the filename from the URL
        // Supabase URLs are typically in the format:
        // https://{supabase-project}.supabase.co/storage/v1/object/public/{bucket}/{path}
        String filename = null;
        try {
            // Parse URL to extract the path
            URI uri = new URI(imageUrl);
            String path = uri.getPath();
            
            System.out.println("DELETE IMAGE: URL Path: " + path);
            
            // Extract the part after /storage/v1/object/public/{bucket}/
            if (path.contains("/public/")) {
                filename = path.substring(path.indexOf("/public/") + "/public/".length());
                System.out.println("DELETE IMAGE: Method 1 - Extracted filename: " + filename);
            } else if (path.contains("/object/")) {
                filename = path.substring(path.indexOf("/object/") + "/object/".length());
                System.out.println("DELETE IMAGE: Method 2 - Extracted filename: " + filename);
            } else {
                // Fallback: just use the last part of the path
                String[] parts = path.split("/");
                filename = parts.length > 0 ? parts[parts.length - 1] : null;
                System.out.println("DELETE IMAGE: Method 3 - Extracted filename: " + filename);
            }
            
            // If bucket name is in the path, remove it from the filename
            if (filename != null && filename.startsWith(bucketName + "/")) {
                filename = filename.substring(bucketName.length() + 1);
                System.out.println("DELETE IMAGE: Removed bucket name - Extracted filename: " + filename);
            }
        } catch (Exception e) {
            System.err.println("DELETE IMAGE ERROR: Failed to parse URL: " + e.getMessage());
            // Fallback extraction method
            try {
                // Simple string parsing as fallback
                String[] parts = imageUrl.split("/");
                filename = parts[parts.length - 1];
                String secondLast = parts[parts.length - 2];
                if (secondLast != null && !secondLast.equals("public")) {
                    filename = secondLast + "/" + filename;
                }
                System.out.println("DELETE IMAGE: Fallback method - Extracted filename: " + filename);
            } catch (Exception ex) {
                System.err.println("DELETE IMAGE ERROR: Failed fallback URL parsing: " + ex.getMessage());
                throw new IOException("Could not extract filename from URL: " + imageUrl);
            }
        }
        
        if (filename == null || filename.trim().isEmpty()) {
            System.err.println("DELETE IMAGE ERROR: Could not extract filename from URL: " + imageUrl);
            throw new IOException("Could not extract filename from URL: " + imageUrl);
        }
        
        // Try direct deletion with full URL path first
        try {
            String deleteUrl = null;
            
            // Try to construct a delete URL based on our URL structure knowledge
            if (imageUrl.contains("/storage/v1/object/public/")) {
                // Replace 'public' with actual bucket in the URL for deletion
                deleteUrl = imageUrl.replace("/storage/v1/object/public/", "/storage/v1/object/");
                System.out.println("DELETE IMAGE: Method 1 - Constructed delete URL: " + deleteUrl);
            } else {
                // Fallback to standard delete URL
                deleteUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filename;
                System.out.println("DELETE IMAGE: Method 2 - Constructed delete URL: " + deleteUrl);
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
                    System.err.println("DELETE IMAGE ERROR: Failed to delete image: " + response.message() + " - " + errorBody);
                    throw new IOException("Failed to delete image: " + response.message());
                }
                System.out.println("DELETE IMAGE SUCCESS: Image deleted successfully with status: " + response.code());
                
                // If it was successful (or 404 which is fine), we're done
                return;
            }
        } catch (Exception e) {
            // If the first attempt failed, log it and try the second method
            System.err.println("DELETE IMAGE WARNING: First delete attempt failed: " + e.getMessage());
            // Continue to try the second method
        }
        
        // Second attempt - try with bucket name and filename
        try {
            String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filename;
            System.out.println("DELETE IMAGE: Second attempt - Using delete URL: " + deleteUrl);
            
            Request request = new Request.Builder()
                    .url(deleteUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .delete()
                    .build();
    
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() && response.code() != 404) { // Ignore 404 errors
                    String errorBody = response.body() != null ? response.body().string() : "";
                    System.err.println("DELETE IMAGE ERROR: Second attempt failed: " + response.message() + " - " + errorBody);
                    throw new IOException("Failed to delete image: " + response.message());
                }
                System.out.println("DELETE IMAGE SUCCESS: Image deleted successfully with second attempt, status: " + response.code());
            }
        } catch (Exception e) {
            System.err.println("DELETE IMAGE ERROR: All deletion attempts failed: " + e.getMessage());
            throw new IOException("Failed to delete image after multiple attempts: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a registration from Supabase by ID
     */
    public void deleteRegistration(Long registrationId) throws IOException {
        System.out.println("Deleting registration from Supabase with ID: " + registrationId);
        
        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/registration?id=eq." + registrationId)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .delete()
                .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful() && response.code() != 404) { // Ignore 404 not found
            String errorMessage = "Failed to delete registration: " + response.message();
            if (response.body() != null) {
                errorMessage += " - " + response.body().string();
            }
            System.err.println(errorMessage);
            throw new IOException(errorMessage);
        } else {
            System.out.println("Registration deleted successfully from Supabase");
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
                System.out.println("Uploaded processed image URL: " + imageUrl);
                uploadedImageUrls.add(imageUrl);
            }
        }
        
        // Create or update the registration image folder record
        RegistrationImageFolder imageFolder = registrationImageFolderRepository
                .findFirstByRegistrationId(registrationId)
                .orElse(new RegistrationImageFolder(registrationId, folderPath));
        
        // Save the folder information to the database
        registrationImageFolderRepository.save(imageFolder);
        
        // Log the uploaded URLs for debugging
        System.out.println("Uploaded " + uploadedImageUrls.size() + " images for registration " + registrationId);
        for (String url : uploadedImageUrls) {
            System.out.println(" - " + url);
        }
        
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
            System.err.println("Image processing failed, using original bytes: " + e.getMessage());
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
        
        System.out.println("Fetching images for registration ID: " + registrationId + " from path: " + folderPath);
        
        // Build request to list objects in the folder
        Request request = new Request.Builder()
                .url(supabaseUrl + "/storage/v1/object/list/" + bucketName + "?prefix=" + folderPath + "/")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .get()
                .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Failed to list images for registration " + registrationId + ": " + response.message());
                throw new IOException("Failed to list images: " + response.message());
            }
            
            // Parse the response to get filenames
            List<String> imageUrls = new ArrayList<>();
            String responseBody = response.body().string();
            
            System.out.println("Storage list response for registration " + registrationId + ": " + responseBody);
            
            // Using jackson to parse the JSON response
            List<Map<String, Object>> files = objectMapper.readValue(responseBody, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            
            System.out.println("Found " + files.size() + " files for registration " + registrationId);
            
            for (Map<String, Object> file : files) {
                String name = (String) file.get("name");
                
                // Skip hidden folder marker files
                if (name.endsWith("/.hidden_folder") || name.endsWith("/.folder")) {
                    System.out.println("Skipping hidden folder marker: " + name);
                    continue;
                }
                
                // IMPORTANT: Generate direct public URL for the image using a consistent format
                // Format: {supabaseUrl}/storage/v1/object/public/{bucketName}/{name}
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + name;
                
                System.out.println("Adding image URL for registration " + registrationId + ": " + publicUrl);
                imageUrls.add(publicUrl);
            }
            
            // Test if URLs are accessible
            if (!imageUrls.isEmpty()) {
                System.out.println("Testing first image URL for registration " + registrationId + ": " + imageUrls.get(0));
                try {
                    Request testRequest = new Request.Builder()
                            .url(imageUrls.get(0))
                            .head() // Only request headers, not the full image
                            .build();
                    
                    try (Response testResponse = client.newCall(testRequest).execute()) {
                        System.out.println("Image URL test result for registration " + registrationId + ": " + 
                            testResponse.code() + " " + testResponse.message());
                        if (!testResponse.isSuccessful()) {
                            System.err.println("WARNING: Test image URL is not accessible for registration " + 
                                registrationId + ": " + imageUrls.get(0));
                            System.err.println("Testing with a different URL format...");
                            
                            // Try an alternative URL format if the first one fails
                            String altUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + 
                                folderPath + "/" + imageUrls.get(0).substring(imageUrls.get(0).lastIndexOf('/') + 1);
                            
                            Request altTestRequest = new Request.Builder()
                                .url(altUrl)
                                .head()
                                .build();
                                
                            try (Response altTestResponse = client.newCall(altTestRequest).execute()) {
                                System.out.println("Alternative URL test result: " + 
                                    altTestResponse.code() + " " + altTestResponse.message());
                                    
                                if (altTestResponse.isSuccessful()) {
                                    System.out.println("Alternative URL format works, updating all URLs");
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
                    System.err.println("Error testing image URL for registration " + registrationId + ": " + e.getMessage());
                }
            }
            
            System.out.println("Returning " + imageUrls.size() + " image URLs for registration " + registrationId);
            
            // Print all final URLs for debugging
            for (int i = 0; i < imageUrls.size(); i++) {
                System.out.println("Final image URL " + (i+1) + " for registration " + registrationId + ": " + imageUrls.get(i));
            }
            
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
        System.out.println("DELETE IMAGES: Starting comprehensive image deletion for vehicle ID: " + registrationId);
        
        // Track if we found any folders to delete
        boolean foundFolders = false;
        
        // APPROACH 1: Try to get folder paths from database
        try {
            List<RegistrationImageFolder> folders = registrationImageFolderRepository.findByRegistrationId(registrationId);

            if (!folders.isEmpty()) {
                foundFolders = true;
                System.out.println("DELETE IMAGES: Found " + folders.size() + " folders in database for registration ID: " + registrationId);

                for (RegistrationImageFolder folder : folders) {
                    String folderPath = folder.getFolderPath(); // e.g. "123" (we actually stored images as 123/filename)
                    System.out.println("DELETE IMAGES: Deleting folder from database record: " + folderPath);

                    // Delete using both prefix variants to cover Supabase list API expectations
                    boolean deletedBase = deleteFilesWithPrefix(folderPath);       // may miss files if API expects trailing slash
                    boolean deletedSlash = deleteFilesWithPrefix(folderPath + "/"); // explicit folder prefix form used during upload
                    if (deletedBase || deletedSlash) {
                        System.out.println("DELETE IMAGES: Folder contents removed for: " + folderPath);
                    } else {
                        System.out.println("DELETE IMAGES: No files matched for folder path variants: '" + folderPath + "' and '" + folderPath + "/'");
                    }
                }
            } else {
                System.out.println("DELETE IMAGES: No folders found in database for registration ID: " + registrationId + ". Trying direct ID-based prefixes.");
                // Attempt direct deletion using registrationId base folder assumptions
                String idPath = registrationId.toString();
                boolean idDeletedBase = deleteFilesWithPrefix(idPath);
                boolean idDeletedSlash = deleteFilesWithPrefix(idPath + "/");
                if (idDeletedBase || idDeletedSlash) {
                    foundFolders = true;
                    System.out.println("DELETE IMAGES: Removed files using direct registration ID prefixes.");
                }
            }
        } catch (Exception e) {
            System.err.println("DELETE IMAGES: Error finding folders in database: " + e.getMessage());
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
                System.out.println("DELETE IMAGES: Trying pattern: " + pattern);
                boolean deleted = deleteFilesWithPrefix(pattern);
                if (deleted) {
                    foundFolders = true;
                }
            }
        } catch (Exception e) {
            System.err.println("DELETE IMAGES: Error deleting with common patterns: " + e.getMessage());
        }
        
        // APPROACH 3: Search for files containing the registration ID
        try {
            System.out.println("DELETE IMAGES: Searching for any files containing registration ID: " + registrationId);
            searchAndDeleteFilesByRegistrationId(registrationId);
        } catch (Exception e) {
            System.err.println("DELETE IMAGES: Error in search and delete: " + e.getMessage());
        }
        
        // Clean up database records regardless of success
        try {
            int deleted = jdbcTemplate.update("DELETE FROM registration_image_folders WHERE registration_id = ?", registrationId);
            System.out.println("DELETE IMAGES: Removed " + deleted + " folder records from database");
        } catch (Exception e) {
            System.err.println("DELETE IMAGES: Failed to remove folder records from database: " + e.getMessage());
        }
        
        if (!foundFolders) {
            System.out.println("DELETE IMAGES: Warning - No folders were found for deletion. Images may still exist in storage.");
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
        System.out.println("DELETE FILES: Deleting files with prefix: " + prefix);
        
        try {
            // List all files with the given prefix
            Request listRequest = new Request.Builder()
                    .url(supabaseUrl + "/storage/v1/object/list/" + bucketName + "?prefix=" + prefix)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .get()
                    .build();
            
            int filesDeleted = 0;
            
            try (Response response = client.newCall(listRequest).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("DELETE FILES: Failed to list files with prefix " + prefix + ": " + response.code() + " " + response.message());
                    return false;
                }
                
                // Parse the response to get file paths
                String responseBody = response.body().string();
                JsonNode fileList = objectMapper.readTree(responseBody);
                
                if (fileList.size() == 0) {
                    System.out.println("DELETE FILES: No files found with prefix: " + prefix);
                    return false;
                }
                
                System.out.println("DELETE FILES: Found " + fileList.size() + " files with prefix: " + prefix);
                
                // Delete each file
                for (JsonNode file : fileList) {
                    String filePath = file.get("name").asText();
                    System.out.println("DELETE FILES: Deleting file: " + filePath);
                    
                    Request deleteRequest = new Request.Builder()
                            .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath)
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + supabaseKey)
                            .delete()
                            .build();
                    
                    try (Response deleteResponse = client.newCall(deleteRequest).execute()) {
                        if (!deleteResponse.isSuccessful()) {
                            System.err.println("DELETE FILES: Failed to delete file " + filePath + ": " + deleteResponse.code() + " " + deleteResponse.message());
                        } else {
                            System.out.println("DELETE FILES: Successfully deleted file: " + filePath);
                            filesDeleted++;
                        }
                    }
                }
            }
            
            System.out.println("DELETE FILES: Deleted " + filesDeleted + " files with prefix: " + prefix);
            return filesDeleted > 0;
            
        } catch (Exception e) {
            System.err.println("DELETE FILES: Error deleting files with prefix " + prefix + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Search for and delete any files that contain the registration ID in their path
     * 
     * @param registrationId The registration ID to search for
     * @throws IOException If deletion fails
     */
    private void searchAndDeleteFilesByRegistrationId(Long registrationId) throws IOException {
        System.out.println("SEARCH DELETE: Searching for files containing registration ID: " + registrationId);
        
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
                    System.err.println("SEARCH DELETE: Failed to list files: " + response.code() + " " + response.message());
                    return;
                }
                
                // Parse the response to get file paths
                String responseBody = response.body().string();
                JsonNode fileList = objectMapper.readTree(responseBody);
                
                System.out.println("SEARCH DELETE: Checking " + fileList.size() + " files for registration ID: " + registrationId);
                
                // Find and delete files containing the registration ID
                String registrationIdStr = registrationId.toString();
                for (JsonNode file : fileList) {
                    String filePath = file.get("name").asText();
                    
                    // Check if the file path contains the registration ID
                    if (filePath.contains(registrationIdStr)) {
                        System.out.println("SEARCH DELETE: Found matching file: " + filePath);
                        
                        Request deleteRequest = new Request.Builder()
                                .url(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath)
                                .addHeader("apikey", supabaseKey)
                                .addHeader("Authorization", "Bearer " + supabaseKey)
                                .delete()
                                .build();
                        
                        try (Response deleteResponse = client.newCall(deleteRequest).execute()) {
                            if (!deleteResponse.isSuccessful()) {
                                System.err.println("SEARCH DELETE: Failed to delete file " + filePath + ": " + deleteResponse.code() + " " + deleteResponse.message());
                            } else {
                                System.out.println("SEARCH DELETE: Successfully deleted file: " + filePath);
                                filesDeleted++;
                            }
                        }
                    }
                }
            }
            
            System.out.println("SEARCH DELETE: Deleted " + filesDeleted + " files containing registration ID: " + registrationId);
            
        } catch (Exception e) {
            System.err.println("SEARCH DELETE: Error searching and deleting files: " + e.getMessage());
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
        System.out.println("DELETE FOLDER: Using enhanced deletion for registration ID: " + registrationId);
        deleteAllVehicleImages(registrationId);
    }

    /**
     * Archive a vehicle's images to the deleted-images bucket
     * This method is now disabled since archiving is no longer needed
     */
    public List<String> archiveVehicleImages(Long registrationId) throws IOException {
        // Simply delete the images directly without archiving
        System.out.println("VEHICLE DELETE: Directly deleting vehicle images for registration ID: " + registrationId);
        deleteRegistrationFolder(registrationId);
        return new ArrayList<>();
    }

    /**
     * Archive user data
     * This method is now disabled since archiving is no longer needed
     */
    @Transactional
    public void archiveUserData(Long userId) throws IOException {
        System.out.println("USER DELETE: Directly deleting user data for user ID: " + userId);
        
        // Find the user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IOException("User not found with ID: " + userId));
        
        // Delete profile photo if exists
        if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
            System.out.println("USER DELETE: Deleting profile photo for user ID: " + userId);
            deleteProfilePhoto(user.getProfilePhotoUrl());
        }
        
        // Find all registrations associated with this user
        List<Registration> registrations = registrationRepository.findByUserId(userId);
        
        // Delete all registrations and their images
        for (Registration registration : registrations) {
            System.out.println("USER DELETE: Deleting vehicle registration: " + registration.getId());
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
        
        System.out.println("Ensuring folder exists for path: " + folderPath);
        
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
                System.err.println("Failed to create folder marker at " + markerFileName + ": " + response.message());
                throw new IOException("Failed to create folder: " + response.message());
            }
            System.out.println("Successfully created folder marker at " + markerFileName);
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
            
            // Log upload attempt
            System.out.println("Uploading " + documentType + " document for registration " + registrationId + " to bucket " + bucketName);
            System.out.println("File size: " + document.getSize() + " bytes, Content type: " + document.getContentType());
            
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
                System.out.println("Successfully uploaded " + documentType + " document: " + publicUrl);
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
            throw new IOException("Cannot determine bucket from URL: " + documentUrl);
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
            System.out.println("Deleting file from bucket " + bucketName + ": " + filePath);
            
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
                
                System.out.println("Successfully deleted file or file not found (404)");
                return null;
            }
        }, "deleteFile");
    }
}
