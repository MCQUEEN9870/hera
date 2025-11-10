package com.example.demo.controller;

import com.example.demo.service.GeoIpService;
import com.example.demo.service.PostalLookupService;
import com.example.demo.service.PostalLookupService.PostalInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Geo bootstrap endpoint called AFTER user lands on site.
 * Flow: get client IP -> ipwho (city+postal) -> postal API (district/state).
 * Returns lightweight JSON for front-end form auto-fill.
 */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final GeoIpService geoIpService;
    private final PostalLookupService postalLookupService;
    private static final Logger log = LoggerFactory.getLogger(GeoController.class);

    public GeoController(GeoIpService geoIpService, PostalLookupService postalLookupService) {
        this.geoIpService = geoIpService;
        this.postalLookupService = postalLookupService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<Map<String,Object>> bootstrap(HttpServletRequest request) {
        Map<String,Object> payload = new HashMap<>();
        try {
            String ip = clientIp(request);
            var geo = geoIpService.resolve(ip);
            PostalInfo postalInfo = null;
            if (geo.postal() != null && !geo.postal().isBlank()) {
                postalInfo = postalLookupService.resolve(geo.postal());
            }
            payload.put("ip", ip);
            payload.put("ipCity", geo.city());
            payload.put("postal", geo.postal());
            payload.put("country", geo.country());
            payload.put("provider", geo.provider());
            payload.put("geoError", geo.error());
            payload.put("district", postalInfo != null ? postalInfo.district() : null);
            payload.put("state", postalInfo != null ? postalInfo.state() : null);
            // Front-end convenience: bestCity picks district first then ipCity
            String bestCity = postalInfo != null && postalInfo.district() != null && !postalInfo.district().isBlank()
                    ? postalInfo.district() : geo.city();
            payload.put("bestCity", bestCity);
            log.info("Geo bootstrap: ip={} ipCity={} postal={} district={} state={} provider={} error={}",
                    ip, geo.city(), geo.postal(),
                    postalInfo != null ? postalInfo.district() : null,
                    postalInfo != null ? postalInfo.state() : null,
                    geo.provider(), geo.error());
            return ResponseEntity.ok(payload);
        } catch (Exception ex) {
            payload.put("error", "bootstrap-failed");
            payload.put("message", ex.getMessage());
            payload.put("stackHint", ex.getClass().getSimpleName());
            log.warn("Geo bootstrap failed: {}", ex.toString());
            return ResponseEntity.ok(payload); // return 200 with error payload to avoid white-label page
        }
    }

    private String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }

    @GetMapping("/echo")
    public ResponseEntity<Map<String,Object>> echo(HttpServletRequest request) {
        Map<String,Object> out = new HashMap<>();
        out.put("remoteAddr", request.getRemoteAddr());
        out.put("CF-Connecting-IP", request.getHeader("CF-Connecting-IP"));
        out.put("X-Forwarded-For", request.getHeader("X-Forwarded-For"));
        out.put("X-Real-IP", request.getHeader("X-Real-IP"));
        out.put("User-Agent", request.getHeader("User-Agent"));
        return ResponseEntity.ok(out);
    }
}
