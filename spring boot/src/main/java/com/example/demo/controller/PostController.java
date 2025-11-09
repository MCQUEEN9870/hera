package com.example.demo.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.PostCreateRequest;
import com.example.demo.dto.PostResponse;
import com.example.demo.service.PostService;
import com.example.demo.service.SupabaseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = {"https://herapherigoods.in", "https://www.herapherigoods.in", "https://api.herapherigoods.in", "http://localhost:8080", "http://localhost:5500", "http://127.0.0.1:5500"})
public class PostController {

    private final PostService postService;
    private final SupabaseService supabaseService;

    public PostController(PostService postService, SupabaseService supabaseService) {
        this.postService = postService;
        this.supabaseService = supabaseService;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody PostCreateRequest req) {
        try {
            return ResponseEntity.ok(postService.create(req));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Server error");
        }
    }

    @GetMapping
    public ResponseEntity<List<PostResponse>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "updatedAfter", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime updatedAfter
    ) {
        return ResponseEntity.ok(postService.list(category, updatedAfter));
    }
// Upload a single post image, returns { url: "..." }
    @PostMapping(path = "/upload-image", consumes = { "multipart/form-data" })
    public ResponseEntity<?> uploadPostImage(@RequestPart("image") MultipartFile image) {
        try {
            String url = supabaseService.uploadPostImage(image);
            return ResponseEntity.ok(java.util.Map.of("url", url));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Image upload failed");
        }
    }
}


