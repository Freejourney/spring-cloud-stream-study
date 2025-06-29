package com.example.springcloudstream.domain.order.service;

import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.domain.order.model.OrderEvent;
import com.example.springcloudstream.domain.order.producer.OrderProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business service for order domain operations.
 * Handles order lifecycle management, validation, and business rules.
 * 
 * @author Spring Cloud Stream Study
 * @version 1.0
 * @since 2024-01-01
 */
@Service
public class OrderBusinessService {

    private static final Logger logger = LoggerFactory.getLogger(OrderBusinessService.class);

    // In-memory storage for demo purposes
    private final Map<String, Order> orderRepository = new ConcurrentHashMap<>();
    private final Map<String, OrderEvent> eventHistory = new ConcurrentHashMap<>();

    @Autowired
    private OrderProducerService orderProducerService;

    /**
     * Create a new order with validation
     */
    public Order createOrder(String orderId, String userId, BigDecimal totalAmount, String description) {
        logger.info("Creating new order: {} for user: {}", orderId, userId);

        // Validation
        validateOrderCreation(orderId, userId, totalAmount);

        // Create order
        Order order = Order.builder()
            .id(orderId)
            .userId(userId)
            .totalAmount(totalAmount)
            .description(description)
            .status(Order.OrderStatus.PENDING)
            .orderDate(LocalDateTime.now())
            .build();

        // Store order
        orderRepository.put(orderId, order);

        // Send order created event
        orderProducerService.sendOrderCreated(order);

        logger.info("Successfully created order: {}", orderId);
        return order;
    }

    /**
     * Update order status with business rules
     */
    public Order updateOrderStatus(String orderId, Order.OrderStatus newStatus) {
        logger.info("Updating order status: {} to {}", orderId, newStatus);

        Order order = orderRepository.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        Order.OrderStatus previousStatus = order.getStatus();
        
        // Validate status transition
        validateStatusTransition(previousStatus, newStatus);

        // Update status
        order.setStatus(newStatus);
        order.setLastUpdated(LocalDateTime.now());
        
        // Store updated order
        orderRepository.put(orderId, order);

        // Send status update event
        orderProducerService.sendOrderStatusUpdate(order, previousStatus);

        // Handle specific status changes
        handleStatusChange(order, previousStatus, newStatus);

        logger.info("Successfully updated order status: {} from {} to {}", orderId, previousStatus, newStatus);
        return order;
    }

    /**
     * Cancel order with reason
     */
    public Order cancelOrder(String orderId, String reason) {
        logger.info("Cancelling order: {} with reason: {}", orderId, reason);

        Order order = orderRepository.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        // Validate cancellation is allowed
        if (order.getStatus() == Order.OrderStatus.SHIPPED || 
            order.getStatus() == Order.OrderStatus.DELIVERED ||
            order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }

        Order.OrderStatus previousStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setLastUpdated(LocalDateTime.now());
        
        orderRepository.put(orderId, order);
        
        // Send cancellation event
        orderProducerService.sendOrderCancelled(order, reason);

        logger.info("Successfully cancelled order: {}", orderId);
        return order;
    }

    /**
     * Process order created event
     */
    public void processOrderCreated(Order order) {
        logger.info("Processing order created: {}", order.getId());
        
        // Inventory check
        if (checkInventoryAvailability(order)) {
            updateOrderStatus(order.getId(), Order.OrderStatus.CONFIRMED);
        } else {
            updateOrderStatus(order.getId(), Order.OrderStatus.BACKORDERED);
        }
    }

    /**
     * Process order created with correlation
     */
    public void processOrderCreatedWithCorrelation(Order order, String correlationId) {
        logger.info("Processing order created with correlation: {} - {}", order.getId(), correlationId);
        
        // Enhanced processing with correlation tracking
        processOrderCreated(order);
        
        // Log correlation for tracking
        logger.info("Order {} processed with correlation: {}", order.getId(), correlationId);
    }

    /**
     * Process order status update
     */
    public void processOrderStatusUpdate(Order order) {
        logger.info("Processing order status update: {} - {}", order.getId(), order.getStatus());
        
        // Business logic based on status
        switch (order.getStatus()) {
            case CONFIRMED:
                initiatePaymentProcessing(order);
                break;
            case PAID:
                sendOrderToFulfillment(order);
                break;
            case SHIPPED:
                sendShippingNotification(order);
                break;
            case DELIVERED:
                processOrderCompletion(order);
                break;
            default:
                logger.info("No specific processing for status: {}", order.getStatus());
        }
    }

    /**
     * Process order status update with history
     */
    public void processOrderStatusUpdateWithHistory(Order order, String previousStatus, String newStatus) {
        logger.info("Processing order status update with history: {} from {} to {}", 
            order.getId(), previousStatus, newStatus);
        
        // Record status change history
        recordStatusChangeHistory(order, previousStatus, newStatus);
        
        processOrderStatusUpdate(order);
    }

    /**
     * Process order cancellation
     */
    public void processOrderCancellation(Order order, Map<String, Object> eventData) {
        logger.info("Processing order cancellation: {}", order.getId());
        
        // Handle refund if payment was processed
        if (order.getStatus() == Order.OrderStatus.PAID) {
            initiateRefund(order);
        }
        
        // Release inventory
        releaseInventory(order);
        
        // Send cancellation notifications
        sendCancellationNotifications(order);
    }

    /**
     * Process order cancellation with reason
     */
    public void processOrderCancellationWithReason(Order order, String reason) {
        logger.info("Processing order cancellation with reason: {} - {}", order.getId(), reason);
        
        // Enhanced cancellation processing
        processOrderCancellation(order, Map.of("reason", reason));
        
        // Log detailed reason
        logger.info("Order {} cancelled with reason: {}", order.getId(), reason);
    }

    /**
     * Process high priority fulfillment
     */
    public void processHighPriorityFulfillment(Order order) {
        logger.info("Processing HIGH PRIORITY fulfillment for order: {}", order.getId());
        
        // Expedited processing
        updateOrderStatus(order.getId(), Order.OrderStatus.PROCESSING);
        
        // Send to expedited fulfillment queue
        orderProducerService.sendOrderToFulfillment(order);
        
        // Notify priority handling team
        notifyPriorityTeam(order);
    }

    /**
     * Process standard fulfillment
     */
    public void processStandardFulfillment(Order order) {
        logger.info("Processing STANDARD fulfillment for order: {}", order.getId());
        
        updateOrderStatus(order.getId(), Order.OrderStatus.PROCESSING);
        orderProducerService.sendOrderToFulfillment(order);
    }

    /**
     * Process order analytics
     */
    public void processOrderAnalytics(Order order) {
        logger.info("Processing order analytics for order: {}", order.getId());
        
        // Collect analytics data
        Map<String, Object> analyticsData = collectAnalyticsData(order);
        
        // Store analytics (would typically send to analytics service)
        logger.info("Analytics data collected for order {}: {}", order.getId(), analyticsData);
    }

    /**
     * Notify high value order
     */
    public void notifyHighValueOrder(Order order) {
        logger.info("Notifying high value order: {} with amount: {}", order.getId(), order.getTotalAmount());
        
        // Send notifications to management
        sendHighValueNotifications(order);
        
        // Apply VIP processing
        applyVipProcessing(order);
    }

    /**
     * Handle failed order processing
     */
    public void handleFailedOrder(OrderEvent orderEvent, String failureReason) {
        logger.warn("Handling failed order processing: {} - {}", orderEvent.getOrderId(), failureReason);
        
        // Record failure
        recordFailure(orderEvent, failureReason);
        
        // Determine recovery action
        if (isRecoverable(failureReason)) {
            scheduleRetry(orderEvent);
        } else {
            escalateFailure(orderEvent, failureReason);
        }
    }

    /**
     * Alert critical failure
     */
    public void alertCriticalFailure(OrderEvent orderEvent, Exception exception) {
        logger.error("CRITICAL FAILURE for order: {} - {}", orderEvent.getOrderId(), exception.getMessage());
        
        // Send critical alerts
        sendCriticalAlert(orderEvent, exception);
        
        // Record in failure database
        recordCriticalFailure(orderEvent, exception);
    }

    // Private helper methods

    private void validateOrderCreation(String orderId, String userId, BigDecimal totalAmount) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }
        if (orderRepository.containsKey(orderId)) {
            throw new IllegalArgumentException("Order already exists: " + orderId);
        }
    }

    private void validateStatusTransition(Order.OrderStatus from, Order.OrderStatus to) {
        // Define valid transitions
        boolean isValidTransition = switch (from) {
            case PENDING -> to == Order.OrderStatus.CONFIRMED || to == Order.OrderStatus.CANCELLED || to == Order.OrderStatus.BACKORDERED;
            case CONFIRMED -> to == Order.OrderStatus.PAID || to == Order.OrderStatus.CANCELLED;
            case PAID -> to == Order.OrderStatus.PROCESSING || to == Order.OrderStatus.CANCELLED;
            case PROCESSING -> to == Order.OrderStatus.SHIPPED || to == Order.OrderStatus.CANCELLED;
            case SHIPPED -> to == Order.OrderStatus.DELIVERED;
            case BACKORDERED -> to == Order.OrderStatus.CONFIRMED || to == Order.OrderStatus.CANCELLED;
            default -> false;
        };

        if (!isValidTransition) {
            throw new IllegalStateException("Invalid status transition from " + from + " to " + to);
        }
    }

    private void handleStatusChange(Order order, Order.OrderStatus previousStatus, Order.OrderStatus newStatus) {
        // Handle specific business logic for status changes
        if (newStatus == Order.OrderStatus.CONFIRMED) {
            orderProducerService.sendOrderAnalytics(order);
        } else if (newStatus == Order.OrderStatus.PAID && 
                   order.getTotalAmount().compareTo(BigDecimal.valueOf(1000)) > 0) {
            orderProducerService.sendHighValueOrderNotification(order);
        }
    }

    private boolean checkInventoryAvailability(Order order) {
        // Simulate inventory check
        return Math.random() > 0.1; // 90% availability
    }

    private void initiatePaymentProcessing(Order order) {
        logger.info("Initiating payment processing for order: {}", order.getId());
        // Simulate payment processing
    }

    private void sendOrderToFulfillment(Order order) {
        logger.info("Sending order to fulfillment: {}", order.getId());
        orderProducerService.sendOrderToFulfillment(order);
    }

    private void sendShippingNotification(Order order) {
        logger.info("Sending shipping notification for order: {}", order.getId());
        // Send shipping notification
    }

    private void processOrderCompletion(Order order) {
        logger.info("Processing order completion: {}", order.getId());
        // Handle order completion logic
    }

    private void recordStatusChangeHistory(Order order, String previousStatus, String newStatus) {
        logger.info("Recording status change history for order: {} from {} to {}", 
            order.getId(), previousStatus, newStatus);
        // Record in history
    }

    private void initiateRefund(Order order) {
        logger.info("Initiating refund for order: {}", order.getId());
        // Handle refund logic
    }

    private void releaseInventory(Order order) {
        logger.info("Releasing inventory for order: {}", order.getId());
        // Release inventory
    }

    private void sendCancellationNotifications(Order order) {
        logger.info("Sending cancellation notifications for order: {}", order.getId());
        // Send notifications
    }

    private void notifyPriorityTeam(Order order) {
        logger.info("Notifying priority team for order: {}", order.getId());
        // Notify priority team
    }

    private Map<String, Object> collectAnalyticsData(Order order) {
        return Map.of(
            "orderId", order.getId(),
            "userId", order.getUserId(),
            "amount", order.getTotalAmount(),
            "status", order.getStatus(),
            "orderDate", order.getOrderDate(),
            "processingTime", calculateProcessingTime(order)
        );
    }

    private long calculateProcessingTime(Order order) {
        return java.time.Duration.between(order.getOrderDate(), 
            order.getLastUpdated() != null ? order.getLastUpdated() : LocalDateTime.now()).toMinutes();
    }

    private void sendHighValueNotifications(Order order) {
        logger.info("Sending high value notifications for order: {}", order.getId());
        // Send notifications
    }

    private void applyVipProcessing(Order order) {
        logger.info("Applying VIP processing for order: {}", order.getId());
        // Apply VIP processing rules
    }

    private void recordFailure(OrderEvent orderEvent, String failureReason) {
        logger.info("Recording failure for order: {} - {}", orderEvent.getOrderId(), failureReason);
        // Record failure
    }

    private boolean isRecoverable(String failureReason) {
        return !failureReason.contains("CRITICAL") && !failureReason.contains("PERMANENT");
    }

    private void scheduleRetry(OrderEvent orderEvent) {
        logger.info("Scheduling retry for order: {}", orderEvent.getOrderId());
        // Schedule retry
    }

    private void escalateFailure(OrderEvent orderEvent, String failureReason) {
        logger.warn("Escalating failure for order: {} - {}", orderEvent.getOrderId(), failureReason);
        // Escalate failure
    }

    private void sendCriticalAlert(OrderEvent orderEvent, Exception exception) {
        logger.error("Sending critical alert for order: {}", orderEvent.getOrderId());
        // Send critical alert
    }

    private void recordCriticalFailure(OrderEvent orderEvent, Exception exception) {
        logger.error("Recording critical failure for order: {}", orderEvent.getOrderId());
        // Record critical failure
    }

    // Public getters for repository access (for testing/demo)
    public Order getOrder(String orderId) {
        return orderRepository.get(orderId);
    }

    public Map<String, Order> getAllOrders() {
        return new ConcurrentHashMap<>(orderRepository);
    }
} 