package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.RegistrationImageFolder;

@Repository
public interface RegistrationImageFolderRepository extends JpaRepository<RegistrationImageFolder, Long> {
    List<RegistrationImageFolder> findByRegistrationId(Long registrationId);
    Optional<RegistrationImageFolder> findFirstByRegistrationId(Long registrationId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM RegistrationImageFolder f WHERE f.registrationId = ?1")
    void deleteByRegistrationId(Long registrationId);
} 