package com.example.demo.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.demo.util.CityUtil;
import com.example.demo.util.VehicleUtil;

@Service
public class SeoService {

    public record SeoMeta(
            String title,
            String description,
            String keywords,
            String heading,
            String canonicalUrl,
            String jsonLd
    ) {}

    public SeoMeta buildMeta(String path,
                             String baseUrl,
                             String cityParam,
                             String typeParam,
                             String intentParam,
                             Map<String, String> requestParams) {

        String normalizedCity = normalizeCity(cityParam);
        String normalizedType = VehicleUtil.normalizeType(typeParam);
        String intent = normalizeIntent(intentParam, normalizedType);

        // Build canonical URL with preserved relevant params
        String canonicalUrl = buildCanonicalUrl(baseUrl, path, normalizedCity, normalizedType, intent, requestParams);

        String title;
        String description;
        String heading;

        if ("register".equalsIgnoreCase(intent) || path.startsWith("/register")) {
            String locStr = normalizedCity != null && !normalizedCity.isBlank() ? (" in " + normalizedCity) : "";
            title = "Register Your " + (normalizedType != null && !normalizedType.isBlank() ? normalizedType : "Transport Vehicle") + locStr + " | Herapheri Goods";
            description = "List your " + (normalizedType != null && !normalizedType.isBlank() ? normalizedType : "transport vehicle")
                    + (normalizedCity != null && !normalizedCity.isBlank() ? " in " + normalizedCity : " at your pincode")
                    + ". Free listing on Herapheri Goods. Get direct enquiries from nearby customers.";
            heading = "Register Your " + (normalizedType != null && !normalizedType.isBlank() ? normalizedType : "Vehicle")
                    + (normalizedCity != null && !normalizedCity.isBlank() ? " in " + normalizedCity : "");
        } else if (path.startsWith("/vehicles") || "find".equalsIgnoreCase(intent)) {
            String typeStr = (normalizedType != null && !normalizedType.isBlank()) ? normalizedType : "Transport Vehicles";
            String cityStr = (normalizedCity != null && !normalizedCity.isBlank()) ? (" in " + normalizedCity) : "";
            title = "Find " + typeStr + cityStr + " | Herapheri Goods";
            description = "Find the best " + typeStr.toLowerCase(Locale.ROOT) + (normalizedCity != null && !normalizedCity.isBlank() ? " near you in " + normalizedCity : " near you")
                    + ". Select your pincode and vehicle type to see verified owners with photos. No middlemen.";
            heading = "Find " + typeStr + cityStr;
        } else { // homepage and others
            if ((normalizedType != null && !normalizedType.isBlank()) || (normalizedCity != null && !normalizedCity.isBlank())) {
                String cityStr2 = (normalizedCity != null && !normalizedCity.isBlank()) ? (" in " + normalizedCity) : "";
                title = "Herapheri Goods" + cityStr2 + " | Find " + (normalizedType != null && !normalizedType.isBlank() ? normalizedType : "Transport Vehicles") + cityStr2;
                description = "Search by pincode to find " + (normalizedType != null && !normalizedType.isBlank() ? normalizedType.toLowerCase(Locale.ROOT) : "transport vehicles")
                        + (normalizedCity != null && !normalizedCity.isBlank() ? " near you in " + normalizedCity : " near you")
                        + ". Contact owners directly. No brokers.";
                heading = "Finding " + (normalizedType != null && !normalizedType.isBlank() ? normalizedType : "Transport Vehicles") + cityStr2;
            } else {
                title = "Herapheri Goods | Aapka Apna Transport Network";
                description = "Find nearby transport vehicles at your pincode and register your own vehicle for free. Contact owners directly. No middlemen.";
                heading = "Herapheri Goods";
            }
        }

        String keywords = buildKeywords(normalizedCity, normalizedType);
        String jsonLd = buildJsonLd(path, intent, baseUrl);

        return new SeoMeta(title, description, keywords, heading, canonicalUrl, jsonLd);
    }

    private String normalizeCity(String cityParam) {
        if (cityParam == null || cityParam.isBlank()) return null;
        String candidate = CityUtil.toTitle(cityParam.trim());
        return CityUtil.isKnownCity(candidate) ? candidate : candidate; // Allow any title-cased city; expand list in util
    }

    private String normalizeIntent(String intentParam, String normalizedType) {
        if (intentParam != null && !intentParam.isBlank()) return intentParam.toLowerCase(Locale.ROOT);
        if (normalizedType != null && !normalizedType.isBlank()) return "find";
        return null;
    }

    private String buildCanonicalUrl(String baseUrl, String path, String city, String type, String intent, Map<String, String> allParams) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl);
        if (!path.startsWith("/")) sb.append('/');
        sb.append(path);

        boolean first = true;
        if (city != null && !city.isBlank()) {
            sb.append(first ? "?" : "&"); first = false;
            sb.append("city=").append(encode(city));
        }
        if (type != null && !type.isBlank()) {
            sb.append(first ? "?" : "&"); first = false;
            sb.append("type=").append(encode(type));
        }
        if (intent != null && !intent.isBlank()) {
            sb.append(first ? "?" : "&"); first = false;
            sb.append("intent=").append(encode(intent));
        }
        return sb.toString();
    }

    private String buildKeywords(String city, String type) {
        StringBuilder kw = new StringBuilder();
        if (type != null && !type.isBlank()) kw.append(type).append(", ");
        if (city != null && !city.isBlank()) kw.append(city).append(", ");
        kw.append("transport, vehicle, trucks, register vehicle, herapheri goods");
        return kw.toString();
    }

    private String buildJsonLd(String path, String intent, String baseUrl) {
        boolean isRegister = (intent != null && intent.equalsIgnoreCase("register")) || path.startsWith("/register");

        String website = new StringBuilder()
            .append("{")
            .append("\"@context\":\"https://schema.org\",")
            .append("\"@type\":\"WebSite\",")
            .append("\"name\":\"Herapheri Goods\",")
            .append("\"url\":\"").append(escapeJson(baseUrl)).append("\",")
            .append("\"potentialAction\":{\"@type\":\"SearchAction\",\"target\":\"")
            .append(escapeJson(baseUrl)).append("/?q={search_term_string}\",\"query-input\":\"required name=search_term_string\"}}")
            .toString();

        String org = new StringBuilder()
            .append("{")
            .append("\"@context\":\"https://schema.org\",")
            .append("\"@type\":\"Organization\",")
            .append("\"name\":\"Herapheri Goods\",")
            .append("\"url\":\"").append(escapeJson(baseUrl)).append("\",")
            .append("\"logo\":\"").append(escapeJson(baseUrl)).append("/attached_assets/images/new-logo.png\",")
            .append("\"sameAs\":[\"https://www.herapherigoods.in\"]}")
            .toString();

        if (!isRegister) {
            return "[" + website + "," + org + "]"; // Site + Organization
        }

        String faq = new StringBuilder()
            .append("{")
            .append("\"@context\":\"https://schema.org\",")
            .append("\"@type\":\"FAQPage\",")
            .append("\"mainEntity\":[")
            .append("{\"@type\":\"Question\",\"name\":\"Apne truck ko kaise register kare?\",\"acceptedAnswer\":{\"@type\":\"Answer\",\"text\":\"Herapheri Goods par aap apna transport vehicle free mein register kar sakte hain. Bas Register page par jaakar form fill karein, 4 photos upload karein, aur submit kar dein.\"}},")
            .append("{\"@type\":\"Question\",\"name\":\"Kya registration free hai?\",\"acceptedAnswer\":{\"@type\":\"Answer\",\"text\":\"Haan, basic listing free hai. Premium plan se aap 5 vehicles tak register kar sakte hain aur zyada visibility pa sakte hain.\"}}]")
            .append("}")
            .toString();

        return "[" + website + "," + org + "," + faq + "]";
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}


