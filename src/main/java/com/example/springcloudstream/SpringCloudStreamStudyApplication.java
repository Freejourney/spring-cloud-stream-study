package com.example.springcloudstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Stream Study Application
 * 
 * This is the main entry point for our Spring Cloud Stream tutorial application.
 * It demonstrates various messaging patterns and binder integrations.
 * 
 * @SpringBootApplication enables:
 * - @Configuration: Tags the class as a source of bean definitions
 * - @EnableAutoConfiguration: Tells Spring Boot to start adding beans based on classpath settings
 * - @ComponentScan: Tells Spring to look for other components, configurations, and services
 */
@SpringBootApplication
public class SpringCloudStreamStudyApplication {

    /**
     * Main method to start the Spring Boot application
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringCloudStreamStudyApplication.class, args);
    }
} 