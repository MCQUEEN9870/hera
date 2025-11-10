package com.example.demo.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
public class ReverseGeocodeService {

    public record ReverseInfo(String postal, String city, String district, String state) {}

    private static final Logger log = LoggerFactory.getLogger(ReverseGeocodeService.class);
    private final RestTemplate restTemplate;

    private static class CacheEntry { ReverseInfo info; long expiry; CacheEntry(ReverseInfo i, long e){info=i;expiry=e;} }
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL = 12L * 60L * 60L * 1000L; // 12h

    public ReverseGeocodeService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(f);
    }

    public ReverseInfo reverse(double lat, double lon) {
        String key = String.format("%.3f,%.3f", lat, lon);
        long now = Instant.now().toEpochMilli();
        CacheEntry ce = cache.get(key);
        if (ce != null && ce.expiry > now) return ce.info;

        try {
            String url = "https://nominatim.openstreetmap.org/reverse?format=json&addressdetails=1&lat="
                    + URLEncoder.encode(String.valueOf(lat), StandardCharsets.UTF_8)
                    + "&lon="
                    + URLEncoder.encode(String.valueOf(lon), StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.add("User-Agent", "HerapheriGoods/1.0 (contact: support@herapherigoods.in)");
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String,Object>> resp = (ResponseEntity<Map<String,Object>>) (ResponseEntity<?>) restTemplate.exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String,Object> body = resp.getBody();
            if (body == null) return null;
            Object addressObj = body.get("address");
            if (!(addressObj instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String,Object> addr = (Map<String,Object>) addressObj;
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
            ReverseInfo info = new ReverseInfo(postal, clean(city), clean(district), clean(state));
            cache.put(key, new CacheEntry(info, now + TTL));
            log.info("ReverseGeocode: lat={},lon={} -> postal={}, city={}, district={}, state={}", lat, lon, postal, city, district, state);
            return info;
        } catch (Exception e) {
            log.info("ReverseGeocode: error lat={},lon={} -> {}", lat, lon, e.toString());
            return null;
        }
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) { if (v != null && !v.isBlank()) return v; }
        return null;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String clean(String s) { return s == null ? null : s.trim(); }
}
