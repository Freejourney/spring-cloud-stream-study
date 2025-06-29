package com.example.springcloudstream.common.util;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Message Utilities
 * 
 * Provides common utility methods for working with Spring Cloud Stream messages.
 * This utility class centralizes message creation, header manipulation,
 * and other common message-related operations.
 * 
 * Common Operations:
 * - Message creation with standard headers
 * - Header extraction and validation
 * - Correlation ID generation
 * - Message metadata handling
 * - Content type management
 */
public final class MessageUtils {
    
    // Private constructor to prevent instantiation
    private MessageUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    // Standard header names
    public static final String CORRELATION_ID_HEADER = "correlation-id";
    public static final String EVENT_TYPE_HEADER = "event-type";
    public static final String SOURCE_SERVICE_HEADER = "source-service";
    public static final String EVENT_TIMESTAMP_HEADER = "event-timestamp";
    public static final String MESSAGE_ID_HEADER = "message-id";
    public static final String RETRY_COUNT_HEADER = "retry-count";
    public static final String PARTITION_KEY_HEADER = "partition-key";
    
    /**
     * Create a message with standard headers
     * 
     * @param payload The message payload
     * @param eventType The type of event
     * @param sourceService The service that created the message
     * @param correlationId Optional correlation ID (generated if null)
     * @return Message with standard headers
     */
    public static <T> Message<T> createMessage(T payload, String eventType, String sourceService, String correlationId) {
        String finalCorrelationId = correlationId != null ? correlationId : generateCorrelationId();
        
        return MessageBuilder.withPayload(payload)
                .setHeader(MESSAGE_ID_HEADER, UUID.randomUUID().toString())
                .setHeader(EVENT_TYPE_HEADER, eventType)
                .setHeader(SOURCE_SERVICE_HEADER, sourceService)
                .setHeader(CORRELATION_ID_HEADER, finalCorrelationId)
                .setHeader(EVENT_TIMESTAMP_HEADER, System.currentTimeMillis())
                .setHeader("timestamp", System.currentTimeMillis()) // Spring Cloud Stream standard
                .build();
    }
    
    /**
     * Create a message with custom headers
     * 
     * @param payload The message payload
     * @param headers Map of custom headers
     * @return Message with custom headers
     */
    public static <T> Message<T> createMessageWithHeaders(T payload, Map<String, Object> headers) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(payload);
        
        // Add custom headers
        headers.forEach(builder::setHeader);
        
        // Ensure standard headers are present
        if (!headers.containsKey(MESSAGE_ID_HEADER)) {
            builder.setHeader(MESSAGE_ID_HEADER, UUID.randomUUID().toString());
        }
        if (!headers.containsKey(EVENT_TIMESTAMP_HEADER)) {
            builder.setHeader(EVENT_TIMESTAMP_HEADER, System.currentTimeMillis());
        }
        if (!headers.containsKey(CORRELATION_ID_HEADER)) {
            builder.setHeader(CORRELATION_ID_HEADER, generateCorrelationId());
        }
        
        return builder.build();
    }
    
    /**
     * Create a partitioned message
     * 
     * @param payload The message payload
     * @param eventType The type of event
     * @param sourceService The service that created the message
     * @param partitionKey The partition key
     * @param correlationId Optional correlation ID
     * @return Partitioned message
     */
    public static <T> Message<T> createPartitionedMessage(T payload, String eventType, String sourceService, 
                                                         String partitionKey, String correlationId) {
        return MessageBuilder.withPayload(payload)
                .setHeader(MESSAGE_ID_HEADER, UUID.randomUUID().toString())
                .setHeader(EVENT_TYPE_HEADER, eventType)
                .setHeader(SOURCE_SERVICE_HEADER, sourceService)
                .setHeader(CORRELATION_ID_HEADER, correlationId != null ? correlationId : generateCorrelationId())
                .setHeader(EVENT_TIMESTAMP_HEADER, System.currentTimeMillis())
                .setHeader(PARTITION_KEY_HEADER, partitionKey)
                .setHeader("partition", partitionKey) // Alternative header name
                .build();
    }
    
    /**
     * Generate a unique correlation ID
     * 
     * @return Generated correlation ID
     */
    public static String generateCorrelationId() {
        return "corr-" + UUID.randomUUID().toString();
    }
    
    /**
     * Generate a unique message ID
     * 
     * @return Generated message ID
     */
    public static String generateMessageId() {
        return "msg-" + UUID.randomUUID().toString();
    }
    
    /**
     * Extract correlation ID from message headers
     * 
     * @param message The message
     * @return Correlation ID or null if not present
     */
    public static String getCorrelationId(Message<?> message) {
        return (String) message.getHeaders().get(CORRELATION_ID_HEADER);
    }
    
    /**
     * Extract event type from message headers
     * 
     * @param message The message
     * @return Event type or null if not present
     */
    public static String getEventType(Message<?> message) {
        return (String) message.getHeaders().get(EVENT_TYPE_HEADER);
    }
    
    /**
     * Extract source service from message headers
     * 
     * @param message The message
     * @return Source service or null if not present
     */
    public static String getSourceService(Message<?> message) {
        return (String) message.getHeaders().get(SOURCE_SERVICE_HEADER);
    }
    
    /**
     * Extract timestamp from message headers
     * 
     * @param message The message
     * @return Timestamp or null if not present
     */
    public static Long getEventTimestamp(Message<?> message) {
        Object timestamp = message.getHeaders().get(EVENT_TIMESTAMP_HEADER);
        if (timestamp instanceof Long) {
            return (Long) timestamp;
        } else if (timestamp instanceof String) {
            try {
                return Long.valueOf((String) timestamp);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Check if message has required headers
     * 
     * @param message The message to validate
     * @return true if all required headers are present
     */
    public static boolean hasRequiredHeaders(Message<?> message) {
        Map<String, Object> headers = message.getHeaders();
        return headers.containsKey(MESSAGE_ID_HEADER) &&
               headers.containsKey(EVENT_TYPE_HEADER) &&
               headers.containsKey(SOURCE_SERVICE_HEADER) &&
               headers.containsKey(CORRELATION_ID_HEADER);
    }
    
    /**
     * Create a retry message with incremented retry count
     * 
     * @param originalMessage The original message
     * @return Message with incremented retry count
     */
    public static <T> Message<T> createRetryMessage(Message<T> originalMessage) {
        Object retryCountObj = originalMessage.getHeaders().get(RETRY_COUNT_HEADER);
        int retryCount = 0;
        
        if (retryCountObj instanceof Integer) {
            retryCount = (Integer) retryCountObj;
        } else if (retryCountObj instanceof String) {
            try {
                retryCount = Integer.parseInt((String) retryCountObj);
            } catch (NumberFormatException e) {
                retryCount = 0;
            }
        }
        
        return MessageBuilder.fromMessage(originalMessage)
                .setHeader(RETRY_COUNT_HEADER, retryCount + 1)
                .setHeader(EVENT_TIMESTAMP_HEADER, System.currentTimeMillis())
                .build();
    }
    
    /**
     * Get retry count from message
     * 
     * @param message The message
     * @return Retry count (0 if not present)
     */
    public static int getRetryCount(Message<?> message) {
        Object retryCountObj = message.getHeaders().get(RETRY_COUNT_HEADER);
        
        if (retryCountObj instanceof Integer) {
            return (Integer) retryCountObj;
        } else if (retryCountObj instanceof String) {
            try {
                return Integer.parseInt((String) retryCountObj);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        
        return 0;
    }

    /**
     * Send message with retry mechanism
     * 
     * @param sendOperation Supplier function that performs the send operation
     * @param maxRetries Maximum number of retry attempts
     * @param operationId Identifier for the operation (for logging)
     * @param logger Logger instance for logging retry attempts
     * @return true if message was sent successfully, false otherwise
     */
    public static boolean sendWithRetry(java.util.function.Supplier<Boolean> sendOperation, 
                                       int maxRetries, String operationId, org.slf4j.Logger logger) {
        int attempt = 0;
        long baseDelay = 1000; // 1 second base delay
        
        while (attempt <= maxRetries) {
            try {
                logger.debug("Attempting to send message for operation: {} (attempt {}/{})", 
                    operationId, attempt + 1, maxRetries + 1);
                
                boolean result = sendOperation.get();
                if (result) {
                    if (attempt > 0) {
                        logger.info("Message sent successfully for operation: {} after {} retries", 
                            operationId, attempt);
                    } else {
                        logger.debug("Message sent successfully for operation: {} on first attempt", operationId);
                    }
                    return true;
                }
                
                logger.warn("Send operation returned false for operation: {} (attempt {}/{})", 
                    operationId, attempt + 1, maxRetries + 1);
                
            } catch (Exception e) {
                logger.warn("Send operation failed for operation: {} (attempt {}/{}): {}", 
                    operationId, attempt + 1, maxRetries + 1, e.getMessage());
                
                if (attempt == maxRetries) {
                    logger.error("All retry attempts exhausted for operation: {}", operationId, e);
                    return false;
                }
            }
            
            attempt++;
            
            if (attempt <= maxRetries) {
                // Calculate exponential backoff delay
                long delay = calculateExponentialBackoffDelay(attempt, baseDelay);
                
                logger.debug("Retrying operation: {} in {} ms (attempt {}/{})", 
                    operationId, delay, attempt + 1, maxRetries + 1);
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry operation interrupted for operation: {}", operationId);
                    return false;
                }
            }
        }
        
        logger.error("Failed to send message for operation: {} after {} attempts", operationId, maxRetries + 1);
        return false;
    }

    /**
     * Calculate exponential backoff delay
     * 
     * @param attempt Current attempt number (1-based)
     * @param baseDelay Base delay in milliseconds
     * @return Calculated delay in milliseconds
     */
    public static long calculateExponentialBackoffDelay(int attempt, long baseDelay) {
        // Exponential backoff: baseDelay * 2^(attempt-1) with jitter
        long delay = baseDelay * (1L << Math.min(attempt - 1, 10)); // Cap at 2^10
        
        // Add jitter (random factor between 0.5 and 1.5)
        double jitter = 0.5 + Math.random();
        delay = (long) (delay * jitter);
        
        // Cap maximum delay at 30 seconds
        return Math.min(delay, 30000);
    }

    /**
     * Create a dead letter queue message
     * 
     * @param originalMessage The original failed message
     * @param errorReason Reason for the failure
     * @param retryCount Number of retries attempted
     * @return Message formatted for dead letter queue
     */
    public static <T> Message<T> createDeadLetterMessage(Message<T> originalMessage, String errorReason, int retryCount) {
        return MessageBuilder.fromMessage(originalMessage)
                .setHeader("dlq-reason", errorReason)
                .setHeader("dlq-timestamp", System.currentTimeMillis())
                .setHeader("dlq-retry-count", retryCount)
                .setHeader("dlq-original-destination", originalMessage.getHeaders().get("destination"))
                .build();
    }

    /**
     * Check if message is expired based on TTL header
     * 
     * @param message The message to check
     * @return true if message is expired, false otherwise
     */
    public static boolean isMessageExpired(Message<?> message) {
        Object ttlObj = message.getHeaders().get("ttl");
        Object timestampObj = message.getHeaders().get(EVENT_TIMESTAMP_HEADER);
        
        if (ttlObj == null || timestampObj == null) {
            return false;
        }
        
        try {
            long ttl = Long.parseLong(ttlObj.toString());
            long timestamp = Long.parseLong(timestampObj.toString());
            long currentTime = System.currentTimeMillis();
            
            return (currentTime - timestamp) > ttl;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Calculate message processing duration
     * 
     * @param message The message
     * @return Processing duration in milliseconds, or -1 if timestamp not available
     */
    public static long calculateProcessingDuration(Message<?> message) {
        Long eventTimestamp = getEventTimestamp(message);
        if (eventTimestamp == null) {
            return -1;
        }
        
        return System.currentTimeMillis() - eventTimestamp;
    }

    /**
     * Create message with timeout
     * 
     * @param payload The message payload
     * @param eventType The type of event
     * @param sourceService The service that created the message
     * @param timeoutMs Timeout in milliseconds
     * @return Message with timeout header
     */
    public static <T> Message<T> createMessageWithTimeout(T payload, String eventType, String sourceService, long timeoutMs) {
        return MessageBuilder.withPayload(payload)
                .setHeader(MESSAGE_ID_HEADER, UUID.randomUUID().toString())
                .setHeader(EVENT_TYPE_HEADER, eventType)
                .setHeader(SOURCE_SERVICE_HEADER, sourceService)
                .setHeader(CORRELATION_ID_HEADER, generateCorrelationId())
                .setHeader(EVENT_TIMESTAMP_HEADER, System.currentTimeMillis())
                .setHeader("ttl", timeoutMs)
                .setHeader("deadline", System.currentTimeMillis() + timeoutMs)
                .build();
    }
} 