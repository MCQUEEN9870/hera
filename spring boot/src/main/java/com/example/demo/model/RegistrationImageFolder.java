package com.example.demo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "registration_image_folders")
public class RegistrationImageFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;
    
    @Column(name = "folder_path", nullable = false)
    private String folderPath;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Default constructor
    public RegistrationImageFolder() {
        this.createdAt = LocalDateTime.now();
    }
    
    // Parameterized constructor
    public RegistrationImageFolder(Long registrationId, String folderPath) {
        this.registrationId = registrationId;
        this.folderPath = folderPath;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getRegistrationId() {
        return registrationId;
    }
    
    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }
    
    public String getFolderPath() {
        return folderPath;
    }
    
    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "RegistrationImageFolder{" +
                "id=" + id +
                ", registrationId=" + registrationId +
                ", folderPath='" + folderPath + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
} 