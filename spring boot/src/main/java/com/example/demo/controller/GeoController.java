package com.example.demo.controller;

import com.example.demo.service.PostalSearchService;
import com.example.demo.service.PostalSearchService.PostalSearchInfo;
import com.example.demo.service.PostalLookupService;
import com.example.demo.service.PostalLookupService.PostalInfo;
import com.example.demo.service.PostalLookupService.PincodeDetails;
import com.example.demo.service.ReverseGeocodeService;
import com.example.demo.service.ReverseGeocodeService.ReverseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simplified geo controller: only GPS reverse geocoding remains.
 */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final PostalLookupService postalLookupService;
    private final ReverseGeocodeService reverseGeocodeService;
    private final PostalSearchService postalSearchService;
    private final JdbcTemplate jdbcTemplate;
    private static final Logger log = LoggerFactory.getLogger(GeoController.class);

    public GeoController(PostalLookupService postalLookupService, ReverseGeocodeService reverseGeocodeService, PostalSearchService postalSearchService, JdbcTemplate jdbcTemplate) {
        this.postalLookupService = postalLookupService;
        this.reverseGeocodeService = reverseGeocodeService;
        this.postalSearchService = postalSearchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static String normalizePin(String pincode) {
        return pincode == null ? "" : pincode.trim();
    }

    private static boolean isValidPin(String pincode) {
        return pincode != null && pincode.matches("\\d{6}");
    }

    private static final Map<String, String> PUDUCHERRY_LOCAL_PIN_TO_DISTRICT = Map.of(
        "605001", "Puducherry",
        "605007", "Puducherry",
        "605110", "Puducherry",
        "605008", "Puducherry",
        "605014", "Puducherry",
        "609602", "Karaikal",
        "673310", "Mahe",
        "533464", "Yanam"
    );

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
     * Resolve a pincode into district + state.
     * Priority order:
     * 1) Local known mapping (Puducherry enclaves)
     * 2) Your own registrations table (most reliable for your userbase)
     * 3) India Post API (authoritative when available)
     * 4) Nominatim postal search (best-effort fallback)
     */
    @GetMapping("/resolve")
    public ResponseEntity<Map<String,Object>> resolve(String pincode) {
        Map<String,Object> out = new HashMap<>();
        String pin = normalizePin(pincode);
        out.put("pincode", pin);

        if (!isValidPin(pin)) {
            out.put("valid", false);
            out.put("message", "Invalid pincode");
            return ResponseEntity.ok(out);
        }

        // 1) Local mapping (Puducherry UT enclaves)
        String localDistrict = PUDUCHERRY_LOCAL_PIN_TO_DISTRICT.get(pin);
        if (localDistrict != null) {
            out.put("district", localDistrict);
            out.put("state", "Puducherry");
            out.put("source", "local");
            out.put("valid", true);
            return ResponseEntity.ok(out);
        }

        // 2) DB fallback: use last known registration location
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT city, state FROM registration WHERE pincode = ? ORDER BY id DESC LIMIT 1",
                pin
            );
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                String city = r.get("city") == null ? null : String.valueOf(r.get("city")).trim();
                String st = r.get("state") == null ? null : String.valueOf(r.get("state")).trim();
                if (city != null && !city.isBlank() && st != null && !st.isBlank()) {
                    out.put("district", city);
                    out.put("city", city);
                    out.put("state", st);
                    out.put("source", "db");
                    out.put("valid", true);
                    return ResponseEntity.ok(out);
                }
            }
        } catch (Exception e) {
            log.debug("Geo resolve: DB lookup failed for pincode={}", pin, e);
        }

        // 3) India Post
        try {
            PostalInfo pinInfo = postalLookupService.resolve(pin);
            if (pinInfo != null) {
                String d = pinInfo.district();
                String s = pinInfo.state();
                if (d != null && !d.isBlank() && s != null && !s.isBlank()) {
                    out.put("district", d);
                    out.put("state", s);
                    out.put("source", "postal");
                    out.put("valid", true);
                    return ResponseEntity.ok(out);
                }
            }
        } catch (Exception e) {
            log.info("Geo resolve: IndiaPost failed for {} -> {}", pin, e.toString());
        }

        // 4) Nominatim fallback
        try {
            PostalSearchInfo info = postalSearchService.searchByPincode(pin);
            if (info != null) {
                String d = info.district();
                String s = info.state();
                if (d != null && !d.isBlank() && s != null && !s.isBlank()) {
                    out.put("district", d);
                    out.put("state", s);
                    if (info.city() != null && !info.city().isBlank()) {
                        out.put("city", info.city());
                    }
                    out.put("source", "nominatim");
                    out.put("valid", true);
                    return ResponseEntity.ok(out);
                }
            }
        } catch (Exception e) {
            log.info("Geo resolve: Nominatim failed for {} -> {}", pin, e.toString());
        }

        out.put("valid", false);
        out.put("message", "Unable to resolve pincode");
        out.put("source", "none");
        return ResponseEntity.ok(out);
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
        
        List<PincodeDetails> pincodes = List.of();
        String source = "postal";

        try {
            pincodes = postalLookupService.getPincodesByDistrict(district, state);
        } catch (Exception e) {
            log.info("Geo pincodes: IndiaPost failed district={}, state={} -> {}", district, state, e.toString());
        }

        // DB fallback if India Post returns nothing or fails
        if (pincodes == null || pincodes.isEmpty()) {
            source = "db";
            try {
                String dist = district.trim();
                String st = state == null ? "" : state.trim();

                StringBuilder sql = new StringBuilder();
                List<Object> params = new ArrayList<>();

                sql.append("SELECT DISTINCT pincode FROM registration WHERE LOWER(TRIM(city)) = LOWER(TRIM(?))");
                params.add(dist);
                if (!st.isBlank()) {
                    sql.append(" AND LOWER(TRIM(state)) = LOWER(TRIM(?))");
                    params.add(st);
                }
                sql.append(" AND pincode IS NOT NULL AND TRIM(pincode) <> ''");
                sql.append(" ORDER BY pincode");
                sql.append(" LIMIT 500");

                List<String> pins = jdbcTemplate.queryForList(sql.toString(), String.class, params.toArray());
                List<PincodeDetails> details = new ArrayList<>();
                if (pins != null) {
                    for (String p : pins) {
                        if (p == null || p.isBlank()) continue;
                        details.add(new PincodeDetails(p.trim(), "", dist, st));
                    }
                }
                pincodes = details;
            } catch (Exception e) {
                log.debug("Geo pincodes: DB fallback failed district={}, state={}", district, state, e);
                pincodes = List.of();
            }
        }

        response.put("district", district);
        response.put("state", state);
        response.put("pincodes", pincodes);
        response.put("count", pincodes == null ? 0 : pincodes.size());
        response.put("source", source);
        return ResponseEntity.ok(response);
    }
}
