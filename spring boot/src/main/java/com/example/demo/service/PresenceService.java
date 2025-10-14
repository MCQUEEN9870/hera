package com.example.demo.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// @Service - Disabled in favor of SessionService
public class PresenceService {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> pageToEmitters = new ConcurrentHashMap<>();

    public SseEmitter add(String page) {
        final String key = normalize(page);
        final SseEmitter emitter = new SseEmitter(0L);
        pageToEmitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));
        // Send initial count immediately
        try {
            emitter.send(SseEmitter.event().name("count").data(Map.of("count", count(key))));
        } catch (IOException ignore) {
            remove(key, emitter);
        }
        // Broadcast updated count to all
        broadcast(key);
        return emitter;
    }

    public int count(String page) {
        final String key = normalize(page);
        return pageToEmitters.getOrDefault(key, new CopyOnWriteArrayList<>()).size();
    }

    public void broadcast(String page) {
        final String key = normalize(page);
        final int c = count(key);
        final var list = pageToEmitters.getOrDefault(key, new CopyOnWriteArrayList<>());
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name("count").data(Map.of("count", c)));
            } catch (IOException e) {
                remove(key, em);
            }
        }
    }

    private void remove(String key, SseEmitter emitter) {
        final var list = pageToEmitters.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                pageToEmitters.remove(key);
            }
        }
        // Notify remaining listeners of new count
        final var remain = pageToEmitters.get(key);
        if (remain != null && !remain.isEmpty()) {
            final int c = remain.size();
            for (SseEmitter em : remain) {
                try { em.send(SseEmitter.event().name("count").data(Map.of("count", c))); }
                catch (IOException ignore) {}
            }
        }
    }

    @Scheduled(fixedDelay = 25000)
    public void keepAlive() {
        // Send ping events to keep connections alive behind proxies/load balancers
        pageToEmitters.forEach((key, list) -> {
            for (SseEmitter em : list) {
                try { em.send(SseEmitter.event().name("ping").data("1")); }
                catch (IOException e) { remove(key, em); }
            }
        });
    }

    private String normalize(String page) {
        if (page == null || page.isBlank()) return "default";
        return page.trim().toLowerCase();
    }
}


