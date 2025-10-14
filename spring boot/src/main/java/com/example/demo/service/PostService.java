package com.example.demo.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.PostCreateRequest;
import com.example.demo.dto.PostResponse;
import com.example.demo.model.Post;
import com.example.demo.repository.PostRepository;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "Vehicle Requirement","Transport Offer","Service Promotion","General Query","Local Updates"
    );

    @Transactional
    public PostResponse create(PostCreateRequest req) {
        if (!ALLOWED_CATEGORIES.contains(req.getCategory())) {
            throw new IllegalArgumentException("Invalid category");
        }

        Post p = new Post();
        p.setUserId(req.getUserId());
        p.setContent(req.getContent());
        p.setCategory(req.getCategory());
        p.setBackgroundCss(req.getBackgroundCss());
        p.setFontSize(req.getFontSize() != null ? req.getFontSize() : 18);
        p.setIsLightText(Boolean.TRUE.equals(req.getIsLightText()));
        p.setImageUrl(req.getImageUrl());
        // Let DB defaults apply for created_at/expires_at/status when possible

        p = postRepository.save(p);
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public List<PostResponse> list(String category, OffsetDateTime updatedAfter) {
        List<Post> list;
        if (updatedAfter != null) {
            list = postRepository.findActiveUpdatedAfter(updatedAfter, OffsetDateTime.now());
        } else if (category != null && !category.isBlank() && !category.equalsIgnoreCase("All")) {
            list = postRepository.findActiveByCategory(category, OffsetDateTime.now());
        } else {
            list = postRepository.findActive(OffsetDateTime.now());
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    private PostResponse toDto(Post p) {
        PostResponse r = new PostResponse();
        r.setId(p.getId());
        r.setUserId(p.getUserId());
        r.setContent(p.getContent());
        r.setBackgroundCss(p.getBackgroundCss());
        r.setFontSize(p.getFontSize());
        r.setIsLightText(p.getIsLightText());
        r.setCategory(p.getCategory());
        r.setImageUrl(p.getImageUrl());
        r.setStatus(p.getStatus());
        r.setCreatedAt(p.getCreatedAt());
        r.setExpiresAt(p.getExpiresAt());
        return r;
    }
}


