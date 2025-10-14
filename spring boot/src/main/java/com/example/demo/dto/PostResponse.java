package com.example.demo.dto;

import java.time.OffsetDateTime;

public class PostResponse {
    private String id;
    private Long userId;
    private String content;
    private String backgroundCss;
    private Integer fontSize;
    private Boolean isLightText;
    private String category;
    private String imageUrl;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;

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
    public void setIsLightText(Boolean isLightText) { this.isLightText = isLightText; }
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
}


