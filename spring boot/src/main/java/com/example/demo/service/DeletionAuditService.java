package com.example.demo.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.util.ClientIpUtil;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class DeletionAuditService {

    private static final Logger log = LoggerFactory.getLogger(DeletionAuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DeletionAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public Long record(
        String eventType,
        String actorContact,
        String subjectContact,
        Long subjectUserId,
        Long registrationId,
        Map<String, Object> payload,
        HttpServletRequest request
    ) {
        String payloadJson = toJson(payload);

        String actorIp = ClientIpUtil.getClientIp(request);
        String userAgent = request != null ? safeHeader(request, "User-Agent") : null;

        try {
            return jdbcTemplate.queryForObject(
                "INSERT INTO deletion_audit (event_type, actor_contact, actor_ip, actor_user_agent, subject_contact, subject_user_id, registration_id, payload) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id",
                Long.class,
                eventType,
                actorContact,
                actorIp,
                userAgent,
                subjectContact,
                subjectUserId,
                registrationId,
                payloadJson
            );
        } catch (Exception e) {
            // We should never break deletion because audit write failed.
            log.warn("Failed to write deletion_audit record (eventType={}, subjectContact={}, subjectUserId={}, registrationId={})", eventType, subjectContact, subjectUserId, registrationId, e);
            return null;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String safeHeader(HttpServletRequest request, String name) {
        try {
            String v = request.getHeader(name);
            return v != null && !v.isBlank() ? v : null;
        } catch (Exception _e) {
            return null;
        }
    }

}

