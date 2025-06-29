package com.example.springcloudstream.domain.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * User Model Class
 * 
 * This class represents a User entity that will be used in Spring Cloud Stream messaging.
 * It demonstrates:
 * - JSON serialization/deserialization
 * - Lombok annotations for reducing boilerplate code
 * - Proper data modeling for messaging scenarios
 * 
 * @Data - Lombok annotation that generates getters, setters, toString, equals, and hashCode
 * @NoArgsConstructor - Generates a no-args constructor (required for JSON deserialization)
 * @AllArgsConstructor - Generates a constructor with all fields as parameters
 * @Builder - Lombok annotation that generates a builder pattern
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * User Status Enumeration
     */
    public enum UserStatus {
        ACTIVE("Active"),
        INACTIVE("Inactive"),
        SUSPENDED("Suspended"),
        PENDING("Pending");
        
        private final String displayName;
        
        UserStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Unique identifier for the user
     * Used for message routing and correlation
     */
    private String id;

    /**
     * User's full name
     * Demonstrates string field handling in messages
     */
    private String name;

    /**
     * User's email address
     * Used for notification scenarios
     */
    private String email;

    /**
     * User's age
     * Demonstrates numeric field handling
     */
    private Integer age;

    /**
     * User creation timestamp
     * Demonstrates date/time handling in messages
     * @JsonFormat ensures proper ISO-8601 format in JSON
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * User's department
     * Used for content-based routing examples
     */
    private String department;

    /**
     * User status (ACTIVE, INACTIVE, SUSPENDED)
     * Demonstrates enum handling in messages
     */
    private UserStatus status;

    /**
     * Factory method to create a new User with current timestamp
     * 
     * @param id user ID
     * @param name user name
     * @param email user email
     * @param age user age
     * @param department user department
     * @return new User instance with current timestamp
     */
    public static User createUser(String id, String name, String email, Integer age, String department) {
        return new User(id, name, email, age, LocalDateTime.now(), department, UserStatus.ACTIVE);
    }
} 