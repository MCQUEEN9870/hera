package com.example.demo.repository.ai;

import com.example.demo.model.ai.AIConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIConversationRepository extends JpaRepository<AIConversation, Long> {
    
    List<AIConversation> findBySessionId(String sessionId);
    
    List<AIConversation> findByVehicleTypeAndPincode(String vehicleType, String pincode);
} 