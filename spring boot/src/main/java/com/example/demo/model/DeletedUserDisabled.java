package com.example.demo.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Entity for storing information about deleted users and their archived profile photos
 * This class is disabled and will not be used for archiving
 */
@ConditionalOnProperty(name = "app.archiving.enabled", havingValue = "true", matchIfMissing = false)
public class DeletedUserDisabled implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private Long id;

    private Long originalId;

    private String contactNumber;

    private String fullName;
    
    private String email;
    
    private String profilePhotoUrl;

    // Default constructor
    public DeletedUserDisabled() {
    }
    
    // Constructor to create from a User
    public DeletedUserDisabled(User user, String archivedProfilePhotoUrl) {
        this.originalId = user.getId();
        this.contactNumber = user.getContactNumber();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.profilePhotoUrl = archivedProfilePhotoUrl;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOriginalId() {
        return originalId;
    }

    public void setOriginalId(Long originalId) {
        this.originalId = originalId;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    @Override
    public String toString() {
        return "DeletedUser [id=" + id + ", originalId=" + originalId + 
               ", contactNumber=" + contactNumber + "]";
    }
} 