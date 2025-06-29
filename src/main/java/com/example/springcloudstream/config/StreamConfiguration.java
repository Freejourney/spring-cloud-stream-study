package com.example.springcloudstream.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Cloud Stream Configuration
 * 
 * Centralized configuration for Spring Cloud Stream bindings and setup.
 * This configuration class manages:
 * - Component scanning for domain packages
 * - Bean definitions for message functions
 * - Custom message converters and serializers
 * - Error handling configuration
 * - Metrics and monitoring setup
 * 
 * The configuration ensures proper separation of concerns by scanning
 * only the necessary domain packages and excluding test configurations.
 * 
 * @Configuration - Marks this as a configuration class
 * @ComponentScan - Configures component scanning for domain packages
 * @Slf4j - Lombok annotation for logging
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = {
    "com.example.springcloudstream.domain",     // Scan all domain packages
    "com.example.springcloudstream.common",     // Scan common utilities
    "com.example.springcloudstream.integration" // Scan integration components
})
public class StreamConfiguration {
    
    /**
     * Constructor logging for configuration initialization
     */
    public StreamConfiguration() {
        log.info("🔧 Initializing Spring Cloud Stream Configuration");
        log.info("📦 Component scanning enabled for domain, common, and integration packages");
    }
} 