package com.example.demo.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PostalSearchService {

    public record PostalSearchInfo(String pincode, String city, String district, String state) {}

    private static final Logger log = LoggerFactory.getLogger(PostalSearchService.class);

    private final RestTemplate restTemplate;

    private static class CacheEntry {
        final PostalSearchInfo info;
        final long expiry;
        CacheEntry(PostalSearchInfo info, long expiry) { this.info = info; this.expiry = expiry; }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL_MILLIS = 24L * 60L * 60L * 1000L; // 24 hours

    public PostalSearchService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(f);
    }

    /**
     * Fallback resolver using Nominatim structured search by postal code.
     * Not authoritative, but useful when India Post is down.
     */
    public PostalSearchInfo searchByPincode(String pincode) {
        if (pincode == null) return null;
        String pin = pincode.trim();
        if (!pin.matches("\\d{6}")) return null;

        long now = Instant.now().toEpochMilli();
        CacheEntry ce = cache.get(pin);
        if (ce != null && ce.expiry > now) return ce.info;

        try {
            String url = "https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&countrycodes=in&postalcode="
                + URLEncoder.encode(pin, StandardCharsets.UTF_8)
                + "&limit=1";

            HttpHeaders headers = new HttpHeaders();
            headers.add("User-Agent", "HerapheriGoods/1.0 (contact: support@herapherigoods.in)");

            @SuppressWarnings("unchecked")
            ResponseEntity<List<Object>> resp = (ResponseEntity<List<Object>>) (ResponseEntity<?>) restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
            );

            List<Object> body = resp.getBody();
            if (body == null || body.isEmpty()) return null;
            Object first = body.get(0);
            if (!(first instanceof Map)) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) first;

            Object addressObj = obj.get("address");
            if (!(addressObj instanceof Map)) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> addr = (Map<String, Object>) addressObj;

            String postal = str(addr.get("postcode"));
            String city = firstNonBlank(
                str(addr.get("city")),
                str(addr.get("town")),
                str(addr.get("village")),
                str(addr.get("suburb"))
            );
            String district = firstNonBlank(
                str(addr.get("district")),
                str(addr.get("county")),
                str(addr.get("state_district"))
            );
            String state = str(addr.get("state"));

            PostalSearchInfo info = new PostalSearchInfo(
                postal != null && !postal.isBlank() ? postal : pin,
                clean(city),
                clean(district),
                clean(state)
            );

            cache.put(pin, new CacheEntry(info, now + TTL_MILLIS));
            log.info("PostalSearch: pincode={} -> city={}, district={}, state={}", pin, info.city(), info.district(), info.state());
            return info;
        } catch (Exception e) {
            log.info("PostalSearch: error resolving pincode {} -> {}", pin, e.toString());
            return null;
        }
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? t : t;
    }
}
