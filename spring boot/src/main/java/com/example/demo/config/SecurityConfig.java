package com.example.demo.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.RateLimitingFilter;

@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsProperty;

    private final Environment environment;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(Environment environment, JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitingFilter rateLimitingFilter) {
        this.environment = environment;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        final boolean isProd = isProdProfile();

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Step 7: rate limiting for auth/OTP/payment endpoints (blocks brute-force & OTP spamming)
            .addFilterBefore(rateLimitingFilter, SecurityContextHolderFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Unauthorized\"}");
                })
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; img-src 'self' data: https: blob:; media-src 'self' data: https:; script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://checkout.razorpay.com https://www.google.com https://www.gstatic.com 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; font-src 'self' data: https://fonts.gstatic.com; connect-src 'self' http://localhost:8080 http://localhost:8081 https://herapherigoods.in https://www.herapherigoods.in https://api.herapherigoods.in https://kmwcinmjonqetgircdtr.supabase.co https://api.razorpay.com https://www.google.com https://www.gstatic.com; frame-src 'self' https://checkout.razorpay.com https://www.google.com https://www.gstatic.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .xssProtection(xss -> xss.disable())
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true))
                .contentTypeOptions(contentType -> {})
            )
            .authorizeHttpRequests(auth -> {
                // Preflight
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                if (isProd) {
                    auth
                        .requestMatchers(
                            "/api/debug/**",
                            "/api/force-delete-vehicle/**",
                            "/api/vehicles/force-delete/**",
                            "/api/migration/**",
                            "/api/registration/migrate-urls",
                            "/api/admin/**"
                        ).denyAll();
                }

                // Public pages + auth endpoints
                auth
                    .requestMatchers(
                        "/",
                        "/error",
                        "/auth/**",
                        "/robots.txt",
                        "/sitemap.xml"
                    ).permitAll();

                // Require auth for endpoints that would otherwise match /api/registration/*
                auth
                    .requestMatchers(HttpMethod.GET,
                        "/api/registration/search",
                        "/api/registration/migrate-urls"
                    ).authenticated();

                // Public read-only APIs
                auth
                    .requestMatchers(HttpMethod.GET,
                        "/api/geo/**",
                        "/api/posts/**",
                        "/api/vehicles/search",
                        "/api/vehicles/check",
                        // NOTE: keep registration details fetch public, but not nested endpoints like documents
                        "/api/registration/*",
                        "/api/registration-images/**",
                        "/api/images/**",
                        "/api/get-feedback",
                        "/api/get-all-feedback",
                        "/api/get-user-feedback",
                        "/api/get-user-locations"
                    ).permitAll();

                // Public write APIs (intentionally public)
                auth
                    .requestMatchers(HttpMethod.POST,
                        "/api/contact-submissions",
                        "/api/mail/test",
                        "/api/save-feedback",
                        "/api/save-user-feedback"
                    ).permitAll();

                // Everything else requires a valid JWT
                auth.anyRequest().authenticated();
            });
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final boolean isProd = isProdProfile();
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = List.of();
        if (allowedOriginsProperty != null && !allowedOriginsProperty.isBlank()) {
            origins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        if (isProd) {
            if (origins.isEmpty()) {
                throw new IllegalStateException("CORS allowlist missing in production. Set 'app.cors.allowed-origins' to your frontend domain(s).");
            }
            for (String origin : origins) {
                if ("*".equals(origin) || "null".equalsIgnoreCase(origin)) {
                    throw new IllegalStateException("Unsafe CORS origin configured for production: '" + origin + "'. Remove it from 'app.cors.allowed-origins'.");
                }
            }
            configuration.setAllowedOrigins(origins);
        } else {
            if (!origins.isEmpty()) {
                configuration.setAllowedOrigins(origins);
            } else {
                // Dev-friendly defaults: allow common local origins and file:// (Origin: null)
                configuration.setAllowedOriginPatterns(List.of(
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "http://192.168.*.*:*",
                    "null"
                ));
            }
        }
        configuration.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        // Allow all request headers to avoid preflight failures (dev and prod with explicit origins)
        configuration.setAllowedHeaders(List.of("*"));
        // Keep credentials off for now (current frontend uses token/header/localStorage, not cookies).
        // This also avoids dangerous combinations like Origin: null + credentials.
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
