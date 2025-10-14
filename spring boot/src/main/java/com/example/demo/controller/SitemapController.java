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

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class SitemapController {

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

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" +
               String.join("", urls) +
               "</urlset>";
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


