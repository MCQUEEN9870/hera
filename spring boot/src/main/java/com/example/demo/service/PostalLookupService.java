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
}
