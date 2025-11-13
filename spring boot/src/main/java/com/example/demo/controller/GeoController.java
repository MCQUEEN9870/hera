package com.example.demo.controller;

import com.example.demo.service.PostalLookupService;
import com.example.demo.service.PostalLookupService.PostalInfo;
import com.example.demo.service.ReverseGeocodeService;
import com.example.demo.service.ReverseGeocodeService.ReverseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Simplified geo controller: only GPS reverse geocoding remains.
 */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final PostalLookupService postalLookupService;
    private final ReverseGeocodeService reverseGeocodeService;
    private static final Logger log = LoggerFactory.getLogger(GeoController.class);

    public GeoController(PostalLookupService postalLookupService, ReverseGeocodeService reverseGeocodeService) {
        this.postalLookupService = postalLookupService;
        this.reverseGeocodeService = reverseGeocodeService;
    }

    /**
     * Reverse geocode GPS coordinates to postal, district, state using OSM Nominatim.
     */
    @GetMapping("/reverse")
    public ResponseEntity<Map<String,Object>> reverse(double lat, double lon) {
        Map<String,Object> out = new HashMap<>();
        try {
            ReverseInfo info = reverseGeocodeService.reverse(lat, lon);
            if (info != null) {
                out.put("postal", info.postal());
                out.put("pincode", info.postal()); // Add pincode alias for frontend
                out.put("city", info.city());
                out.put("district", info.district());
                out.put("state", info.state());
                // Enrich with authoritative district/state via postal API if postal present
                if (info.postal() != null && !info.postal().isBlank()) {
                    try {
                        PostalInfo pinInfo = postalLookupService.resolve(info.postal());
                        if (pinInfo != null) {
                            if (pinInfo.district() != null && !pinInfo.district().isBlank()) {
                                out.put("district", pinInfo.district());
                            }
                            if (pinInfo.state() != null && !pinInfo.state().isBlank()) {
                                out.put("state", pinInfo.state());
                            }
                        }
                    } catch (Exception e) {
                        log.info("Reverse: postal enrichment failed {}", e.toString());
                    }
                }
            }
            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            out.put("error", ex.getClass().getSimpleName());
            out.put("message", ex.getMessage());
            return ResponseEntity.ok(out);
        }
    }
    
    /**
     * Get all pincodes for a given district and state.
     * Used for populating the pincode dropdown when user selects a district.
     */
    @GetMapping("/pincodes")
    public ResponseEntity<Map<String, Object>> getPincodes(String district, String state) {
        Map<String, Object> response = new HashMap<>();
        
        if (district == null || district.trim().isEmpty()) {
            response.put("error", "District parameter is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            var pincodes = postalLookupService.getPincodesByDistrict(district, state);
            response.put("district", district);
            response.put("state", state);
            response.put("pincodes", pincodes);
            response.put("count", pincodes.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching pincodes for district={}, state={}: {}", district, state, e.toString());
            response.put("error", "Failed to fetch pincodes");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
