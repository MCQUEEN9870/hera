package com.example.demo.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.Fast2SMSService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*") // Frontend requests allow karne ke liye
public class AuthController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private Fast2SMSService fast2SMSService;

    @Value("${captcha.enabled:false}")
    private boolean captchaEnabled;

    @Value("${captcha.secret:}")
    private String captchaSecret;

    @Value("${twofactor.template.login:userloginotp}")
    private String twoFactorTemplateLogin;

    @Value("${twofactor.template.signup:usersignup}")
    private String twoFactorTemplateSignup;

    private boolean verifyCaptcha(String token, String ip) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://www.google.com/recaptcha/api/siteverify?secret=" + captchaSecret + "&response=" + token + "&remoteip=" + ip;
            Map<?, ?> res = restTemplate.postForObject(url, null, Map.class);
            Object success = res != null ? res.get("success") : null;
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            System.err.println("Captcha verification error: " + e.getMessage());
            return false;
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signupUser(@RequestBody Map<String, String> request, HttpServletRequest httpReq) {
        // Optional CAPTCHA check in prod
        if (captchaEnabled) {
            String token = httpReq.getHeader("X-Captcha-Token");
            String ip = httpReq.getRemoteAddr();
            if (token == null || token.isEmpty() || !verifyCaptcha(token, ip)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Captcha verification failed"));
            }
        }
        String fullName = request.get("fullName");
        String contactNumber = request.get("contactNumber");
        String email = request.get("email");
        String password = request.getOrDefault("password", "");

        if (fullName == null || contactNumber == null || fullName.isEmpty() || contactNumber.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(Map.of("message", "Full Name and Contact Number are required."));
        }
        
        // Validate phone number
        if (!contactNumber.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(Map.of("message", "Please enter a valid 10-digit mobile number."));
        }

        try {
            // Check if user already exists - use existsByContactNumber for initial check
            boolean userExists = userRepository.existsByContactNumber(contactNumber);
            
            if (userExists) {
                // User exists, load user data and update OTP
                User existingUser = userRepository.findByContactNumber(contactNumber);
                
                if (existingUser == null) {
                    // This should not happen if existsByContactNumber returned true
                    System.err.println("ERROR: User existence check passed but findByContactNumber returned null");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("message", "Failed to retrieve user data. Please try again later."));
                }
                
                // If password provided, update hash
                if (password != null && password.length() >= 4) {
                    try {
                        String hash = new BCryptPasswordEncoder().encode(password);
                        existingUser.setPasswordHash(hash);
                    } catch (Exception ex) {
                        System.err.println("Password hash error: " + ex.getMessage());
                    }
                }

                // Use 2Factor AUTOGEN for existing user as well (template-based)
                String sessionIdExisting = fast2SMSService.sendAutogenOtp(contactNumber, twoFactorTemplateSignup, true);
                if (sessionIdExisting == null || sessionIdExisting.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("message", "Failed to initiate OTP. Please try again."));
                }

                existingUser.setOtp(sessionIdExisting);
                existingUser.setOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

                // Save and log the values
                User savedUser = userRepository.save(existingUser);
                System.out.println("Existing user updated sessionId after save: " + savedUser.getOtp() + ", Expires at: " + savedUser.getOtpExpiresAt());
                
                return ResponseEntity.ok(Map.of(
                    "message", "OTP sent successfully!", 
                    "contactNumber", contactNumber,
                    "userExists", "true"
                ));
            }

            // Initiate AUTOGEN OTP for signup
            String sessionId = fast2SMSService.sendAutogenOtp(contactNumber, twoFactorTemplateSignup, true);
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Failed to initiate OTP. Please try again."));
            }

            // Create a new user
            User newUser = new User();
            newUser.setFullName(fullName);
            newUser.setContactNumber(contactNumber);
            newUser.setEmail(email);
            newUser.setOtp(sessionId); // store session id
            newUser.setOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
            newUser.setRating(0); // Explicitly set rating to 0
            newUser.setReviewText(""); // Explicitly set reviewText to empty

            // Set password hash if provided
            if (password != null && password.length() >= 4) {
                try {
                    String hash = new BCryptPasswordEncoder().encode(password);
                    newUser.setPasswordHash(hash);
                } catch (Exception ex) {
                    System.err.println("Password hash error: " + ex.getMessage());
                }
            }
            
            // Store the new user
            User savedUser = userRepository.save(newUser);
            System.out.println("New user OTP after save: " + savedUser.getOtp() + ", Expires at: " + savedUser.getOtpExpiresAt());
            
            // Verify OTP was saved correctly
            User verifiedUser = userRepository.findByContactNumber(contactNumber);
            if (verifiedUser == null) {
                System.err.println("ERROR: Failed to retrieve newly created user! Database may be having issues.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Failed to create user account. Please try again later."));
            }
            
            if (verifiedUser.getOtp() == null || verifiedUser.getOtp().isEmpty()) {
                System.err.println("WARNING: OTP not properly saved to database for new user!");
                userRepository.updateOtp(contactNumber, sessionId);
            }

            // No direct SMS here; sent via AUTOGEN already

            return ResponseEntity.ok(Map.of(
                "message", "OTP sent successfully!", 
                "contactNumber", contactNumber,
                "userExists", "false"
            ));
        } catch (Exception e) {
            System.err.println("Error in signup process: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to process signup. Please try again later."));
        }
    }

    @PostMapping("/signup-direct")
    public ResponseEntity<Map<String, String>> signupDirect(@RequestBody Map<String, String> request, HttpServletRequest httpReq) {
        // CAPTCHA is recommended for signup
        if (captchaEnabled) {
            String token = httpReq.getHeader("X-Captcha-Token");
            String ip = httpReq.getRemoteAddr();
            if (token == null || token.isEmpty() || !verifyCaptcha(token, ip)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Captcha verification failed"));
            }
        }

        String fullName = request.get("fullName");
        String contactNumber = request.get("contactNumber");
        String email = request.get("email");
        String password = request.getOrDefault("password", "");

        if (fullName == null || fullName.isEmpty() || contactNumber == null || contactNumber.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Full Name and Contact Number are required."));
        }
        if (!contactNumber.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Please enter a valid 10-digit mobile number."));
        }
        if (password == null || password.length() < 4) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Please provide a valid password (min 4 chars)."));
        }

        try {
            boolean userExists = userRepository.existsByContactNumber(contactNumber);
            if (userExists) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This number is already registered. Please login instead."));
            }

            User user = new User();
            user.setFullName(fullName);
            user.setContactNumber(contactNumber);
            user.setEmail(email);
            try {
                String hash = new BCryptPasswordEncoder().encode(password);
                user.setPasswordHash(hash);
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Failed to set password"));
            }
            user.setVerified(true);
            user.setJoinDate(LocalDateTime.now());
            if (user.getRating() == null) user.setRating(0);
            if (user.getReviewText() == null) user.setReviewText("");

            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Signup successful",
                    "contactNumber", contactNumber
            ));
        } catch (Exception e) {
            System.err.println("Error in direct signup: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to process signup. Please try again later."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> loginUser(@RequestBody Map<String, String> request, HttpServletRequest httpReq) {
        String contactNumber = request.get("contactNumber");
        String password = request.getOrDefault("password", "");
        String otpLoginFlag = request.getOrDefault("otpLogin", "false");
        boolean otpLogin = Boolean.parseBoolean(otpLoginFlag);

        System.out.println("Login attempt for number: " + contactNumber);

        if (contactNumber == null || contactNumber.isEmpty()) {
            System.out.println("Login failed: Contact number is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(Map.of("message", "Contact number is required."));
        }
        
        // Validate phone number
        if (!contactNumber.matches("^[6-9]\\d{9}$")) {
            System.out.println("Login failed: Invalid phone number format");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(Map.of("message", "Please enter a valid 10-digit mobile number."));
        }

        // Optional CAPTCHA check in prod ONLY when doing OTP login (i.e., password not provided and explicitly requested)
        if (captchaEnabled && (password == null || password.isEmpty()) && otpLogin) {
            String token = httpReq.getHeader("X-Captcha-Token");
            String ip = httpReq.getRemoteAddr();
            if (token == null || token.isEmpty() || !verifyCaptcha(token, ip)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Captcha verification failed"));
            }
        }

        // Check if user exists
        boolean userExists = userRepository.existsByContactNumber(contactNumber);
        System.out.println("User exists check for " + contactNumber + ": " + userExists);
        
        if (!userExists) {
            System.out.println("Login failed: User not found for number: " + contactNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(Map.of("message", "User not found, please sign up."));
        }

        // Get user after confirming existence
        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) {
            System.err.println("ERROR: User existence check passed but findByContactNumber returned null for: " + contactNumber);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Map.of("message", "Failed to retrieve user data. Please try again later."));
        }

        // If password provided, try password login first
        if (password != null && !password.isEmpty()) {
            try {
                if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
                    boolean match = new BCryptPasswordEncoder().matches(password, user.getPasswordHash());
                    if (match) {
                        // Successful password login
                        // Ensure verified flag
                        user.setVerified(true);
                        if (user.getJoinDate() == null) user.setJoinDate(LocalDateTime.now());
                        if (user.getRating() == null) user.setRating(0);
                        if (user.getReviewText() == null) user.setReviewText("");
                        userRepository.save(user);
                        return ResponseEntity.ok(Map.of(
                            "message", "Login successful via password!",
                            "contactNumber", contactNumber,
                            "userExists", "true"
                        ));
                    } else {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("message", "Invalid password"));
                    }
                } else {
                    // No password set yet -> do NOT fallback to OTP from password flow
                    System.out.println("Password not set for this user; rejecting password login and suggesting reset.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("message", "No password set for this account. Please use Forgot Password to set one."));
                }
            } catch (Exception ex) {
                System.err.println("Password verify error: " + ex.getMessage());
            }
        }
        
        // OTP login is allowed only when explicitly requested
        if (!otpLogin) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Please enter your password to login. For OTP login, use Forgot Password."));
        }

        // Use 2Factor AUTOGEN (lets 2Factor generate OTP and deliver using approved template)
        String sessionId = fast2SMSService.sendAutogenOtp(contactNumber, twoFactorTemplateLogin, true);
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to initiate OTP. Please try again."));
        }

        // Store sessionId as OTP (repurpose field) and expiry
        String otp = "0000"; // placeholder; actual OTP will be verified via 2Factor
        user.setOtp(sessionId); // save session-id in otp field for now
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5);
        user.setOtpExpiresAt(expiryTime);
        
        // Set join date if it's null
        if (user.getJoinDate() == null) {
            user.setJoinDate(LocalDateTime.now());
        }
        
        // Set rating and review_text if they're null
        if (user.getRating() == null) {
            user.setRating(0);
        }
        
        if (user.getReviewText() == null) {
            user.setReviewText("");
        }
        
        try {
            // Save and log the values
            User savedUser = userRepository.save(user);
            System.out.println("Login OTP after save: " + savedUser.getOtp() + ", Expires at: " + savedUser.getOtpExpiresAt());

            // No direct SMS here; sent via AUTOGEN already

            return ResponseEntity.ok(Map.of(
                "message", "OTP sent successfully!",
                "contactNumber", contactNumber,
                "userExists", "true"
            ));
        } catch (Exception e) {
            System.err.println("Error saving OTP: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to process login. Please try again later."));
        }
    }

    @PostMapping("/forgot-init")
    public ResponseEntity<Map<String, String>> forgotInit(@RequestBody Map<String, String> request, HttpServletRequest httpReq) {
        // Optional CAPTCHA check in prod
        if (captchaEnabled) {
            String token = httpReq.getHeader("X-Captcha-Token");
            String ip = httpReq.getRemoteAddr();
            if (token == null || token.isEmpty() || !verifyCaptcha(token, ip)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Captcha verification failed"));
            }
        }
        String contactNumber = request.get("contactNumber");
        if (contactNumber == null || !contactNumber.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Please enter a valid 10-digit mobile number."));
        }
        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found."));
        }
        String sessionId = fast2SMSService.sendAutogenOtp(contactNumber, twoFactorTemplateLogin, true);
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to initiate OTP. Please try again."));
        }
        user.setOtp(sessionId);
        user.setOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "OTP sent for password reset"));
    }

    @PostMapping("/forgot-complete")
    public ResponseEntity<Map<String, String>> forgotComplete(@RequestBody Map<String, String> request) {
        String contactNumber = request.get("contactNumber");
        String otp = request.get("otp");
        String newPassword = request.get("newPassword");
        if (contactNumber == null || otp == null || newPassword == null || newPassword.length() < 4) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid request"));
        }
        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found."));
        }
        String sessionId = user.getOtp();
        boolean valid = (sessionId != null && !sessionId.isEmpty()) && fast2SMSService.verifyAutogenOtp(sessionId, otp);
        if (!valid || user.getOtpExpiresAt() == null || user.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired OTP."));
        }
        // Set new password
        try {
            String hash = new BCryptPasswordEncoder().encode(newPassword);
            user.setPasswordHash(hash);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to set new password"));
        }
        // Clear OTP
        user.setOtp("");
        user.setOtpExpiresAt(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

    @PostMapping("/forgot-verify")
    public ResponseEntity<Map<String, String>> forgotVerify(@RequestBody Map<String, String> request) {
        String contactNumber = request.get("contactNumber");
        String otp = request.get("otp");
        if (contactNumber == null || otp == null || contactNumber.isEmpty() || otp.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid request"));
        }
        User user = userRepository.findByContactNumber(contactNumber);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found."));
        }
        String sessionId = user.getOtp();
        boolean valid = (sessionId != null && !sessionId.isEmpty()) && fast2SMSService.verifyAutogenOtp(sessionId, otp);
        if (!valid || user.getOtpExpiresAt() == null || user.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired OTP."));
        }
        // Do not clear OTP here; next step will set password and clear.
        return ResponseEntity.ok(Map.of("message", "OTP verified"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@RequestBody Map<String, String> request) {
        String contactNumber = request.get("contactNumber");
        String otp = request.get("otp");
        boolean isSignup = Boolean.parseBoolean(request.getOrDefault("isSignup", "false"));

        if (contactNumber == null || otp == null || contactNumber.isEmpty() || otp.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(Map.of("message", "Contact number and OTP are required."));
        }

        try {
            // Find user
            User user = userRepository.findByContactNumber(contactNumber);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(Map.of("message", "User not found."));
            }
            
            System.out.println("Verifying OTP: User provided [" + otp + "], Stored in database [" + user.getOtp() + "]");

            // Verify via 2Factor using stored sessionId
            String sessionId = user.getOtp();
            boolean valid = (sessionId != null && !sessionId.isEmpty()) && fast2SMSService.verifyAutogenOtp(sessionId, otp);
            if (!valid || user.getOtpExpiresAt() == null || user.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
                
                String errorReason;
                errorReason = "Invalid or expired OTP";
                
                System.out.println("OTP verification failed: " + errorReason);
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                     .body(Map.of("message", "Invalid or expired OTP."));
            }
            
            // If it's a new user signup, set join date
            if (isSignup || user.getJoinDate() == null) {
                user.setJoinDate(LocalDateTime.now()); 
            }

            // Clear OTP after verification
            user.setOtp(""); // Use empty string instead of null
            user.setOtpExpiresAt(LocalDateTime.now()); // Set to current time (expired) instead of null
            user.setVerified(true); // Mark user as verified
            
            // First save to ensure database captures the changes
            User savedUser = userRepository.save(user);
            
            // Verify changes were applied correctly
            User verifiedUser = userRepository.findByContactNumber(contactNumber);
            if (verifiedUser != null) {
                System.out.println("After verification - User in DB: OTP=[" + verifiedUser.getOtp() + 
                                   "], OTP Expires=[" + verifiedUser.getOtpExpiresAt() + "]");
            }

            return ResponseEntity.ok(Map.of(
                "message", "OTP verified successfully! You are logged in.",
                "contactNumber", user.getContactNumber(),
                "fullName", user.getFullName() != null ? user.getFullName() : "",
                "verified", "true"
            ));
        } catch (Exception e) {
            System.err.println("Error in OTP verification: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to verify OTP. Please try again."));
        }
    }
    
    // Helper method to generate a 4-digit OTP
    private String generateOtp() {
        Random random = new Random();
        int otp = 1000 + random.nextInt(9000); // 1000-9999
        return String.valueOf(otp);
    }
}