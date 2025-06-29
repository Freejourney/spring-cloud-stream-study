package com.example.springcloudstream.domain.order.model;

import com.example.springcloudstream.common.model.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Event Class
 * 
 * Represents different types of order-related events in the system.
 * This class extends BaseEvent to leverage common event properties
 * and provides order-specific event data and factory methods.
 * 
 * Event Types:
 * - ORDER_CREATED: When a new order is placed
 * - ORDER_CONFIRMED: When order validation succeeds
 * - ORDER_CANCELLED: When order is cancelled
 * - PAYMENT_PROCESSED: When payment is successful
 * - PAYMENT_FAILED: When payment fails
 * - INVENTORY_RESERVED: When inventory is reserved
 * - INVENTORY_UNAVAILABLE: When inventory is insufficient
 * - ORDER_SHIPPED: When order is shipped
 * - ORDER_DELIVERED: When order is delivered
 * - ORDER_REFUNDED: When order is refunded
 * 
 * @Data - Lombok annotation for getters, setters, toString, equals, hashCode
 * @EqualsAndHashCode(callSuper = true) - Include parent class fields in equals/hashCode
 * @NoArgsConstructor - Lombok annotation for default constructor
 * @AllArgsConstructor - Lombok annotation for constructor with all fields
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent extends BaseEvent {
    
    /**
     * Enumeration of possible order event types
     */
    public enum EventType {
        ORDER_CREATED("Order Created"),
        ORDER_CONFIRMED("Order Confirmed"),
        ORDER_CANCELLED("Order Cancelled"),
        PAYMENT_PROCESSED("Payment Processed"),
        PAYMENT_FAILED("Payment Failed"),
        INVENTORY_RESERVED("Inventory Reserved"),
        INVENTORY_UNAVAILABLE("Inventory Unavailable"),
        ORDER_SHIPPED("Order Shipped"),
        ORDER_DELIVERED("Order Delivered"),
        ORDER_REFUNDED("Order Refunded"),
        ORDER_UPDATED("Order Updated");
        
        private final String description;
        
        EventType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Type of the order event
     */
    private EventType orderEventType;
    
    /**
     * The order associated with this event
     */
    private Order order;
    
    /**
     * Previous state of the order (for update events)
     */
    private Order previousOrder;
    
    /**
     * Payment amount (for payment-related events)
     */
    private BigDecimal paymentAmount;
    
    /**
     * Payment method (for payment events)
     */
    private String paymentMethod;
    
    /**
     * Inventory items (for inventory events)
     */
    private List<String> inventoryItems;
    
    /**
     * Shipping carrier (for shipping events)
     */
    private String shippingCarrier;
    
    /**
     * Tracking number (for shipping events)
     */
    private String trackingNumber;
    
    /**
     * Estimated delivery date (for shipping events)
     */
    private LocalDateTime estimatedDeliveryDate;
    
    /**
     * Failure reason (for failed events)
     */
    private String failureReason;
    
    /**
     * Refund amount (for refund events)
     */
    private BigDecimal refundAmount;
    
    /**
     * Refund reason (for refund events)
     */
    private String refundReason;
    
    /**
     * Additional event-specific data
     */
    private String additionalData;

    // ===============================
    // New factory methods for service compatibility
    // ===============================

    /**
     * Factory method for order created event (simple version)
     */
    public static OrderEvent orderCreated(Order order) {
        return createOrderCreatedEvent(order, "order-service", null);
    }

    /**
     * Factory method for order status updated event
     */
    public static OrderEvent orderStatusUpdated(Order order, Order.OrderStatus previousStatus) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent("order-service", null);
        event.setOrderEventType(EventType.ORDER_UPDATED);
        event.setOrder(order);
        // Store previous status in additional data for now
        event.setAdditionalData("previousStatus:" + previousStatus.toString());
        return event;
    }

    /**
     * Factory method for order cancelled event (simple version)
     */
    public static OrderEvent orderCancelled(Order order, String reason) {
        return createOrderCancelledEvent(order, reason, "order-service", null);
    }

    /**
     * Get order ID from the associated order
     */
    public String getOrderId() {
        return order != null ? order.getId() : null;
    }



    /**
     * Get event data as a map (for compatibility)
     */
    public java.util.Map<String, Object> getEventData() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("eventType", getEventType());
        data.put("orderId", getOrderId());
        if (order != null) {
            data.put("userId", order.getUserId());
            data.put("status", order.getStatus());
            data.put("totalAmount", order.getTotalAmount());
        }
        if (additionalData != null) {
            data.put("additionalData", additionalData);
        }
        return data;
    }
    
    /**
     * Factory method to create an order created event
     */
    public static OrderEvent createOrderCreatedEvent(Order order, String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.ORDER_CREATED);
        event.setOrder(order);
        return event;
    }
    
    /**
     * Factory method to create an order confirmed event
     */
    public static OrderEvent createOrderConfirmedEvent(Order order, String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.ORDER_CONFIRMED);
        event.setOrder(order);
        return event;
    }
    
    /**
     * Factory method to create an order cancelled event
     */
    public static OrderEvent createOrderCancelledEvent(Order order, String reason, String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.ORDER_CANCELLED);
        event.setOrder(order);
        event.setFailureReason(reason);
        return event;
    }
    
    /**
     * Factory method to create a payment processed event
     */
    public static OrderEvent createPaymentProcessedEvent(Order order, BigDecimal amount, String paymentMethod, 
                                                        String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.PAYMENT_PROCESSED);
        event.setOrder(order);
        event.setPaymentAmount(amount);
        event.setPaymentMethod(paymentMethod);
        return event;
    }
    
    /**
     * Factory method to create a payment failed event
     */
    public static OrderEvent createPaymentFailedEvent(Order order, BigDecimal amount, String reason, 
                                                     String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.PAYMENT_FAILED);
        event.setOrder(order);
        event.setPaymentAmount(amount);
        event.setFailureReason(reason);
        return event;
    }
    
    /**
     * Factory method to create an inventory reserved event
     */
    public static OrderEvent createInventoryReservedEvent(Order order, List<String> items, 
                                                         String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.INVENTORY_RESERVED);
        event.setOrder(order);
        event.setInventoryItems(items);
        return event;
    }
    
    /**
     * Factory method to create an inventory unavailable event
     */
    public static OrderEvent createInventoryUnavailableEvent(Order order, List<String> unavailableItems, 
                                                           String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.INVENTORY_UNAVAILABLE);
        event.setOrder(order);
        event.setInventoryItems(unavailableItems);
        event.setFailureReason("Insufficient inventory for items: " + String.join(", ", unavailableItems));
        return event;
    }
    
    /**
     * Factory method to create an order shipped event
     */
    public static OrderEvent createOrderShippedEvent(Order order, String carrier, String trackingNumber, 
                                                    LocalDateTime estimatedDelivery, String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.ORDER_SHIPPED);
        event.setOrder(order);
        event.setShippingCarrier(carrier);
        event.setTrackingNumber(trackingNumber);
        event.setEstimatedDeliveryDate(estimatedDelivery);
        return event;
    }
    
    /**
     * Factory method to create an order delivered event
     */
    public static OrderEvent createOrderDeliveredEvent(Order order, String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.ORDER_DELIVERED);
        event.setOrder(order);
        return event;
    }
    
    /**
     * Factory method to create an order refunded event
     */
    public static OrderEvent createOrderRefundedEvent(Order order, BigDecimal refundAmount, String reason, 
                                                     String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.ORDER_REFUNDED);
        event.setOrder(order);
        event.setRefundAmount(refundAmount);
        event.setRefundReason(reason);
        return event;
    }
    
    /**
     * Factory method to create an order updated event
     */
    public static OrderEvent createOrderUpdatedEvent(Order currentOrder, Order previousOrder, 
                                                    String sourceService, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.initializeBaseEvent(sourceService, correlationId);
        event.setOrderEventType(EventType.ORDER_UPDATED);
        event.setOrder(currentOrder);
        event.setPreviousOrder(previousOrder);
        return event;
    }
    
    @Override
    public String getEventType() {
        return orderEventType != null ? orderEventType.name().toLowerCase().replace('_', '-') : "unknown";
    }
    
    @Override
    public String getAggregateId() {
        return order != null ? order.getId() : "unknown";
    }
    
    /**
     * Get human-readable event description
     */
    public String getEventDescription() {
        return orderEventType != null ? orderEventType.getDescription() : "Unknown Event";
    }
    
    /**
     * Check if this is a successful event
     */
    public boolean isSuccessEvent() {
        return orderEventType != null && 
               !orderEventType.name().contains("FAILED") && 
               !orderEventType.name().contains("CANCELLED") && 
               !orderEventType.name().contains("UNAVAILABLE");
    }
    
    /**
     * Check if this is a failure event
     */
    public boolean isFailureEvent() {
        return orderEventType != null && (
               orderEventType.name().contains("FAILED") || 
               orderEventType.name().contains("CANCELLED") || 
               orderEventType.name().contains("UNAVAILABLE")
        );
    }
} 