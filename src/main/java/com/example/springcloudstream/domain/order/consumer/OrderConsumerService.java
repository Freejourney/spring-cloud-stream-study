package com.example.springcloudstream.domain.order.consumer;

import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.domain.order.model.OrderEvent;
import com.example.springcloudstream.domain.order.service.OrderBusinessService;
import com.example.springcloudstream.common.model.MessageHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service for consuming order-related messages from various messaging sources.
 * Handles order lifecycle events, status updates, and business logic execution.
 * 
 * @author Spring Cloud Stream Study
 * @version 1.0
 * @since 2024-01-01
 */
@Service
public class OrderConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumerService.class);

    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger fulfillmentCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    @Autowired
    private OrderBusinessService orderBusinessService;

    /**
     * Basic order event consumer
     */
    @Bean
    public Consumer<OrderEvent> orderConsumer() {
        return orderEvent -> {
            try {
                logger.info("Processing order event: {} for order: {}", 
                    orderEvent.getEventType(), orderEvent.getOrderId());
                
                processOrderEvent(orderEvent);
                processedCount.incrementAndGet();
                
                logger.info("Successfully processed order event: {}", orderEvent.getEventType());
            } catch (Exception e) {
                logger.error("Failed to process order event: {}", orderEvent.getEventType(), e);
                errorCount.incrementAndGet();
                throw e; // Re-throw to trigger retry mechanism
            }
        };
    }

    /**
     * Order consumer with message headers
     */
    @Bean
    public Consumer<Message<OrderEvent>> orderWithHeadersConsumer() {
        return message -> {
            try {
                OrderEvent orderEvent = message.getPayload();
                String eventType = (String) message.getHeaders().get(MessageHeaders.EVENT_TYPE);
                String orderId = (String) message.getHeaders().get(MessageHeaders.ORDER_ID);
                String userId = (String) message.getHeaders().get(MessageHeaders.USER_ID);
                
                logger.info("Processing order event with headers - Type: {}, Order ID: {}, User ID: {}", 
                    eventType, orderId, userId);
                
                // Process based on event type
                switch (eventType) {
                    case "order-created":
                        handleOrderCreated(orderEvent, message);
                        break;
                    case "order-status-updated":
                        handleOrderStatusUpdated(orderEvent, message);
                        break;
                    case "order-cancelled":
                        handleOrderCancelled(orderEvent, message);
                        break;
                    default:
                        logger.warn("Unknown order event type: {}", eventType);
                }
                
                processedCount.incrementAndGet();
            } catch (Exception e) {
                logger.error("Failed to process order event with headers", e);
                errorCount.incrementAndGet();
                throw e;
            }
        };
    }

    /**
     * Order fulfillment consumer
     */
    @Bean
    public Consumer<Message<Order>> orderFulfillmentConsumer() {
        return message -> {
            try {
                Order order = message.getPayload();
                String priority = (String) message.getHeaders().get(MessageHeaders.PRIORITY);
                
                logger.info("Processing order fulfillment for order: {} with priority: {}", 
                    order.getId(), priority);
                
                // Process order fulfillment based on priority
                if ("HIGH".equals(priority)) {
                    processHighPriorityOrder(order);
                } else {
                    processStandardOrder(order);
                }
                
                fulfillmentCount.incrementAndGet();
                logger.info("Successfully processed order fulfillment for order: {}", order.getId());
            } catch (Exception e) {
                logger.error("Failed to process order fulfillment", e);
                errorCount.incrementAndGet();
                throw e;
            }
        };
    }

    /**
     * High-value order notification consumer
     */
    @Bean
    public Consumer<Message<Order>> highValueOrderConsumer() {
        return message -> {
            try {
                Order order = message.getPayload();
                String notificationType = (String) message.getHeaders().get(MessageHeaders.NOTIFICATION_TYPE);
                
                logger.info("Processing high-value order notification for order: {} with type: {}", 
                    order.getId(), notificationType);
                
                // Send notifications to relevant stakeholders
                orderBusinessService.notifyHighValueOrder(order);
                
                logger.info("Successfully processed high-value order notification for order: {}", order.getId());
            } catch (Exception e) {
                logger.error("Failed to process high-value order notification", e);
                errorCount.incrementAndGet();
                throw e;
            }
        };
    }

    /**
     * Order analytics consumer
     */
    @Bean
    public Consumer<Message<Order>> orderAnalyticsConsumer() {
        return message -> {
            try {
                Order order = message.getPayload();
                
                logger.info("Processing order analytics for order: {}", order.getId());
                
                // Process analytics data
                orderBusinessService.processOrderAnalytics(order);
                
                logger.info("Successfully processed order analytics for order: {}", order.getId());
            } catch (Exception e) {
                logger.error("Failed to process order analytics", e);
                errorCount.incrementAndGet();
                // Don't re-throw for analytics - it's not critical
            }
        };
    }

    /**
     * Dead letter queue consumer for failed orders
     */
    @Bean
    public Consumer<Message<OrderEvent>> orderDlqConsumer() {
        return message -> {
            try {
                OrderEvent orderEvent = message.getPayload();
                String failureReason = (String) message.getHeaders().get(MessageHeaders.FAILURE_REASON);
                Integer retryCount = (Integer) message.getHeaders().get(MessageHeaders.RETRY_COUNT);
                
                logger.warn("Processing order from DLQ - Order: {}, Failure: {}, Retries: {}", 
                    orderEvent.getOrderId(), failureReason, retryCount);
                
                // Handle failed order processing
                orderBusinessService.handleFailedOrder(orderEvent, failureReason);
                
                logger.info("Successfully processed order from DLQ: {}", orderEvent.getOrderId());
            } catch (Exception e) {
                logger.error("Failed to process order from DLQ", e);
                // Final failure - log and alert
                orderBusinessService.alertCriticalFailure(message.getPayload(), e);
            }
        };
    }

    /**
     * Process order event based on type
     */
    private void processOrderEvent(OrderEvent orderEvent) {
        switch (orderEvent.getEventType()) {
            case "order-created":
                orderBusinessService.processOrderCreated(orderEvent.getOrder());
                break;
            case "order-status-updated":
                orderBusinessService.processOrderStatusUpdate(orderEvent.getOrder());
                break;
            case "order-cancelled":
                orderBusinessService.processOrderCancellation(orderEvent.getOrder(), orderEvent.getEventData());
                break;
            default:
                logger.warn("Unknown order event type: {}", orderEvent.getEventType());
        }
    }

    /**
     * Handle order created event
     */
    private void handleOrderCreated(OrderEvent orderEvent, Message<OrderEvent> message) {
        String correlationId = (String) message.getHeaders().get(MessageHeaders.CORRELATION_ID);
        orderBusinessService.processOrderCreatedWithCorrelation(orderEvent.getOrder(), correlationId);
    }

    /**
     * Handle order status updated event
     */
    private void handleOrderStatusUpdated(OrderEvent orderEvent, Message<OrderEvent> message) {
        String previousStatus = (String) message.getHeaders().get(MessageHeaders.PREVIOUS_STATUS);
        String newStatus = (String) message.getHeaders().get(MessageHeaders.NEW_STATUS);
        
        orderBusinessService.processOrderStatusUpdateWithHistory(
            orderEvent.getOrder(), previousStatus, newStatus);
    }

    /**
     * Handle order cancelled event
     */
    private void handleOrderCancelled(OrderEvent orderEvent, Message<OrderEvent> message) {
        String cancellationReason = (String) message.getHeaders().get(MessageHeaders.CANCELLATION_REASON);
        orderBusinessService.processOrderCancellationWithReason(
            orderEvent.getOrder(), cancellationReason);
    }

    /**
     * Process high priority order fulfillment
     */
    private void processHighPriorityOrder(Order order) {
        logger.info("Processing HIGH PRIORITY order fulfillment: {}", order.getId());
        orderBusinessService.processHighPriorityFulfillment(order);
    }

    /**
     * Process standard order fulfillment
     */
    private void processStandardOrder(Order order) {
        logger.info("Processing STANDARD order fulfillment: {}", order.getId());
        orderBusinessService.processStandardFulfillment(order);
    }

    // Metrics and status methods
    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getFulfillmentCount() {
        return fulfillmentCount.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public void resetCounts() {
        processedCount.set(0);
        fulfillmentCount.set(0);
        errorCount.set(0);
    }

    public double getSuccessRate() {
        int total = processedCount.get() + errorCount.get();
        return total == 0 ? 0.0 : (double) processedCount.get() / total * 100.0;
    }
} 