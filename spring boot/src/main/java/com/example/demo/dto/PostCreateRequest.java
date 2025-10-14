package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PostCreateRequest {
    @NotNull
    private Long userId;

    @NotBlank
    @Size(min = 50, max = 500)
    private String content;

    @NotBlank
    private String category; // must be one of the allowed list

    @Min(16)
    @Max(48)
    private Integer fontSize;

    @NotNull
    private Boolean isLightText;

    private String backgroundCss;

    // one image only
    private String imageUrl;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }
    public Boolean getIsLightText() { return isLightText; }
    public void setIsLightText(Boolean isLightText) { this.isLightText = isLightText; }
    public String getBackgroundCss() { return backgroundCss; }
    public void setBackgroundCss(String backgroundCss) { this.backgroundCss = backgroundCss; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}


