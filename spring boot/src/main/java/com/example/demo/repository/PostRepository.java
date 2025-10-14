package com.example.demo.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.model.Post;

public interface PostRepository extends JpaRepository<Post, String> {

    @Query("select p from Post p where p.status = 'active' and p.expiresAt > :now order by p.createdAt desc")
    List<Post> findActive(@Param("now") OffsetDateTime now);

    @Query("select p from Post p where p.status = 'active' and p.expiresAt > :now and p.category = :category order by p.createdAt desc")
    List<Post> findActiveByCategory(@Param("category") String category, @Param("now") OffsetDateTime now);

    @Query("select p from Post p where p.status = 'active' and p.expiresAt > :now and p.createdAt > :after order by p.createdAt desc")
    List<Post> findActiveUpdatedAfter(@Param("after") OffsetDateTime after, @Param("now") OffsetDateTime now);
}


