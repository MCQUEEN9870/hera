package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.ContactSubmission;

public interface ContactSubmissionRepository extends JpaRepository<ContactSubmission, Long> { }


