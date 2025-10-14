package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.model.Registration;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    List<Registration> findByContactNumber(String contactNumber);
    List<Registration> findByUserId(Long userId);
    Registration findByVehiclePlateNumber(String vehiclePlateNumber);
    
    // Count vehicles by type and pincode
    @Query("SELECT COUNT(r) FROM Registration r WHERE r.vehicleType = :vehicleType AND r.pincode = :pincode")
    long countByVehicleTypeAndPincode(@Param("vehicleType") String vehicleType, @Param("pincode") String pincode);
    
    // Here we can add custom queries if needed in future
    
    // Find registrations by full name containing the search term (case insensitive)
    List<Registration> findByFullNameContainingIgnoreCase(String fullName);
}
