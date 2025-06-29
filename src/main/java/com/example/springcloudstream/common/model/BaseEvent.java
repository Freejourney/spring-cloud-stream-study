package com.example.springcloudstream.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Base Event Class
 * 
 * Provides common properties and behavior for all events in the system.
 * This abstract class ensures consistency across different domain events
 * and provides a foundation for event-driven architecture patterns.
 * 
 * Common Properties:
 * - Event ID for unique identification
 * - Event timestamp for ordering and audit
 * - Source service identification
 * - Correlation ID for distributed tracing
 * - Event version for schema evolution
 * 
 * @Data - Lombok annotation for getters, setters, toString, equals, hashCode
 * @NoArgsConstructor - Lombok annotation for default constructor
 * @AllArgsConstructor - Lombok annotation for constructor with all fields
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    
    /**
     * Unique identifier for this event
     * Should be generated for each event occurrence
     */
    private String eventId;
    
    /**
     * Timestamp when the event occurred
     * Used for ordering and audit purposes
     */
    private LocalDateTime eventTimestamp;
    
    /**
     * Service or component that triggered this event
     * Useful for debugging and monitoring
     */
    private String sourceService;
    
    /**
     * Correlation ID for tracing related events across services
     * Essential for distributed system observability
     */
    private String correlationId;
    
    /**
     * Version of the event schema
     * Important for backward compatibility during schema evolution
     */
    private String eventVersion;
    
    /**
     * Additional metadata about the event
     * Can contain context-specific information
     */
    private String metadata;
    
    /**
     * Initialize common event properties
     * Should be called by concrete event classes
     */
    protected void initializeBaseEvent(String sourceService, String correlationId) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.eventTimestamp = LocalDateTime.now();
        this.sourceService = sourceService;
        this.correlationId = correlationId;
        this.eventVersion = "1.0";
    }
    
    /**
     * Initialize with custom event ID
     * Useful for idempotent event processing
     */
    protected void initializeBaseEvent(String eventId, String sourceService, String correlationId) {
        this.eventId = eventId;
        this.eventTimestamp = LocalDateTime.now();
        this.sourceService = sourceService;
        this.correlationId = correlationId;
        this.eventVersion = "1.0";
    }
    
    /**
     * Abstract method to get the event type
     * Must be implemented by concrete event classes
     */
    public abstract String getEventType();
    
    /**
     * Abstract method to get the aggregate ID
     * Should return the ID of the aggregate that this event relates to
     */
    public abstract String getAggregateId();
    
    /**
     * Check if this event is valid
     * Basic validation for common properties
     */
    public boolean isValid() {
        return eventId != null && !eventId.trim().isEmpty() &&
               eventTimestamp != null &&
               sourceService != null && !sourceService.trim().isEmpty() &&
               eventVersion != null && !eventVersion.trim().isEmpty();
    }
} 