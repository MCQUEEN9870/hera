package com.example.demo.controller;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.demo.util.CityUtil;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.model.Registration;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class SitemapController {

    private final RegistrationRepository registrationRepository;

    public SitemapController(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap(HttpServletRequest request) {
        String base = getBaseUrl(request);
        String lastmod = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        List<String> urls = new ArrayList<>();
        // Core pages
        urls.add(url(base + "/", lastmod));
        urls.add(url(base + "/vehicles", lastmod));
        urls.add(url(base + "/register", lastmod));

        // City pages
        for (String city : CityUtil.getAllCities()) {
            String encodedCity = city.replace(' ', '-');
            urls.add(url(base + "/city/" + encodedCity, lastmod));
            urls.add(url(base + "/vehicles/" + encodedCity, lastmod));
            // a few popular types
            urls.add(url(base + "/vehicles/" + encodedCity + "/truck", lastmod));
            urls.add(url(base + "/register/" + encodedCity + "/tata-ace", lastmod));
        }

        // Dynamic vehicle detail pages
        try {
            List<Registration> registrations = registrationRepository.findAll();
            for (Registration reg : registrations) {
                if (reg != null && reg.getId() != null) {
                    String slug = buildSlug(reg);
                    urls.add(url(base + "/vehicles/" + slug, lastmod));
                }
            }
        } catch (Exception e) {
            // Log and ignore to prevent sitemap breaking if DB lookup fails
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" +
               String.join("", urls) +
               "</urlset>";
    }

    private static String buildSlug(Registration reg) {
        String ownerName = reg.getFullName();
        String vehicleType = reg.getVehicleType();
        String city = reg.getCity();
        String state = reg.getState();
        
        String ownerSlug = slugify(ownerName);
        String typeSlug = slugify(vehicleType);
        String citySlug = slugify(city);
        String stateSlug = slugify(state);
        
        StringBuilder locSlug = new StringBuilder();
        if (!citySlug.isEmpty()) locSlug.append(citySlug);
        if (!stateSlug.isEmpty()) {
            if (locSlug.length() > 0) locSlug.append("-");
            locSlug.append(stateSlug);
        }
        
        StringBuilder slug = new StringBuilder();
        slug.append(ownerSlug.isEmpty() ? "owner" : ownerSlug);
        slug.append("-").append(typeSlug.isEmpty() ? "vehicle" : typeSlug);
        if (locSlug.length() > 0) {
            slug.append("-").append(locSlug);
        }
        slug.append("-").append(reg.getId());
        
        return slug.toString();
    }

    private static String slugify(String value) {
        if (value == null || value.isBlank()) return "";
        String s = value.toLowerCase(java.util.Locale.ROOT);
        s = s.replace("&", " and ");
        s = s.replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("-+", "-");
        s = s.replaceAll("^-|-$", "");
        return s;
    }

    private static String url(String loc, String lastmod) {
        return "<url><loc>" + escape(loc) + "</loc><lastmod>" + lastmod + "</lastmod></url>";
    }

    private static String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        if (host == null || host.isBlank()) host = request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "");
        return scheme + "://" + host;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}


