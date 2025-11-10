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
        String ip = clientIp(request);
    var geo = geoIpService.resolve(ip);
        PostalInfo postalInfo = null;
        if (geo.postal() != null && !geo.postal().isBlank()) {
            postalInfo = postalLookupService.resolve(geo.postal());
        }
        Map<String,Object> payload = new HashMap<>();
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
        log.info("Geo bootstrap: ip={} ipCity={} postal={} district={} state={}", ip, geo.city(), geo.postal(), postalInfo != null ? postalInfo.district() : null, postalInfo != null ? postalInfo.state() : null);
        return ResponseEntity.ok(payload);
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
}
