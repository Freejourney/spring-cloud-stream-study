package com.example.springcloudstream.config;

import com.example.springcloudstream.common.util.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Producer Configuration
 * 
 * Centralized configuration for message producers across all domains.
 * This configuration class provides:
 * - Producer-specific beans and utilities
 * - Async message sending capabilities
 * - Producer metrics and monitoring
 * - Error handling for producer operations
 * - Message serialization configuration
 * 
 * @Configuration - Marks this as a configuration class
 * @Slf4j - Lombok annotation for logging
 */
@Slf4j
@Configuration
public class ProducerConfiguration {
    
    /**
     * Executor for async message production
     * Provides thread pool for non-blocking message sending
     */
    @Bean("producerExecutor")
    public Executor producerExecutor() {
        log.info("🧵 Creating producer executor with {} threads", 
                Runtime.getRuntime().availableProcessors());
        return Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors())
        );
    }
    
    /**
     * Async message sender wrapper
     * Provides non-blocking message sending capabilities
     */
    @Bean
    public AsyncMessageSender asyncMessageSender(StreamBridge streamBridge, Executor producerExecutor) {
        return new AsyncMessageSender(streamBridge, producerExecutor);
    }
    
    /**
     * Producer metrics collector
     * Tracks message production statistics
     */
    @Bean
    public ProducerMetrics producerMetrics() {
        return new ProducerMetrics();
    }
    
    /**
     * Async Message Sender
     * Provides asynchronous message sending capabilities
     */
    public static class AsyncMessageSender {
        private final StreamBridge streamBridge;
        private final Executor executor;
        
        public AsyncMessageSender(StreamBridge streamBridge, Executor executor) {
            this.streamBridge = streamBridge;
            this.executor = executor;
        }
        
        /**
         * Send message asynchronously
         */
        public <T> CompletableFuture<Boolean> sendAsync(String destination, T payload) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Message<T> message = MessageUtils.createMessage(
                        payload, 
                        "async-message", 
                        "async-producer", 
                        null
                    );
                    return streamBridge.send(destination, message);
                } catch (Exception e) {
                    log.error("Failed to send async message to {}", destination, e);
                    return false;
                }
            }, executor);
        }
        
        /**
         * Send message with custom headers asynchronously
         */
        public <T> CompletableFuture<Boolean> sendAsync(String destination, Message<T> message) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return streamBridge.send(destination, message);
                } catch (Exception e) {
                    log.error("Failed to send async message to {}", destination, e);
                    return false;
                }
            }, executor);
        }
    }
    
    /**
     * Producer Metrics Collector
     * Tracks production statistics
     */
    public static class ProducerMetrics {
        private volatile long totalMessagesSent = 0;
        private volatile long totalFailures = 0;
        private volatile long lastMessageTime = 0;
        
        public void recordSuccess() {
            totalMessagesSent++;
            lastMessageTime = System.currentTimeMillis();
        }
        
        public void recordFailure() {
            totalFailures++;
        }
        
        public long getTotalMessagesSent() {
            return totalMessagesSent;
        }
        
        public long getTotalFailures() {
            return totalFailures;
        }
        
        public double getSuccessRate() {
            long total = totalMessagesSent + totalFailures;
            return total > 0 ? (double) totalMessagesSent / total * 100.0 : 0.0;
        }
        
        public long getLastMessageTime() {
            return lastMessageTime;
        }
        
        public void reset() {
            totalMessagesSent = 0;
            totalFailures = 0;
            lastMessageTime = 0;
        }
    }
} 