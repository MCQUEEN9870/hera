package com.example.demo.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;



@Entity
@Table(name = "users")  // Yahan tumhare Supabase me jo table ka naam hai, wahi likhna
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contact_number", unique = true, nullable = false)
    private String contactNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    // Store either the raw OTP (legacy) or 2Factor session-id (AUTOGEN). Use larger length.
    @Column(name = "otp", length = 128)
    private String otp;

    @Column(name = "otp_expires_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime otpExpiresAt;

    @Column(name = "full_name")
    private String fullName;
    
    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;
    
    @Column(name = "membership")
    private String membership = "Standard"; // Default to Standard membership
    
    @Column(name = "membership_purchase_time", columnDefinition = "TIMESTAMP")
    private LocalDateTime membershipPurchaseTime;
    
    @Column(name = "membership_expire_time", columnDefinition = "TIMESTAMP")
    private LocalDateTime membershipExpireTime;
    
    @Column(name = "join_date", columnDefinition = "TIMESTAMP")
    private LocalDateTime joinDate;
    
    @Column(name = "verified")
    private Boolean verified = false;

    @Column(name = "rating")
    private Integer rating = 0; // Default to 0
    
    @Column(name = "review_text")
    private String reviewText = ""; // Default to empty string

    // Email OTP password reset fields (stored in users table)
    @Column(name = "otp_email")
    private String otpEmail;

    @Column(name = "email_otp_code_hash")
    private String emailOtpCodeHash;

    // NOTE: Assuming column name is email_otp_expires_at (common). If your DB column is different,
    // tell me the exact column name and I'll adjust mapping.
    @Column(name = "email_otp_expires_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime emailOtpExpiresAt;

    @Column(name = "email_otp_attempts")
    private Integer emailOtpAttempts = 0;

    @Column(name = "email_reset_token_hash")
    private String emailResetTokenHash;

    @Column(name = "email_reset_token_expires_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime emailResetTokenExpiresAt;

    @Column(name = "email_otp_verified_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime emailOtpVerifiedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public LocalDateTime getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }
    
    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }
    
    public String getMembership() {
        return membership;
    }
    
    public void setMembership(String membership) {
        this.membership = membership;
    }
    
    public LocalDateTime getMembershipPurchaseTime() {
        return membershipPurchaseTime;
    }
    
    public void setMembershipPurchaseTime(LocalDateTime membershipPurchaseTime) {
        this.membershipPurchaseTime = membershipPurchaseTime;
    }
    
    public LocalDateTime getMembershipExpireTime() {
        return membershipExpireTime;
    }
    
    public void setMembershipExpireTime(LocalDateTime membershipExpireTime) {
        this.membershipExpireTime = membershipExpireTime;
    }

    public LocalDateTime getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDateTime joinDate) {
        this.joinDate = joinDate;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }
    
    public Integer getRating() {
        return rating;
    }
    
    public void setRating(Integer rating) {
        this.rating = rating;
    }
    
    public String getReviewText() {
        return reviewText;
    }
    
    public void setReviewText(String reviewText) {
        this.reviewText = reviewText;
    }

    public String getOtpEmail() {
        return otpEmail;
    }

    public void setOtpEmail(String otpEmail) {
        this.otpEmail = otpEmail;
    }

    public String getEmailOtpCodeHash() {
        return emailOtpCodeHash;
    }

    public void setEmailOtpCodeHash(String emailOtpCodeHash) {
        this.emailOtpCodeHash = emailOtpCodeHash;
    }

    public LocalDateTime getEmailOtpExpiresAt() {
        return emailOtpExpiresAt;
    }

    public void setEmailOtpExpiresAt(LocalDateTime emailOtpExpiresAt) {
        this.emailOtpExpiresAt = emailOtpExpiresAt;
    }

    public Integer getEmailOtpAttempts() {
        return emailOtpAttempts;
    }

    public void setEmailOtpAttempts(Integer emailOtpAttempts) {
        this.emailOtpAttempts = emailOtpAttempts;
    }

    public String getEmailResetTokenHash() {
        return emailResetTokenHash;
    }

    public void setEmailResetTokenHash(String emailResetTokenHash) {
        this.emailResetTokenHash = emailResetTokenHash;
    }

    public LocalDateTime getEmailResetTokenExpiresAt() {
        return emailResetTokenExpiresAt;
    }

    public void setEmailResetTokenExpiresAt(LocalDateTime emailResetTokenExpiresAt) {
        this.emailResetTokenExpiresAt = emailResetTokenExpiresAt;
    }

    public LocalDateTime getEmailOtpVerifiedAt() {
        return emailOtpVerifiedAt;
    }

    public void setEmailOtpVerifiedAt(LocalDateTime emailOtpVerifiedAt) {
        this.emailOtpVerifiedAt = emailOtpVerifiedAt;
    }
}