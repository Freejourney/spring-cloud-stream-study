package com.example.springcloudstream.common.util;

import org.slf4j.Logger;

/**
 * Test-specific MessageUtils with optimized retry delays
 * 
 * This utility provides the same retry functionality as MessageUtils
 * but with much faster delays to speed up test execution.
 */
public final class TestMessageUtils {
    
    private TestMessageUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Send message with retry mechanism - OPTIMIZED FOR TESTS
     * Uses much faster delays compared to production MessageUtils
     * 
     * @param sendOperation Supplier function that performs the send operation
     * @param maxRetries Maximum number of retry attempts
     * @param operationId Identifier for the operation (for logging)
     * @param logger Logger instance for logging retry attempts
     * @return true if message was sent successfully, false otherwise
     */
    public static boolean sendWithRetry(java.util.function.Supplier<Boolean> sendOperation, 
                                       int maxRetries, String operationId, Logger logger) {
        int attempt = 0;
        long baseDelay = 10; // 10ms base delay (100x faster than production)
        
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
                // Calculate fast test delay (much faster than production)
                long delay = calculateTestRetryDelay(attempt, baseDelay);
                
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
     * Calculate test-optimized retry delay
     * Much faster than production delays for quick test execution
     * 
     * @param attempt Current attempt number (1-based)
     * @param baseDelay Base delay in milliseconds
     * @return Calculated delay in milliseconds
     */
    public static long calculateTestRetryDelay(int attempt, long baseDelay) {
        // Minimal exponential backoff for tests: baseDelay * 1.5^(attempt-1)
        long delay = (long) (baseDelay * Math.pow(1.5, Math.min(attempt - 1, 4))); // Cap at 1.5^4
        
        // Add minimal jitter (random factor between 0.8 and 1.2)
        double jitter = 0.8 + (Math.random() * 0.4);
        delay = (long) (delay * jitter);
        
        // Cap maximum delay at 100ms (vs 30 seconds in production)
        return Math.min(delay, 100);
    }
} 