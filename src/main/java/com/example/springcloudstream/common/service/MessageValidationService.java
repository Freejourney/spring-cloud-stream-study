package com.example.springcloudstream.common.service;

import com.example.springcloudstream.common.model.BaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Message Validation Service
 * 
 * Provides comprehensive validation for Spring Cloud Stream messages.
 * Validates message structure, headers, payload, and business rules.
 * 
 * @author Spring Cloud Stream Study
 * @version 1.0
 * @since 2024-01-01
 */
@Service
public class MessageValidationService {

    private static final Logger logger = LoggerFactory.getLogger(MessageValidationService.class);

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    // ID validation pattern (alphanumeric with optional dashes)
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }

    /**
     * Validate basic message structure
     */
    public ValidationResult validateMessage(Message<?> message) {
        List<String> errors = new ArrayList<>();

        if (message == null) {
            errors.add("Message cannot be null");
            return new ValidationResult(false, errors);
        }

        // Validate payload
        if (message.getPayload() == null) {
            errors.add("Message payload cannot be null");
        }

        // Validate headers
        if (message.getHeaders() == null) {
            errors.add("Message headers cannot be null");
        } else {
            validateRequiredHeaders(message, errors);
        }

        boolean isValid = errors.isEmpty();
        if (isValid) {
            logger.debug("Message validation passed");
        } else {
            logger.warn("Message validation failed: {}", String.join("; ", errors));
        }

        return new ValidationResult(isValid, errors);
    }

    /**
     * Validate BaseEvent structure
     */
    public ValidationResult validateBaseEvent(BaseEvent event) {
        List<String> errors = new ArrayList<>();

        if (event == null) {
            errors.add("Event cannot be null");
            return new ValidationResult(false, errors);
        }

        // Validate event ID
        if (isNullOrEmpty(event.getEventId())) {
            errors.add("Event ID cannot be null or empty");
        } else if (!ID_PATTERN.matcher(event.getEventId()).matches()) {
            errors.add("Event ID contains invalid characters");
        }

        // Validate event type
        if (isNullOrEmpty(event.getEventType())) {
            errors.add("Event type cannot be null or empty");
        }

        // Validate aggregate ID
        if (isNullOrEmpty(event.getAggregateId())) {
            errors.add("Aggregate ID cannot be null or empty");
        }

        // Validate source service
        if (isNullOrEmpty(event.getSourceService())) {
            errors.add("Source service cannot be null or empty");
        }

        // Validate timestamp
        if (event.getEventTimestamp() == null) {
            errors.add("Event timestamp cannot be null");
        }

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, errors);
    }

    /**
     * Validate email format
     */
    public ValidationResult validateEmail(String email) {
        List<String> errors = new ArrayList<>();

        if (isNullOrEmpty(email)) {
            errors.add("Email cannot be null or empty");
        } else if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            errors.add("Invalid email format: " + email);
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate ID format
     */
    public ValidationResult validateId(String id, String fieldName) {
        List<String> errors = new ArrayList<>();

        if (isNullOrEmpty(id)) {
            errors.add(fieldName + " cannot be null or empty");
        } else if (!ID_PATTERN.matcher(id.trim()).matches()) {
            errors.add(fieldName + " contains invalid characters: " + id);
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate user data
     */
    public ValidationResult validateUserData(String userId, String name, String email, Integer age, String department) {
        List<String> errors = new ArrayList<>();

        // Validate user ID
        ValidationResult userIdResult = validateId(userId, "User ID");
        if (!userIdResult.isValid()) {
            errors.addAll(userIdResult.getErrors());
        }

        // Validate name
        if (isNullOrEmpty(name)) {
            errors.add("User name cannot be null or empty");
        } else if (name.trim().length() < 2) {
            errors.add("User name must be at least 2 characters long");
        }

        // Validate email
        ValidationResult emailResult = validateEmail(email);
        if (!emailResult.isValid()) {
            errors.addAll(emailResult.getErrors());
        }

        // Validate age
        if (age != null && (age < 0 || age > 150)) {
            errors.add("Age must be between 0 and 150");
        }

        // Validate department
        if (isNullOrEmpty(department)) {
            errors.add("Department cannot be null or empty");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate order data
     */
    public ValidationResult validateOrderData(String orderId, String userId, java.math.BigDecimal totalAmount) {
        List<String> errors = new ArrayList<>();

        // Validate order ID
        ValidationResult orderIdResult = validateId(orderId, "Order ID");
        if (!orderIdResult.isValid()) {
            errors.addAll(orderIdResult.getErrors());
        }

        // Validate user ID
        ValidationResult userIdResult = validateId(userId, "User ID");
        if (!userIdResult.isValid()) {
            errors.addAll(userIdResult.getErrors());
        }

        // Validate total amount
        if (totalAmount == null) {
            errors.add("Total amount cannot be null");
        } else if (totalAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Total amount must be positive");
        } else if (totalAmount.compareTo(java.math.BigDecimal.valueOf(1000000)) > 0) {
            errors.add("Total amount cannot exceed $1,000,000");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate message size
     */
    public ValidationResult validateMessageSize(String content, int maxSizeBytes) {
        List<String> errors = new ArrayList<>();

        if (content != null) {
            int sizeBytes = content.getBytes().length;
            if (sizeBytes > maxSizeBytes) {
                errors.add(String.format("Message size (%d bytes) exceeds maximum allowed size (%d bytes)", 
                    sizeBytes, maxSizeBytes));
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate message rate limits
     */
    public ValidationResult validateRateLimit(String sourceService, int messagesPerMinute, int maxAllowed) {
        List<String> errors = new ArrayList<>();

        if (messagesPerMinute > maxAllowed) {
            errors.add(String.format("Rate limit exceeded for service %s: %d messages/minute (max: %d)", 
                sourceService, messagesPerMinute, maxAllowed));
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validate JSON payload structure
     */
    public ValidationResult validateJsonStructure(String jsonPayload) {
        List<String> errors = new ArrayList<>();

        if (isNullOrEmpty(jsonPayload)) {
            errors.add("JSON payload cannot be null or empty");
            return new ValidationResult(false, errors);
        }

        try {
            // Basic JSON structure validation
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(jsonPayload);
        } catch (Exception e) {
            errors.add("Invalid JSON structure: " + e.getMessage());
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Batch validation for multiple messages
     */
    public List<ValidationResult> validateMessages(List<Message<?>> messages) {
        List<ValidationResult> results = new ArrayList<>();
        
        if (messages == null) {
            results.add(new ValidationResult(false, List.of("Message list cannot be null")));
            return results;
        }

        for (Message<?> message : messages) {
            results.add(validateMessage(message));
        }

        return results;
    }

    /**
     * Get validation summary
     */
    public String getValidationSummary(List<ValidationResult> results) {
        if (results == null || results.isEmpty()) {
            return "No validation results";
        }

        long validCount = results.stream().mapToLong(r -> r.isValid() ? 1 : 0).sum();
        long invalidCount = results.size() - validCount;

        return String.format("Validation Summary: %d valid, %d invalid out of %d total messages", 
            validCount, invalidCount, results.size());
    }

    // Private helper methods

    private void validateRequiredHeaders(Message<?> message, List<String> errors) {
        // Check for required headers
        if (!message.getHeaders().containsKey("event-type")) {
            errors.add("Missing required header: event-type");
        }

        if (!message.getHeaders().containsKey("source-service")) {
            errors.add("Missing required header: source-service");
        }

        if (!message.getHeaders().containsKey("event-timestamp")) {
            errors.add("Missing required header: event-timestamp");
        }
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Custom validation for specific business rules
     */
    public ValidationResult validateBusinessRules(Object payload, String eventType) {
        List<String> errors = new ArrayList<>();

        // Add custom business validation logic here
        switch (eventType) {
            case "user-created":
                // Additional business rules for user creation
                break;
            case "order-created":
                // Additional business rules for order creation
                break;
            default:
                // Default validation
                break;
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }
} 