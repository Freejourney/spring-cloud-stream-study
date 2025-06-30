package com.example.springcloudstream.domain.order.workflow;

import com.example.springcloudstream.domain.order.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Order Processing Service
 * 
 * This service demonstrates complex event-driven workflows with Spring Cloud Stream:
 * - Order state machine and lifecycle management
 * - Complex message transformation and enrichment
 * - Event choreography and saga patterns
 * - Business rule validation and processing
 * - Multi-step workflow orchestration
 * 
 * Workflow Example:
 * 1. Order Created -> Validation -> Payment -> Inventory -> Shipping -> Delivery
 * 2. Each step can succeed or fail, triggering appropriate events
 * 3. Compensating actions for rollback scenarios
 * 
 * @Slf4j - Lombok annotation for logging
 * @Service - Spring stereotype annotation for service layer components
 */
@Slf4j
@Service
public class OrderProcessingService {

    private final StreamBridge streamBridge;

    /**
     * Constructor injection of StreamBridge
     * 
     * @param streamBridge Spring Cloud Stream bridge for sending messages
     */
    public OrderProcessingService(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Order validation consumer
     * Validates incoming orders and routes them to next step or rejection
     * 
     * @return Consumer function for validating orders
     */
    @Bean
    public Consumer<Order> orderValidator() {
        return order -> {
            try {
                log.info("🔍 Validating order: {} for customer: {}", 
                        order.getId(), order.getUserId());

                // Business validation logic
                boolean isValid = validateOrder(order);
                
                if (isValid) {
                    // Update order status and send to payment processing
                    order.setStatus(Order.OrderStatus.CONFIRMED);
                    sendOrderEvent(order, "order-validated", "payment-processor");
                    log.info("✅ Order validated successfully: {}", order.getId());
                } else {
                    // Send to rejection handling
                    order.setStatus(Order.OrderStatus.CANCELLED);
                    sendOrderEvent(order, "order-rejected", "rejection-handler");
                    log.warn("❌ Order validation failed: {}", order.getId());
                }
                
            } catch (Exception e) {
                log.error("💥 Error validating order: {}", order.getId(), e);
                order.setStatus(Order.OrderStatus.CANCELLED);
                sendOrderEvent(order, "order-validation-error", "error-handler");
                throw new RuntimeException("Order validation failed", e);
            }
        };
    }

    /**
     * Payment processing consumer
     * Processes payment for validated orders
     * 
     * @return Consumer function for processing payments
     */
    @Bean
    public Consumer<Order> paymentProcessor() {
        return order -> {
            try {
                log.info("💳 Processing payment for order: {} amount: {}", 
                        order.getId(), order.getTotalAmount());

                // Simulate payment processing
                boolean paymentSuccessful = processPayment(order);
                
                if (paymentSuccessful) {
                    order.setStatus(Order.OrderStatus.PROCESSING);
                    sendOrderEvent(order, "payment-successful", "inventory-manager");
                    log.info("✅ Payment processed successfully for order: {}", order.getId());
                } else {
                    order.setStatus(Order.OrderStatus.CANCELLED);
                    sendOrderEvent(order, "payment-failed", "payment-failure-handler");
                    log.warn("❌ Payment failed for order: {}", order.getId());
                }
                
            } catch (Exception e) {
                log.error("💥 Error processing payment for order: {}", order.getId(), e);
                order.setStatus(Order.OrderStatus.CANCELLED);
                sendOrderEvent(order, "payment-error", "error-handler");
                throw new RuntimeException("Payment processing failed", e);
            }
        };
    }

    /**
     * Inventory management consumer
     * Checks and reserves inventory for paid orders
     * 
     * @return Consumer function for managing inventory
     */
    @Bean
    public Consumer<Order> inventoryManager() {
        return order -> {
            try {
                log.info("📦 Checking inventory for order: {} with {} items", 
                        order.getId(), order.getItems().size());

                // Check inventory availability
                boolean inventoryAvailable = checkInventory(order);
                
                if (inventoryAvailable) {
                    // Reserve inventory
                    reserveInventory(order);
                    sendOrderEvent(order, "inventory-reserved", "shipping-coordinator");
                    log.info("✅ Inventory reserved for order: {}", order.getId());
                } else {
                    // Trigger compensation - refund payment
                    sendOrderEvent(order, "inventory-unavailable", "payment-refund-handler");
                    log.warn("❌ Insufficient inventory for order: {}", order.getId());
                }
                
            } catch (Exception e) {
                log.error("💥 Error managing inventory for order: {}", order.getId(), e);
                sendOrderEvent(order, "inventory-error", "error-handler");
                throw new RuntimeException("Inventory management failed", e);
            }
        };
    }

    /**
     * Shipping coordinator consumer
     * Coordinates shipping for orders with reserved inventory
     * 
     * @return Consumer function for coordinating shipping
     */
    @Bean
    public Consumer<Order> shippingCoordinator() {
        return order -> {
            try {
                log.info("🚚 Coordinating shipping for order: {} to {}", 
                        order.getId(), order.getShippingAddress().getCity());

                // Calculate shipping details
                LocalDateTime estimatedDelivery = calculateDeliveryDate(order);
                order.setExpectedDeliveryDate(estimatedDelivery);
                order.setStatus(Order.OrderStatus.SHIPPED);
                
                sendOrderEvent(order, "order-shipped", "delivery-tracker");
                sendNotification(order, "shipping-notification");
                
                log.info("✅ Shipping coordinated for order: {} - Expected delivery: {}", 
                        order.getId(), estimatedDelivery);
                
            } catch (Exception e) {
                log.error("💥 Error coordinating shipping for order: {}", order.getId(), e);
                sendOrderEvent(order, "shipping-error", "error-handler");
                throw new RuntimeException("Shipping coordination failed", e);
            }
        };
    }

    /**
     * Order enrichment function
     * Enriches orders with additional data and business rules
     * 
     * @return Function for enriching order data
     */
    @Bean
    public Function<Order, Order> orderEnricher() {
        return order -> {
            try {
                log.info("🔧 Enriching order: {}", order.getId());

                // Create enriched copy
                Order enrichedOrder = new Order();
                enrichedOrder.setId(order.getId());
                enrichedOrder.setUserId(order.getUserId());
                enrichedOrder.setItems(order.getItems());
                enrichedOrder.setTotalAmount(order.getTotalAmount());
                enrichedOrder.setStatus(order.getStatus());
                enrichedOrder.setShippingAddress(order.getShippingAddress());
                enrichedOrder.setOrderDate(order.getOrderDate());
                enrichedOrder.setPaymentMethod(order.getPaymentMethod());
                enrichedOrder.setDeliveryNotes(order.getDeliveryNotes());

                // Add enrichment data
                enrichedOrder.setPriority(calculatePriority(order));
                enrichedOrder.setExpectedDeliveryDate(calculateDeliveryDate(order));
                
                // Add business rules
                if (order.getTotalAmount().compareTo(new BigDecimal("1000")) > 0) {
                    enrichedOrder.setDeliveryNotes(
                        (enrichedOrder.getDeliveryNotes() != null ? enrichedOrder.getDeliveryNotes() + "; " : "") +
                        "HIGH VALUE ORDER - Signature required"
                    );
                }

                log.info("✅ Order enriched: {} - Priority: {}", 
                        enrichedOrder.getId(), enrichedOrder.getPriority());
                
                return enrichedOrder;
                
            } catch (Exception e) {
                log.error("💥 Error enriching order: {}", order.getId(), e);
                throw new RuntimeException("Order enrichment failed", e);
            }
        };
    }

    /**
     * Order aggregator function
     * Aggregates multiple order events into summary
     * 
     * @return Function for aggregating order data
     */
    @Bean
    public Function<Message<Order>, String> orderAggregator() {
        return message -> {
            try {
                Order order = message.getPayload();
                String eventType = (String) message.getHeaders().get("event-type");
                
                log.info("📊 Aggregating order event: {} for order: {}", eventType, order.getId());

                // Create aggregation summary
                String summary = String.format(
                    "Order Summary: ID=%s, Customer=%s, Status=%s, Amount=%s, Event=%s, Timestamp=%s",
                    order.getId(),
                    order.getUserId(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    eventType,
                    LocalDateTime.now()
                );

                log.info("✅ Order aggregation completed: {}", order.getId());
                return summary;
                
            } catch (Exception e) {
                log.error("💥 Error aggregating order data", e);
                throw new RuntimeException("Order aggregation failed", e);
            }
        };
    }

    /**
     * Send order-related event to specific destination
     */
    private void sendOrderEvent(Order order, String eventType, String destination) {
        Message<Order> message = MessageBuilder
            .withPayload(order)
            .setHeader("event-type", eventType)
                            .setHeader("order-id", order.getId())
                .setHeader("customer-id", order.getUserId())
            .setHeader("order-status", order.getStatus().toString())
            .setHeader("destination", destination)
            .build();

        streamBridge.send("order-events-out-0", message);
        log.debug("📤 Sent order event: {} for order: {}", eventType, order.getId());
    }

    /**
     * Send notification for order updates
     */
    private void sendNotification(Order order, String notificationType) {
        Message<Order> notification = MessageBuilder
            .withPayload(order)
            .setHeader("notification-type", notificationType)
                            .setHeader("customer-id", order.getUserId())
                .setHeader("order-id", order.getId())
            .build();

        streamBridge.send("order-notifications-out-0", notification);
        log.debug("🔔 Sent notification: {} for order: {}", notificationType, order.getId());
    }

    /**
     * Validate order business rules
     */
    private boolean validateOrder(Order order) {
        // Basic validation rules
        if (order.getItems() == null || order.getItems().isEmpty()) {
                        log.warn("Order validation failed: No items in order {}", order.getId());
            return false;
        }
        
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Order validation failed: Invalid total amount for order {}", order.getId());
            return false;
        }
        
        if (order.getShippingAddress() == null) {
            log.warn("Order validation failed: No shipping address for order {}", order.getId());
            return false;
        }

        // Additional business rules can be added here
        return true;
    }

    /**
     * Simulate payment processing
     */
    private boolean processPayment(Order order) {
        // Simulate payment processing delay
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Deterministic payment logic for testing
        // Payment fails for negative amounts or invalid payment methods
        if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // Payment fails for orders with no payment method or invalid payment method
        if (order.getPaymentMethod() == null || order.getPaymentMethod().isEmpty()) {
            return false;
        }
        
        // Payment fails for specific test scenarios (orders with "FAIL" in the ID)
        if (order.getId() != null && order.getId().contains("FAIL")) {
            return false;
        }

        // Otherwise payment succeeds
        return true;
    }

    /**
     * Check inventory availability
     */
    private boolean checkInventory(Order order) {
        // Simulate inventory check
        return order.getItems().stream()
            .allMatch(item -> Math.random() > 0.1); // 90% availability rate
    }

    /**
     * Reserve inventory for order
     */
    private void reserveInventory(Order order) {
        // Simulate inventory reservation
        log.debug("Reserving inventory for order: {}", order.getId());
    }

    /**
     * Calculate order priority based on business rules
     */
    private Order.OrderPriority calculatePriority(Order order) {
        BigDecimal amount = order.getTotalAmount();
        
        if (amount.compareTo(new BigDecimal("2000")) > 0) {
            return Order.OrderPriority.URGENT;
        } else if (amount.compareTo(new BigDecimal("1000")) > 0) {
            return Order.OrderPriority.HIGH;
        } else if (amount.compareTo(new BigDecimal("100")) > 0) {
            return Order.OrderPriority.NORMAL;
        } else {
            return Order.OrderPriority.LOW;
        }
    }

    /**
     * Calculate estimated delivery date
     */
    private LocalDateTime calculateDeliveryDate(Order order) {
        // Business logic for delivery calculation
        int deliveryDays = switch (order.getPriority()) {
            case URGENT -> 1;
            case HIGH -> 2;
            case NORMAL -> 3;
            case LOW -> 5;
        };
        
        return LocalDateTime.now().plusDays(deliveryDays);
    }
} 