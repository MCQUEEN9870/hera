package com.example.demo.service.ai;

import com.example.demo.model.ai.AIConversation;
import com.example.demo.repository.ai.AIConversationRepository;
import com.example.demo.repository.RegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIConversationService {

    @Autowired
    private AIConversationRepository aiConversationRepository;
    
    @Autowired
    private RegistrationRepository registrationRepository;
    
    // Map of common variations of vehicle type names to their standard names in the database
    private static final Map<String, String> VEHICLE_TYPE_MAPPING = new HashMap<>();
    
    static {
        // Initialize the mapping
        // Manual Cart / Thela variations
        VEHICLE_TYPE_MAPPING.put("manual cart", "Manual Cart (Thel / Rickshaw)");
        VEHICLE_TYPE_MAPPING.put("thel", "Manual Cart (Thel / Rickshaw)");
        VEHICLE_TYPE_MAPPING.put("thela", "Manual Cart (Thel / Rickshaw)");
        VEHICLE_TYPE_MAPPING.put("rickshaw", "Manual Cart (Thel / Rickshaw)");
        
        // Auto Loader variations
        VEHICLE_TYPE_MAPPING.put("auto loader", "Auto Loader (CNG loader)");
        VEHICLE_TYPE_MAPPING.put("cng loader", "Auto Loader (CNG loader)");
        VEHICLE_TYPE_MAPPING.put("auto", "Auto Loader (CNG loader)");
        
        // Tata Ace variations
        VEHICLE_TYPE_MAPPING.put("tata ace", "Tata Ace (Chhota Hathi)");
        VEHICLE_TYPE_MAPPING.put("chhota hathi", "Tata Ace (Chhota Hathi)");
        VEHICLE_TYPE_MAPPING.put("chota hathi", "Tata Ace (Chhota Hathi)");
        
        // E-Rickshaw variations
        VEHICLE_TYPE_MAPPING.put("e-rickshaw", "E-Rickshaw Loader (Tuk-Tuk)");
        VEHICLE_TYPE_MAPPING.put("e rickshaw", "E-Rickshaw Loader (Tuk-Tuk)");
        VEHICLE_TYPE_MAPPING.put("tuk-tuk", "E-Rickshaw Loader (Tuk-Tuk)");
        VEHICLE_TYPE_MAPPING.put("tuk tuk", "E-Rickshaw Loader (Tuk-Tuk)");
        
        // Mini Truck variations
        VEHICLE_TYPE_MAPPING.put("mini truck", "Mini Truck (Eicher Canter)");
        VEHICLE_TYPE_MAPPING.put("eicher canter", "Mini Truck (Eicher Canter)");
        VEHICLE_TYPE_MAPPING.put("canter", "Mini Truck (Eicher Canter)");
        
        // Vikram Tempo variations
        VEHICLE_TYPE_MAPPING.put("vikram", "Vikram Tempo");
        VEHICLE_TYPE_MAPPING.put("tempo", "Vikram Tempo");
        
        // Bolero Pickup variations
        VEHICLE_TYPE_MAPPING.put("bolero", "Bolero Pickup (MaXX)");
        VEHICLE_TYPE_MAPPING.put("bolero pickup", "Bolero Pickup (MaXX)");
        VEHICLE_TYPE_MAPPING.put("maxx", "Bolero Pickup (MaXX)");
        
        // Tata 407 variations
        VEHICLE_TYPE_MAPPING.put("tata 407", "Tata 407");
        VEHICLE_TYPE_MAPPING.put("407", "Tata 407");
        
        // Wheeler variations
        VEHICLE_TYPE_MAPPING.put("6 wheeler", "6 wheeler");
        VEHICLE_TYPE_MAPPING.put("8 wheeler", "8 Wheeler");
        VEHICLE_TYPE_MAPPING.put("12 wheeler", "12 Wheeler");
        
        // Container Truck variations
        VEHICLE_TYPE_MAPPING.put("container", "Container Truck");
        VEHICLE_TYPE_MAPPING.put("container truck", "Container Truck");
        
        // Open Body Truck variations
        VEHICLE_TYPE_MAPPING.put("open body", "Open Body Truck (6 wheeler)");
        VEHICLE_TYPE_MAPPING.put("open body truck", "Open Body Truck (6 wheeler)");
        
        // Closed Body Truck variations
        VEHICLE_TYPE_MAPPING.put("closed body", "Closed Body Truck");
        VEHICLE_TYPE_MAPPING.put("closed body truck", "Closed Body Truck");
        
        // Flatbed Truck variations
        VEHICLE_TYPE_MAPPING.put("flatbed", "Flatbed Truck");
        VEHICLE_TYPE_MAPPING.put("flatbed truck", "Flatbed Truck");
        VEHICLE_TYPE_MAPPING.put("flat bed", "Flatbed Truck");
        
        // JCB variations
        VEHICLE_TYPE_MAPPING.put("jcb", "JCB");
        
        // Crane variations
        VEHICLE_TYPE_MAPPING.put("crane", "Crane");
        
        // Trailer Truck variations
        VEHICLE_TYPE_MAPPING.put("trailer", "Trailer Truck");
        VEHICLE_TYPE_MAPPING.put("trailer truck", "Trailer Truck");
        
        // Tipper Truck variations
        VEHICLE_TYPE_MAPPING.put("tipper", "Tipper Truck (Dumper Truck)");
        VEHICLE_TYPE_MAPPING.put("tipper truck", "Tipper Truck (Dumper Truck)");
        VEHICLE_TYPE_MAPPING.put("dumper", "Tipper Truck (Dumper Truck)");
        VEHICLE_TYPE_MAPPING.put("dumper truck", "Tipper Truck (Dumper Truck)");
        
        // Tanker Truck variations
        VEHICLE_TYPE_MAPPING.put("tanker", "Tanker Truck");
        VEHICLE_TYPE_MAPPING.put("tanker truck", "Tanker Truck");
        
        // Garbage Truck variations
        VEHICLE_TYPE_MAPPING.put("garbage", "Garbage Truck");
        VEHICLE_TYPE_MAPPING.put("garbage truck", "Garbage Truck");
        
        // Ambulance variations
        VEHICLE_TYPE_MAPPING.put("ambulance", "Ambulance");
        
        // Refrigerated variations
        VEHICLE_TYPE_MAPPING.put("refrigerated van", "Refrigerated Vans");
        VEHICLE_TYPE_MAPPING.put("refrigerated vans", "Refrigerated Vans");
        VEHICLE_TYPE_MAPPING.put("chilled van", "Refrigerated Vans");
        VEHICLE_TYPE_MAPPING.put("refrigerated truck", "Refrigerated Trucks");
        VEHICLE_TYPE_MAPPING.put("refrigerated trucks", "Refrigerated Trucks");
        VEHICLE_TYPE_MAPPING.put("chilled truck", "Refrigerated Trucks");
        
        // Packers & Movers variations
        VEHICLE_TYPE_MAPPING.put("packer", "Packer&Movers");
        VEHICLE_TYPE_MAPPING.put("mover", "Packer&Movers");
        VEHICLE_TYPE_MAPPING.put("packers", "Packer&Movers");
        VEHICLE_TYPE_MAPPING.put("movers", "Packer&Movers");
        VEHICLE_TYPE_MAPPING.put("packer&movers", "Packer&Movers");
        VEHICLE_TYPE_MAPPING.put("packers and movers", "Packer&Movers");
        VEHICLE_TYPE_MAPPING.put("packers & movers", "Packer&Movers");
        
        // Parcel Delivery variations
        VEHICLE_TYPE_MAPPING.put("parcel", "Parcel Delivery");
        VEHICLE_TYPE_MAPPING.put("parcel delivery", "Parcel Delivery");
        VEHICLE_TYPE_MAPPING.put("courier", "Parcel Delivery");
        VEHICLE_TYPE_MAPPING.put("delivery", "Parcel Delivery");
    }
    
    /**
     * Store a conversation between the user and the AI assistant
     */
    public AIConversation storeConversation(String sessionId, String userMessage, 
                                           String aiResponse, String vehicleType, 
                                           String pincode, Boolean isSuccessful) {
        
        AIConversation conversation = new AIConversation();
        conversation.setSessionId(sessionId);
        conversation.setUserMessage(userMessage);
        conversation.setAiResponse(aiResponse);
        conversation.setVehicleType(vehicleType);
        conversation.setPincode(pincode);
        conversation.setIsSuccessful(isSuccessful);
        
        return aiConversationRepository.save(conversation);
    }
    
    /**
     * Normalize vehicle type by matching it to standard names in the database
     */
    public String normalizeVehicleType(String vehicleType) {
        if (vehicleType == null) {
            return null;
        }
        
        // Convert to lowercase for case-insensitive matching
        String lowerCaseType = vehicleType.toLowerCase();
        
        // Check if there's a direct mapping
        if (VEHICLE_TYPE_MAPPING.containsKey(lowerCaseType)) {
            return VEHICLE_TYPE_MAPPING.get(lowerCaseType);
        }
        
        // If no direct mapping, try to find a partial match
        for (Map.Entry<String, String> entry : VEHICLE_TYPE_MAPPING.entrySet()) {
            if (lowerCaseType.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // If no match found, return the original value
        return vehicleType;
    }
    
    /**
     * Check if vehicles of a specific type are available at a given pincode
     */
    public VehicleAvailabilityResult checkVehicleAvailability(String vehicleType, String pincode) {
        // Normalize the vehicle type
        String normalizedVehicleType = normalizeVehicleType(vehicleType);
        
        // Count the number of vehicles of the specified type at the given pincode
        long count = registrationRepository.countByVehicleTypeAndPincode(normalizedVehicleType, pincode);
        
        VehicleAvailabilityResult result = new VehicleAvailabilityResult();
        result.setExists(count > 0);
        result.setCount((int) count);
        result.setNormalizedVehicleType(normalizedVehicleType);
        
        return result;
    }
    
    /**
     * Inner class to represent vehicle availability check results
     */
    public static class VehicleAvailabilityResult {
        private boolean exists;
        private int count;
        private String normalizedVehicleType;
        
        public boolean isExists() {
            return exists;
        }
        
        public void setExists(boolean exists) {
            this.exists = exists;
        }
        
        public int getCount() {
            return count;
        }
        
        public void setCount(int count) {
            this.count = count;
        }
        
        public String getNormalizedVehicleType() {
            return normalizedVehicleType;
        }
        
        public void setNormalizedVehicleType(String normalizedVehicleType) {
            this.normalizedVehicleType = normalizedVehicleType;
        }
    }
} 