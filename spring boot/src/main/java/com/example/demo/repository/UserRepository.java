package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
