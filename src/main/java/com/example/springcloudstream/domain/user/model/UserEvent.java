package com.example.springcloudstream.domain.user.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User Event Class
 * 
 * Represents different types of user-related events in the system.
 * This class provides a structured way to handle user lifecycle events
 * and enables proper event-driven architecture patterns.
 * 
 * Event Types:
 * - USER_CREATED: When a new user is created
 * - USER_UPDATED: When user information is modified
 * - USER_DELETED: When a user is deactivated/deleted
 * - USER_ACTIVATED: When a user account is activated
 * - USER_DEACTIVATED: When a user account is deactivated
 * - DEPARTMENT_CHANGED: When user changes department
 * 
 * @Data - Lombok annotation for getters, setters, toString, equals, hashCode
 * @NoArgsConstructor - Lombok annotation for default constructor
 * @AllArgsConstructor - Lombok annotation for constructor with all fields
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {
    
    /**
     * Enumeration of possible user event types
     */
    public enum EventType {
        USER_CREATED("User Created"),
        USER_UPDATED("User Updated"), 
        USER_DELETED("User Deleted"),
        USER_ACTIVATED("User Activated"),
        USER_DEACTIVATED("User Deactivated"),
        DEPARTMENT_CHANGED("Department Changed"),
        PROFILE_UPDATED("Profile Updated"),
        PASSWORD_CHANGED("Password Changed");
        
        private final String description;
        
        EventType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Unique identifier for this event
     */
    private String eventId;
    
    /**
     * Type of the user event
     */
    private EventType eventType;
    
    /**
     * The user associated with this event
     */
    private User user;
    
    /**
     * Previous state of the user (for update events)
     */
    private User previousUser;
    
    /**
     * Timestamp when the event occurred
     */
    private LocalDateTime eventTimestamp;
    
    /**
     * Service or component that triggered this event
     */
    private String sourceService;
    
    /**
     * Correlation ID for tracing related events
     */
    private String correlationId;
    
    /**
     * Additional metadata about the event
     */
    private String metadata;
    
    /**
     * Department before change (for department change events)
     */
    private String previousDepartment;
    
    /**
     * New department (for department change events)
     */
    private String newDepartment;
    
    /**
     * Factory method to create a user created event
     */
    public static UserEvent createUserCreatedEvent(User user, String sourceService, String correlationId) {
        UserEvent event = new UserEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType(EventType.USER_CREATED);
        event.setUser(user);
        event.setEventTimestamp(LocalDateTime.now());
        event.setSourceService(sourceService);
        event.setCorrelationId(correlationId);
        return event;
    }
    
    /**
     * Factory method to create a user updated event
     */
    public static UserEvent createUserUpdatedEvent(User currentUser, User previousUser, String sourceService, String correlationId) {
        UserEvent event = new UserEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType(EventType.USER_UPDATED);
        event.setUser(currentUser);
        event.setPreviousUser(previousUser);
        event.setEventTimestamp(LocalDateTime.now());
        event.setSourceService(sourceService);
        event.setCorrelationId(correlationId);
        return event;
    }
    
    /**
     * Factory method to create a department changed event
     */
    public static UserEvent createDepartmentChangedEvent(User user, String previousDept, String newDept, String sourceService, String correlationId) {
        UserEvent event = new UserEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType(EventType.DEPARTMENT_CHANGED);
        event.setUser(user);
        event.setPreviousDepartment(previousDept);
        event.setNewDepartment(newDept);
        event.setEventTimestamp(LocalDateTime.now());
        event.setSourceService(sourceService);
        event.setCorrelationId(correlationId);
        return event;
    }
    
    /**
     * Factory method to create a user deleted event
     */
    public static UserEvent createUserDeletedEvent(User user, String sourceService, String correlationId) {
        UserEvent event = new UserEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType(EventType.USER_DELETED);
        event.setUser(user);
        event.setEventTimestamp(LocalDateTime.now());
        event.setSourceService(sourceService);
        event.setCorrelationId(correlationId);
        return event;
    }
} 