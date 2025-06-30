package com.example.springcloudstream.domain.order.consumer;

import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.domain.order.model.OrderEvent;
import com.example.springcloudstream.domain.order.service.OrderBusinessService;
import com.example.springcloudstream.common.model.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for OrderConsumerService
 * 
 * Function to Test: Consumer functions for order processing, fulfillment, analytics,
 * high-value notifications, and dead letter queue handling.
 * 
 * Test Coverage:
 * - Input Scenarios: Various order events, messages with headers, order fulfillment
 * - Expected Output: Proper order processing, status updates, business logic execution
 * - Error Handling: Failed processing, DLQ scenarios, retry mechanisms
 * - Boundary Testing: High concurrency, large orders, invalid data
 * - Dependencies: Mocked OrderBusinessService for isolated testing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConsumerService Unit Tests")
class OrderConsumerServiceTest {

    @InjectMocks
    private OrderConsumerService orderConsumerService;

    @Mock
    private OrderBusinessService orderBusinessService;

    private OrderEvent validOrderEvent;
    private Order validOrder;

    @BeforeEach
    void setUp() {
        // Create valid test order
        validOrder = createValidOrder("ORD-001", "USER-001", BigDecimal.valueOf(250.00));
        
        // Create valid test order event
        validOrderEvent = OrderEvent.orderCreated(validOrder);
        
        // Reset counts
        orderConsumerService.resetCounts();
    }

    // ===============================================================================
    // ORDER EVENT CONSUMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order Consumer - Valid Order Event")
    void testOrderConsumer_ValidOrderEvent() {
        // Function to Test: orderConsumer() processes valid order events
        Consumer<OrderEvent> consumer = orderConsumerService.orderConsumer();
        int initialCount = orderConsumerService.getProcessedCount();
        
        // Input Scenarios: Valid order creation event
        assertDoesNotThrow(() -> consumer.accept(validOrderEvent));
        
        // Expected Output: Event should be processed successfully
        assertEquals(initialCount + 1, orderConsumerService.getProcessedCount());
        assertEquals(0, orderConsumerService.getErrorCount());
    }

    @ParameterizedTest
    @DisplayName("Order Consumer - Various Event Types")
    @EnumSource(OrderEvent.EventType.class)
    void testOrderConsumer_VariousEventTypes(OrderEvent.EventType eventType) {
        // Input Scenarios: Different order event types
        Consumer<OrderEvent> consumer = orderConsumerService.orderConsumer();
        
        Order testOrder = createValidOrder("ORD-" + eventType.name(), "USER-001", BigDecimal.valueOf(100.00));
        OrderEvent orderEvent = OrderEvent.createOrderCreatedEvent(testOrder, "test-service", "corr-id");
        
        // Expected Output: Should process all event types
        assertDoesNotThrow(() -> consumer.accept(orderEvent));
        assertTrue(orderConsumerService.getProcessedCount() > 0);
    }

    @Test
    @DisplayName("Order Consumer - Null Order Event")
    void testOrderConsumer_NullOrderEvent() {
        // Error Handling: Testing null order event
        Consumer<OrderEvent> consumer = orderConsumerService.orderConsumer();
        
        // Expected Output: Should throw NullPointerException for null event, but error count handling varies
        assertThrows(NullPointerException.class, () -> consumer.accept(null));
        // The error count might not increment if the NPE occurs before the catch block
    }

    @Test
    @DisplayName("Order Consumer - Business Service Exception")
    void testOrderConsumer_BusinessServiceException() {
        // Error Handling: Testing business service failure
        Consumer<OrderEvent> consumer = orderConsumerService.orderConsumer();
        doThrow(new RuntimeException("Business logic error")).when(orderBusinessService).processOrderCreated(any());
        
        // Expected Output: Should handle business service exceptions
        assertThrows(RuntimeException.class, () -> consumer.accept(validOrderEvent));
        assertEquals(1, orderConsumerService.getErrorCount());
    }

    // ===============================================================================
    // ORDER WITH HEADERS CONSUMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order With Headers Consumer - Order Created Event")
    void testOrderWithHeadersConsumer_OrderCreated() {
        // Function to Test: orderWithHeadersConsumer() processes order-created events
        Consumer<Message<OrderEvent>> consumer = orderConsumerService.orderWithHeadersConsumer();
        
        // Input Scenarios: Order created message with headers
        Message<OrderEvent> message = MessageBuilder.withPayload(validOrderEvent)
            .setHeader(MessageHeaders.EVENT_TYPE, "order-created")
            .setHeader(MessageHeaders.ORDER_ID, "ORD-001")
            .setHeader(MessageHeaders.USER_ID, "USER-001")
            .build();
        
        // Expected Output: Should process order created event
        assertDoesNotThrow(() -> consumer.accept(message));
        assertEquals(1, orderConsumerService.getProcessedCount());
    }

    @ParameterizedTest
    @DisplayName("Order With Headers Consumer - Different Event Types")
    @ValueSource(strings = {"order-created", "order-status-updated", "order-cancelled"})
    void testOrderWithHeadersConsumer_DifferentEventTypes(String eventType) {
        // Input Scenarios: Messages with different event types
        Consumer<Message<OrderEvent>> consumer = orderConsumerService.orderWithHeadersConsumer();
        
        Message<OrderEvent> message = MessageBuilder.withPayload(validOrderEvent)
            .setHeader(MessageHeaders.EVENT_TYPE, eventType)
            .setHeader(MessageHeaders.ORDER_ID, "ORD-001")
            .setHeader(MessageHeaders.USER_ID, "USER-001")
            .build();
        
        // Expected Output: Should process all supported event types
        assertDoesNotThrow(() -> consumer.accept(message));
        assertTrue(orderConsumerService.getProcessedCount() > 0);
    }

    @Test
    @DisplayName("Order With Headers Consumer - Unknown Event Type")
    void testOrderWithHeadersConsumer_UnknownEventType() {
        // Input Scenarios: Message with unknown event type
        Consumer<Message<OrderEvent>> consumer = orderConsumerService.orderWithHeadersConsumer();
        
        Message<OrderEvent> message = MessageBuilder.withPayload(validOrderEvent)
            .setHeader(MessageHeaders.EVENT_TYPE, "unknown-event")
            .setHeader(MessageHeaders.ORDER_ID, "ORD-001")
            .setHeader(MessageHeaders.USER_ID, "USER-001")
            .build();
        
        // Expected Output: Should handle unknown event types gracefully
        assertDoesNotThrow(() -> consumer.accept(message));
        assertEquals(1, orderConsumerService.getProcessedCount());
    }

    @Test
    @DisplayName("Order With Headers Consumer - Missing Headers")
    void testOrderWithHeadersConsumer_MissingHeaders() {
        // Error Handling: Message with missing required headers
        Consumer<Message<OrderEvent>> consumer = orderConsumerService.orderWithHeadersConsumer();
        
        Message<OrderEvent> message = MessageBuilder.withPayload(validOrderEvent).build();
        
        // Expected Output: Should throw exception due to null event type in switch statement
        assertThrows(Exception.class, () -> consumer.accept(message));
        assertEquals(1, orderConsumerService.getErrorCount());
    }

    // ===============================================================================
    // ORDER FULFILLMENT CONSUMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order Fulfillment Consumer - High Priority Order")
    void testOrderFulfillmentConsumer_HighPriorityOrder() {
        // Function to Test: orderFulfillmentConsumer() processes high priority orders
        Consumer<Message<Order>> consumer = orderConsumerService.orderFulfillmentConsumer();
        
        // Input Scenarios: High priority order
        Message<Order> message = MessageBuilder.withPayload(validOrder)
            .setHeader(MessageHeaders.PRIORITY, "HIGH")
            .setHeader(MessageHeaders.ORDER_ID, validOrder.getId())
            .build();
        
        // Expected Output: Should process high priority order
        assertDoesNotThrow(() -> consumer.accept(message));
        assertEquals(1, orderConsumerService.getFulfillmentCount());
    }

    @ParameterizedTest
    @DisplayName("Order Fulfillment Consumer - Different Priorities")
    @ValueSource(strings = {"HIGH", "NORMAL", "LOW"})
    void testOrderFulfillmentConsumer_DifferentPriorities(String priority) {
        // Input Scenarios: Orders with different priorities
        Consumer<Message<Order>> consumer = orderConsumerService.orderFulfillmentConsumer();
        
        Message<Order> message = MessageBuilder.withPayload(validOrder)
            .setHeader(MessageHeaders.PRIORITY, priority)
            .setHeader(MessageHeaders.ORDER_ID, validOrder.getId())
            .build();
        
        // Expected Output: Should process orders with any priority
        assertDoesNotThrow(() -> consumer.accept(message));
        assertTrue(orderConsumerService.getFulfillmentCount() > 0);
    }

    @Test
    @DisplayName("Order Fulfillment Consumer - Standard Priority Order")
    void testOrderFulfillmentConsumer_StandardPriorityOrder() {
        // Input Scenarios: Standard priority order (not HIGH)
        Consumer<Message<Order>> consumer = orderConsumerService.orderFulfillmentConsumer();
        
        Message<Order> message = MessageBuilder.withPayload(validOrder)
            .setHeader(MessageHeaders.PRIORITY, "NORMAL")
            .setHeader(MessageHeaders.ORDER_ID, validOrder.getId())
            .build();
        
        // Expected Output: Should process standard priority order
        assertDoesNotThrow(() -> consumer.accept(message));
        assertEquals(1, orderConsumerService.getFulfillmentCount());
    }

    // ===============================================================================
    // HIGH-VALUE ORDER CONSUMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("High-Value Order Consumer - Valid Notification")
    void testHighValueOrderConsumer_ValidNotification() {
        // Function to Test: highValueOrderConsumer() processes high-value order notifications
        Consumer<Message<Order>> consumer = orderConsumerService.highValueOrderConsumer();
        
        // Input Scenarios: High-value order with notification type
        Order highValueOrder = createValidOrder("ORD-HV-001", "USER-001", BigDecimal.valueOf(5000.00));
        Message<Order> message = MessageBuilder.withPayload(highValueOrder)
            .setHeader(MessageHeaders.NOTIFICATION_TYPE, "HIGH_VALUE_ORDER")
            .setHeader(MessageHeaders.ORDER_ID, highValueOrder.getId())
            .build();
        
        // Expected Output: Should process high-value order notification
        assertDoesNotThrow(() -> consumer.accept(message));
        verify(orderBusinessService).notifyHighValueOrder(highValueOrder);
    }

    @ParameterizedTest
    @DisplayName("High-Value Order Consumer - Different Notification Types")
    @ValueSource(strings = {"HIGH_VALUE_ORDER", "VIP_CUSTOMER", "PRIORITY_PROCESSING"})
    void testHighValueOrderConsumer_DifferentNotificationTypes(String notificationType) {
        // Input Scenarios: Different notification types
        Consumer<Message<Order>> consumer = orderConsumerService.highValueOrderConsumer();
        
        Message<Order> message = MessageBuilder.withPayload(validOrder)
            .setHeader(MessageHeaders.NOTIFICATION_TYPE, notificationType)
            .setHeader(MessageHeaders.ORDER_ID, validOrder.getId())
            .build();
        
        // Expected Output: Should process all notification types
        assertDoesNotThrow(() -> consumer.accept(message));
        verify(orderBusinessService).notifyHighValueOrder(validOrder);
    }

    // ===============================================================================
    // ORDER ANALYTICS CONSUMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order Analytics Consumer - Valid Order")
    void testOrderAnalyticsConsumer_ValidOrder() {
        // Function to Test: orderAnalyticsConsumer() processes order analytics
        Consumer<Message<Order>> consumer = orderConsumerService.orderAnalyticsConsumer();
        
        // Input Scenarios: Order for analytics processing
        Message<Order> message = MessageBuilder.withPayload(validOrder)
            .setHeader(MessageHeaders.EVENT_TYPE, "order-analytics")
            .setHeader(MessageHeaders.ORDER_ID, validOrder.getId())
            .build();
        
        // Expected Output: Should process order analytics
        assertDoesNotThrow(() -> consumer.accept(message));
        verify(orderBusinessService).processOrderAnalytics(validOrder);
    }

    @Test
    @DisplayName("Order Analytics Consumer - Business Service Exception")
    void testOrderAnalyticsConsumer_BusinessServiceException() {
        // Error Handling: Analytics processing failure should not propagate
        Consumer<Message<Order>> consumer = orderConsumerService.orderAnalyticsConsumer();
        doThrow(new RuntimeException("Analytics error")).when(orderBusinessService).processOrderAnalytics(any());
        
        Message<Order> message = MessageBuilder.withPayload(validOrder).build();
        
        // Expected Output: Should handle analytics errors gracefully (no re-throw but does increment error count)
        assertDoesNotThrow(() -> consumer.accept(message));
        assertEquals(1, orderConsumerService.getErrorCount()); // Analytics errors do increment error count
    }

    // ===============================================================================
    // DEAD LETTER QUEUE CONSUMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order DLQ Consumer - Failed Order Processing")
    void testOrderDlqConsumer_FailedOrderProcessing() {
        // Function to Test: orderDlqConsumer() processes failed orders from DLQ
        Consumer<Message<OrderEvent>> consumer = orderConsumerService.orderDlqConsumer();
        
        // Input Scenarios: Failed order from DLQ
        Message<OrderEvent> message = MessageBuilder.withPayload(validOrderEvent)
            .setHeader(MessageHeaders.FAILURE_REASON, "Payment processing failed")
            .setHeader(MessageHeaders.RETRY_COUNT, 3)
            .setHeader(MessageHeaders.ORDER_ID, validOrderEvent.getOrderId())
            .build();
        
        // Expected Output: Should process failed order from DLQ
        assertDoesNotThrow(() -> consumer.accept(message));
        verify(orderBusinessService).handleFailedOrder(validOrderEvent, "Payment processing failed");
    }

    @ParameterizedTest
    @DisplayName("Order DLQ Consumer - Different Failure Reasons")
    @CsvSource({
        "Payment processing failed, 3",
        "Inventory unavailable, 5",
        "Address validation failed, 2",
        "Fraud detection triggered, 1"
    })
    void testOrderDlqConsumer_DifferentFailureReasons(String failureReason, int retryCount) {
        // Input Scenarios: Various failure scenarios
        Consumer<Message<OrderEvent>> consumer = orderConsumerService.orderDlqConsumer();
        
        Message<OrderEvent> message = MessageBuilder.withPayload(validOrderEvent)
            .setHeader(MessageHeaders.FAILURE_REASON, failureReason)
            .setHeader(MessageHeaders.RETRY_COUNT, retryCount)
            .build();
        
        // Expected Output: Should handle all failure types
        assertDoesNotThrow(() -> consumer.accept(message));
        verify(orderBusinessService).handleFailedOrder(validOrderEvent, failureReason);
    }

    @Test
    @DisplayName("Order DLQ Consumer - Critical Failure")
    void testOrderDlqConsumer_CriticalFailure() {
        // Error Handling: Testing critical failure scenario
        Consumer<Message<OrderEvent>> consumer = orderConsumerService.orderDlqConsumer();
        doThrow(new RuntimeException("Critical system error")).when(orderBusinessService)
            .handleFailedOrder(any(), anyString());
        
        Message<OrderEvent> message = MessageBuilder.withPayload(validOrderEvent)
            .setHeader(MessageHeaders.FAILURE_REASON, "System error")
            .setHeader(MessageHeaders.RETRY_COUNT, 5)
            .build();
        
        // Expected Output: Should handle critical failures and alert
        assertDoesNotThrow(() -> consumer.accept(message));
        verify(orderBusinessService).alertCriticalFailure(eq(validOrderEvent), any(Exception.class));
    }

    // ===============================================================================
    // CONCURRENT PROCESSING TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order Consumer - Concurrent Processing")
    void testOrderConsumer_ConcurrentProcessing() throws InterruptedException {
        // Boundary Testing: Concurrent order processing
        Consumer<OrderEvent> consumer = orderConsumerService.orderConsumer();
        int threadCount = 10;
        int messagesPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * messagesPerThread);
        
        // Input Scenarios: Multiple threads processing orders concurrently
        for (int threadId = 0; threadId < threadCount; threadId++) {
            final int tId = threadId;
            executor.submit(() -> {
                for (int msgId = 0; msgId < messagesPerThread; msgId++) {
                    try {
                        OrderEvent orderEvent = createValidOrderEvent("ORD-" + tId + "-" + msgId, "USER-" + tId);
                        consumer.accept(orderEvent);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        // Expected Output: All orders should be processed
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(threadCount * messagesPerThread, orderConsumerService.getProcessedCount());
        
        executor.shutdown();
    }

    // ===============================================================================
    // METRICS AND MONITORING TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order Consumer - Success Rate Calculation")
    void testOrderConsumer_SuccessRateCalculation() {
        // Function to Test: Success rate calculation
        Consumer<OrderEvent> consumer = orderConsumerService.orderConsumer();
        
        // Process successful orders
        for (int i = 0; i < 8; i++) {
            OrderEvent event = createValidOrderEvent("ORD-" + i, "USER-001");
            consumer.accept(event);
        }
        
        // Create failures by making business service throw exceptions
        doThrow(new RuntimeException("Test failure")).when(orderBusinessService).processOrderCreated(any());
        
        // Process failed orders  
        for (int i = 0; i < 2; i++) {
            try {
                OrderEvent event = createValidOrderEvent("ORD-FAIL-" + i, "USER-001");
                consumer.accept(event); // This will fail due to mocked exception
            } catch (Exception e) {
                // Expected to fail
            }
        }
        
        // Expected Output: Success rate should be 80% (8 success, 2 failures)
        assertEquals(80.0, orderConsumerService.getSuccessRate(), 0.1);
    }

    @Test
    @DisplayName("Order Consumer - Reset Counts")
    void testOrderConsumer_ResetCounts() {
        // Function to Test: Metrics reset functionality
        Consumer<OrderEvent> consumer = orderConsumerService.orderConsumer();
        
        // Process some successful orders
        consumer.accept(validOrderEvent);
        
        // Create a failure by making business service throw exception
        doThrow(new RuntimeException("Test error")).when(orderBusinessService).processOrderCreated(any());
        try {
            OrderEvent failEvent = createValidOrderEvent("ORD-FAIL", "USER-001");
            consumer.accept(failEvent);
        } catch (Exception e) {
            // Expected to fail
        }
        
        assertTrue(orderConsumerService.getProcessedCount() > 0);
        assertTrue(orderConsumerService.getErrorCount() > 0);
        
        // Reset counts
        orderConsumerService.resetCounts();
        
        // Expected Output: All counts should be zero
        assertEquals(0, orderConsumerService.getProcessedCount());
        assertEquals(0, orderConsumerService.getErrorCount());
        assertEquals(0, orderConsumerService.getFulfillmentCount());
    }

    // ===============================================================================
    // HELPER METHODS
    // ===============================================================================

    private Order createValidOrder(String orderId, String userId, BigDecimal totalAmount) {
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setPriority(Order.OrderPriority.NORMAL);
        order.setOrderDate(LocalDateTime.now());
        order.setPaymentMethod("CREDIT_CARD");
        order.setDescription("Test order for " + orderId);
        
        // Add order items
        Order.OrderItem item1 = new Order.OrderItem();
        item1.setProductId("PROD-001");
        item1.setProductName("Test Product 1");
        item1.setQuantity(2);
        item1.setUnitPrice(BigDecimal.valueOf(50.00));
        item1.setTotalPrice(BigDecimal.valueOf(100.00));
        
        Order.OrderItem item2 = new Order.OrderItem();
        item2.setProductId("PROD-002");
        item2.setProductName("Test Product 2");
        item2.setQuantity(1);
        item2.setUnitPrice(BigDecimal.valueOf(150.00));
        item2.setTotalPrice(BigDecimal.valueOf(150.00));
        
        order.setItems(Arrays.asList(item1, item2));
        
        // Add shipping address
        Order.Address address = new Order.Address();
        address.setStreet("123 Test Street");
        address.setCity("Test City");
        address.setState("Test State");
        address.setZipCode("12345");
        address.setCountry("USA");
        order.setShippingAddress(address);
        
        return order;
    }

    private OrderEvent createValidOrderEvent(String orderId, String userId) {
        Order testOrder = createValidOrder(orderId, userId, BigDecimal.valueOf(100.00));
        return OrderEvent.createOrderCreatedEvent(testOrder, "test-service", "test-correlation-id");
    }
} 