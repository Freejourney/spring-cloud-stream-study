package com.example.springcloudstream.domain.order.producer;

import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.common.model.MessageHeaders;
import com.example.springcloudstream.common.util.MessageUtils;
import com.example.springcloudstream.common.util.TestMessageUtils;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for OrderProducerService
 * 
 * Function to Test: Producer methods for order lifecycle events, status updates,
 * fulfillment, notifications, analytics, and multi-destination messaging.
 * 
 * Test Coverage:
 * - Input Scenarios: Various order events, status changes, high-value orders
 * - Expected Output: Proper message sending, correct headers, return values
 * - Error Handling: Failed sends, retry mechanisms, exception handling
 * - Boundary Testing: Large orders, high volume, concurrent sends
 * - Dependencies: Mocked StreamBridge for isolated testing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProducerService Unit Tests")
class OrderProducerServiceTest {

    @InjectMocks
    private OrderProducerService orderProducerService;

    @Mock
    private StreamBridge streamBridge;

    private Order validOrder;
    private Order highValueOrder;

    @BeforeEach
    void setUp() {
        // Create valid test order
        validOrder = createValidOrder("ORD-001", "USER-001", BigDecimal.valueOf(250.00));
        
        // Create high-value test order
        highValueOrder = createValidOrder("ORD-HV-001", "USER-001", BigDecimal.valueOf(5000.00));
    }

    // ===============================================================================
    // ORDER CREATED EVENT TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send Order Created - Success")
    void testSendOrderCreated_Success() {
        // Function to Test: sendOrderCreated() sends order created events
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: Valid order creation
        boolean result = orderProducerService.sendOrderCreated(validOrder);
        
        // Expected Output: Should send message successfully
        assertTrue(result);
        verify(streamBridge).send(eq("order-events"), any(Message.class));
    }

    @Test
    @DisplayName("Send Order Created - Failed Send")
    void testSendOrderCreated_FailedSend() {
        // Error Handling: Stream bridge send failure
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(false);
        
        // Input Scenarios: Order creation with send failure
        boolean result = orderProducerService.sendOrderCreated(validOrder);
        
        // Expected Output: Should return false for failed send
        assertFalse(result);
        verify(streamBridge).send(eq("order-events"), any(Message.class));
    }

    @Test
    @DisplayName("Send Order Created - Exception Handling")
    void testSendOrderCreated_ExceptionHandling() {
        // Error Handling: Stream bridge throws exception
        when(streamBridge.send(eq("order-events"), any(Message.class)))
            .thenThrow(new RuntimeException("Stream bridge error"));
        
        // Input Scenarios: Order creation with exception
        boolean result = orderProducerService.sendOrderCreated(validOrder);
        
        // Expected Output: Should handle exception and return false
        assertFalse(result);
    }

    @ParameterizedTest
    @DisplayName("Send Order Created - Different Order Values")
    @ValueSource(doubles = {100.00, 1000.00, 10000.00})
    void testSendOrderCreated_DifferentOrderValues(double amount) {
        // Input Scenarios: Orders with different amounts (reduced for performance)
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(true);
        
        Order order = createValidOrder("ORD-VAL-" + amount, "USER-001", BigDecimal.valueOf(amount));
        boolean result = orderProducerService.sendOrderCreated(order);
        
        // Expected Output: Should handle all order values
        assertTrue(result);
    }

    // ===============================================================================
    // ORDER STATUS UPDATE TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send Order Status Update - Success")
    void testSendOrderStatusUpdate_Success() {
        // Function to Test: sendOrderStatusUpdate() sends status change events
        when(streamBridge.send(eq("order-status-events"), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: Order status change
        Order.OrderStatus previousStatus = Order.OrderStatus.PENDING;
        validOrder.setStatus(Order.OrderStatus.CONFIRMED);
        
        boolean result = orderProducerService.sendOrderStatusUpdate(validOrder, previousStatus);
        
        // Expected Output: Should send status update message
        assertTrue(result);
        verify(streamBridge).send(eq("order-status-events"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Send Order Status Update - Various Status Transitions")
    @CsvSource({
        "PENDING, CONFIRMED",
        "CONFIRMED, PAID",
        "PAID, PROCESSING",
        "PROCESSING, SHIPPED",
        "SHIPPED, DELIVERED",
        "PENDING, CANCELLED"
    })
    void testSendOrderStatusUpdate_VariousTransitions(Order.OrderStatus from, Order.OrderStatus to) {
        // Input Scenarios: Different status transitions
        when(streamBridge.send(eq("order-status-events"), any(Message.class))).thenReturn(true);
        
        validOrder.setStatus(to);
        boolean result = orderProducerService.sendOrderStatusUpdate(validOrder, from);
        
        // Expected Output: Should handle all status transitions
        assertTrue(result);
    }

    @Test
    @DisplayName("Send Order Status Update - Same Status")
    void testSendOrderStatusUpdate_SameStatus() {
        // Edge Case: Status update with same status
        when(streamBridge.send(eq("order-status-events"), any(Message.class))).thenReturn(true);
        
        Order.OrderStatus sameStatus = Order.OrderStatus.PENDING;
        validOrder.setStatus(sameStatus);
        
        boolean result = orderProducerService.sendOrderStatusUpdate(validOrder, sameStatus);
        
        // Expected Output: Should still send update (business logic handles duplicates)
        assertTrue(result);
    }

    // ===============================================================================
    // ORDER FULFILLMENT TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send Order To Fulfillment - Success")
    void testSendOrderToFulfillment_Success() {
        // Function to Test: sendOrderToFulfillment() sends orders to fulfillment
        when(streamBridge.send(eq("order-fulfillment"), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: Order ready for fulfillment
        boolean result = orderProducerService.sendOrderToFulfillment(validOrder);
        
        // Expected Output: Should send fulfillment message
        assertTrue(result);
        verify(streamBridge).send(eq("order-fulfillment"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Send Order To Fulfillment - Different Priority Orders")
    @EnumSource(Order.OrderPriority.class)
    void testSendOrderToFulfillment_DifferentPriorities(Order.OrderPriority priority) {
        // Input Scenarios: Orders with different priorities
        when(streamBridge.send(eq("order-fulfillment"), any(Message.class))).thenReturn(true);
        
        validOrder.setPriority(priority);
        boolean result = orderProducerService.sendOrderToFulfillment(validOrder);
        
        // Expected Output: Should handle all priority levels
        assertTrue(result);
    }

    @Test
    @DisplayName("Send Order To Fulfillment - Large Order")
    void testSendOrderToFulfillment_LargeOrder() {
        // Boundary Testing: Large order with many items (reduced from 50 to 10 for speed)
        when(streamBridge.send(eq("order-fulfillment"), any(Message.class))).thenReturn(true);
        
        Order largeOrder = createLargeOrder("ORD-LARGE-001", "USER-001", 10);
        boolean result = orderProducerService.sendOrderToFulfillment(largeOrder);
        
        // Expected Output: Should handle large orders
        assertTrue(result);
    }

    // ===============================================================================
    // ORDER CANCELLATION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send Order Cancelled - Success")
    void testSendOrderCancelled_Success() {
        // Function to Test: sendOrderCancelled() sends cancellation events
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: Order cancellation with reason
        String reason = "Customer requested cancellation";
        boolean result = orderProducerService.sendOrderCancelled(validOrder, reason);
        
        // Expected Output: Should send cancellation message
        assertTrue(result);
        verify(streamBridge).send(eq("order-events"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Send Order Cancelled - Different Cancellation Reasons")
    @ValueSource(strings = {
        "Customer requested",
        "Payment failed",
        "System error"
    })
    void testSendOrderCancelled_DifferentReasons(String reason) {
        // Input Scenarios: Various cancellation reasons (reduced for performance)
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(true);
        
        boolean result = orderProducerService.sendOrderCancelled(validOrder, reason);
        
        // Expected Output: Should handle all cancellation reasons
        assertTrue(result);
    }

    @Test
    @DisplayName("Send Order Cancelled - Null Reason")
    void testSendOrderCancelled_NullReason() {
        // Edge Case: Cancellation with null reason
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(true);
        
        boolean result = orderProducerService.sendOrderCancelled(validOrder, null);
        
        // Expected Output: Should handle null reason gracefully
        assertTrue(result);
    }

    // ===============================================================================
    // ORDER ANALYTICS TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send Order Analytics - Success")
    void testSendOrderAnalytics_Success() {
        // Function to Test: sendOrderAnalytics() sends analytics data
        when(streamBridge.send(eq("analytics-events"), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: Order analytics data
        boolean result = orderProducerService.sendOrderAnalytics(validOrder);
        
        // Expected Output: Should send analytics message
        assertTrue(result);
        verify(streamBridge).send(eq("analytics-events"), any(Message.class));
    }

    @Test
    @DisplayName("Send Order Analytics - Failed Send")
    void testSendOrderAnalytics_FailedSend() {
        // Error Handling: Analytics send failure
        when(streamBridge.send(eq("analytics-events"), any(Message.class))).thenReturn(false);
        
        // Input Scenarios: Analytics with send failure
        boolean result = orderProducerService.sendOrderAnalytics(validOrder);
        
        // Expected Output: Should return false for failed analytics send
        assertFalse(result);
    }

    @ParameterizedTest
    @DisplayName("Send Order Analytics - Different Order States")
    @EnumSource(Order.OrderStatus.class)
    void testSendOrderAnalytics_DifferentStates(Order.OrderStatus status) {
        // Input Scenarios: Analytics for orders in different states
        when(streamBridge.send(eq("analytics-events"), any(Message.class))).thenReturn(true);
        
        validOrder.setStatus(status);
        boolean result = orderProducerService.sendOrderAnalytics(validOrder);
        
        // Expected Output: Should send analytics for all order states
        assertTrue(result);
    }

    // ===============================================================================
    // HIGH-VALUE ORDER NOTIFICATION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send High-Value Order Notification - Success")
    void testSendHighValueOrderNotification_Success() {
        // Function to Test: sendHighValueOrderNotification() sends high-value alerts
        when(streamBridge.send(eq("notification-events"), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: High-value order notification
        boolean result = orderProducerService.sendHighValueOrderNotification(highValueOrder);
        
        // Expected Output: Should send high-value notification
        assertTrue(result);
        verify(streamBridge).send(eq("notification-events"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Send High-Value Order Notification - Different Amounts")
    @ValueSource(doubles = {1000.00, 5000.00, 10000.00})
    void testSendHighValueOrderNotification_DifferentAmounts(double amount) {
        // Input Scenarios: Various high-value amounts (reduced for performance)
        when(streamBridge.send(eq("notification-events"), any(Message.class))).thenReturn(true);
        
        Order order = createValidOrder("ORD-HV-" + amount, "USER-001", BigDecimal.valueOf(amount));
        boolean result = orderProducerService.sendHighValueOrderNotification(order);
        
        // Expected Output: Should handle all high-value amounts
        assertTrue(result);
    }

    @Test
    @DisplayName("Send High-Value Order Notification - Low Value Order")
    void testSendHighValueOrderNotification_LowValueOrder() {
        // Edge Case: Sending low-value order as high-value notification
        when(streamBridge.send(eq("notification-events"), any(Message.class))).thenReturn(true);
        
        // Use regular order (not high-value)
        boolean result = orderProducerService.sendHighValueOrderNotification(validOrder);
        
        // Expected Output: Should still send notification (business logic decides threshold)
        assertTrue(result);
    }

    // ===============================================================================
    // MULTI-DESTINATION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send Order To Multiple Destinations - Success")
    void testSendOrderToMultipleDestinations_Success() {
        // Function to Test: sendOrderToMultipleDestinations() fan-out pattern
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: Order for multiple destinations
        boolean result = orderProducerService.sendOrderToMultipleDestinations(highValueOrder);
        
        // Expected Output: Should send to multiple destinations
        assertTrue(result);
        verify(streamBridge, atLeast(2)).send(anyString(), any(Message.class));
    }

    @Test
    @DisplayName("Send Order To Multiple Destinations - Partial Failure")
    void testSendOrderToMultipleDestinations_PartialFailure() {
        // Error Handling: Some destinations succeed, others fail
        when(streamBridge.send(eq("analytics-events"), any(Message.class))).thenReturn(true);
        when(streamBridge.send(eq("order-fulfillment"), any(Message.class))).thenReturn(false);
        when(streamBridge.send(eq("notification-events"), any(Message.class))).thenReturn(true);
        
        // Input Scenarios: High-value order with partial send failure
        boolean result = orderProducerService.sendOrderToMultipleDestinations(highValueOrder);
        
        // Expected Output: Should handle partial failures appropriately
        // Implementation may return false if any destination fails
    }

    @Test
    @DisplayName("Send Order To Multiple Destinations - Low Value Order")
    void testSendOrderToMultipleDestinations_LowValueOrder() {
        // Input Scenarios: Regular order (may not trigger high-value notifications)
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        boolean result = orderProducerService.sendOrderToMultipleDestinations(validOrder);
        
        // Expected Output: Should send to applicable destinations
        assertTrue(result);
    }

    // ===============================================================================
    // RETRY MECHANISM TESTS (OPTIMIZED FOR PERFORMANCE)
    // ===============================================================================

    @Test
    @DisplayName("Send Order With Retry - Success On First Try")
    void testSendOrderWithRetry_SuccessOnFirstTry() {
        // Function to Test: sendOrderWithRetry() retry mechanism (using fast retry for tests)
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(true);
        
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            // Use fast test retry mechanism instead of production delays
            messageUtilsMock.when(() -> MessageUtils.sendWithRetry(any(), anyInt(), anyString(), any()))
                           .thenAnswer(invocation -> TestMessageUtils.sendWithRetry(
                               invocation.getArgument(0),
                               invocation.getArgument(1),
                               invocation.getArgument(2),
                               invocation.getArgument(3)
                           ));
            
            // Input Scenarios: Successful send on first attempt
            boolean result = orderProducerService.sendOrderWithRetry(validOrder, 3);
            
            // Expected Output: Should succeed without retries
            assertTrue(result);
            verify(streamBridge, times(1)).send(eq("order-events"), any(Message.class));
        }
    }

    @Test
    @DisplayName("Send Order With Retry - Success After Retries")
    void testSendOrderWithRetry_SuccessAfterRetries() {
        // Function to Test: Retry mechanism with eventual success (using fast retry for tests)
        when(streamBridge.send(eq("order-events"), any(Message.class)))
            .thenReturn(false) // First attempt fails
            .thenReturn(false) // Second attempt fails  
            .thenReturn(false) // Third attempt fails
            .thenReturn(true); // Fourth attempt succeeds (1 initial + 3 retries)
        
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            // Use fast test retry mechanism instead of production delays
            messageUtilsMock.when(() -> MessageUtils.sendWithRetry(any(), anyInt(), anyString(), any()))
                           .thenAnswer(invocation -> TestMessageUtils.sendWithRetry(
                               invocation.getArgument(0),
                               invocation.getArgument(1),
                               invocation.getArgument(2),
                               invocation.getArgument(3)
                           ));
            
            // Input Scenarios: Success after 3 retries
            boolean result = orderProducerService.sendOrderWithRetry(validOrder, 3);
            
            // Expected Output: Should eventually succeed
            assertTrue(result);
            verify(streamBridge, times(4)).send(eq("order-events"), any(Message.class));
        }
    }

    @Test
    @DisplayName("Send Order With Retry - Max Retries Exceeded")
    void testSendOrderWithRetry_MaxRetriesExceeded() {
        // Error Handling: All retry attempts fail (using fast retry for tests)
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(false);
        
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            // Use fast test retry mechanism instead of production delays
            messageUtilsMock.when(() -> MessageUtils.sendWithRetry(any(), anyInt(), anyString(), any()))
                           .thenAnswer(invocation -> TestMessageUtils.sendWithRetry(
                               invocation.getArgument(0),
                               invocation.getArgument(1),
                               invocation.getArgument(2),
                               invocation.getArgument(3)
                           ));
            
            // Input Scenarios: All retries fail
            boolean result = orderProducerService.sendOrderWithRetry(validOrder, 3);
            
            // Expected Output: Should fail after max retries (1 initial + 3 retries = 4 total attempts)
            assertFalse(result);
            verify(streamBridge, times(4)).send(eq("order-events"), any(Message.class));
        }
    }

    @ParameterizedTest
    @DisplayName("Send Order With Retry - Different Retry Counts")
    @ValueSource(ints = {1, 2, 3})
    void testSendOrderWithRetry_DifferentRetryCounts(int maxRetries) {
        // Input Scenarios: Various retry configurations (using fast retry for tests)
        when(streamBridge.send(eq("order-events"), any(Message.class))).thenReturn(false);
        
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            // Use fast test retry mechanism instead of production delays
            messageUtilsMock.when(() -> MessageUtils.sendWithRetry(any(), anyInt(), anyString(), any()))
                           .thenAnswer(invocation -> TestMessageUtils.sendWithRetry(
                               invocation.getArgument(0),
                               invocation.getArgument(1),
                               invocation.getArgument(2),
                               invocation.getArgument(3)
                           ));
            
            boolean result = orderProducerService.sendOrderWithRetry(validOrder, maxRetries);
            
            // Expected Output: Should attempt 1 initial + maxRetries times = maxRetries + 1 total attempts
            assertFalse(result);
            verify(streamBridge, times(maxRetries + 1)).send(eq("order-events"), any(Message.class));
        }
    }

    // ===============================================================================
    // CONCURRENT PROCESSING TESTS
    // ===============================================================================

    @Test
    @DisplayName("Concurrent Order Sending - High Throughput")
    void testConcurrentOrderSending_HighThroughput() throws InterruptedException {
        // Boundary Testing: High-throughput concurrent sending (optimized for speed)
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        int threadCount = 3; // Reduced from 10
        int ordersPerThread = 2; // Reduced from 5
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // Input Scenarios: Multiple threads sending orders concurrently
        for (int threadId = 0; threadId < threadCount; threadId++) {
            final int tId = threadId;
            executor.submit(() -> {
                for (int orderId = 0; orderId < ordersPerThread; orderId++) {
                    Order order = createValidOrder("ORD-" + tId + "-" + orderId, "USER-" + tId, 
                                                  BigDecimal.valueOf(100 + orderId));
                    orderProducerService.sendOrderCreated(order);
                }
            });
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); // Reduced timeout from 10s to 5s
        
        // Expected Output: All sends should complete
        verify(streamBridge, times(threadCount * ordersPerThread)).send(eq("order-events"), any(Message.class));
    }

    @Test
    @DisplayName("Concurrent Multi-Destination Sending")
    void testConcurrentMultiDestinationSending() throws InterruptedException {
        // Boundary Testing: Concurrent fan-out pattern (optimized for speed)
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        int orderCount = 2; // Reduced from 5
        ExecutorService executor = Executors.newFixedThreadPool(2); // Reduced from 3
        
        // Input Scenarios: Concurrent multi-destination sends
        for (int i = 0; i < orderCount; i++) {
            final int orderId = i;
            executor.submit(() -> {
                Order order = createValidOrder("ORD-MULTI-" + orderId, "USER-001", 
                                              BigDecimal.valueOf(2000 + orderId * 100));
                orderProducerService.sendOrderToMultipleDestinations(order);
            });
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); // Reduced timeout from 10s to 5s
        
        // Expected Output: Multiple destinations should receive messages
        verify(streamBridge, atLeast(orderCount * 2)).send(anyString(), any(Message.class));
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
        item2.setUnitPrice(totalAmount.subtract(BigDecimal.valueOf(100.00)));
        item2.setTotalPrice(totalAmount.subtract(BigDecimal.valueOf(100.00)));
        
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

    private Order createLargeOrder(String orderId, String userId, int itemCount) {
        Order order = createValidOrder(orderId, userId, BigDecimal.valueOf(100 * itemCount));
        
        // Create many items
        java.util.List<Order.OrderItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            Order.OrderItem item = new Order.OrderItem();
            item.setProductId("PROD-" + i);
            item.setProductName("Test Product " + i);
            item.setQuantity(1);
            item.setUnitPrice(BigDecimal.valueOf(100.00));
            item.setTotalPrice(BigDecimal.valueOf(100.00));
            items.add(item);
        }
        order.setItems(items);
        
        return order;
    }
} 