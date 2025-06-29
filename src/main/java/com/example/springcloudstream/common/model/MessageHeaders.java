package com.example.springcloudstream.common.model;

/**
 * Message Headers Constants
 * 
 * Centralized definition of standard message headers used across the application.
 * This class provides consistent header names and utilities for working with
 * Spring Cloud Stream message headers.
 * 
 * Header Categories:
 * - Standard Headers: Basic message identification and metadata
 * - Routing Headers: Content-based routing and partitioning
 * - Correlation Headers: Distributed tracing and correlation
 * - Business Headers: Domain-specific business information
 * - Technical Headers: Technical metadata and processing information
 */
public final class MessageHeaders {
    
    // Private constructor to prevent instantiation
    private MessageHeaders() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    // ==================== STANDARD HEADERS ====================
    
    /**
     * Unique identifier for the message
     */
    public static final String MESSAGE_ID = "message-id";
    
    /**
     * Type of event or message
     */
    public static final String EVENT_TYPE = "event-type";
    
    /**
     * Service that created/sent the message
     */
    public static final String SOURCE_SERVICE = "source-service";
    
    /**
     * Target service for the message
     */
    public static final String TARGET_SERVICE = "target-service";
    
    /**
     * Timestamp when the event occurred
     */
    public static final String EVENT_TIMESTAMP = "event-timestamp";
    
    /**
     * Message format version for schema evolution
     */
    public static final String MESSAGE_VERSION = "message-version";
    
    /**
     * Content type of the message payload
     */
    public static final String CONTENT_TYPE = "content-type";
    
    // ==================== ROUTING HEADERS ====================
    
    /**
     * Partition key for message routing
     */
    public static final String PARTITION_KEY = "partition-key";
    
    /**
     * Alternative partition header name
     */
    public static final String PARTITION = "partition";
    
    /**
     * Routing key for content-based routing
     */
    public static final String ROUTING_KEY = "routing-key";
    
    /**
     * Destination override for dynamic routing
     */
    public static final String DESTINATION_OVERRIDE = "destination-override";
    
    /**
     * Message priority for priority-based routing
     */
    public static final String MESSAGE_PRIORITY = "message-priority";
    
    /**
     * Short form of priority
     */
    public static final String PRIORITY = "priority";
    
    // ==================== CORRELATION HEADERS ====================
    
    /**
     * Correlation ID for distributed tracing
     */
    public static final String CORRELATION_ID = "correlation-id";
    
    /**
     * Trace ID for distributed tracing
     */
    public static final String TRACE_ID = "trace-id";
    
    /**
     * Span ID for distributed tracing
     */
    public static final String SPAN_ID = "span-id";
    
    /**
     * Parent span ID for distributed tracing
     */
    public static final String PARENT_SPAN_ID = "parent-span-id";
    
    /**
     * Request ID for request correlation
     */
    public static final String REQUEST_ID = "request-id";
    
    /**
     * Session ID for session correlation
     */
    public static final String SESSION_ID = "session-id";
    
    /**
     * Transaction ID for transaction correlation
     */
    public static final String TRANSACTION_ID = "transaction-id";
    
    // ==================== BUSINESS HEADERS ====================
    
    /**
     * User ID associated with the message
     */
    public static final String USER_ID = "user-id";
    
    /**
     * Department for departmental routing
     */
    public static final String DEPARTMENT = "department";
    
    /**
     * Organization ID for multi-tenant scenarios
     */
    public static final String ORGANIZATION_ID = "organization-id";
    
    /**
     * Region for geographic routing
     */
    public static final String REGION = "region";
    
    /**
     * Environment (dev, test, prod)
     */
    public static final String ENVIRONMENT = "environment";
    
    /**
     * Business process ID
     */
    public static final String PROCESS_ID = "process-id";
    
    /**
     * Aggregate ID (DDD aggregate identifier)
     */
    public static final String AGGREGATE_ID = "aggregate-id";
    
    /**
     * Aggregate type (DDD aggregate type)
     */
    public static final String AGGREGATE_TYPE = "aggregate-type";
    
    // ==================== TECHNICAL HEADERS ====================
    
    /**
     * Retry count for failed message processing
     */
    public static final String RETRY_COUNT = "retry-count";
    
    /**
     * Maximum retry attempts
     */
    public static final String MAX_RETRIES = "max-retries";
    
    /**
     * Retry delay in milliseconds
     */
    public static final String RETRY_DELAY = "retry-delay";
    
    /**
     * Original timestamp when message was first created
     */
    public static final String ORIGINAL_TIMESTAMP = "original-timestamp";
    
    /**
     * Processing deadline timestamp
     */
    public static final String DEADLINE = "deadline";
    
    /**
     * Time-to-live in milliseconds
     */
    public static final String TTL = "ttl";
    
    /**
     * Message encoding
     */
    public static final String ENCODING = "encoding";
    
    /**
     * Compression algorithm used
     */
    public static final String COMPRESSION = "compression";
    
    /**
     * Security token or JWT
     */
    public static final String SECURITY_TOKEN = "security-token";
    
    /**
     * Authorization header
     */
    public static final String AUTHORIZATION = "authorization";
    
    // ==================== ERROR HANDLING HEADERS ====================
    
    /**
     * Error code for failed messages
     */
    public static final String ERROR_CODE = "error-code";
    
    /**
     * Error message for failed messages
     */
    public static final String ERROR_MESSAGE = "error-message";
    
    /**
     * Stack trace for failed messages
     */
    public static final String ERROR_STACK_TRACE = "error-stack-trace";
    
    /**
     * Original destination before error routing
     */
    public static final String ORIGINAL_DESTINATION = "original-destination";
    
    /**
     * Failed processing service
     */
    public static final String FAILED_SERVICE = "failed-service";
    
    /**
     * Failure timestamp
     */
    public static final String FAILURE_TIMESTAMP = "failure-timestamp";
    
    /**
     * Failure reason for error handling
     */
    public static final String FAILURE_REASON = "failure-reason";
    
    // ==================== AUDIT HEADERS ====================
    
    /**
     * Created by user/service
     */
    public static final String CREATED_BY = "created-by";
    
    /**
     * Creation timestamp
     */
    public static final String CREATED_AT = "created-at";
    
    /**
     * Last modified by user/service
     */
    public static final String MODIFIED_BY = "modified-by";
    
    /**
     * Last modification timestamp
     */
    public static final String MODIFIED_AT = "modified-at";
    
    /**
     * Audit trail identifier
     */
    public static final String AUDIT_ID = "audit-id";
    
    // ==================== ORDER HEADERS ====================
    
    /**
     * Order ID
     */
    public static final String ORDER_ID = "order-id";
    
    /**
     * Order value/amount
     */
    public static final String ORDER_VALUE = "order-value";
    
    /**
     * Order date
     */
    public static final String ORDER_DATE = "order-date";
    
    /**
     * Previous status (for status change events)
     */
    public static final String PREVIOUS_STATUS = "previous-status";
    
    /**
     * New status (for status change events)
     */
    public static final String NEW_STATUS = "new-status";
    
    /**
     * Status changed flag
     */
    public static final String STATUS_CHANGED = "status-changed";
    
    /**
     * Cancellation reason
     */
    public static final String CANCELLATION_REASON = "cancellation-reason";
    
    /**
     * Notification type
     */
    public static final String NOTIFICATION_TYPE = "notification-type";
    
    // ==================== CUSTOM DOMAIN HEADERS ====================
    
    /**
     * Order-specific headers
     */
    public static final class Order {
        public static final String ORDER_ID = "order-id";
        public static final String CUSTOMER_ID = "customer-id";
        public static final String ORDER_STATUS = "order-status";
        public static final String ORDER_PRIORITY = "order-priority";
        public static final String PAYMENT_METHOD = "payment-method";
        public static final String SHIPPING_ADDRESS = "shipping-address";
        public static final String BILLING_ADDRESS = "billing-address";
    }
    
    /**
     * User-specific headers
     */
    public static final class User {
        public static final String USER_NAME = "user-name";
        public static final String USER_EMAIL = "user-email";
        public static final String USER_STATUS = "user-status";
        public static final String USER_DEPARTMENT = "user-department";
        public static final String USER_ROLE = "user-role";
        public static final String PREVIOUS_DEPARTMENT = "previous-department";
        public static final String NEW_DEPARTMENT = "new-department";
        public static final String DEPARTMENT_CHANGED = "department-changed";
    }
    
    /**
     * Payment-specific headers
     */
    public static final class Payment {
        public static final String PAYMENT_ID = "payment-id";
        public static final String PAYMENT_AMOUNT = "payment-amount";
        public static final String PAYMENT_CURRENCY = "payment-currency";
        public static final String PAYMENT_STATUS = "payment-status";
        public static final String PAYMENT_PROCESSOR = "payment-processor";
        public static final String CARD_LAST_FOUR = "card-last-four";
    }
    
    /**
     * Inventory-specific headers
     */
    public static final class Inventory {
        public static final String ITEM_ID = "item-id";
        public static final String ITEM_SKU = "item-sku";
        public static final String QUANTITY = "quantity";
        public static final String WAREHOUSE_ID = "warehouse-id";
        public static final String RESERVATION_ID = "reservation-id";
        public static final String AVAILABILITY_STATUS = "availability-status";
    }
    
    /**
     * Shipping-specific headers
     */
    public static final class Shipping {
        public static final String SHIPMENT_ID = "shipment-id";
        public static final String TRACKING_NUMBER = "tracking-number";
        public static final String CARRIER = "carrier";
        public static final String SERVICE_TYPE = "service-type";
        public static final String ESTIMATED_DELIVERY = "estimated-delivery";
        public static final String ACTUAL_DELIVERY = "actual-delivery";
        public static final String DELIVERY_STATUS = "delivery-status";
    }
} 