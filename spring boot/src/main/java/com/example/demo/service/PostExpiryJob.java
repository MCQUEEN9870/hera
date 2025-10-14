package com.example.demo.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.Post;
import com.example.demo.repository.PostRepository;

@Component
public class PostExpiryJob {

    private final PostRepository postRepository;
    private final SupabaseService supabaseService;

    public PostExpiryJob(PostRepository postRepository, SupabaseService supabaseService) {
        this.postRepository = postRepository;
        this.supabaseService = supabaseService;
    }

    // every 5 minutes
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void expireAndPurge() {
        OffsetDateTime now = OffsetDateTime.now();
        // mark expired
        List<Post> active = postRepository.findActive(now);
        for (Post p : active) {
            if (p.getExpiresAt() != null && !p.getExpiresAt().isAfter(now)) {
                p.setStatus("expired");
            }
        }
        postRepository.saveAll(active);

        // Immediately purge anything that has reached expiry time (no extra delay)
        // In production, use a bulk delete query for efficiency
        List<Post> all = postRepository.findAll();
        for (Post p : all) {
            if (p.getExpiresAt() != null && !p.getExpiresAt().isAfter(now)) {
                try {
                    if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
                        supabaseService.deletePostImageByUrl(p.getImageUrl());
                    }
                } catch (Exception ignore) {
                    // log only in real app
                }
                postRepository.delete(p);
            }
        }
    }
}


