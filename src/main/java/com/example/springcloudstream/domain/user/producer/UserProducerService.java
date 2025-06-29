package com.example.springcloudstream.domain.user.producer;

import com.example.springcloudstream.domain.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * User Producer Service
 * 
 * This service demonstrates various patterns for producing messages with Spring Cloud Stream:
 * - Basic message sending using StreamBridge
 * - Message headers and metadata
 * - Different content types
 * - Message routing and targeting
 * 
 * StreamBridge is the recommended way to send messages imperatively (not reactively)
 * It provides a bridge between imperative and reactive programming models
 * 
 * @Slf4j - Lombok annotation for logging
 * @Service - Spring stereotype annotation for service layer components
 */
@Slf4j
@Service
public class UserProducerService {

    private final StreamBridge streamBridge;

    /**
     * Constructor injection of StreamBridge
     * StreamBridge allows sending messages to any destination dynamically
     * 
     * @param streamBridge Spring Cloud Stream bridge for sending messages
     */
    public UserProducerService(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Send a simple user message without headers
     * Demonstrates basic message sending
     * 
     * @param user the user object to send
     * @return true if message was sent successfully
     */
    public boolean sendUser(User user) {
        try {
            // Validate input
            if (user == null) {
                log.error("❌ Cannot send null user message");
                return false;
            }
            
            // Simple send - Spring Cloud Stream will handle serialization
            boolean sent = streamBridge.send("user-out-0", user);
            log.info("Sent user message: {} - Success: {}", 
                    user.getName() != null ? user.getName() : "Unknown", sent);
            return sent;
        } catch (Exception e) {
            log.error("Failed to send user message", e);
            return false;
        }
    }

    /**
     * Send user message with custom headers
     * Demonstrates message metadata and routing capabilities
     * 
     * @param user the user object to send
     * @param eventType the type of event (created, updated, deleted)
     * @param correlationId correlation ID for message tracing
     * @return true if message was sent successfully
     */
    public boolean sendUserWithHeaders(User user, String eventType, String correlationId) {
        try {
            // Build message with headers for metadata and routing
            Message<User> message = MessageBuilder
                .withPayload(user)
                .setHeader("event-type", eventType)           // Custom business header
                .setHeader("correlation-id", correlationId)   // Tracing header
                .setHeader("source-service", "user-service")  // Source identification
                .setHeader("event-timestamp", System.currentTimeMillis()) // Event timestamp
                .setHeader("user-department", user.getDepartment()) // Content-based routing
                .build();

            boolean sent = streamBridge.send("user-out-0", message);
            log.info("Sent user message with headers - User: {}, Event: {}, Correlation: {} - Success: {}", 
                    user.getName(), eventType, correlationId, sent);
            return sent;
        } catch (Exception e) {
            log.error("Failed to send user message with headers", e);
            return false;
        }
    }

    /**
     * Send user to specific partition
     * Demonstrates partitioning for scalability and ordering
     * 
     * @param user the user object to send
     * @param partition the target partition number
     * @return true if message was sent successfully
     */
    public boolean sendUserToPartition(User user, int partition) {
        try {
            Message<User> message = MessageBuilder
                .withPayload(user)
                .setHeader("partition", partition)  // Partition selection
                .setHeader("event-type", "user-created")
                .build();

            boolean sent = streamBridge.send("user-partitioned-out-0", message);
            log.info("Sent user to partition {} - User: {} - Success: {}", 
                    partition, user.getName(), sent);
            return sent;
        } catch (Exception e) {
            log.error("Failed to send user to partition {}", partition, e);
            return false;
        }
    }

    /**
     * Send user update event
     * Demonstrates domain-specific messaging patterns
     * 
     * @param user the updated user object
     * @param originalUser the original user before update
     * @return true if message was sent successfully
     */
    public boolean sendUserUpdateEvent(User user, User originalUser) {
        try {
            Message<User> message = MessageBuilder
                .withPayload(user)
                .setHeader("event-type", "user-updated")
                .setHeader("original-department", originalUser.getDepartment())
                .setHeader("new-department", user.getDepartment())
                .setHeader("department-changed", 
                          !originalUser.getDepartment().equals(user.getDepartment()))
                .build();

            boolean sent = streamBridge.send("user-updates-out-0", message);
            log.info("Sent user update event - User: {} - Success: {}", user.getName(), sent);
            return sent;
        } catch (Exception e) {
            log.error("Failed to send user update event", e);
            return false;
        }
    }

    /**
     * Send message to multiple destinations
     * Demonstrates fan-out messaging pattern
     * 
     * @param user the user object to send
     * @return true if all messages were sent successfully
     */
    public boolean sendUserToMultipleDestinations(User user) {
        try {
            boolean success = true;
            
            // Send to audit stream
            success &= streamBridge.send("user-audit-out-0", 
                MessageBuilder.withPayload(user)
                    .setHeader("event-type", "audit")
                    .setHeader("audit-timestamp", System.currentTimeMillis())
                    .build());
            
            // Send to notification stream
            success &= streamBridge.send("user-notifications-out-0", 
                MessageBuilder.withPayload(user)
                    .setHeader("event-type", "notification")
                    .setHeader("notification-type", "user-created")
                    .build());
            
            // Send to analytics stream
            success &= streamBridge.send("user-analytics-out-0", 
                MessageBuilder.withPayload(user)
                    .setHeader("event-type", "analytics")
                    .setHeader("department", user.getDepartment())
                    .build());
            
            log.info("Sent user to multiple destinations - User: {} - Overall Success: {}", 
                    user.getName(), success);
            return success;
        } catch (Exception e) {
            log.error("Failed to send user to multiple destinations", e);
            return false;
        }
    }
} 