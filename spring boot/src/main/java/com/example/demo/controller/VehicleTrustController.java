package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleTrustController {

    private static final Logger log = LoggerFactory.getLogger(VehicleTrustController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Endpoint to increment the trust counter for a vehicle
     * 
     * @param vehicleId The ID of the vehicle to update
     * @return ResponseEntity with success/failure message
     */
    @PostMapping("/update-trust-counter/{vehicleId}")
    public ResponseEntity<?> updateTrustCounter(@PathVariable String vehicleId) {
        try {
            // Input validation
            if (vehicleId == null || vehicleId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Vehicle ID is required");
            }

            // First get the current value
            Integer currentValue = jdbcTemplate.queryForObject(
                "SELECT call_tracking FROM registration WHERE id = ?", 
                Integer.class, 
                vehicleId
            );

            // Handle case where vehicle doesn't exist
            if (currentValue == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Vehicle not found");
            }

            // Increment the counter
            int newValue = currentValue + 1;
            
            // Update the database
            int rowsAffected = jdbcTemplate.update(
                "UPDATE registration SET call_tracking = ? WHERE id = ?",
                newValue, 
                vehicleId
            );

            // Check if update was successful
            if (rowsAffected > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Trust counter updated successfully");
                response.put("vehicleId", vehicleId);
                response.put("newValue", newValue);
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Vehicle not found or update failed");
            }
        } catch (Exception e) {
            log.warn("Error updating trust counter", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error updating trust counter");
        }
    }
} 