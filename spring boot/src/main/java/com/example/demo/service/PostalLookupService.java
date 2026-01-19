package com.example.demo.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Service
public class PostalLookupService {

    public record PostalInfo(String pincode, String district, String state) {}
    
    public record PincodeDetails(String pincode, String postOfficeName, String district, String state) {}

    private static final Logger log = LoggerFactory.getLogger(PostalLookupService.class);

    private static class CacheEntry {
        final PostalInfo info;
        final long expiry;
        CacheEntry(PostalInfo info, long expiry) { this.info = info; this.expiry = expiry; }
    }

    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL_MILLIS = 24L * 60L * 60L * 1000L; // 24 hours

    public PostalLookupService() {
        // Configure short timeouts so the page doesn't hang if API is slow
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    public PostalInfo resolve(String pincode) {
        if (pincode == null) return null;
        String pin = pincode.trim();
        if (pin.isEmpty() || pin.length() < 4) return null;

        CacheEntry ce = cache.get(pin);
        long now = Instant.now().toEpochMilli();
        if (ce != null && ce.expiry > now) {
            return ce.info;
        }

        try {
            String url = "https://api.postalpincode.in/pincode/" + pin;
            @SuppressWarnings("unchecked")
            List<Object> list = restTemplate.getForObject(url, List.class);
            if (list == null || list.isEmpty()) return null;
            Object first = list.get(0);
            if (!(first instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) first;
            String status = str(obj.get("Status"));
            if (!"Success".equalsIgnoreCase(status)) return null;
            @SuppressWarnings("unchecked")
            List<Object> offices = (List<Object>) obj.getOrDefault("PostOffice", new ArrayList<>());
            if (offices.isEmpty()) return null;

            String district = null;
            String state = null;
            for (Object o : offices) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> office = (Map<String, Object>) o;
                String d = clean(str(office.get("District")));
                String s = clean(str(office.get("State")));
                if (d != null && !d.isBlank()) {
                    district = d;
                    state = s;
                    break;
                }
            }
            PostalInfo info = new PostalInfo(pin, district, state);
            cache.put(pin, new CacheEntry(info, now + TTL_MILLIS));
            if (district != null && !district.isBlank()) {
                log.info("PostalLookup: pincode={} -> district={}, state={}", pin, district, state);
            } else {
                log.info("PostalLookup: pincode={} resolved but district missing", pin);
            }
            return info;
        } catch (Exception e) {
            log.info("PostalLookup: error resolving pincode {} -> {}", pin, e.toString());
            return null;
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return t;
        // Title-case light cleanup
        String[] parts = t.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String normalizeStateKey(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        if (t.isEmpty()) return "";
        // Normalize common variations: '&' vs 'and', punctuation, extra spaces
        t = t.replace("&", " and ");
        t = t.replaceAll("[^a-z0-9]+", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }
    
    /**
     * Fetch all pincodes for a given district and state.
     * Uses India Post API's postoffice search endpoint.
     * 
     * @param district The district name
     * @param state The state name
     * @return List of pincode details with post office names
     */
    public List<PincodeDetails> getPincodesByDistrict(String district, String state) {
        if (district == null || district.trim().isEmpty()) {
            return List.of();
        }
        
        try {
            // Use postoffice search by district name
            String url = "https://api.postalpincode.in/postoffice/" + district.trim();
            @SuppressWarnings("unchecked")
            List<Object> list = restTemplate.getForObject(url, List.class);
            
            if (list == null || list.isEmpty()) {
                log.info("PostalLookup: No results for district={}", district);
                return List.of();
            }
            
            Object first = list.get(0);
            if (!(first instanceof Map)) return List.of();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) first;
            String status = str(obj.get("Status"));
            
            if (!"Success".equalsIgnoreCase(status)) {
                log.info("PostalLookup: Failed status for district={}: {}", district, status);
                return List.of();
            }
            
            @SuppressWarnings("unchecked")
            List<Object> offices = (List<Object>) obj.getOrDefault("PostOffice", new ArrayList<>());
            
            if (offices.isEmpty()) {
                log.info("PostalLookup: No post offices found for district={}", district);
                return List.of();
            }
            
            List<PincodeDetails> pincodes = new ArrayList<>();
            final String requestedStateKey = normalizeStateKey(state);
            for (Object o : offices) {
                if (!(o instanceof Map)) continue;
                
                @SuppressWarnings("unchecked")
                Map<String, Object> office = (Map<String, Object>) o;
                
                String pincode = str(office.get("Pincode"));
                String officeName = str(office.get("Name"));
                String officeDistrict = clean(str(office.get("District")));
                String officeState = clean(str(office.get("State")));

                // Filter by state if provided (tolerant compare)
                if (!requestedStateKey.isEmpty()) {
                    String officeStateKey = normalizeStateKey(officeState);
                    boolean match = officeStateKey.equals(requestedStateKey);

                    // Common India Post variant: "Nct Of Delhi" for Delhi
                    if (!match && "delhi".equals(requestedStateKey) && "nct of delhi".equals(officeStateKey)) {
                        match = true;
                    }

                    // Sometimes Ladakh post offices may still be tagged under Jammu and Kashmir
                    if (!match && "ladakh".equals(requestedStateKey) && "jammu and kashmir".equals(officeStateKey)) {
                        match = true;
                    }

                    if (!match) {
                        continue;
                    }
                }
                
                if (pincode != null && !pincode.isBlank()) {
                    pincodes.add(new PincodeDetails(
                        pincode,
                        officeName != null ? officeName : "Post Office",
                        officeDistrict,
                        officeState
                    ));
                }
            }
            
            log.info("PostalLookup: Found {} pincodes for district={}, state={}", 
                     pincodes.size(), district, state);
            return pincodes;
            
        } catch (Exception e) {
            log.error("PostalLookup: Error fetching pincodes for district={}, state={}: {}", 
                      district, state, e.toString());
            return List.of();
        }
    }
}
