package com.example.springcloudstream.domain.order.workflow;

import com.example.springcloudstream.domain.order.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for OrderProcessingService Workflow
 * 
 * Function to Test: Order workflow orchestration including validation, 
 * payment processing, inventory management, shipping, and error handling.
 * 
 * Test Coverage:
 * - Input Scenarios: Various order states, validation rules, business conditions
 * - Expected Output: Proper workflow transitions, event generation, status updates
 * - Error Handling: Validation failures, payment failures, inventory issues
 * - Boundary Testing: Edge cases, concurrent processing, timeout scenarios
 * - Dependencies: Mocked StreamBridge for isolated workflow testing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProcessingService Workflow Tests")
class OrderProcessingServiceTest {

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    @Mock
    private StreamBridge streamBridge;

    private Order validOrder;
    private Order invalidOrder;
    private Order highValueOrder;
    private Order lowValueOrder;

    @BeforeEach
    void setUp() {
        // Create valid test order
        validOrder = createValidOrder("ORD-WORKFLOW-001", "USER-001", BigDecimal.valueOf(250.00));
        
        // Create invalid test order (missing required fields)
        invalidOrder = createInvalidOrder("ORD-INVALID-001", "", BigDecimal.valueOf(-10.00));
        
        // Create high-value test order
        highValueOrder = createValidOrder("ORD-HV-001", "USER-VIP", BigDecimal.valueOf(5000.00));
        
        // Create low-value test order
        lowValueOrder = createValidOrder("ORD-LV-001", "USER-REG", BigDecimal.valueOf(15.00));
    }

    // ===============================================================================
    // ORDER VALIDATION WORKFLOW TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order Validator - Valid Order Success")
    void testOrderValidator_ValidOrderSuccess() {
        // Function to Test: orderValidator() validates and routes successful orders
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Consumer<Order> validator = orderProcessingService.orderValidator();
        
        // Input Scenarios: Valid order for validation
        validator.accept(validOrder);
        
        // Expected Output: Order should be validated and routed to payment processor
        assertEquals(Order.OrderStatus.CONFIRMED, validOrder.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Order Validator - Invalid Order Rejection")
    void testOrderValidator_InvalidOrderRejection() {
        // Error Handling: Invalid order should be rejected
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Consumer<Order> validator = orderProcessingService.orderValidator();
        
        // Input Scenarios: Invalid order for validation
        validator.accept(invalidOrder);
        
        // Expected Output: Order should be cancelled and routed to rejection handler
        assertEquals(Order.OrderStatus.CANCELLED, invalidOrder.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Order Validator - Exception Handling")
    void testOrderValidator_ExceptionHandling() {
        // Error Handling: StreamBridge failure should trigger error handling
        when(streamBridge.send(anyString(), any(Message.class))).thenThrow(new RuntimeException("Stream error"));
        
        Consumer<Order> validator = orderProcessingService.orderValidator();
        
        // Input Scenarios: Valid order but stream failure
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            validator.accept(validOrder);
        });
        
        // Expected Output: Should handle exception and set order to cancelled
        assertEquals("Stream error", exception.getMessage());
        assertEquals(Order.OrderStatus.CANCELLED, validOrder.getStatus());
        // Verify that streamBridge.send was called at least once (original call that failed + error handler call)
        verify(streamBridge, atLeast(1)).send(eq("order-events-out-0"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Order Validator - Different Order Values")
    @ValueSource(doubles = {0.01, 50.00, 100.00, 999.99, 1000.00, 5000.00})
    void testOrderValidator_DifferentOrderValues(double amount) {
        // Input Scenarios: Orders with various amounts
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Order order = createValidOrder("ORD-VAL-" + amount, "USER-001", BigDecimal.valueOf(amount));
        Consumer<Order> validator = orderProcessingService.orderValidator();
        
        validator.accept(order);
        
        // Expected Output: All valid orders should be confirmed regardless of amount
        assertEquals(Order.OrderStatus.CONFIRMED, order.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    // ===============================================================================
    // PAYMENT PROCESSING WORKFLOW TESTS
    // ===============================================================================

    @Test
    @DisplayName("Payment Processor - Successful Payment")
    void testPaymentProcessor_SuccessfulPayment() {
        // Function to Test: paymentProcessor() handles payment validation and processing
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Consumer<Order> paymentProcessor = orderProcessingService.paymentProcessor();
        validOrder.setStatus(Order.OrderStatus.CONFIRMED);
        
        // Input Scenarios: Confirmed order for payment processing
        paymentProcessor.accept(validOrder);
        
        // Expected Output: Order should be processing and routed to inventory manager
        assertEquals(Order.OrderStatus.PROCESSING, validOrder.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Payment Processor - Payment Failure")
    void testPaymentProcessor_PaymentFailure() {
        // Error Handling: Payment failure should trigger proper handling
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        // Create order that will fail payment (negative amount)
        Order failedPaymentOrder = createValidOrder("ORD-PAY-FAIL", "USER-001", BigDecimal.valueOf(-50.00));
        failedPaymentOrder.setStatus(Order.OrderStatus.CONFIRMED);
        
        Consumer<Order> paymentProcessor = orderProcessingService.paymentProcessor();
        
        // Input Scenarios: Order that fails payment validation
        paymentProcessor.accept(failedPaymentOrder);
        
        // Expected Output: Order should be cancelled and routed to payment failure handler
        assertEquals(Order.OrderStatus.CANCELLED, failedPaymentOrder.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Payment Processor - Different Payment Methods")
    @ValueSource(strings = {"CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER", "CRYPTO"})
    void testPaymentProcessor_DifferentPaymentMethods(String paymentMethod) {
        // Input Scenarios: Various payment methods
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Order order = createValidOrder("ORD-PAY-" + paymentMethod, "USER-001", BigDecimal.valueOf(100.00));
        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.setPaymentMethod(paymentMethod);
        
        Consumer<Order> paymentProcessor = orderProcessingService.paymentProcessor();
        paymentProcessor.accept(order);
        
        // Expected Output: All valid payment methods should be processed
        assertEquals(Order.OrderStatus.PROCESSING, order.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    // ===============================================================================
    // INVENTORY MANAGEMENT WORKFLOW TESTS
    // ===============================================================================

    @Test
    @DisplayName("Inventory Manager - Sufficient Inventory")
    void testInventoryManager_SufficientInventory() {
        // Function to Test: inventoryManager() validates product availability
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Consumer<Order> inventoryManager = orderProcessingService.inventoryManager();
        validOrder.setStatus(Order.OrderStatus.PROCESSING);
        
        // Input Scenarios: Order with available inventory
        inventoryManager.accept(validOrder);
        
        // Expected Output: Inventory should be reserved and routed to shipping coordinator
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Inventory Manager - Insufficient Inventory")
    void testInventoryManager_InsufficientInventory() {
        // Error Handling: Insufficient inventory should trigger refund
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        // Create order that will fail inventory check (large quantity)
        Order inventoryFailOrder = createLargeQuantityOrder("ORD-INV-FAIL", "USER-001", 1000);
        inventoryFailOrder.setStatus(Order.OrderStatus.PROCESSING);
        
        Consumer<Order> inventoryManager = orderProcessingService.inventoryManager();
        
        // Input Scenarios: Order with insufficient inventory
        inventoryManager.accept(inventoryFailOrder);
        
        // Expected Output: Should trigger payment refund
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    // ===============================================================================
    // SHIPPING WORKFLOW TESTS
    // ===============================================================================

    @Test
    @DisplayName("Shipping Coordinator - Successful Shipping")
    void testShippingCoordinator_SuccessfulShipping() {
        // Function to Test: shippingCoordinator() handles shipping preparation and dispatch
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Consumer<Order> shippingCoordinator = orderProcessingService.shippingCoordinator();
        validOrder.setStatus(Order.OrderStatus.PROCESSING);
        
        // Input Scenarios: Processing order for shipping
        shippingCoordinator.accept(validOrder);
        
        // Expected Output: Order should be shipped and routed to delivery tracking
        assertEquals(Order.OrderStatus.SHIPPED, validOrder.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Shipping Coordinator - Different Priorities")
    @EnumSource(Order.OrderPriority.class)
    void testShippingCoordinator_DifferentPriorities(Order.OrderPriority priority) {
        // Input Scenarios: Orders with different priority levels
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        Order order = createValidOrder("ORD-SHIP-" + priority, "USER-001", BigDecimal.valueOf(100.00));
        order.setStatus(Order.OrderStatus.PROCESSING);
        order.setPriority(priority);
        
        Consumer<Order> shippingCoordinator = orderProcessingService.shippingCoordinator();
        shippingCoordinator.accept(order);
        
        // Expected Output: All priorities should be processed and shipped
        assertEquals(Order.OrderStatus.SHIPPED, order.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
    }

    // ===============================================================================
    // ORDER ENRICHMENT WORKFLOW TESTS
    // ===============================================================================

    @Test
    @DisplayName("Order Enricher - Successful Enrichment")
    void testOrderEnricher_SuccessfulEnrichment() {
        // Function to Test: orderEnricher() adds additional business data to orders
        validOrder.setTotalAmount(BigDecimal.valueOf(1500.00)); // High value order
        
        java.util.function.Function<Order, Order> enricher = orderProcessingService.orderEnricher();
        
        // Input Scenarios: Order that needs enrichment
        Order enrichedOrder = enricher.apply(validOrder);
        
        // Expected Output: Order should be enriched with priority and delivery notes
        assertNotNull(enrichedOrder);
        assertNotNull(enrichedOrder.getPriority());
        assertNotNull(enrichedOrder.getExpectedDeliveryDate());
        assertTrue(enrichedOrder.getDeliveryNotes().contains("HIGH VALUE ORDER"));
    }

    // ===============================================================================
    // CONCURRENT WORKFLOW TESTS
    // ===============================================================================

    @Test
    @DisplayName("Concurrent Order Processing - High Throughput")
    void testConcurrentOrderProcessing_HighThroughput() throws InterruptedException {
        // Boundary Testing: Multiple orders processed concurrently
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        int orderCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Consumer<Order> validator = orderProcessingService.orderValidator();
        
        // Input Scenarios: Multiple orders processed concurrently
        for (int i = 0; i < orderCount; i++) {
            final int orderId = i;
            executor.submit(() -> {
                Order order = createValidOrder("ORD-CONCURRENT-" + orderId, "USER-" + orderId, 
                                             BigDecimal.valueOf(100 + orderId));
                validator.accept(order);
            });
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Expected Output: All orders should be processed successfully
        verify(streamBridge, times(orderCount)).send(eq("order-events-out-0"), any(Message.class));
    }

    // ===============================================================================
    // WORKFLOW INTEGRATION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Complete Workflow - Happy Path")
    void testCompleteWorkflow_HappyPath() {
        // Integration Test: Complete order workflow from validation to delivery
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        // Create workflow processors
        Consumer<Order> validator = orderProcessingService.orderValidator();
        Consumer<Order> paymentProcessor = orderProcessingService.paymentProcessor();
        Consumer<Order> inventoryManager = orderProcessingService.inventoryManager();
        Consumer<Order> shippingCoordinator = orderProcessingService.shippingCoordinator();
        java.util.function.Function<Order, Order> enricher = orderProcessingService.orderEnricher();
        
        // Execute complete workflow
        validator.accept(validOrder);
        assertEquals(Order.OrderStatus.CONFIRMED, validOrder.getStatus());
        
        paymentProcessor.accept(validOrder);
        assertEquals(Order.OrderStatus.PROCESSING, validOrder.getStatus());
        
        inventoryManager.accept(validOrder);
        // Inventory manager doesn't change status, it routes to shipping
        
        shippingCoordinator.accept(validOrder);
        assertEquals(Order.OrderStatus.SHIPPED, validOrder.getStatus());
        
        // Test enrichment
        Order enrichedOrder = enricher.apply(validOrder);
        assertNotNull(enrichedOrder.getPriority());
        
        // Verify all workflow steps were executed - each step sends to order-events-out-0
        verify(streamBridge, times(4)).send(eq("order-events-out-0"), any(Message.class));
        verify(streamBridge, times(1)).send(eq("order-notifications-out-0"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Workflow - Different Order Scenarios")
    @CsvSource({
        "HIGH_VALUE, URGENT, VIP_CUSTOMER",
        "MEDIUM_VALUE, HIGH, REGULAR_CUSTOMER", 
        "LOW_VALUE, NORMAL, NEW_CUSTOMER",
        "HIGH_VALUE, LOW, BULK_CUSTOMER"
    })
    void testWorkflow_DifferentOrderScenarios(String valueCategory, Order.OrderPriority priority, String customerType) {
        // Input Scenarios: Various order characteristics and customer types
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        
        BigDecimal amount = switch (valueCategory) {
            case "HIGH_VALUE" -> BigDecimal.valueOf(2000.00);
            case "MEDIUM_VALUE" -> BigDecimal.valueOf(500.00);
            default -> BigDecimal.valueOf(50.00);
        };
        
        Order order = createValidOrder("ORD-SCENARIO-" + valueCategory, customerType, amount);
        order.setPriority(priority);
        
        Consumer<Order> validator = orderProcessingService.orderValidator();
        validator.accept(order);
        
        // Expected Output: All order types should be validated successfully
        assertEquals(Order.OrderStatus.CONFIRMED, order.getStatus());
        verify(streamBridge).send(eq("order-events-out-0"), any(Message.class));
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
        Order.OrderItem item = new Order.OrderItem();
        item.setProductId("PROD-001");
        item.setProductName("Test Product");
        item.setQuantity(1);
        item.setUnitPrice(totalAmount);
        item.setTotalPrice(totalAmount);
        order.setItems(Arrays.asList(item));
        
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

    private Order createInvalidOrder(String orderId, String userId, BigDecimal totalAmount) {
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId); // Empty user ID
        order.setTotalAmount(totalAmount); // Negative amount
        order.setStatus(Order.OrderStatus.PENDING);
        // Missing other required fields intentionally
        return order;
    }

    private Order createLargeQuantityOrder(String orderId, String userId, int quantity) {
        Order order = createValidOrder(orderId, userId, BigDecimal.valueOf(100.00));
        
        // Create item with large quantity to trigger inventory failure
        Order.OrderItem item = new Order.OrderItem();
        item.setProductId("PROD-LIMITED");
        item.setProductName("Limited Stock Product");
        item.setQuantity(quantity); // Large quantity
        item.setUnitPrice(BigDecimal.valueOf(1.00));
        item.setTotalPrice(BigDecimal.valueOf(quantity));
        order.setItems(Arrays.asList(item));
        
        return order;
    }
} 