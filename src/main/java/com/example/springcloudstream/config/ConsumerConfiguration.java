package com.example.springcloudstream.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Consumer Configuration
 * 
 * Centralized configuration for message consumers across all domains.
 * This configuration class provides:
 * - Consumer-specific beans and utilities
 * - Message processing metrics and monitoring
 * - Error handling for consumer operations
 * - Dead letter queue management
 * - Consumer health checking
 * 
 * @Configuration - Marks this as a configuration class
 * @Slf4j - Lombok annotation for logging
 */
@Slf4j
@Configuration
public class ConsumerConfiguration {
    
    /**
     * Consumer metrics collector
     * Tracks message consumption statistics
     */
    @Bean
    public ConsumerMetrics consumerMetrics() {
        return new ConsumerMetrics();
    }
    
    /**
     * Message processor registry
     * Manages message processing state and coordination
     */
    @Bean
    public MessageProcessorRegistry messageProcessorRegistry() {
        return new MessageProcessorRegistry();
    }
    
    /**
     * Dead letter queue handler
     * Handles messages that fail processing
     */
    @Bean
    public DeadLetterQueueHandler deadLetterQueueHandler() {
        return new DeadLetterQueueHandler();
    }
    
    /**
     * Consumer health checker
     * Monitors consumer health and performance
     */
    @Bean
    public ConsumerHealthChecker consumerHealthChecker(ConsumerMetrics consumerMetrics) {
        return new ConsumerHealthChecker(consumerMetrics);
    }
    
    /**
     * Consumer Metrics Collector
     * Tracks consumption statistics across all consumers
     */
    public static class ConsumerMetrics {
        private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
        private final AtomicLong totalProcessingFailures = new AtomicLong(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);
        private final Map<String, AtomicLong> consumerCounts = new ConcurrentHashMap<>();
        private volatile long lastMessageTime = 0;
        
        public void recordProcessingStart(String consumerName) {
            consumerCounts.computeIfAbsent(consumerName, k -> new AtomicLong(0));
            lastMessageTime = System.currentTimeMillis();
        }
        
        public void recordProcessingSuccess(String consumerName, long processingTimeMs) {
            totalMessagesProcessed.incrementAndGet();
            totalProcessingTime.addAndGet(processingTimeMs);
            consumerCounts.get(consumerName).incrementAndGet();
        }
        
        public void recordProcessingFailure(String consumerName) {
            totalProcessingFailures.incrementAndGet();
        }
        
        public long getTotalMessagesProcessed() {
            return totalMessagesProcessed.get();
        }
        
        public long getTotalProcessingFailures() {
            return totalProcessingFailures.get();
        }
        
        public double getSuccessRate() {
            long total = totalMessagesProcessed.get() + totalProcessingFailures.get();
            return total > 0 ? (double) totalMessagesProcessed.get() / total * 100.0 : 0.0;
        }
        
        public double getAverageProcessingTime() {
            long processed = totalMessagesProcessed.get();
            return processed > 0 ? (double) totalProcessingTime.get() / processed : 0.0;
        }
        
        public long getConsumerCount(String consumerName) {
            return consumerCounts.getOrDefault(consumerName, new AtomicLong(0)).get();
        }
        
        public Map<String, Long> getAllConsumerCounts() {
            Map<String, Long> result = new ConcurrentHashMap<>();
            consumerCounts.forEach((key, value) -> result.put(key, value.get()));
            return result;
        }
        
        public long getLastMessageTime() {
            return lastMessageTime;
        }
        
        public void reset() {
            totalMessagesProcessed.set(0);
            totalProcessingFailures.set(0);
            totalProcessingTime.set(0);
            consumerCounts.clear();
            lastMessageTime = 0;
        }
    }
    
    /**
     * Message Processor Registry
     * Manages active message processing state
     */
    public static class MessageProcessorRegistry {
        private final Map<String, ProcessingInfo> activeProcessing = new ConcurrentHashMap<>();
        
        public void registerProcessingStart(String messageId, String consumerName) {
            activeProcessing.put(messageId, new ProcessingInfo(consumerName, LocalDateTime.now()));
        }
        
        public void registerProcessingComplete(String messageId) {
            activeProcessing.remove(messageId);
        }
        
        public boolean isProcessing(String messageId) {
            return activeProcessing.containsKey(messageId);
        }
        
        public int getActiveProcessingCount() {
            return activeProcessing.size();
        }
        
        public Map<String, ProcessingInfo> getActiveProcessing() {
            return new ConcurrentHashMap<>(activeProcessing);
        }
        
        public static class ProcessingInfo {
            private final String consumerName;
            private final LocalDateTime startTime;
            
            public ProcessingInfo(String consumerName, LocalDateTime startTime) {
                this.consumerName = consumerName;
                this.startTime = startTime;
            }
            
            public String getConsumerName() { return consumerName; }
            public LocalDateTime getStartTime() { return startTime; }
        }
    }
    
    /**
     * Dead Letter Queue Handler
     * Handles messages that fail processing
     */
    public static class DeadLetterQueueHandler {
        private final Map<String, FailedMessage> failedMessages = new ConcurrentHashMap<>();
        
        public void handleFailedMessage(Message<?> message, String consumerName, Exception error) {
            String messageId = getMessageId(message);
            FailedMessage failedMessage = new FailedMessage(
                message, 
                consumerName, 
                error.getMessage(), 
                LocalDateTime.now()
            );
            failedMessages.put(messageId, failedMessage);
            
            log.error("Message failed processing in consumer {} - Message ID: {}, Error: {}", 
                     consumerName, messageId, error.getMessage());
        }
        
        public Map<String, FailedMessage> getFailedMessages() {
            return new ConcurrentHashMap<>(failedMessages);
        }
        
        public void clearFailedMessages() {
            failedMessages.clear();
        }
        
        private String getMessageId(Message<?> message) {
            Object messageId = message.getHeaders().get("message-id");
            return messageId != null ? messageId.toString() : "unknown-" + System.currentTimeMillis();
        }
        
        public static class FailedMessage {
            private final Message<?> message;
            private final String consumerName;
            private final String errorMessage;
            private final LocalDateTime failureTime;
            
            public FailedMessage(Message<?> message, String consumerName, String errorMessage, LocalDateTime failureTime) {
                this.message = message;
                this.consumerName = consumerName;
                this.errorMessage = errorMessage;
                this.failureTime = failureTime;
            }
            
            public Message<?> getMessage() { return message; }
            public String getConsumerName() { return consumerName; }
            public String getErrorMessage() { return errorMessage; }
            public LocalDateTime getFailureTime() { return failureTime; }
        }
    }
    
    /**
     * Consumer Health Checker
     * Monitors consumer health and performance
     */
    public static class ConsumerHealthChecker {
        private final ConsumerMetrics consumerMetrics;
        
        public ConsumerHealthChecker(ConsumerMetrics consumerMetrics) {
            this.consumerMetrics = consumerMetrics;
        }
        
        public HealthStatus checkHealth() {
            double successRate = consumerMetrics.getSuccessRate();
            double averageProcessingTime = consumerMetrics.getAverageProcessingTime();
            long lastMessageTime = consumerMetrics.getLastMessageTime();
            
            // Consider healthy if success rate > 90% and average processing time < 5000ms
            boolean isHealthy = successRate > 90.0 && averageProcessingTime < 5000.0;
            
            // Check if consumers are processing messages recently (within last 5 minutes)
            long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;
            boolean isActive = timeSinceLastMessage < 300000; // 5 minutes
            
            return new HealthStatus(isHealthy, isActive, successRate, averageProcessingTime, timeSinceLastMessage);
        }
        
        public static class HealthStatus {
            private final boolean healthy;
            private final boolean active;
            private final double successRate;
            private final double averageProcessingTime;
            private final long timeSinceLastMessage;
            
            public HealthStatus(boolean healthy, boolean active, double successRate, 
                              double averageProcessingTime, long timeSinceLastMessage) {
                this.healthy = healthy;
                this.active = active;
                this.successRate = successRate;
                this.averageProcessingTime = averageProcessingTime;
                this.timeSinceLastMessage = timeSinceLastMessage;
            }
            
            public boolean isHealthy() { return healthy; }
            public boolean isActive() { return active; }
            public double getSuccessRate() { return successRate; }
            public double getAverageProcessingTime() { return averageProcessingTime; }
            public long getTimeSinceLastMessage() { return timeSinceLastMessage; }
        }
    }
} 