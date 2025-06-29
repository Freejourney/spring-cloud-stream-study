package com.example.springcloudstream.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Error Handling Service
 * 
 * Centralized error handling for Spring Cloud Stream messages.
 * Provides error logging, retry mechanisms, dead letter queue handling,
 * and error analytics.
 * 
 * @author Spring Cloud Stream Study
 * @version 1.0
 * @since 2024-01-01
 */
@Service
public class ErrorHandlingService {

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandlingService.class);

    // Error tracking
    private final Map<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastErrorTimes = new ConcurrentHashMap<>();
    private final Map<String, String> lastErrorMessages = new ConcurrentHashMap<>();

    /**
     * Error severity levels
     */
    public enum ErrorSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Error information class
     */
    public static class ErrorInfo {
        private final String errorId;
        private final String errorType;
        private final String errorMessage;
        private final String stackTrace;
        private final ErrorSeverity severity;
        private final LocalDateTime timestamp;
        private final String sourceService;
        private final Object originalPayload;

        public ErrorInfo(String errorId, String errorType, String errorMessage, String stackTrace,
                        ErrorSeverity severity, String sourceService, Object originalPayload) {
            this.errorId = errorId;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.stackTrace = stackTrace;
            this.severity = severity;
            this.timestamp = LocalDateTime.now();
            this.sourceService = sourceService;
            this.originalPayload = originalPayload;
        }

        // Getters
        public String getErrorId() { return errorId; }
        public String getErrorType() { return errorType; }
        public String getErrorMessage() { return errorMessage; }
        public String getStackTrace() { return stackTrace; }
        public ErrorSeverity getSeverity() { return severity; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getSourceService() { return sourceService; }
        public Object getOriginalPayload() { return originalPayload; }
    }

    /**
     * Handle general exception
     */
    public ErrorInfo handleError(Exception exception, String sourceService, Object originalPayload) {
        String errorId = generateErrorId();
        String errorType = exception.getClass().getSimpleName();
        String errorMessage = exception.getMessage() != null ? exception.getMessage() : "Unknown error";
        String stackTrace = getStackTraceAsString(exception);
        ErrorSeverity severity = determineSeverity(exception);

        ErrorInfo errorInfo = new ErrorInfo(errorId, errorType, errorMessage, stackTrace, 
            severity, sourceService, originalPayload);

        logError(errorInfo);
        trackError(sourceService, errorType);
        
        // Handle critical errors
        if (severity == ErrorSeverity.CRITICAL) {
            handleCriticalError(errorInfo);
        }

        return errorInfo;
    }

    /**
     * Handle message processing error
     */
    public ErrorInfo handleMessageError(Exception exception, Message<?> originalMessage) {
        String sourceService = getHeaderValue(originalMessage, "source-service", "unknown");
        Object payload = originalMessage.getPayload();

        ErrorInfo errorInfo = handleError(exception, sourceService, payload);
        
        // Add message-specific error handling
        handleMessageSpecificError(errorInfo, originalMessage);
        
        return errorInfo;
    }

    /**
     * Create error message for dead letter queue
     */
    public Message<?> createErrorMessage(ErrorInfo errorInfo, Message<?> originalMessage) {
        return MessageBuilder
            .withPayload(originalMessage.getPayload())
            .setHeader("error-id", errorInfo.getErrorId())
            .setHeader("error-type", errorInfo.getErrorType())
            .setHeader("error-message", errorInfo.getErrorMessage())
            .setHeader("error-severity", errorInfo.getSeverity().toString())
            .setHeader("error-timestamp", errorInfo.getTimestamp().toString())
            .setHeader("original-source-service", errorInfo.getSourceService())
            .setHeader("failure-reason", "Message processing failed")
            .setHeader("retry-count", getRetryCount(originalMessage) + 1)
            .copyHeaders(originalMessage.getHeaders())
            .build();
    }

    /**
     * Determine if error is retryable
     */
    public boolean isRetryableError(Exception exception) {
        // Temporary network issues, timeout exceptions, etc.
        return exception instanceof java.net.SocketTimeoutException ||
               exception instanceof java.net.ConnectException ||
               exception instanceof org.springframework.dao.TransientDataAccessException ||
               exception.getMessage() != null && exception.getMessage().contains("timeout");
    }

    /**
     * Calculate retry delay with exponential backoff
     */
    public long calculateRetryDelay(int retryCount, long baseDelayMs) {
        // Exponential backoff: baseDelay * 2^retryCount, with max delay
        long delay = baseDelayMs * (1L << Math.min(retryCount, 10)); // Cap at 2^10
        return Math.min(delay, 300000); // Max 5 minutes
    }

    /**
     * Check if retry limit exceeded
     */
    public boolean isRetryLimitExceeded(Message<?> message, int maxRetries) {
        int currentRetryCount = getRetryCount(message);
        return currentRetryCount >= maxRetries;
    }

    /**
     * Handle poison message (message that repeatedly fails)
     */
    public void handlePoisonMessage(Message<?> message, String reason) {
        String messageId = getHeaderValue(message, "message-id", "unknown");
        String sourceService = getHeaderValue(message, "source-service", "unknown");
        
        logger.error("Poison message detected - Message ID: {}, Source: {}, Reason: {}", 
            messageId, sourceService, reason);
        
        // Send to poison message queue or special handling
        sendToPoisonMessageQueue(message, reason);
        
        // Alert administrators
        alertPoisonMessage(messageId, sourceService, reason);
    }

    /**
     * Handle circuit breaker errors
     */
    public void handleCircuitBreakerError(String serviceKey, Exception exception) {
        logger.warn("Circuit breaker triggered for service: {}, Error: {}", serviceKey, exception.getMessage());
        
        // Track circuit breaker events
        trackError(serviceKey, "CircuitBreakerOpen");
        
        // Implement fallback logic if needed
        handleServiceUnavailable(serviceKey);
    }

    /**
     * Generate error report
     */
    public String generateErrorReport(String timeWindow) {
        StringBuilder report = new StringBuilder();
        report.append("=== ERROR REPORT (").append(timeWindow).append(") ===\n");
        
        errorCounts.forEach((key, count) -> {
            LocalDateTime lastError = lastErrorTimes.get(key);
            String lastMessage = lastErrorMessages.get(key);
            
            report.append(String.format("Service/Error: %s | Count: %d | Last Error: %s | Message: %s\n",
                key, count.get(), lastError, lastMessage));
        });
        
        return report.toString();
    }

    /**
     * Clear error statistics
     */
    public void clearErrorStatistics() {
        errorCounts.clear();
        lastErrorTimes.clear();
        lastErrorMessages.clear();
        logger.info("Error statistics cleared");
    }

    /**
     * Get error count for service
     */
    public int getErrorCount(String serviceKey) {
        return errorCounts.getOrDefault(serviceKey, new AtomicInteger(0)).get();
    }

    /**
     * Check if service is healthy based on error rate
     */
    public boolean isServiceHealthy(String serviceKey, double errorThreshold) {
        int errorCount = getErrorCount(serviceKey);
        // Simplified health check - in real scenario, consider time windows
        return errorCount < errorThreshold;
    }

    /**
     * Handle timeout errors specifically
     */
    public ErrorInfo handleTimeoutError(java.util.concurrent.TimeoutException exception, 
                                      String operation, String sourceService) {
        logger.warn("Timeout occurred in operation: {} for service: {}", operation, sourceService);
        
        ErrorInfo errorInfo = handleError(exception, sourceService, operation);
        
        // Specific timeout handling
        handleTimeoutSpecificLogic(operation, sourceService);
        
        return errorInfo;
    }

    /**
     * Handle validation errors
     */
    public ErrorInfo handleValidationError(String validationMessage, String sourceService, Object payload) {
        IllegalArgumentException exception = new IllegalArgumentException(validationMessage);
        ErrorInfo errorInfo = handleError(exception, sourceService, payload);
        
        // Validation errors are usually not retryable
        logger.info("Validation error handled - not retryable: {}", validationMessage);
        
        return errorInfo;
    }

    // Private helper methods

    private String generateErrorId() {
        return "ERR-" + System.currentTimeMillis() + "-" + Math.random();
    }

    private void logError(ErrorInfo errorInfo) {
        switch (errorInfo.getSeverity()) {
            case CRITICAL:
                logger.error("CRITICAL ERROR [{}]: {} | Service: {} | Type: {}", 
                    errorInfo.getErrorId(), errorInfo.getErrorMessage(), 
                    errorInfo.getSourceService(), errorInfo.getErrorType());
                break;
            case HIGH:
                logger.error("HIGH SEVERITY ERROR [{}]: {} | Service: {}", 
                    errorInfo.getErrorId(), errorInfo.getErrorMessage(), errorInfo.getSourceService());
                break;
            case MEDIUM:
                logger.warn("MEDIUM SEVERITY ERROR [{}]: {} | Service: {}", 
                    errorInfo.getErrorId(), errorInfo.getErrorMessage(), errorInfo.getSourceService());
                break;
            case LOW:
                logger.info("LOW SEVERITY ERROR [{}]: {} | Service: {}", 
                    errorInfo.getErrorId(), errorInfo.getErrorMessage(), errorInfo.getSourceService());
                break;
        }
    }

    private void trackError(String serviceKey, String errorType) {
        String key = serviceKey + ":" + errorType;
        errorCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        lastErrorTimes.put(key, LocalDateTime.now());
        lastErrorMessages.put(key, errorType);
    }

    private ErrorSeverity determineSeverity(Exception exception) {
        if (exception instanceof NullPointerException || 
            exception instanceof IllegalStateException ||
            exception.getMessage() != null && exception.getMessage().toLowerCase().contains("critical")) {
            return ErrorSeverity.CRITICAL;
        } else if (exception instanceof IllegalArgumentException ||
                   exception instanceof java.util.concurrent.TimeoutException) {
            return ErrorSeverity.HIGH;
        } else if (exception instanceof java.net.ConnectException) {
            return ErrorSeverity.MEDIUM;
        } else {
            return ErrorSeverity.LOW;
        }
    }

    private String getStackTraceAsString(Exception exception) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    private String getHeaderValue(Message<?> message, String headerName, String defaultValue) {
        Object value = message.getHeaders().get(headerName);
        return value != null ? value.toString() : defaultValue;
    }

    private int getRetryCount(Message<?> message) {
        Object retryCount = message.getHeaders().get("retry-count");
        return retryCount instanceof Integer ? (Integer) retryCount : 0;
    }

    private void handleCriticalError(ErrorInfo errorInfo) {
        // Send alerts, notifications, etc.
        logger.error("CRITICAL ERROR ALERT: {}", errorInfo.getErrorMessage());
        // Implementation would include sending to monitoring systems
    }

    private void handleMessageSpecificError(ErrorInfo errorInfo, Message<?> originalMessage) {
        // Add message-specific error handling logic
        String messageType = getHeaderValue(originalMessage, "event-type", "unknown");
        logger.debug("Handling error for message type: {}", messageType);
    }

    private void sendToPoisonMessageQueue(Message<?> message, String reason) {
        // Implementation would send to poison message queue
        logger.info("Sending message to poison queue: {}", reason);
    }

    private void alertPoisonMessage(String messageId, String sourceService, String reason) {
        // Implementation would send alerts
        logger.error("POISON MESSAGE ALERT - ID: {}, Service: {}, Reason: {}", 
            messageId, sourceService, reason);
    }

    private void handleServiceUnavailable(String serviceKey) {
        // Implementation would handle service unavailability
        logger.info("Handling service unavailability for: {}", serviceKey);
    }

    private void handleTimeoutSpecificLogic(String operation, String sourceService) {
        // Specific timeout handling logic
        logger.debug("Timeout-specific handling for operation: {} in service: {}", operation, sourceService);
    }
} 