package com.example.springcloudstream.domain.user.consumer;

import com.example.springcloudstream.domain.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * User Consumer Service
 * 
 * This service demonstrates various patterns for consuming messages with Spring Cloud Stream:
 * - Basic message consumption using functional interfaces
 * - Message header processing
 * - Error handling and acknowledgment
 * - Content-based routing
 * - Message transformation
 * 
 * Spring Cloud Stream 3.x uses functional programming model with java.util.function interfaces:
 * - Consumer<T>: For message consumption (sink)
 * - Function<T,R>: For message transformation (processor)
 * - Supplier<T>: For message production (source)
 * 
 * @Slf4j - Lombok annotation for logging
 * @Service - Spring stereotype annotation for service layer components
 */
@Slf4j
@Service
public class UserConsumerService {

    private final AtomicInteger processedCount = new AtomicInteger(0);

    /**
     * Basic user message consumer
     * Demonstrates simple message consumption pattern
     * 
     * Bean name 'userConsumer' maps to binding name 'userConsumer-in-0'
     * Spring Cloud Stream automatically binds this function to message channels
     * 
     * @return Consumer function for processing User messages
     */
    @Bean
    public Consumer<User> userConsumer() {
        return user -> {
            try {
                // Validate input
                if (user == null) {
                    log.error("❌ Received null user message");
                    throw new IllegalArgumentException("User message cannot be null");
                }
                
                log.info("🔵 Processing user message: ID={}, Name={}, Department={}", 
                        user.getId(), user.getName(), user.getDepartment());
                
                // Simulate business logic processing
                processUser(user);
                
                int count = processedCount.incrementAndGet();
                log.info("✅ Successfully processed user message. Total processed: {}", count);
                
            } catch (Exception e) {
                log.error("❌ Failed to process user message: {}", user, e);
                // In production, you might want to send to DLQ or retry
                throw new RuntimeException("User processing failed", e);
            }
        };
    }

    /**
     * User message consumer with header processing
     * Demonstrates accessing message headers and metadata
     * 
     * @return Consumer function for processing User messages with headers
     */
    @Bean
    public Consumer<Message<User>> userWithHeadersConsumer() {
        return message -> {
            try {
                User user = message.getPayload();
                var headers = message.getHeaders();
                
                String eventType = (String) headers.get("event-type");
                String correlationId = (String) headers.get("correlation-id");
                String sourceService = (String) headers.get("source-service");
                Long timestamp = (Long) headers.get("timestamp");
                
                log.info("🔵 Processing user message with headers:");
                log.info("   User: ID={}, Name={}", user.getId(), user.getName());
                log.info("   Event Type: {}", eventType);
                log.info("   Correlation ID: {}", correlationId);
                log.info("   Source Service: {}", sourceService);
                log.info("   Timestamp: {}", timestamp);
                
                // Process based on event type
                switch (eventType != null ? eventType : "unknown") {
                    case "created":
                        handleUserCreated(user, correlationId);
                        break;
                    case "updated":
                        handleUserUpdated(user, correlationId);
                        break;
                    case "deleted":
                        handleUserDeleted(user, correlationId);
                        break;
                    default:
                        log.warn("⚠️ Unknown event type: {}", eventType);
                        handleGenericUserEvent(user);
                        break;
                }
                
                log.info("✅ Successfully processed user message with headers");
                
            } catch (Exception e) {
                log.error("❌ Failed to process user message with headers", e);
                throw new RuntimeException("User header processing failed", e);
            }
        };
    }

    /**
     * Departmental user consumer
     * Demonstrates content-based routing and filtering
     * 
     * @return Consumer function for processing department-specific User messages
     */
    @Bean
    public Consumer<Message<User>> departmentalUserConsumer() {
        return message -> {
            try {
                User user = message.getPayload();
                String department = user.getDepartment();
                
                log.info("🏢 Processing departmental user message: User={}, Department={}", 
                        user.getName(), department);
                
                // Route based on department
                switch (department != null ? department.toUpperCase() : "UNKNOWN") {
                    case "IT":
                        processItDepartmentUser(user);
                        break;
                    case "HR":
                        processHrDepartmentUser(user);
                        break;
                    case "FINANCE":
                        processFinanceDepartmentUser(user);
                        break;
                    case "SALES":
                        processSalesDepartmentUser(user);
                        break;
                    default:
                        processGenericDepartmentUser(user, department);
                        break;
                }
                
                log.info("✅ Successfully processed departmental user message");
                
            } catch (Exception e) {
                log.error("❌ Failed to process departmental user message", e);
                throw new RuntimeException("Departmental user processing failed", e);
            }
        };
    }

    /**
     * User message transformer
     * Demonstrates message transformation pattern (processor)
     * 
     * @return Function for transforming User messages
     */
    @Bean
    public Function<User, User> userTransformer() {
        return user -> {
            try {
                // Validate input
                if (user == null) {
                    log.error("❌ Received null user for transformation");
                    throw new IllegalArgumentException("User cannot be null for transformation");
                }
                
                // Validate required fields
                if (user.getName() == null || user.getName().trim().isEmpty()) {
                    log.error("❌ User name is null or empty for transformation");
                    throw new IllegalArgumentException("User name cannot be null or empty");
                }
                if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                    log.error("❌ User email is null or empty for transformation");
                    throw new IllegalArgumentException("User email cannot be null or empty");
                }
                
                log.info("🔄 Transforming user message: {}", user.getName());
                
                // Create transformed user with normalized data
                User transformedUser = new User();
                transformedUser.setId(user.getId());
                transformedUser.setName(user.getName().trim().toUpperCase());
                transformedUser.setEmail(user.getEmail().toLowerCase().trim());
                transformedUser.setAge(user.getAge());
                transformedUser.setCreatedAt(user.getCreatedAt());
                transformedUser.setDepartment(user.getDepartment() != null ? user.getDepartment().toUpperCase() : "UNKNOWN");
                transformedUser.setStatus(user.getStatus());
                
                log.info("✅ Successfully transformed user message: {} -> {}", 
                        user.getName(), transformedUser.getName());
                
                return transformedUser;
                
            } catch (Exception e) {
                log.error("❌ Failed to transform user message: {}", user, e);
                throw new RuntimeException("User transformation failed", e);
            }
        };
    }

    /**
     * Simulate user processing business logic
     */
    private void processUser(User user) {
        // Simulate processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.debug("Processing user business logic for: {}", user.getName());
        // Add your business logic here
    }

    /**
     * Handle user created event
     */
    private void handleUserCreated(User user, String correlationId) {
        log.info("📝 Handling user created event: {} (Correlation: {})", user.getName(), correlationId);
        // Implementation for user creation handling
    }

    /**
     * Handle user updated event
     */
    private void handleUserUpdated(User user, String correlationId) {
        log.info("✏️ Handling user updated event: {} (Correlation: {})", user.getName(), correlationId);
        // Implementation for user update handling
    }

    /**
     * Handle user deleted event
     */
    private void handleUserDeleted(User user, String correlationId) {
        log.info("🗑️ Handling user deleted event: {} (Correlation: {})", user.getName(), correlationId);
        // Implementation for user deletion handling
    }

    /**
     * Handle generic user event
     */
    private void handleGenericUserEvent(User user) {
        log.info("📋 Handling generic user event: {}", user.getName());
        // Implementation for generic event handling
    }

    /**
     * Process IT department user
     */
    private void processItDepartmentUser(User user) {
        log.info("💻 Processing IT department user: {}", user.getName());
        // IT-specific processing logic
    }

    /**
     * Process HR department user
     */
    private void processHrDepartmentUser(User user) {
        log.info("👥 Processing HR department user: {}", user.getName());
        // HR-specific processing logic
    }

    /**
     * Process Finance department user
     */
    private void processFinanceDepartmentUser(User user) {
        log.info("💰 Processing Finance department user: {}", user.getName());
        // Finance-specific processing logic
    }

    /**
     * Process Sales department user
     */
    private void processSalesDepartmentUser(User user) {
        log.info("📊 Processing Sales department user: {}", user.getName());
        // Sales-specific processing logic
    }

    /**
     * Process generic department user
     */
    private void processGenericDepartmentUser(User user, String department) {
        log.info("🏢 Processing {} department user: {}", department, user.getName());
        // Generic department processing logic
    }

    /**
     * Get processed message count for monitoring
     */
    public int getProcessedCount() {
        return processedCount.get();
    }

    /**
     * Reset processed message count
     */
    public void resetProcessedCount() {
        processedCount.set(0);
        log.info("🔄 Reset processed message count");
    }
} 