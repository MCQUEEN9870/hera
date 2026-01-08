package com.example.demo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Entity for storing information about deleted vehicles and their archived images
 * This class is disabled and will not be used for archiving
 */
@ConditionalOnProperty(name = "app.archiving.enabled", havingValue = "true", matchIfMissing = false)
public class DeletedVehicleDisabled implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private Long id;

    private Long userId;

    private String fullName;

    private String vehicleType;

    private String contactNumber;

    private String vehiclePlateNumber;
    
    private String vehicleImageUrlsJson;
    
    private List<String> vehicleImageUrls = new ArrayList<>();

    // Default constructor
    public DeletedVehicleDisabled() {
        // Removed initialization of deletionTimestamp
    }
    
    // Constructor to create from a Registration
    public DeletedVehicleDisabled(Registration registration, List<String> archivedUrls) {
        this.userId = registration.getUserId();
        this.fullName = registration.getFullName();
        this.vehicleType = registration.getVehicleType();
        this.contactNumber = registration.getContactNumber();
        this.vehiclePlateNumber = registration.getVehiclePlateNumber();
        // Removed originalRegistrationId and deletionTimestamp assignments
        
        // Make sure we have a non-null list of URLs
        if (archivedUrls == null) {
            archivedUrls = new ArrayList<>();
        }
        
        // Check if we have any URLs and log them
        // Intentionally do not log archived URLs (sensitive)
        
        // Set the URLs using our improved setter method
        this.setVehicleImageUrls(archivedUrls);
        
        // Double-check the JSON field was set properly
        if (this.vehicleImageUrlsJson == null || this.vehicleImageUrlsJson.isEmpty() || "null".equals(this.vehicleImageUrlsJson)) {
            // Manually create the JSON array
            StringBuilder jsonArray = new StringBuilder("[");
            for (int i = 0; i < archivedUrls.size(); i++) {
                if (i > 0) {
                    jsonArray.append(",");
                }
                // Escape special characters in the URL
                String escapedUrl = archivedUrls.get(i)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
                jsonArray.append("\"").append(escapedUrl).append("\"");
            }
            jsonArray.append("]");
            
            this.vehicleImageUrlsJson = jsonArray.toString();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getVehiclePlateNumber() {
        return vehiclePlateNumber;
    }

    public void setVehiclePlateNumber(String vehiclePlateNumber) {
        this.vehiclePlateNumber = vehiclePlateNumber;
    }
    
    // Modified getter to convert JSON to List - renamed
    public List<String> getVehicleImageUrls() {
        if (vehicleImageUrls == null || vehicleImageUrls.isEmpty()) {
            // If transient field is empty but we have JSON data, parse it
            if (vehicleImageUrlsJson != null && !vehicleImageUrlsJson.isEmpty()) {
                try {
                    if ("[null]".equals(vehicleImageUrlsJson) || "[]".equals(vehicleImageUrlsJson)) {
                        // Handle special cases of empty arrays
                        vehicleImageUrls = new ArrayList<>();
                    } else {
                        ObjectMapper objectMapper = new ObjectMapper();
                        vehicleImageUrls = objectMapper.readValue(vehicleImageUrlsJson, 
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                        
                        // Filter out null values and empty strings
                        if (vehicleImageUrls != null) {
                            vehicleImageUrls.removeIf(url -> url == null || url.isEmpty());
                        }
                    }
                } catch (Exception e) {
                    vehicleImageUrls = new ArrayList<>();
                }
            } else {
                vehicleImageUrls = new ArrayList<>();
            }
        }
        return vehicleImageUrls != null ? vehicleImageUrls : new ArrayList<>();
    }
    
    // Modified setter to convert List to JSON - renamed
    public void setVehicleImageUrls(List<String> vehicleImageUrls) {
        // Filter out null values and empty strings
        if (vehicleImageUrls != null) {
            vehicleImageUrls.removeIf(url -> url == null || url.isEmpty());
            this.vehicleImageUrls = vehicleImageUrls;
        } else {
            this.vehicleImageUrls = new ArrayList<>();
        }
        
        // Also update the JSON field
        try {
            // Instead of using ObjectMapper, manually create the JSON array for better control
            StringBuilder jsonArray = new StringBuilder("[");
            if (this.vehicleImageUrls != null && !this.vehicleImageUrls.isEmpty()) {
                for (int i = 0; i < this.vehicleImageUrls.size(); i++) {
                    if (i > 0) {
                        jsonArray.append(",");
                    }
                    // Escape special characters in the URL
                    String escapedUrl = this.vehicleImageUrls.get(i)
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"");
                    jsonArray.append("\"").append(escapedUrl).append("\"");
                }
            }
            jsonArray.append("]");
            
            this.vehicleImageUrlsJson = jsonArray.toString();
            
            // Double check the JSON string is not empty or null
            if (this.vehicleImageUrlsJson == null || this.vehicleImageUrlsJson.isEmpty() || "null".equals(this.vehicleImageUrlsJson)) {
                this.vehicleImageUrlsJson = "[]";
            }
            
        } catch (Exception e) {
            this.vehicleImageUrlsJson = "[]";
        }
    }
    
    // Getter - renamed
    public String getVehicleImageUrlsJson() {
        // Ensure we never return null or empty string
        if (vehicleImageUrlsJson == null || vehicleImageUrlsJson.isEmpty() || "null".equals(vehicleImageUrlsJson)) {
            vehicleImageUrlsJson = "[]";
        }
        return vehicleImageUrlsJson;
    }
    
    // Setter - renamed
    public void setVehicleImageUrlsJson(String vehicleImageUrlsJson) {
        // Ensure we never store null or empty string
        if (vehicleImageUrlsJson == null || vehicleImageUrlsJson.isEmpty() || "null".equals(vehicleImageUrlsJson)) {
            this.vehicleImageUrlsJson = "[]";
        } else {
            this.vehicleImageUrlsJson = vehicleImageUrlsJson;
        }
    }

    @Override
    public String toString() {
        return "DeletedVehicle [id=" + id + ", userId=" + userId + 
               ", vehiclePlateNumber=" + vehiclePlateNumber + "]";
    }
} 