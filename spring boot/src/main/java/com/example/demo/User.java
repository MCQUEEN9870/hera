package com.example.demo;

public class User {
    private String name;
    private String email;
    private String fullName;

    // Constructor
    public User() {}

    // Getter aur Setter methods
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    // ToString method (optional, debugging ke liye useful)
    @Override
    public String toString() {
        return "User{name='" + name + "', email='" + email + "', fullName='" + fullName + "'}";
    }
}
