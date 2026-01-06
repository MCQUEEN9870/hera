package com.example.demo.controller.ai;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.ai.AIConversation;
import com.example.demo.service.ai.AIConversationService;

@RestController
@RequestMapping("/api/ai")
public class AIConversationController {

    @Autowired
    private AIConversationService aiConversationService;

    /**
     * Store a conversation between the user and the AI assistant
     */
    @PostMapping("/conversation")
    public ResponseEntity<AIConversation> storeConversation(@RequestBody ConversationRequest request) {
        AIConversation conversation = aiConversationService.storeConversation(
                request.getSessionId(),
                request.getUserMessage(),
                request.getAiResponse(),
                request.getVehicleType(),
                request.getPincode(),
                request.getIsSuccessful()
        );
        
        return ResponseEntity.ok(conversation);
    }

    /**
     * Check if vehicles of a specific type are available at a given pincode
     */
    @GetMapping("/vehicle-availability")
    public ResponseEntity<Map<String, Object>> checkVehicleAvailability(
            @RequestParam("vehicleType") String vehicleType,
            @RequestParam("pincode") String pincode) {
        
        AIConversationService.VehicleAvailabilityResult result = 
                aiConversationService.checkVehicleAvailability(vehicleType, pincode);
        
        Map<String, Object> response = new HashMap<>();
        response.put("exists", result.isExists());
        response.put("count", result.getCount());
        response.put("normalizedVehicleType", result.getNormalizedVehicleType());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Normalize a vehicle type to the standard name used in the database
     */
    @GetMapping("/normalize-vehicle-type")
    public ResponseEntity<Map<String, String>> normalizeVehicleType(
            @RequestParam("vehicleType") String vehicleType) {
        
        String normalizedType = aiConversationService.normalizeVehicleType(vehicleType);
        
        Map<String, String> response = new HashMap<>();
        response.put("original", vehicleType);
        response.put("normalized", normalizedType);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Request class for storing conversations
     */
    public static class ConversationRequest {
        private String sessionId;
        private String userMessage;
        private String aiResponse;
        private String vehicleType;
        private String pincode;
        private Boolean isSuccessful;

        // Getters and setters
        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public void setUserMessage(String userMessage) {
            this.userMessage = userMessage;
        }

        public String getAiResponse() {
            return aiResponse;
        }

        public void setAiResponse(String aiResponse) {
            this.aiResponse = aiResponse;
        }

        public String getVehicleType() {
            return vehicleType;
        }

        public void setVehicleType(String vehicleType) {
            this.vehicleType = vehicleType;
        }

        public String getPincode() {
            return pincode;
        }

        public void setPincode(String pincode) {
            this.pincode = pincode;
        }

        public Boolean getIsSuccessful() {
            return isSuccessful;
        }

        public void setIsSuccessful(Boolean isSuccessful) {
            this.isSuccessful = isSuccessful;
        }
    }
} 