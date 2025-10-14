package com.example.demo.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@EnableScheduling
public class SessionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Keep in-memory tracking for SSE connections
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> pageToEmitters = new ConcurrentHashMap<>();

    // Ensure schema/bootstrap only once per runtime
    private volatile boolean schemaEnsured = false;

    private void ensureSchema() {
        if (schemaEnsured) return;
        synchronized (this) {
            if (schemaEnsured) return;
            try {
                // Enable pgcrypto to support gen_random_uuid()
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto;");

                // Create table if missing
                jdbcTemplate.execute(
                    "create table if not exists user_sessions (" +
                    "  id uuid primary key default gen_random_uuid()," +
                    "  session_id text not null unique," +
                    "  page text not null default 'posts'," +
                    "  user_agent text," +
                    "  ip_address inet," +
                    "  last_heartbeat timestamptz not null default now()," +
                    "  created_at timestamptz not null default now()," +
                    "  expires_at timestamptz not null default (now() + interval '30 seconds')" +
                    ");"
                );

                // Indexes (idempotent)
                jdbcTemplate.execute("create index if not exists idx_user_sessions_page on user_sessions (page);");
                jdbcTemplate.execute("create index if not exists idx_user_sessions_last_heartbeat on user_sessions (last_heartbeat);");
                jdbcTemplate.execute("create index if not exists idx_user_sessions_expires_at on user_sessions (expires_at);");
                jdbcTemplate.execute("create index if not exists idx_user_sessions_session_id on user_sessions (session_id);");

                // Functions (idempotent)
                jdbcTemplate.execute(
                    "create or replace function cleanup_expired_sessions()" +
                    " returns void as $$" +
                    " begin" +
                    "   delete from user_sessions where expires_at < now();" +
                    " end;" +
                    " $$ language plpgsql;"
                );

                jdbcTemplate.execute(
                    "create or replace function get_live_user_count(page_name text default 'posts')" +
                    " returns integer as $$" +
                    " declare user_count integer;" +
                    " begin" +
                    "   perform cleanup_expired_sessions();" +
                    "   select count(*) into user_count from user_sessions" +
                    "     where page = page_name" +
                    "     and last_heartbeat > (now() - interval '15 seconds')" +
                    "     and expires_at > now();" +
                    "   return coalesce(user_count, 0);" +
                    " end;" +
                    " $$ language plpgsql;"
                );

                jdbcTemplate.execute(
                    "create or replace function update_session_heartbeat(session_id_param text, page_name text default 'posts')" +
                    " returns void as $$" +
                    " begin" +
                    "   insert into user_sessions (session_id, page, last_heartbeat, expires_at)" +
                    "   values (session_id_param, page_name, now(), now() + interval '30 seconds')" +
                    "   on conflict (session_id) do update set" +
                    "     last_heartbeat = now()," +
                    "     expires_at = now() + interval '30 seconds'," +
                    "     page = page_name;" +
                    " end;" +
                    " $$ language plpgsql;"
                );

                jdbcTemplate.execute(
                    "create or replace function remove_session(session_id_param text)" +
                    " returns void as $$" +
                    " begin" +
                    "   delete from user_sessions where session_id = session_id_param;" +
                    " end;" +
                    " $$ language plpgsql;"
                );

                schemaEnsured = true;
                System.out.println("[presence] Schema ensured successfully.");
            } catch (Exception e) {
                // Do not block app start; log and continue. Subsequent calls will retry.
                System.err.println("[presence] Schema ensure failed: " + e.getMessage());
            }
        }
    }

    /**
     * Add a new SSE connection and track session in database
     */
    public SseEmitter add(String page, String sessionId) {
        final String key = normalize(page);
        final SseEmitter emitter = new SseEmitter(0L);
        
        // Add to in-memory tracking
        pageToEmitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        // Set up cleanup callbacks
        emitter.onCompletion(() -> remove(key, emitter, sessionId));
        emitter.onTimeout(() -> remove(key, emitter, sessionId));
        emitter.onError(e -> remove(key, emitter, sessionId));
        
        // Track session in database
        updateSessionHeartbeat(sessionId, page);
        
        // Send initial count immediately
        try {
            int count = getLiveUserCount(page);
            emitter.send(SseEmitter.event().name("count").data(Map.of("count", count)));
        } catch (Exception ignore) {
            remove(key, emitter, sessionId);
        }
        
        // Broadcast updated count to all
        broadcast(key);
        return emitter;
    }

    /**
     * Get live user count from database
     */
    public int getLiveUserCount(String page) {
        try {
            ensureSchema();
            // First try the function approach
            try {
                String sql = "SELECT get_live_user_count(?)";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, normalize(page));
                System.out.println("Database function query successful. Count for page '" + normalize(page) + "': " + count);
                return count != null ? count : 0;
            } catch (Exception funcError) {
                System.out.println("Function approach failed, trying direct query: " + funcError.getMessage());
                
                // Fallback to direct query
                String sql = "SELECT COUNT(*) FROM user_sessions WHERE page = ? AND last_heartbeat > (NOW() - INTERVAL '15 seconds') AND expires_at > NOW()";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, normalize(page));
                System.out.println("Direct query successful. Count for page '" + normalize(page) + "': " + count);
                return count != null ? count : 0;
            }
        } catch (Exception e) {
            System.err.println("Error getting live user count for page '" + normalize(page) + "': " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Update session heartbeat in database
     */
    public void updateSessionHeartbeat(String sessionId, String page) {
        try {
            ensureSchema();
            // First try the function approach
            try {
                String sql = "SELECT update_session_heartbeat(?, ?)";
                jdbcTemplate.queryForObject(sql, Void.class, sessionId, normalize(page));
                System.out.println("Session heartbeat updated successfully (function) for session: " + sessionId + ", page: " + normalize(page));
            } catch (Exception funcError) {
                System.out.println("Function approach failed, trying direct query: " + funcError.getMessage());
                
                // Fallback to direct query
                String sql = "INSERT INTO user_sessions (session_id, page, last_heartbeat, expires_at) " +
                           "VALUES (?, ?, NOW(), NOW() + INTERVAL '30 seconds') " +
                           "ON CONFLICT (session_id) " +
                           "DO UPDATE SET last_heartbeat = NOW(), expires_at = NOW() + INTERVAL '30 seconds', page = ?";
                jdbcTemplate.update(sql, sessionId, normalize(page), normalize(page));
                System.out.println("Session heartbeat updated successfully (direct) for session: " + sessionId + ", page: " + normalize(page));
            }
        } catch (Exception e) {
            System.err.println("Error updating session heartbeat for session: " + sessionId + ", page: " + normalize(page) + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove session from database
     */
    public void removeSession(String sessionId) {
        try {
            // First try the function approach
            try {
                String sql = "SELECT remove_session(?)";
                jdbcTemplate.queryForObject(sql, Void.class, sessionId);
                System.out.println("Session removed successfully (function): " + sessionId);
            } catch (Exception funcError) {
                System.out.println("Function approach failed, trying direct query: " + funcError.getMessage());
                
                // Fallback to direct query
                String sql = "DELETE FROM user_sessions WHERE session_id = ?";
                int deleted = jdbcTemplate.update(sql, sessionId);
                System.out.println("Session removed successfully (direct): " + sessionId + " (deleted: " + deleted + ")");
            }
        } catch (Exception e) {
            System.err.println("Error removing session: " + sessionId + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Broadcast count to all SSE connections for a page
     */
    public void broadcast(String page) {
        final String key = normalize(page);
        final int c = getLiveUserCount(key);
        final var list = pageToEmitters.getOrDefault(key, new CopyOnWriteArrayList<>());
        
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name("count").data(Map.of("count", c)));
            } catch (Exception e) {
                remove(key, em, null);
            }
        }
    }

    /**
     * Remove SSE connection and session
     */
    private void remove(String key, SseEmitter emitter, String sessionId) {
        // Remove from in-memory tracking
        final var list = pageToEmitters.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                pageToEmitters.remove(key);
            }
        }
        
        // Remove from database if sessionId provided
        if (sessionId != null) {
            removeSession(sessionId);
        }
        
        // Notify remaining listeners of new count
        final var remain = pageToEmitters.get(key);
        if (remain != null && !remain.isEmpty()) {
            final int c = getLiveUserCount(key);
            for (SseEmitter em : remain) {
                try {
                    em.send(SseEmitter.event().name("count").data(Map.of("count", c)));
                } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Clean up expired sessions every 30 seconds
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanupExpiredSessions() {
        try {
            ensureSchema();
            String sql = "SELECT cleanup_expired_sessions()";
            jdbcTemplate.queryForObject(sql, Void.class);
        } catch (Exception e) {
            System.err.println("Error cleaning up expired sessions: " + e.getMessage());
        }
    }

    /**
     * Send keep-alive pings every 25 seconds
     */
    @Scheduled(fixedDelay = 25000)
    public void keepAlive() {
        // Send ping events to keep connections alive behind proxies/load balancers
        pageToEmitters.forEach((key, list) -> {
            for (SseEmitter em : list) {
                try {
                    em.send(SseEmitter.event().name("ping").data("1"));
                } catch (Exception e) {
                    remove(key, em, null);
                }
            }
        });
    }

    /**
     * Normalize page name
     */
    private String normalize(String page) {
        if (page == null || page.isBlank()) return "default";
        return page.trim().toLowerCase();
    }
}
