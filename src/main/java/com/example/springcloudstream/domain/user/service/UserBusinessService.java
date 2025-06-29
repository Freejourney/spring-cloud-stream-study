package com.example.springcloudstream.domain.user.service;

import com.example.springcloudstream.domain.user.model.User;
import com.example.springcloudstream.domain.user.model.UserEvent;
import com.example.springcloudstream.domain.user.producer.UserProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User Business Service
 * 
 * Encapsulates business logic and rules for user domain operations.
 * This service provides:
 * - User lifecycle management (create, update, activate, deactivate)
 * - Business rule validation
 * - Domain event orchestration
 * - User state management
 * - Department transfer workflows
 * 
 * This service coordinates between the domain model and message producers,
 * ensuring business rules are enforced and appropriate events are published.
 * 
 * @Service - Spring stereotype annotation
 * @Slf4j - Lombok annotation for logging
 */
@Slf4j
@Service
public class UserBusinessService {
    
    private final UserProducerService userProducerService;
    private final Map<String, User> userRepository = new ConcurrentHashMap<>();
    
    public UserBusinessService(UserProducerService userProducerService) {
        this.userProducerService = userProducerService;
    }
    
    /**
     * Create a new user with business validation
     * 
     * @param userData User data for creation
     * @return Created user with generated ID
     */
    public User createUser(User userData) {
        log.info("🆕 Creating new user: {}", userData.getName());
        
        // Business validation
        validateUserForCreation(userData);
        
        // Generate user ID and set creation timestamp
        String userId = generateUserId();
        User user = User.builder()
                .id(userId)
                .name(userData.getName())
                .email(userData.getEmail())
                .age(userData.getAge())
                .department(userData.getDepartment())
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Store user
        userRepository.put(userId, user);
        
        // Publish user created event
        boolean eventSent = userProducerService.sendUserWithHeaders(
            user, 
            "user-created", 
            UUID.randomUUID().toString()
        );
        
        if (eventSent) {
            log.info("✅ User created successfully: {} (ID: {})", user.getName(), userId);
        } else {
            log.warn("⚠️ User created but event publishing failed: {}", userId);
        }
        
        return user;
    }
    
    /**
     * Update an existing user
     * 
     * @param userId User ID to update
     * @param updateData Updated user data
     * @return Updated user
     */
    public User updateUser(String userId, User updateData) {
        log.info("🔄 Updating user: {}", userId);
        
        User existingUser = userRepository.get(userId);
        if (existingUser == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        
        // Validate update
        validateUserForUpdate(updateData);
        
        // Store original user for event
        User originalUser = User.builder()
                .id(existingUser.getId())
                .name(existingUser.getName())
                .email(existingUser.getEmail())
                .age(existingUser.getAge())
                .department(existingUser.getDepartment())
                .status(existingUser.getStatus())
                .createdAt(existingUser.getCreatedAt())
                .build();
        
        // Update user
        User updatedUser = User.builder()
                .id(existingUser.getId())
                .name(updateData.getName() != null ? updateData.getName() : existingUser.getName())
                .email(updateData.getEmail() != null ? updateData.getEmail() : existingUser.getEmail())
                .age(updateData.getAge() != null ? updateData.getAge() : existingUser.getAge())
                .department(updateData.getDepartment() != null ? updateData.getDepartment() : existingUser.getDepartment())
                .status(existingUser.getStatus())
                .createdAt(existingUser.getCreatedAt())
                .build();
        
        userRepository.put(userId, updatedUser);
        
        // Check if department changed for special event
        if (!originalUser.getDepartment().equals(updatedUser.getDepartment())) {
            handleDepartmentChange(originalUser, updatedUser);
        }
        
        // Publish user updated event
        userProducerService.sendUserUpdateEvent(updatedUser, originalUser);
        
        log.info("✅ User updated successfully: {}", userId);
        
        return updatedUser;
    }
    
    /**
     * Transfer user to a different department
     * 
     * @param userId User ID
     * @param newDepartment New department
     * @param reason Transfer reason
     * @return Updated user
     */
    public User transferUserDepartment(String userId, String newDepartment, String reason) {
        log.info("🏢 Transferring user {} to department: {}", userId, newDepartment);
        
        User user = userRepository.get(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        
        String oldDepartment = user.getDepartment();
        
        // Business validation for department transfer
        validateDepartmentTransfer(user, newDepartment);
        
        // Update user department
        User updatedUser = User.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .age(user.getAge())
                .department(newDepartment)
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
        
        userRepository.put(userId, updatedUser);
        
        // Create and send department change event
        UserEvent departmentChangeEvent = UserEvent.createDepartmentChangedEvent(
            updatedUser, 
            oldDepartment, 
            newDepartment, 
            "user-business-service", 
            UUID.randomUUID().toString()
        );
        departmentChangeEvent.setMetadata("transfer-reason: " + reason);
        
        // Send the event (Note: This would need a method to send UserEvent objects)
        boolean eventSent = userProducerService.sendUserWithHeaders(
            updatedUser,
            "department-changed",
            departmentChangeEvent.getCorrelationId()
        );
        
        if (eventSent) {
            log.info("✅ User department transfer completed: {} {} -> {}", 
                    userId, oldDepartment, newDepartment);
        } else {
            log.warn("⚠️ Department transfer completed but event publishing failed: {}", userId);
        }
        
        return updatedUser;
    }
    
    /**
     * Activate a user account
     * 
     * @param userId User ID
     * @return Activated user
     */
    public User activateUser(String userId) {
        log.info("✅ Activating user: {}", userId);
        
        User user = userRepository.get(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        
        if (user.getStatus() == User.UserStatus.ACTIVE) {
            log.info("ℹ️ User is already active: {}", userId);
            return user;
        }
        
        User activatedUser = User.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .age(user.getAge())
                .department(user.getDepartment())
                .status(User.UserStatus.ACTIVE)
                .createdAt(user.getCreatedAt())
                .build();
        
        userRepository.put(userId, activatedUser);
        
        // Send activation event
        userProducerService.sendUserWithHeaders(
            activatedUser,
            "user-activated",
            UUID.randomUUID().toString()
        );
        
        log.info("✅ User activated successfully: {}", userId);
        
        return activatedUser;
    }
    
    /**
     * Deactivate a user account
     * 
     * @param userId User ID
     * @param reason Deactivation reason
     * @return Deactivated user
     */
    public User deactivateUser(String userId, String reason) {
        log.info("🚫 Deactivating user: {} (Reason: {})", userId, reason);
        
        User user = userRepository.get(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        
        if (user.getStatus() == User.UserStatus.INACTIVE) {
            log.info("ℹ️ User is already inactive: {}", userId);
            return user;
        }
        
        User deactivatedUser = User.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .age(user.getAge())
                .department(user.getDepartment())
                .status(User.UserStatus.INACTIVE)
                .createdAt(user.getCreatedAt())
                .build();
        
        userRepository.put(userId, deactivatedUser);
        
        // Send deactivation event
        userProducerService.sendUserWithHeaders(
            deactivatedUser,
            "user-deactivated",
            UUID.randomUUID().toString()
        );
        
        log.info("✅ User deactivated successfully: {} (Reason: {})", userId, reason);
        
        return deactivatedUser;
    }
    
    /**
     * Get user by ID
     * 
     * @param userId User ID
     * @return User or null if not found
     */
    public User getUserById(String userId) {
        return userRepository.get(userId);
    }
    
    /**
     * Get all users
     * 
     * @return List of all users
     */
    public List<User> getAllUsers() {
        return userRepository.values().stream().collect(Collectors.toList());
    }
    
    /**
     * Get users by department
     * 
     * @param department Department name
     * @return List of users in the department
     */
    public List<User> getUsersByDepartment(String department) {
        return userRepository.values().stream()
                .filter(user -> department.equals(user.getDepartment()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get active users count
     * 
     * @return Count of active users
     */
    public long getActiveUsersCount() {
        return userRepository.values().stream()
                .filter(user -> user.getStatus() == User.UserStatus.ACTIVE)
                .count();
    }
    
    // Private helper methods
    
    private void validateUserForCreation(User user) {
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new UserValidationException("User name is required");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new UserValidationException("User email is required");
        }
        if (user.getAge() == null || user.getAge() < 18 || user.getAge() > 100) {
            throw new UserValidationException("User age must be between 18 and 100");
        }
        if (user.getDepartment() == null || user.getDepartment().trim().isEmpty()) {
            throw new UserValidationException("User department is required");
        }
        
        // Check for duplicate email
        boolean emailExists = userRepository.values().stream()
                .anyMatch(existingUser -> user.getEmail().equals(existingUser.getEmail()));
        if (emailExists) {
            throw new UserValidationException("Email already exists: " + user.getEmail());
        }
    }
    
    private void validateUserForUpdate(User user) {
        if (user.getName() != null && user.getName().trim().isEmpty()) {
            throw new UserValidationException("User name cannot be empty");
        }
        if (user.getEmail() != null && user.getEmail().trim().isEmpty()) {
            throw new UserValidationException("User email cannot be empty");
        }
        if (user.getAge() != null && (user.getAge() < 18 || user.getAge() > 100)) {
            throw new UserValidationException("User age must be between 18 and 100");
        }
        if (user.getDepartment() != null && user.getDepartment().trim().isEmpty()) {
            throw new UserValidationException("User department cannot be empty");
        }
    }
    
    private void validateDepartmentTransfer(User user, String newDepartment) {
        if (newDepartment == null || newDepartment.trim().isEmpty()) {
            throw new UserValidationException("New department is required");
        }
        if (newDepartment.equals(user.getDepartment())) {
            throw new UserValidationException("User is already in department: " + newDepartment);
        }
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new UserValidationException("Cannot transfer inactive user");
        }
    }
    
    private void handleDepartmentChange(User originalUser, User updatedUser) {
        log.info("🏢 Department change detected: {} -> {} for user {}", 
                originalUser.getDepartment(), updatedUser.getDepartment(), updatedUser.getId());
        
        // Additional business logic for department changes
        // For example: notify managers, update access permissions, etc.
    }
    
    private String generateUserId() {
        return "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    // Custom exceptions
    
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class UserValidationException extends RuntimeException {
        public UserValidationException(String message) {
            super(message);
        }
    }
} 