package com.example.demo.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "registration")
public class Registration implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "vehicle_type", nullable = false)
    private String vehicleType;

    @Column(name = "contact_number", nullable = false)
    private String contactNumber;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    @Column(name = "alternate_contact_number")
    private String alternateContactNumber;

    @Column(name = "vehicle_plate_number", nullable = false)
    private String vehiclePlateNumber;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "pincode", nullable = false)
    private String pincode;
    
    @Column(name = "registration_date")
    private LocalDate registrationDate;
    
    @Column(name = "membership")
    private String membership = "Standard";
    
    // Service Highlights - up to 5 columns
    @Column(name = "highlight1")
    private String highlight1;
    
    @Column(name = "highlight2")
    private String highlight2;
    
    @Column(name = "highlight3")
    private String highlight3;
    
    @Column(name = "highlight4")
    private String highlight4;
    
    @Column(name = "highlight5")
    private String highlight5;

    // Document URLs for RC and Driving License
    @Column(name = "rc")
    private String rc;
    
    @Column(name = "d_l")
    private String d_l;

    // Change from ElementCollection to directly storing the image URLs as a JSON string
    // This will store URLs in the same table instead of a separate one
    @Column(name = "vehicle_image_urls_json", columnDefinition = "TEXT")
    private String vehicleImageUrlsJson;
    
    // This is a transient field that will not be stored in the database
    // But will be used to convert between JSON string and List<String>
    @Transient
    private List<String> vehicleImageUrls = new ArrayList<>();

    // Default constructor
    public Registration() {
        this.registrationDate = LocalDate.now(); // Set default registration date to today
    }
    
    // Parameterized constructor
    public Registration(String fullName, String vehicleType, String contactNumber, String whatsappNumber,
                        String alternateContactNumber, String vehiclePlateNumber, String state, 
                        String city, String pincode) {
        this.fullName = fullName;
        this.vehicleType = vehicleType;
        this.contactNumber = contactNumber;
        this.whatsappNumber = whatsappNumber;
        this.alternateContactNumber = alternateContactNumber;
        this.vehiclePlateNumber = vehiclePlateNumber;
        this.state = state;
        this.city = city;
        this.pincode = pincode;
        this.registrationDate = LocalDate.now(); // Set default registration date to today
        this.membership = "Standard"; // Default membership is Standard
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getVehicleType() {
        return vehicleType;
    }
    
    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }
    
    public String getContactNumber() {
        return contactNumber;
    }
    
    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }
    
    public String getWhatsappNumber() {
        return whatsappNumber;
    }
    
    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }
    
    public String getAlternateContactNumber() {
        return alternateContactNumber;
    }
    
    public void setAlternateContactNumber(String alternateContactNumber) {
        this.alternateContactNumber = alternateContactNumber;
    }
    
    public String getVehiclePlateNumber() {
        return vehiclePlateNumber;
    }
    
    public void setVehiclePlateNumber(String vehiclePlateNumber) {
        this.vehiclePlateNumber = vehiclePlateNumber;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getPincode() {
        return pincode;
    }
    
    public void setPincode(String pincode) {
        this.pincode = pincode;
    }
    
    public LocalDate getRegistrationDate() {
        return registrationDate;
    }
    
    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }
    
    public String getMembership() {
        return membership;
    }
    
    public void setMembership(String membership) {
        this.membership = membership;
    }
    
    // Getter and setter for RC
    public String getRc() {
        return rc;
    }
    
    public void setRc(String rc) {
        this.rc = rc;
    }
    
    // Getter and setter for Driving License
    public String getD_l() {
        return d_l;
    }
    
    public void setD_l(String d_l) {
        this.d_l = d_l;
    }

    // Modified getter to convert JSON to List
    public List<String> getVehicleImageUrls() {
        if (vehicleImageUrls == null || vehicleImageUrls.isEmpty()) {
            // If transient field is empty but we have JSON data, parse it
            if (vehicleImageUrlsJson != null && !vehicleImageUrlsJson.isEmpty()) {
                try {
                    if ("[null]".equals(vehicleImageUrlsJson) || "[]".equals(vehicleImageUrlsJson)) {
                        // Handle special cases of empty arrays
                        vehicleImageUrls = new ArrayList<>();
                    } else {
                        ObjectMapper objectMapper = new ObjectMapper();
                        vehicleImageUrls = objectMapper.readValue(vehicleImageUrlsJson, 
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                        
                        // Filter out null values and empty strings
                        if (vehicleImageUrls != null) {
                            vehicleImageUrls.removeIf(url -> url == null || url.isEmpty());
                        }
                    }
                } catch (Exception e) {
                    vehicleImageUrls = new ArrayList<>();
                }
            } else {
                vehicleImageUrls = new ArrayList<>();
            }
        }
        return vehicleImageUrls != null ? vehicleImageUrls : new ArrayList<>();
    }
    
    // Modified setter to convert List to JSON
    public void setVehicleImageUrls(List<String> vehicleImageUrls) {
        // Filter out null values and empty strings
        if (vehicleImageUrls != null) {
            vehicleImageUrls.removeIf(url -> url == null || url.isEmpty());
        this.vehicleImageUrls = vehicleImageUrls;
        } else {
            this.vehicleImageUrls = new ArrayList<>();
        }
        
        // Also update the JSON field
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.vehicleImageUrlsJson = objectMapper.writeValueAsString(this.vehicleImageUrls);
        } catch (Exception e) {
            this.vehicleImageUrlsJson = "[]";
        }
    }
    
    // Add direct getter and setter for the JSON field
    public String getVehicleImageUrlsJson() {
        return vehicleImageUrlsJson;
    }
    
    public void setVehicleImageUrlsJson(String vehicleImageUrlsJson) {
        this.vehicleImageUrlsJson = vehicleImageUrlsJson;
        // Also update the transient field
        if (vehicleImageUrlsJson != null && !vehicleImageUrlsJson.isEmpty()) {
            try {
                if ("[null]".equals(vehicleImageUrlsJson) || "[]".equals(vehicleImageUrlsJson)) {
                    // Handle special cases of empty arrays
                    this.vehicleImageUrls = new ArrayList<>();
                } else {
                    ObjectMapper objectMapper = new ObjectMapper();
                    this.vehicleImageUrls = objectMapper.readValue(vehicleImageUrlsJson, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    
                    // Filter out null values and empty strings
                    if (this.vehicleImageUrls != null) {
                        this.vehicleImageUrls.removeIf(url -> url == null || url.isEmpty());
                    }
                }
            } catch (Exception e) {
                this.vehicleImageUrls = new ArrayList<>();
            }
        } else {
            this.vehicleImageUrls = new ArrayList<>();
        }
    }
    
    // Getters and setters for service highlights
    public String getHighlight1() {
        return highlight1;
    }
    
    public void setHighlight1(String highlight1) {
        this.highlight1 = highlight1;
    }
    
    public String getHighlight2() {
        return highlight2;
    }
    
    public void setHighlight2(String highlight2) {
        this.highlight2 = highlight2;
    }
    
    public String getHighlight3() {
        return highlight3;
    }
    
    public void setHighlight3(String highlight3) {
        this.highlight3 = highlight3;
    }
    
    public String getHighlight4() {
        return highlight4;
    }
    
    public void setHighlight4(String highlight4) {
        this.highlight4 = highlight4;
    }
    
    public String getHighlight5() {
        return highlight5;
    }
    
    public void setHighlight5(String highlight5) {
        this.highlight5 = highlight5;
    }
    
    @Override
    public String toString() {
        return "Registration{" +
                "id=" + id +
                ", userId=" + userId +
                ", fullName='" + fullName + '\'' +
                ", vehicleType='" + vehicleType + '\'' +
                ", contactNumber='" + contactNumber + '\'' +
                ", whatsappNumber='" + whatsappNumber + '\'' +
                ", alternateContactNumber='" + alternateContactNumber + '\'' +
                ", vehiclePlateNumber='" + vehiclePlateNumber + '\'' +
                ", state='" + state + '\'' +
                ", city='" + city + '\'' +
                ", pincode='" + pincode + '\'' +
                ", registrationDate='" + registrationDate + '\'' +
                ", membership='" + membership + '\'' +
                ", highlight1='" + highlight1 + '\'' +
                ", highlight2='" + highlight2 + '\'' +
                ", highlight3='" + highlight3 + '\'' +
                ", highlight4='" + highlight4 + '\'' +
                ", highlight5='" + highlight5 + '\'' +
                ", vehicleImageUrls=" + vehicleImageUrls +
                '}';
    }
}



