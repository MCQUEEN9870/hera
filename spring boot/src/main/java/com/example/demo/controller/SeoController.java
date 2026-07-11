package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.service.SeoService;
import com.example.demo.service.SeoService.SeoMeta;
import com.example.demo.service.PostalLookupService;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.model.Registration;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping
public class SeoController {

    private final SeoService seoService;
    private final PostalLookupService postalLookupService;
    private final RegistrationRepository registrationRepository;
    private static final Logger log = LoggerFactory.getLogger(SeoController.class);
    
    @Value("${app.frontendBaseUrl:https://www.herapherigoods.in}")
    private String frontendBaseUrl;

    public SeoController(SeoService seoService, PostalLookupService postalLookupService, RegistrationRepository registrationRepository) {
        this.seoService = seoService;
        this.postalLookupService = postalLookupService;
        this.registrationRepository = registrationRepository;
    }

    @GetMapping("/vehicles/{slug:[a-zA-Z0-9\\-]+-\\d+}")
    public String vehicleDetail(@PathVariable("slug") String slug, HttpServletRequest request, Model model) {
        log.info("Resolving dynamic SEO page for vehicle slug: {}", slug);
        
        // Extract the ID from the end of the slug
        int lastDashIndex = slug.lastIndexOf('-');
        if (lastDashIndex == -1) {
            log.warn("Invalid vehicle slug pattern: {}", slug);
            return "redirect:/vehicles";
        }
        
        String idStr = slug.substring(lastDashIndex + 1);
        Long vehicleId;
        try {
            vehicleId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid vehicle ID in slug: {}", slug);
            return "redirect:/vehicles";
        }
        
        java.util.Optional<Registration> optReg = registrationRepository.findById(vehicleId);
        if (optReg.isEmpty()) {
            log.warn("Vehicle not found with ID: {}", vehicleId);
            return "redirect:/vehicles";
        }
        
        Registration reg = optReg.get();
        
        // Construct dynamic title, description, and canonical URL
        String vehicleType = reg.getVehicleType();
        String ownerName = reg.getFullName();
        String city = reg.getCity();
        String state = reg.getState();
        String pincode = reg.getPincode();
        
        String title = "Book " + vehicleType + " by " + ownerName + " in " + city + ", " + state + " - " + pincode + " | HeraPheriGoods";
        String description = "Book verified " + vehicleType + " from " + ownerName + " in " + city + ", " + state + " (" + pincode + "). Direct owner contact with 0% commission and 100% commission-free transport service. Click to view details.";
        
        // Handle images
        String imagePath = frontendBaseUrl + "/attached_assets/images/default-vehicle.png";
        java.util.List<String> imageUrls = reg.getVehicleImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            String firstImg = imageUrls.get(0);
            if (firstImg != null && !firstImg.isBlank() && !firstImg.endsWith(".hidden_folder") && !firstImg.endsWith(".folder")) {
                if (firstImg.contains("supabase.co") && firstImg.contains("/storage/v1/object/public/vehicle-images/")) {
                    try {
                        String after = firstImg.split("/storage/v1/object/public/vehicle-images/")[1];
                        imagePath = frontendBaseUrl + "/images/vehicles/" + after;
                    } catch (Exception e) {
                        imagePath = firstImg;
                    }
                } else {
                    imagePath = firstImg;
                }
            }
        }
        
        // Structured Data Schema markup (Product type or LocalBusiness)
        String jsonLd = "{\n" +
                "  \"@context\": \"https://schema.org\",\n" +
                "  \"@type\": \"LocalBusiness\",\n" +
                "  \"name\": \"Book " + escapeJson(vehicleType) + " by " + escapeJson(ownerName) + "\",\n" +
                "  \"image\": \"" + escapeJson(imagePath) + "\",\n" +
                "  \"description\": \"" + escapeJson(description) + "\",\n" +
                "  \"address\": {\n" +
                "    \"@type\": \"PostalAddress\",\n" +
                "    \"addressLocality\": \"" + escapeJson(city) + "\",\n" +
                "    \"addressRegion\": \"" + escapeJson(state) + "\",\n" +
                "    \"postalCode\": \"" + escapeJson(pincode) + "\",\n" +
                "    \"addressCountry\": \"IN\"\n" +
                "  },\n" +
                "  \"telephone\": \"" + escapeJson(reg.getContactNumber()) + "\"\n" +
                "}";
        
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("keywords", vehicleType + ", " + city + ", transport, vehicle, herapherigoods");
        model.addAttribute("heading", "Book " + vehicleType + " by " + ownerName);
        model.addAttribute("canonicalUrl", frontendBaseUrl + "/vehicles/" + slug);
        model.addAttribute("jsonLd", jsonLd);
        model.addAttribute("tagline", "Direct Owner Booking • 0% Commission • Verified");
        
        model.addAttribute("vehicle", reg);
        model.addAttribute("vehicleImage", imagePath);
        model.addAttribute("frontendUrl", frontendBaseUrl + "/find-vehicles/" + slug);
        model.addAttribute("frontendBase", frontendBaseUrl);
        
        return "vehicles";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @GetMapping({"/","/index","/index.html"})
    public String home(@RequestParam(name = "city", required = false) String city,
                       @RequestParam(name = "type", required = false) String type,
                       @RequestParam(name = "intent", required = false) String intent,
                       HttpServletRequest request,
                       Model model) {
        return buildAndRender("/", city, type, intent, request, model, "index");
    }

    @GetMapping({"/vehicles","/vehicles.html"})
    public String vehicles(@RequestParam(name = "city", required = false) String city,
                           @RequestParam(name = "type", required = false) String type,
                           @RequestParam(name = "intent", required = false) String intent,
                           HttpServletRequest request,
                           Model model) {
        return buildAndRender("/vehicles", city, type, intent, request, model, "vehicles");
    }

    // Path-based SEO: /vehicles/{city} and /vehicles/{city}/{type}
    @GetMapping({"/vehicles/{city}", "/vehicles/{city}/{type}"})
    public String vehiclesPath(@org.springframework.web.bind.annotation.PathVariable("city") String city,
                               @org.springframework.web.bind.annotation.PathVariable(name = "type", required = false) String type,
                               HttpServletRequest request,
                               Model model) {
        return buildAndRender("/vehicles", city, type, "find", request, model, "vehicles");
    }

    @GetMapping({"/register","/register.html"})
    public String register(@RequestParam(name = "city", required = false) String city,
                           @RequestParam(name = "type", required = false) String type,
                           @RequestParam(name = "intent", required = false) String intent,
                           HttpServletRequest request,
                           Model model) {
        if (intent == null || intent.isBlank()) intent = "register"; // default intent for /register
        return buildAndRender("/register", city, type, intent, request, model, "register");
    }

    // Path-based SEO: /register/{city} and /register/{city}/{type}
    @GetMapping({"/register/{city}", "/register/{city}/{type}"})
    public String registerPath(@org.springframework.web.bind.annotation.PathVariable("city") String city,
                               @org.springframework.web.bind.annotation.PathVariable(name = "type", required = false) String type,
                               HttpServletRequest request,
                               Model model) {
        return buildAndRender("/register", city, type, "register", request, model, "register");
    }

    // City landing: /city/{city} → tuned homepage for city
    @GetMapping("/city/{city}")
    public String cityLanding(@org.springframework.web.bind.annotation.PathVariable("city") String city,
                              HttpServletRequest request,
                              Model model) {
        return buildAndRender("/", city, null, null, request, model, "index");
    }

    private String buildAndRender(String path,
                                  String city,
                                  String type,
                                  String intent,
                                  HttpServletRequest request,
                                  Model model,
                                  String viewName) {
        String baseUrl = getBaseUrl(request);

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) params.put(k, v[0]);
        });

        // Auto-resolve location if city missing: prefer explicit pincode only (removed IP heuristics)
        String finalCity = city;
        String pincode = params.get("pincode");
        if (finalCity == null || finalCity.isBlank()) {
            String derivedCity = null;
            if (pincode != null && !pincode.isBlank()) {
                var info = postalLookupService.resolve(pincode);
                if (info != null && info.district() != null && !info.district().isBlank()) {
                    derivedCity = info.district();
                }
                log.info("SEO build: pincode={} -> district={} state={}", pincode, info != null ? info.district() : null, info != null ? info.state() : null);
            }
            if (derivedCity != null && !derivedCity.isBlank()) {
                finalCity = derivedCity;
            }
            log.info("SEO build: path={} type={} intent={} finalCity={}", path, type, intent, finalCity);
        }

        SeoMeta meta = seoService.buildMeta(path, baseUrl, finalCity, type, intent, params);
        model.addAttribute("title", meta.title());
        model.addAttribute("description", meta.description());
        model.addAttribute("keywords", meta.keywords());
        model.addAttribute("heading", meta.heading());
        model.addAttribute("canonicalUrl", meta.canonicalUrl());
        model.addAttribute("jsonLd", meta.jsonLd());
        model.addAttribute("tagline", meta.tagline());
        model.addAttribute("frontendUrl", buildFrontendUrl(path, request));
        model.addAttribute("frontendBase", frontendBaseUrl);
        // Ensure vehicleImage and vehicle are always present so templates don't throw on th:if / ternary
        model.addAttribute("vehicleImage", null);
        model.addAttribute("vehicle", null);
        return viewName;
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        if (host == null || host.isBlank()) host = request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "");
        return scheme + "://" + host;
    }

    private String buildFrontendUrl(String path, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(frontendBaseUrl);
        if (!path.startsWith("/")) sb.append('/');
        // Map root to index without trailing slash differences
        if ("/".equals(path) || "/index".equals(path)) {
            // leave as base URL for SPA/static hosting
        } else {
            sb.append(path.startsWith("/") ? path : "/" + path);
        }
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            sb.append(sb.indexOf("?") > 0 ? "&" : "?").append(query);
        }
        return sb.toString();
    }

}


