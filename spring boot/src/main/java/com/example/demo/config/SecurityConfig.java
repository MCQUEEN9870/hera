package com.example.demo.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsProperty;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; img-src 'self' data: https: blob:; media-src 'self' data: https:; script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://checkout.razorpay.com https://www.google.com https://www.gstatic.com 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; font-src 'self' data: https://fonts.gstatic.com; connect-src 'self' http://localhost:8080 http://localhost:8081 https://herapherigoods.in https://www.herapherigoods.in https://api.herapherigoods.in https://kmwcinmjonqetgircdtr.supabase.co https://api.razorpay.com https://www.google.com https://www.gstatic.com; frame-src 'self' https://checkout.razorpay.com https://www.google.com https://www.gstatic.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .xssProtection(xss -> xss.disable())
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true))
                .contentTypeOptions(contentType -> {})
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/error",
                    "/api/vehicles/**",
                    "/api/registration-images/**",
                    "/api/get-feedback",
                    "/api/get-all-feedback",
                    "/api/get-user-feedback",
                    "/api/get-user-locations",
                    "/api/contact-submissions",
                    "/api/presence/**"
                ).permitAll()
                .anyRequest().permitAll() // TODO: tighten later for admin endpoints
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (allowedOriginsProperty != null && !allowedOriginsProperty.isBlank()) {
            configuration.setAllowedOrigins(List.of(allowedOriginsProperty.split(",")));
        } else {
            // Dev-friendly defaults: allow common local origins and file:// (Origin: null)
            configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*.*:*",
                "null"
            ));
        }
        configuration.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        // Allow all request headers to avoid preflight failures (dev and prod with explicit origins)
        configuration.setAllowedHeaders(List.of("*"));
        // Expose common headers (optional)
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
