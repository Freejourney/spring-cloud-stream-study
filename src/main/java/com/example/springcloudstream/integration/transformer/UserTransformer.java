package com.example.springcloudstream.integration.transformer;

import com.example.springcloudstream.domain.user.model.User;
import com.example.springcloudstream.domain.user.model.UserEvent;
import com.example.springcloudstream.common.model.MessageHeaders;
import com.example.springcloudstream.common.util.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;

/**
 * User Message Transformer
 * 
 * This class demonstrates various message transformation patterns for user domain:
 * - User data enrichment and normalization
 * - Message format conversion
 * - Header manipulation and standardization
 * - Legacy system compatibility transformations
 * - Event transformation patterns
 * 
 * Integration Patterns Demonstrated:
 * - Message Translator Pattern
 * - Content Enricher Pattern
 * - Normalizer Pattern
 * - Anti-Corruption Layer Pattern
 * 
 * @author Spring Cloud Stream Tutorial
 * @version 1.0
 */
@Slf4j
@Component
public class UserTransformer {

    /**
     * User data enrichment transformer
     * Enriches user messages with additional metadata and computed fields
     * 
     * Pattern: Content Enricher
     * Input: User message
     * Output: Enriched User message with additional headers
     */
    @Bean
    public Function<Message<User>, Message<User>> userEnricher() {
        return message -> {
            try {
                User user = message.getPayload();
                log.debug("🔧 Enriching user message: {}", user.getId());

                // Add enrichment headers
                Map<String, Object> enrichedHeaders = new HashMap<>(message.getHeaders());
                enrichedHeaders.put("enrichment-timestamp", System.currentTimeMillis());
                enrichedHeaders.put("enrichment-version", "1.0");
                enrichedHeaders.put("processing-stage", "enriched");
                enrichedHeaders.put("user-domain", user.getDepartment() != null ? user.getDepartment().toLowerCase() : "general");
                enrichedHeaders.put("user-tier", calculateUserTier(user));
                enrichedHeaders.put("transformation-applied", "user-enrichment");

                Message<User> enrichedMessage = MessageBuilder
                    .withPayload(user)
                    .copyHeaders(enrichedHeaders)
                    .build();

                log.debug("✅ User enrichment completed: {} -> tier: {}", 
                    user.getId(), enrichedHeaders.get("user-tier"));
                
                return enrichedMessage;

            } catch (Exception e) {
                log.error("💥 Error enriching user message", e);
                throw new RuntimeException("User enrichment failed", e);
            }
        };
    }

    /**
     * User data normalizer transformer
     * Normalizes user data format and standardizes field values
     * 
     * Pattern: Normalizer Pattern
     * Input: User message (potentially from different sources)
     * Output: Normalized User message with standardized format
     */
    @Bean
    public Function<Message<User>, Message<User>> userNormalizer() {
        return message -> {
            try {
                User user = message.getPayload();
                log.debug("🔄 Normalizing user message: {}", user.getId());

                // Create normalized user
                User normalizedUser = User.builder()
                    .id(normalizeId(user.getId()))
                    .name(normalizeName(user.getName()))
                    .email(normalizeEmail(user.getEmail()))
                    .age(user.getAge())
                    .department(user.getDepartment())
                    .status(user.getStatus())
                    .createdAt(user.getCreatedAt())
                    .build();

                // Add normalization headers
                Map<String, Object> normalizedHeaders = new HashMap<>(message.getHeaders());
                normalizedHeaders.put("normalization-timestamp", System.currentTimeMillis());
                normalizedHeaders.put("normalization-rules-applied", "id,name,email");
                normalizedHeaders.put("processing-stage", "normalized");
                normalizedHeaders.put("original-format", detectOriginalFormat(message));
                normalizedHeaders.put("transformation-applied", "user-normalization");

                Message<User> normalizedMessage = MessageBuilder
                    .withPayload(normalizedUser)
                    .copyHeaders(normalizedHeaders)
                    .build();

                log.debug("✅ User normalization completed: {}", user.getId());
                return normalizedMessage;

            } catch (Exception e) {
                log.error("💥 Error normalizing user message", e);
                throw new RuntimeException("User normalization failed", e);
            }
        };
    }

    /**
     * User to UserEvent transformer
     * Transforms User objects into UserEvent messages for event-driven architecture
     * 
     * Pattern: Message Translator Pattern
     * Input: User message
     * Output: UserEvent message
     */
    @Bean
    public Function<Message<User>, Message<UserEvent>> userToUserEvent() {
        return message -> {
            try {
                User user = message.getPayload();
                String eventType = (String) message.getHeaders().getOrDefault("event-type", "USER_UPDATED");
                
                log.debug("🔄 Transforming user to event: {} -> {}", user.getId(), eventType);

                // Create UserEvent using factory methods
                String correlationId = MessageUtils.generateCorrelationId();
                UserEvent userEvent;
                
                switch (eventType.toUpperCase()) {
                    case "USER_CREATED":
                        userEvent = UserEvent.createUserCreatedEvent(user, "user-transformer", correlationId);
                        break;
                    case "USER_DELETED":
                        userEvent = UserEvent.createUserDeletedEvent(user, "user-transformer", correlationId);
                        break;
                    default:
                        userEvent = UserEvent.createUserUpdatedEvent(user, user, "user-transformer", correlationId);
                        break;
                }

                // Transform headers for event message
                Map<String, Object> eventHeaders = new HashMap<>();
                eventHeaders.put(MessageHeaders.EVENT_TYPE, eventType);
                // EVENT_TIMESTAMP is automatically set by Spring MessageBuilder
                eventHeaders.put(MessageHeaders.SOURCE_SERVICE, "user-transformer");
                eventHeaders.put(MessageHeaders.CORRELATION_ID, correlationId);
                eventHeaders.put(MessageHeaders.USER_ID, user.getId());
                eventHeaders.put("user-department", user.getDepartment());
                eventHeaders.put("transformation-applied", "user-to-event");
                eventHeaders.put("original-message-id", message.getHeaders().get("id"));

                Message<UserEvent> eventMessage = MessageBuilder
                    .withPayload(userEvent)
                    .copyHeaders(eventHeaders)
                    .build();

                log.debug("✅ User to event transformation completed: {} -> {}", 
                    user.getId(), eventType);
                return eventMessage;

            } catch (Exception e) {
                log.error("💥 Error transforming user to event", e);
                throw new RuntimeException("User to event transformation failed", e);
            }
        };
    }

    /**
     * Legacy system compatibility transformer
     * Transforms modern User messages to legacy system format
     * 
     * Pattern: Anti-Corruption Layer
     * Input: User message
     * Output: Legacy format message (Map<String, Object>)
     */
    @Bean
    public Function<Message<User>, Message<Map<String, Object>>> userToLegacyFormat() {
        return message -> {
            try {
                User user = message.getPayload();
                log.debug("🔄 Transforming user to legacy format: {}", user.getId());

                // Create legacy format (flat map structure)
                Map<String, Object> legacyUser = new HashMap<>();
                legacyUser.put("user_id", user.getId());
                legacyUser.put("user_name", user.getName() != null ? user.getName().toUpperCase() : null);
                legacyUser.put("email_address", user.getEmail());
                legacyUser.put("age", user.getAge());
                legacyUser.put("dept_code", mapDepartmentToLegacyCode(user.getDepartment()));
                legacyUser.put("status_code", user.getStatus() == User.UserStatus.ACTIVE ? "A" : "I");
                legacyUser.put("created_date", user.getCreatedAt() != null ? 
                    user.getCreatedAt().toString() : null);
                legacyUser.put("last_modified", LocalDateTime.now().toString());
                legacyUser.put("record_version", "1.0");

                // Legacy system headers
                Map<String, Object> legacyHeaders = new HashMap<>();
                legacyHeaders.put("legacy-format", "legacy-v1");
                legacyHeaders.put("legacy-source", "user-transformer");
                legacyHeaders.put("transformation-timestamp", System.currentTimeMillis());
                legacyHeaders.put("source-correlation-id", 
                    message.getHeaders().get(MessageHeaders.CORRELATION_ID));
                legacyHeaders.put("transformation-applied", "legacy-conversion");

                Message<Map<String, Object>> legacyMessage = MessageBuilder
                    .withPayload(legacyUser)
                    .copyHeaders(legacyHeaders)
                    .build();

                log.debug("✅ Legacy format transformation completed: {}", user.getId());
                return legacyMessage;

            } catch (Exception e) {
                log.error("💥 Error transforming user to legacy format", e);
                throw new RuntimeException("Legacy format transformation failed", e);
            }
        };
    }

    /**
     * User audit trail transformer
     * Adds audit information to user messages for compliance and tracking
     * 
     * Pattern: Message History Pattern
     * Input: User message
     * Output: User message with audit trail
     */
    @Bean
    public Function<Message<User>, Message<User>> userAuditTrail() {
        return message -> {
            try {
                User user = message.getPayload();
                log.debug("📋 Adding audit trail to user message: {}", user.getId());

                // Add audit headers
                Map<String, Object> auditHeaders = new HashMap<>(message.getHeaders());
                auditHeaders.put("audit-timestamp", System.currentTimeMillis());
                auditHeaders.put("audit-user", "system");
                auditHeaders.put("audit-action", "message-processed");
                auditHeaders.put("audit-source", "user-transformer");
                auditHeaders.put("audit-trail-id", MessageUtils.generateMessageId());
                auditHeaders.put("processing-node", getProcessingNodeId());
                auditHeaders.put("transformation-applied", "audit-trail");
                
                // Add message path tracking
                String currentPath = (String) auditHeaders.getOrDefault("message-path", "");
                auditHeaders.put("message-path", currentPath + " -> user-transformer");

                Message<User> auditMessage = MessageBuilder
                    .withPayload(user)
                    .copyHeaders(auditHeaders)
                    .build();

                log.debug("✅ Audit trail added to user message: {}", user.getId());
                return auditMessage;

            } catch (Exception e) {
                log.error("💥 Error adding audit trail to user message", e);
                throw new RuntimeException("Audit trail transformation failed", e);
            }
        };
    }

    // ===================
    // PRIVATE HELPER METHODS
    // ===================

    /**
     * Calculate user tier based on business logic
     */
    private String calculateUserTier(User user) {
        String dept = user.getDepartment() != null ? user.getDepartment().toUpperCase() : "";
        if (dept.contains("EXECUTIVE")) {
            return "VIP";
        } else if (dept.contains("ENGINEERING")) {
            return "PREMIUM";
        } else if (dept.contains("MARKETING") || dept.contains("SALES")) {
            return "STANDARD";
        } else if (dept.contains("SUPPORT")) {
            return "BASIC";
        } else {
            return "STANDARD";
        }
    }

    /**
     * Normalize user ID format
     */
    private String normalizeId(String id) {
        if (id == null) return null;
        // Trim whitespace and convert to lowercase, preserve hyphens and alphanumeric
        return id.trim().toLowerCase().replaceAll("[^a-zA-Z0-9-]", "");
    }

    /**
     * Normalize user name format
     */
    private String normalizeName(String name) {
        if (name == null) return null;
        // Trim whitespace and capitalize first letter of each word
        String[] words = name.trim().replaceAll("\\s+", " ").toLowerCase().split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    /**
     * Normalize email format
     */
    private String normalizeEmail(String email) {
        if (email == null) return null;
        // Convert to lowercase and trim
        return email.trim().toLowerCase();
    }

    /**
     * Detect original message format
     */
    private String detectOriginalFormat(Message<?> message) {
        // Check headers for format indicators
        if (message.getHeaders().containsKey("legacy-format")) {
            return "legacy";
        } else if (message.getHeaders().containsKey("xml-format")) {
            return "xml";
        } else {
            return "json";
        }
    }

    /**
     * Map department string to legacy system codes
     */
    private String mapDepartmentToLegacyCode(String department) {
        if (department == null) return "UNK";
        String dept = department.toUpperCase();
        if (dept.contains("ENGINEERING")) return "ENG";
        if (dept.contains("MARKETING")) return "MKT";
        if (dept.contains("SALES")) return "SLS";
        if (dept.contains("HR")) return "HRM";
        if (dept.contains("FINANCE")) return "FIN";
        if (dept.contains("IT")) return "ITD";
        if (dept.contains("OPERATIONS")) return "OPS";
        if (dept.contains("MANAGEMENT")) return "MGT";
        if (dept.contains("EXECUTIVE")) return "EXC";
        return "GEN"; // General
    }

    /**
     * Get processing node identifier
     */
    private String getProcessingNodeId() {
        return System.getProperty("node.id", "node-" + System.currentTimeMillis() % 1000);
    }
} 