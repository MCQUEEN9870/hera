package com.example.demo.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id; // UUID string

    @Column(name = "user_id", nullable = false)
    private Long userId; // align with users.id (Long)

    @Column(name = "content", nullable = false, columnDefinition = "text")
    @Size(min = 50, max = 500)
    private String content;

    @Column(name = "background_css", columnDefinition = "text")
    private String backgroundCss;

    @Column(name = "font_size", nullable = false)
    @Min(16)
    @Max(48)
    private Integer fontSize = 18;

    @Column(name = "is_light_text", nullable = false)
    private Boolean isLightText = true;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "created_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "expires_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

    // Let DB default '{}'::jsonb apply by omitting this column on insert
    @Column(name = "details", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String details;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getBackgroundCss() { return backgroundCss; }
    public void setBackgroundCss(String backgroundCss) { this.backgroundCss = backgroundCss; }

    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }

    public Boolean getIsLightText() { return isLightText; }
    public void setIsLightText(Boolean lightText) { isLightText = lightText; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}


