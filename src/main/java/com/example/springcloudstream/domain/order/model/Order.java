package com.example.springcloudstream.domain.order.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Model Class
 * 
 * This class represents an Order entity for demonstrating complex message structures.
 * It shows:
 * - Complex nested objects in messages
 * - Decimal handling (BigDecimal for monetary values)
 * - List/Collection handling in messages
 * - Different data types and their serialization
 * 
 * Used in scenarios like:
 * - Order processing workflows
 * - Event-driven microservices
 * - Message transformation examples
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /**
     * Order unique identifier
     * Used for message correlation and tracking
     */
    private String id; // Changed from orderId to id for consistency

    /**
     * User ID who placed the order
     * Foreign key reference for message routing
     */
    private String userId; // Changed from customerId to userId and from Long to String

    /**
     * List of items in the order
     * Demonstrates handling of collections in messages
     */
    private List<OrderItem> items;

    /**
     * Total order amount
     * Uses BigDecimal for precise monetary calculations
     */
    private BigDecimal totalAmount;

    /**
     * Order status (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED)
     * Used for state machine and workflow demonstrations
     */
    private OrderStatus status;

    /**
     * Shipping address
     * Demonstrates nested object handling
     */
    private Address shippingAddress;

    /**
     * Order creation timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime orderDate;

    /**
     * Expected delivery date
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedDeliveryDate;

    /**
     * Order priority (LOW, NORMAL, HIGH, URGENT)
     * Used for priority queue demonstrations
     */
    private OrderPriority priority;

    /**
     * Payment method used
     */
    private String paymentMethod;

    /**
     * Special delivery instructions
     */
    private String deliveryNotes;

    /**
     * Order description
     */
    private String description;

    /**
     * Last updated timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;

    /**
     * OrderItem nested class for demonstrating complex structures
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }

    /**
     * Address nested class for shipping information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }

    /**
     * Order Status enumeration
     */
    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        BACKORDERED, // Added BACKORDERED status
        PAID,        // Added PAID status  
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        REFUNDED
    }

    /**
     * Order Priority enumeration
     */
    public enum OrderPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    /**
     * Factory method to create a new order with current timestamp
     */
    public static Order createOrder(String orderId, Long customerId, List<OrderItem> items, 
                                   Address shippingAddress, String paymentMethod) {
        Order order = new Order();
        order.setId(orderId); // Updated to use setId
        order.setUserId(String.valueOf(customerId)); // Updated to use setUserId and convert to String
        order.setItems(items);
        order.setShippingAddress(shippingAddress);
        order.setPaymentMethod(paymentMethod);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        order.setPriority(OrderPriority.NORMAL);
        
        // Calculate total amount
        BigDecimal total = items.stream()
            .map(OrderItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        
        return order;
    }

    /**
     * Builder pattern support - static builder method
     */
    public static OrderBuilder builder() {
        return new OrderBuilder();
    }

    /**
     * Simple builder class for Order
     */
    public static class OrderBuilder {
        private Order order = new Order();

        public OrderBuilder id(String id) {
            order.setId(id);
            return this;
        }

        public OrderBuilder userId(String userId) {
            order.setUserId(userId);
            return this;
        }

        public OrderBuilder totalAmount(BigDecimal totalAmount) {
            order.setTotalAmount(totalAmount);
            return this;
        }

        public OrderBuilder description(String description) {
            order.setDescription(description);
            return this;
        }

        public OrderBuilder status(OrderStatus status) {
            order.setStatus(status);
            return this;
        }

        public OrderBuilder orderDate(LocalDateTime orderDate) {
            order.setOrderDate(orderDate);
            return this;
        }

        public Order build() {
            return order;
        }
    }
} 