package com.example.demo.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Extended geo info including provider + error for diagnostics.
     */
    public record GeoInfo(String city, String postal, String country, String provider, String error) {}

    private static final Pattern PRIVATE_V4 = Pattern.compile("^(10\\.|127\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.).*");
    // Simple checks for IPv6 local/link ranges
    private static final Pattern PRIVATE_V6 = Pattern.compile("^(::1|fc[0-9a-f]{2}:|fd[0-9a-f]{2}:|fe80:).*");

    /**
     * Resolve approximate location from client IP via ipwho.org with fallback to ipapi.co.
     * For private/loopback IPs (local dev) returns empty values with provider=local.
     */
    public GeoInfo resolve(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return new GeoInfo(null, null, null, "none", "blank-ip");
        }
        String ipTrim = clientIp.trim();
        if (isPrivate(ipTrim)) {
            // Local dev: try ipapi without IP to use public egress IP as a rough approximation
            try {
                // Use ipapi without IP (derives from public egress) for local dev fallback
                String url = "https://ipapi.co/json/";
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
                if (resp != null) {
                    String city = toStr(resp.get("city"));
                    String postal = toStr(resp.get("postal"));
                    String country = toStr(resp.get("country_name"));
                    if (nonEmpty(city, postal, country)) {
                        log.info("GeoIpService: private ip={} -> ipapi(no-ip) city={} postal={} country={}", ipTrim, city, postal, country);
                        return new GeoInfo(city, postal, country, "ipapi-noip", null);
                    }
                    log.info("GeoIpService: ipapi(no-ip) empty fields for private ip={} rawResp={}", ipTrim, resp);
                }
            } catch (Exception e) {
                log.info("GeoIpService: ipapi(no-ip) error for private ip={} -> {}", ipTrim, e.toString());
                // Continue to return local/private marker so caller can show normal blank form
            }
            return new GeoInfo(null, null, null, "local", "private-ip");
        }

        // First provider: ipwho.org
        try {
            String ipEnc = URLEncoder.encode(ipTrim, StandardCharsets.UTF_8);
            String url = "https://api.ipwho.org/ip/" + ipEnc + "?get=country,city,postal";
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null) {
                String city = toStr(resp.get("city"));
                String postal = toStr(resp.get("postal"));
                String country = toStr(resp.get("country"));
                if (nonEmpty(city, postal, country)) {
                    log.info("GeoIpService: ipwho success ip={} city={} postal={} country={}", ipTrim, city, postal, country);
                    return new GeoInfo(city, postal, country, "ipwho", null);
                }
                log.info("GeoIpService: ipwho empty fields ip={} rawResp={}", ipTrim, resp);
            } else {
                log.info("GeoIpService: ipwho null response ip={}", ipTrim);
            }
        } catch (Exception e) {
            log.info("GeoIpService: ipwho error ip={} -> {}", ipTrim, e.toString());
        }

        // Fallback provider: ipapi.co (< 1000 free requests/day). Avoid query if private IP.
        try {
            String ipEnc = URLEncoder.encode(ipTrim, StandardCharsets.UTF_8);
            String url = "https://ipapi.co/" + ipEnc + "/json/"; // returns many fields
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null) {
                String city = toStr(resp.get("city"));
                String postal = toStr(resp.get("postal")); // may be 'postal'
                String country = toStr(resp.get("country_name")); // ipapi provides country_name
                if (nonEmpty(city, postal, country)) {
                    log.info("GeoIpService: ipapi success ip={} city={} postal={} country={}", ipTrim, city, postal, country);
                    return new GeoInfo(city, postal, country, "ipapi", null);
                }
                log.info("GeoIpService: ipapi empty fields ip={} rawResp={}", ipTrim, resp);
                return new GeoInfo(city, postal, country, "ipapi", "empty-fields");
            } else {
                log.info("GeoIpService: ipapi null response ip={}", ipTrim);
                return new GeoInfo(null, null, null, "ipapi", "null-response");
            }
        } catch (Exception e) {
            log.info("GeoIpService: ipapi error ip={} -> {}", ipTrim, e.toString());
            return new GeoInfo(null, null, null, "ipapi", e.getClass().getSimpleName());
        }
    }

    private static boolean nonEmpty(String a, String b, String c) {
        return (a != null && !a.isBlank()) || (b != null && !b.isBlank()) || (c != null && !c.isBlank());
    }

    private static boolean isPrivate(String ip) {
        if (ip == null) return true;
        // Expanded IPv6 loopback appears as 0:0:0:0:0:0:0:1 on some servlet containers
        if ("0:0:0:0:0:0:0:1".equals(ip)) return true;
        // Simple heuristic: any IPv6 ending in :1 with all preceding zero groups treat as loopback
        if (ip.contains(":") && ip.replace("0", "").replace(":", "").equals("1")) return true;
        return PRIVATE_V4.matcher(ip).matches() || PRIVATE_V6.matcher(ip).matches();
    }

    private static String toStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
