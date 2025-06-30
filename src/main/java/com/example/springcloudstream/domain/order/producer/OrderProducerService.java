package com.example.springcloudstream.domain.order.producer;

import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.domain.order.model.OrderEvent;
import com.example.springcloudstream.common.model.MessageHeaders;
import com.example.springcloudstream.common.util.MessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Service for producing order-related messages to various messaging destinations.
 * Handles order lifecycle events, status updates, and routing to appropriate channels.
 * 
 * @author Spring Cloud Stream Study
 * @version 1.0
 * @since 2024-01-01
 */
@Service
public class OrderProducerService {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducerService.class);

    @Autowired
    private StreamBridge streamBridge;

    /**
     * Send order creation event
     */
    public boolean sendOrderCreated(Order order) {
        try {
            logger.info("Sending order created event for order: {}", order.getId());
            
            OrderEvent event = OrderEvent.orderCreated(order);
            Message<OrderEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(MessageHeaders.EVENT_TYPE, "order-created")
                .setHeader(MessageHeaders.ORDER_ID, order.getId())
                .setHeader(MessageHeaders.USER_ID, order.getUserId())
                .setHeader(MessageHeaders.SOURCE_SERVICE, "order-service")
                .build();

            return streamBridge.send("order-events", message);
        } catch (Exception e) {
            logger.error("Failed to send order created event for order: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * Send order status update event
     */
    public boolean sendOrderStatusUpdate(Order order, Order.OrderStatus previousStatus) {
        try {
            logger.info("Sending order status update event for order: {} from {} to {}", 
                order.getId(), previousStatus, order.getStatus());
            
            OrderEvent event = OrderEvent.orderStatusUpdated(order, previousStatus);
            Message<OrderEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(MessageHeaders.EVENT_TYPE, "order-status-updated")
                .setHeader(MessageHeaders.ORDER_ID, order.getId())
                .setHeader(MessageHeaders.USER_ID, order.getUserId())
                .setHeader(MessageHeaders.PREVIOUS_STATUS, previousStatus.toString())
                .setHeader(MessageHeaders.NEW_STATUS, order.getStatus().toString())
                .setHeader(MessageHeaders.STATUS_CHANGED, !previousStatus.equals(order.getStatus()))
                .build();

            return streamBridge.send("order-status-events", message);
        } catch (Exception e) {
            logger.error("Failed to send order status update event for order: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * Send order to fulfillment system
     */
    public boolean sendOrderToFulfillment(Order order) {
        try {
            logger.info("Sending order to fulfillment: {}", order.getId());
            
            Message<Order> message = MessageBuilder
                .withPayload(order)
                .setHeader(MessageHeaders.EVENT_TYPE, "order-fulfillment")
                .setHeader(MessageHeaders.ORDER_ID, order.getId())
                .setHeader(MessageHeaders.USER_ID, order.getUserId())
                .setHeader(MessageHeaders.PRIORITY, determinePriority(order))
                .setHeader(MessageHeaders.ROUTING_KEY, "fulfillment")
                .build();

            return streamBridge.send("order-fulfillment", message);
        } catch (Exception e) {
            logger.error("Failed to send order to fulfillment: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * Send order cancellation event
     */
    public boolean sendOrderCancelled(Order order, String reason) {
        try {
            logger.info("Sending order cancellation event for order: {} with reason: {}", order.getId(), reason);
            
            OrderEvent event = OrderEvent.orderCancelled(order, reason);
            Message<OrderEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(MessageHeaders.EVENT_TYPE, "order-cancelled")
                .setHeader(MessageHeaders.ORDER_ID, order.getId())
                .setHeader(MessageHeaders.USER_ID, order.getUserId())
                .setHeader(MessageHeaders.CANCELLATION_REASON, reason)
                .build();

            return streamBridge.send("order-events", message);
        } catch (Exception e) {
            logger.error("Failed to send order cancellation event for order: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * Send order to analytics for reporting
     */
    public boolean sendOrderAnalytics(Order order) {
        try {
            logger.info("Sending order analytics data for order: {}", order.getId());
            
            Message<Order> message = MessageBuilder
                .withPayload(order)
                .setHeader(MessageHeaders.EVENT_TYPE, "order-analytics")
                .setHeader(MessageHeaders.ORDER_ID, order.getId())
                .setHeader(MessageHeaders.USER_ID, order.getUserId())
                .setHeader(MessageHeaders.ORDER_VALUE, order.getTotalAmount())
                .setHeader(MessageHeaders.ORDER_DATE, order.getOrderDate().toString())
                .build();

            return streamBridge.send("analytics-events", message);
        } catch (Exception e) {
            logger.error("Failed to send order analytics data for order: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * Send high-value order notification
     */
    public boolean sendHighValueOrderNotification(Order order) {
        try {
            logger.info("Sending high-value order notification for order: {} with amount: {}", 
                order.getId(), order.getTotalAmount());
            
            Message<Order> message = MessageBuilder
                .withPayload(order)
                .setHeader(MessageHeaders.EVENT_TYPE, "high-value-order")
                .setHeader(MessageHeaders.ORDER_ID, order.getId())
                .setHeader(MessageHeaders.USER_ID, order.getUserId())
                .setHeader(MessageHeaders.ORDER_VALUE, order.getTotalAmount())
                .setHeader(MessageHeaders.PRIORITY, "HIGH")
                .setHeader(MessageHeaders.NOTIFICATION_TYPE, "HIGH_VALUE_ORDER")
                .build();

            return streamBridge.send("notification-events", message);
        } catch (Exception e) {
            logger.error("Failed to send high-value order notification for order: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * Send order to multiple destinations (fan-out pattern)
     */
    public boolean sendOrderToMultipleDestinations(Order order) {
        try {
            logger.info("Sending order to multiple destinations: {}", order.getId());
            
            CompletableFuture<Boolean> analyticsResult = CompletableFuture.supplyAsync(() -> 
                sendOrderAnalytics(order));
            
            CompletableFuture<Boolean> fulfillmentResult = CompletableFuture.supplyAsync(() -> 
                sendOrderToFulfillment(order));
            
            // Send high-value notification if applicable
            CompletableFuture<Boolean> notificationResult = CompletableFuture.supplyAsync(() -> {
                if (order.getTotalAmount().compareTo(java.math.BigDecimal.valueOf(1000)) > 0) {
                    return sendHighValueOrderNotification(order);
                }
                return true;
            });

            // Wait for all to complete
            CompletableFuture.allOf(analyticsResult, fulfillmentResult, notificationResult).join();
            
            return analyticsResult.get() && fulfillmentResult.get() && notificationResult.get();
        } catch (Exception e) {
            logger.error("Failed to send order to multiple destinations: {}", order.getId(), e);
            return false;
        }
    }

    /**
     * Send order with retry mechanism
     */
    public boolean sendOrderWithRetry(Order order, int maxRetries) {
        return MessageUtils.sendWithRetry(
            () -> sendOrderCreated(order),
            maxRetries,
            "order-" + order.getId(),
            logger
        );
    }

    /**
     * Determine order priority based on business rules
     */
    private String determinePriority(Order order) {
        if (order.getTotalAmount().compareTo(java.math.BigDecimal.valueOf(1000)) > 0) {
            return "HIGH";
        } else if (order.getTotalAmount().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
} 