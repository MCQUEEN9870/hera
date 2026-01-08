package com.example.demo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Feedback;
import com.example.demo.model.Registration;
import com.example.demo.model.User;
import com.example.demo.repository.FeedbackRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private static final Logger logger = Logger.getLogger(FeedbackController.class.getName());

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RegistrationRepository registrationRepository;

    private static String maskPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) return "****";
        return "****" + digits.substring(digits.length() - 4);
    }

    /**
     * Get feedback from feedback table only (non-signed-in users)
     * This endpoint is used by the feedback carousel that only shows non-user feedback
     * Only returns feedback that has both rating AND review text
     * @return List of feedback objects from feedback table only
     */
    @GetMapping("/get-feedback")
    public ResponseEntity<List<Map<String, Object>>> getFeedback() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            logger.info("Fetching feedback from feedback table only");
            
            // Get feedback from feedback table
            List<Feedback> feedbacks = feedbackRepository.findAll();
            for (Feedback feedback : feedbacks) {
                // Only include feedback with both rating AND review text
                if (feedback.getRating() != null && feedback.getRating() > 0 
                        && feedback.getReviewText() != null && !feedback.getReviewText().trim().isEmpty()) {
                    Map<String, Object> feedbackMap = new HashMap<>();
                    feedbackMap.put("id", feedback.getId());
                    feedbackMap.put("name", feedback.getName());
                    feedbackMap.put("address", feedback.getAddress());
                    feedbackMap.put("rating", feedback.getRating());
                    feedbackMap.put("reviewText", feedback.getReviewText());
                    feedbackMap.put("source", "feedback");
                    
                    result.add(feedbackMap);
                    logger.info("Added feedback from: " + feedback.getName());
                }
            }
            
            if (result.isEmpty()) {
                logger.info("No feedback found in feedback table with both rating and review text");
                return ResponseEntity.ok(result);
            }
            
            logger.info("Returning " + result.size() + " feedback entries");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching feedback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Get all feedback from both users and feedback tables
     * @return List of feedback objects
     */
    @GetMapping("/get-all-feedback")
    public ResponseEntity<List<Map<String, Object>>> getAllFeedback() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            logger.info("Fetching all feedback from users and feedback tables");
            
            // Get feedback from users table (only include users with ratings)
            List<User> users = userRepository.findByRatingIsNotNull();
            for (User user : users) {
                if (user.getRating() != null && user.getRating() > 0) {
                    Map<String, Object> feedback = new HashMap<>();
                    feedback.put("id", user.getId());
                    feedback.put("name", user.getFullName() != null ? user.getFullName() : "User");
                    feedback.put("address", ""); // Address not available in User model
                    feedback.put("rating", user.getRating());
                    feedback.put("reviewText", user.getReviewText());
                    feedback.put("source", "user");
                    
                    result.add(feedback);
                    logger.info("Added user feedback from: " + user.getFullName());
                }
            }
            
            // Get feedback from feedback table
            List<Feedback> feedbacks = feedbackRepository.findAll();
            for (Feedback feedback : feedbacks) {
                Map<String, Object> feedbackMap = new HashMap<>();
                feedbackMap.put("id", feedback.getId());
                feedbackMap.put("name", feedback.getName());
                feedbackMap.put("address", feedback.getAddress());
                feedbackMap.put("rating", feedback.getRating());
                feedbackMap.put("reviewText", feedback.getReviewText());
                feedbackMap.put("source", "feedback");
                
                result.add(feedbackMap);
                logger.info("Added feedback from: " + feedback.getName());
            }
            
            // Shuffle the results to mix users and feedback entries
            java.util.Collections.shuffle(result);
            
            if (result.isEmpty()) {
                logger.info("No feedback found in both tables");
                return ResponseEntity.ok(result);
            }
            
            logger.info("Returning " + result.size() + " feedback entries");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching all feedback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Get feedback from users table only (signed-in users)
     * This endpoint is used by the vehicle owner feedback carousel
     * Only returns users with rating AND review text
     * @return List of user feedback objects 
     */
    @GetMapping("/get-user-feedback")
    public ResponseEntity<List<Map<String, Object>>> getUserFeedback() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            logger.info("Fetching feedback from users table only");
            
            // Get feedback from users table (only include users with ratings)
            List<User> users = userRepository.findByRatingIsNotNull();
            for (User user : users) {
                if (user.getRating() != null && user.getRating() > 0 
                        && user.getReviewText() != null && !user.getReviewText().trim().isEmpty()) {
                    Map<String, Object> feedback = new HashMap<>();
                    feedback.put("id", user.getId());
                    feedback.put("userId", user.getId()); // Add userId for location mapping
                    feedback.put("name", user.getFullName() != null ? user.getFullName() : "User");
                    feedback.put("address", ""); // Address not consistently available in User model
                    feedback.put("rating", user.getRating());
                    feedback.put("reviewText", user.getReviewText());
                    feedback.put("source", "user");
                    
                    result.add(feedback);
                    logger.info("Added user feedback from: " + user.getFullName());
                }
            }
            
            if (result.isEmpty()) {
                logger.info("No user feedback found with both rating and review text");
                return ResponseEntity.ok(result);
            }
            
            // Shuffle the results for variety
            java.util.Collections.shuffle(result);
            
            logger.info("Returning " + result.size() + " user feedback entries");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching user feedback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Get user locations from registration table
     * This endpoint is used by the vehicle owner feedback carousel to display proper locations
     * @return Map of userId to location (city, state)
     */
    @GetMapping("/get-user-locations")
    public ResponseEntity<Map<String, String>> getUserLocations() {
        Map<String, String> userLocations = new HashMap<>();
        
        try {
            logger.info("Fetching user locations for feedback carousel");
            
            // Find all registrations to get user locations
            List<Registration> registrations = registrationRepository.findAll();
            
            // For each registration, add the user's location to the map
            // If a user has multiple registrations, the latest one's location will be used
            for (Registration reg : registrations) {
                // Only include registrations with a valid userId
                if (reg.getUserId() != null) {
                    String location = reg.getCity();
                    if (reg.getState() != null && !reg.getState().isEmpty()) {
                        location += ", " + reg.getState();
                    }
                    
                    // Map the userId to the location
                    userLocations.put(reg.getUserId().toString(), location);
                }
            }
            
            if (userLocations.isEmpty()) {
                logger.info("No user locations found");
                return ResponseEntity.ok(userLocations);
            }
            
            logger.info("Returning " + userLocations.size() + " user locations");
            return ResponseEntity.ok(userLocations);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching user locations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Handle feedback submission for signed-in users
     * Updates the rating and review_text fields in the user table
     */
    @PostMapping("/save-user-feedback")
    public ResponseEntity<Map<String, Object>> saveUserFeedback(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Received user feedback submission");
            
            // Extract and validate required fields
            if (!payload.containsKey("phone") || !payload.containsKey("rating")) {
                response.put("message", "Missing required fields");
                return ResponseEntity.badRequest().body(response);
            }
            
            String phone = (String) payload.get("phone");
            Integer rating = Integer.parseInt(payload.get("rating").toString());
            String reviewText = payload.containsKey("review_text") ? (String) payload.get("review_text") : "";

            logger.info("Processing user feedback (phone=" + maskPhone(phone) + ", rating=" + rating + ")");
            
            // Validate phone number (basic validation)
            if (phone == null || phone.isEmpty()) {
                response.put("message", "Invalid phone number format - empty");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate rating
            if (rating < 1 || rating > 5) {
                response.put("message", "Rating must be between 1 and 5");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Try to find user by contact number (exact match)
            User user = userRepository.findByContactNumber(phone);
            
            // If not found, try again with various formats
            if (user == null && phone.length() == 10) {
                // Try with country code
                user = userRepository.findByContactNumber("+91" + phone);
                if (user == null) {
                    user = userRepository.findByContactNumber("91" + phone);
                }
                
                // If still not found, try other common formats
                if (user == null) {
                    logger.info("Trying alternative phone formats for: " + phone);
                    // Query all users and search manually as a last resort
                    List<User> allUsers = userRepository.findAll();
                    for (User u : allUsers) {
                        String contactNum = u.getContactNumber();
                        if (contactNum == null) continue;
                        
                        // Try different phone formats
                        if (contactNum.endsWith(phone) || 
                            phone.endsWith(contactNum) ||
                            contactNum.contains(phone) ||
                            phone.contains(contactNum)) {
                            user = u;
                            logger.info("Found matching user by pattern match");
                            break;
                        }
                    }
                }
            }
            
            if (user == null) {
                logger.warning("User not found for phone number: " + maskPhone(phone));
                response.put("message", "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            logger.info("User found: " + user.getId());
            
            // Update user's rating and review
            user.setRating(rating);
            user.setReviewText(reviewText != null ? reviewText : "");
            userRepository.save(user);
            
            logger.info("User feedback saved successfully (phone=" + maskPhone(phone) + ")");
            
            response.put("message", "Feedback submitted successfully");
            return ResponseEntity.ok(response);
            
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid rating format", e);
            response.put("message", "Invalid rating format");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving user feedback", e);
            response.put("message", "Error submitting feedback");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handle feedback submission for non-signed-in users
     * Creates a new record in the feedback table
     */
    @PostMapping("/save-feedback")
    public ResponseEntity<Map<String, Object>> saveFeedback(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Received non-user feedback submission");
            
            // Extract and validate required fields
            if (!payload.containsKey("name") || !payload.containsKey("address") || !payload.containsKey("rating")) {
                response.put("message", "Missing required fields");
                return ResponseEntity.badRequest().body(response);
            }
            
            String name = (String) payload.get("name");
            String address = (String) payload.get("address");
            Integer rating = Integer.parseInt(payload.get("rating").toString());
            String reviewText = payload.containsKey("review_text") ? (String) payload.get("review_text") : "";

            logger.info("Processing non-user feedback (rating=" + rating + ")");
            
            // Validate name and address
            if (name == null || name.isEmpty() || address == null || address.isEmpty()) {
                response.put("message", "Name and address are required");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate rating
            if (rating < 1 || rating > 5) {
                response.put("message", "Rating must be between 1 and 5");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Create and save new feedback
            Feedback feedback = new Feedback();
            feedback.setName(name);
            feedback.setAddress(address);
            feedback.setRating(rating);
            feedback.setReviewText(reviewText != null ? reviewText : "");
            
            Feedback savedFeedback = feedbackRepository.save(feedback);
            
            logger.info("Feedback saved successfully with ID: " + savedFeedback.getId());
            
            response.put("message", "Feedback submitted successfully");
            response.put("id", savedFeedback.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid rating format", e);
            response.put("message", "Invalid rating format");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving feedback", e);
            response.put("message", "Error submitting feedback");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 