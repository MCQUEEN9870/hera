package com.example.demo.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final class State {
        int failures;
        Instant firstFailureAt;
        Instant lockedUntil;
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    @Value("${app.auth.lockout.max-failures:5}")
    private int maxFailures;

    @Value("${app.auth.lockout.window-seconds:900}")
    private long windowSeconds;

    @Value("${app.auth.lockout.lock-seconds:900}")
    private long lockSeconds;

    public boolean isLocked(String contactNumber) {
        if (contactNumber == null || contactNumber.isBlank()) {
            return false;
        }
        State state = states.get(contactNumber);
        if (state == null || state.lockedUntil == null) {
            return false;
        }
        Instant now = Instant.now();
        if (now.isBefore(state.lockedUntil)) {
            return true;
        }
        // Expired lock
        state.lockedUntil = null;
        state.failures = 0;
        state.firstFailureAt = null;
        return false;
    }

    public long secondsUntilUnlock(String contactNumber) {
        if (contactNumber == null || contactNumber.isBlank()) {
            return 0;
        }
        State state = states.get(contactNumber);
        if (state == null || state.lockedUntil == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), state.lockedUntil).getSeconds();
        return Math.max(0, seconds);
    }

    public void recordFailure(String contactNumber) {
        if (contactNumber == null || contactNumber.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        State state = states.computeIfAbsent(contactNumber, k -> new State());

        if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
            return;
        }

        if (state.firstFailureAt == null
                || Duration.between(state.firstFailureAt, now).getSeconds() > windowSeconds) {
            state.firstFailureAt = now;
            state.failures = 1;
        } else {
            state.failures += 1;
        }

        if (state.failures >= maxFailures) {
            state.lockedUntil = now.plusSeconds(lockSeconds);
            state.failures = 0;
            state.firstFailureAt = null;
        }
    }

    public void reset(String contactNumber) {
        if (contactNumber == null || contactNumber.isBlank()) {
            return;
        }
        states.remove(contactNumber);
    }
}
