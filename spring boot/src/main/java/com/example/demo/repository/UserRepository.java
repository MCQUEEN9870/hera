package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Find a user by contact number
    User findByContactNumber(String contactNumber);
    
    // Check if a user exists by contact number (using Spring Data JPA's naming convention)
    boolean existsByContactNumber(String contactNumber);
    
    // Find users who have left a rating
    List<User> findByRatingIsNotNull();
    
    // Update OTP for a user
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.otp = :otp WHERE u.contactNumber = :contactNumber")
    int updateOtp(@Param("contactNumber") String contactNumber, @Param("otp") String otp);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
