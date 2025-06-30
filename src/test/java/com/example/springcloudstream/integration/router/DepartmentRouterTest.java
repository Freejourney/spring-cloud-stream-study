package com.example.springcloudstream.integration.router;

import com.example.springcloudstream.domain.user.model.User;
import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.common.model.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DepartmentRouter class
 * 
 * Tests each router function in isolation following comprehensive testing requirements:
 * - Function behavior verification
 * - Input scenario testing (valid, invalid, edge cases)
 * - Expected output validation
 * - Error handling verification
 * - Boundary testing
 * - Dependency mocking
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Department Router Tests")
class DepartmentRouterTest {

    private DepartmentRouter departmentRouter;
    
    @Mock
    private User mockUser;
    
    @Mock
    private Order mockOrder;

    @BeforeEach
    void setUp() {
        departmentRouter = new DepartmentRouter();
    }

    // ===============================================================================
    // USER DEPARTMENT ROUTER TESTS
    // Function: Routes user messages to department-specific destinations based on user department
    // ===============================================================================

    @Test
    @DisplayName("User Department Router - Valid Engineering Department Input")
    void testUserDepartmentRouter_ValidEngineeringDepartment() {
        // Input Scenarios: Valid department data
        User user = createValidUser("ENGINEERING", User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Should route to tech channel
        String result = router.apply(message);
        assertEquals("user-tech-channel", result);
    }

    @Test
    @DisplayName("User Department Router - Valid Sales Department Input")
    void testUserDepartmentRouter_ValidSalesDepartment() {
        // Input Scenarios: Valid department data
        User user = createValidUser("SALES", User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Should route to business channel
        String result = router.apply(message);
        assertEquals("user-business-channel", result);
    }

    @ParameterizedTest
    @DisplayName("User Department Router - Various Valid Department Inputs")
    @CsvSource({
        "ENGINEERING, user-tech-channel",
        "IT, user-tech-channel", 
        "SALES, user-business-channel",
        "MARKETING, user-business-channel",
        "HR, user-operations-channel",
        "OPERATIONS, user-operations-channel",
        "FINANCE, user-finance-channel",
        "ACCOUNTING, user-finance-channel",
        "EXECUTIVE, user-executive-channel",
        "MANAGEMENT, user-executive-channel"
    })
    void testUserDepartmentRouter_ValidDepartments(String department, String expectedChannel) {
        // Input Scenarios: Multiple valid department inputs
        User user = createValidUser(department, User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Correct channel for each department
        String result = router.apply(message);
        assertEquals(expectedChannel, result);
    }

    @Test
    @DisplayName("User Department Router - Null Department Input")
    void testUserDepartmentRouter_NullDepartment() {
        // Input Scenarios: Edge case with null department
        User user = createValidUser(null, User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Should route to general channel
        String result = router.apply(message);
        assertEquals("user-general-channel", result);
    }

    @ParameterizedTest
    @DisplayName("User Department Router - Empty and Invalid Department Inputs")
    @ValueSource(strings = {"", "   ", "UNKNOWN", "RANDOM", "123", "!@#$"})
    void testUserDepartmentRouter_InvalidDepartments(String department) {
        // Input Scenarios: Edge cases with empty, whitespace, and invalid departments
        User user = createValidUser(department, User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Should route to general channel for unknown departments
        String result = router.apply(message);
        assertEquals("user-general-channel", result);
    }

    @Test
    @DisplayName("User Department Router - Exception Handling with Null Message")
    void testUserDepartmentRouter_ExceptionHandling() {
        // Error Handling: Testing with null message
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Should handle exception and return fallback
        String result = router.apply(null);
        assertEquals("user-default-channel", result);
    }

    @Test
    @DisplayName("User Department Router - Exception Handling with Malformed User")
    void testUserDepartmentRouter_MalformedUser() {
        // Error Handling: Testing with user that has malformed data
        User malformedUser = new User(); // No data set, will cause issues in routing logic
        Message<User> message = MessageBuilder.withPayload(malformedUser).build();
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Should handle gracefully and route to general channel
        String result = router.apply(message);
        assertEquals("user-general-channel", result);
    }

    // ===============================================================================
    // ORDER DEPARTMENT ROUTER TESTS
    // Function: Routes order messages based on customer department and order details
    // ===============================================================================

    @Test
    @DisplayName("Order Department Router - Valid IT Customer Department")
    void testOrderDepartmentRouter_ValidITDepartment() {
        // Input Scenarios: Valid order with IT customer department
        Order order = createValidOrder(new BigDecimal("500"), Order.OrderPriority.NORMAL);
        Map<String, Object> headers = new HashMap<>();
        headers.put("customer-department", "IT");
        Message<Order> message = MessageBuilder.withPayload(order).copyHeaders(headers).build();
        
        Function<Message<Order>, String> router = departmentRouter.orderDepartmentRouter();
        
        // Expected Output: Should route to tech channel
        String result = router.apply(message);
        assertEquals("order-tech-channel", result);
    }

    @Test
    @DisplayName("Order Department Router - High Value Urgent Order Priority Routing")
    void testOrderDepartmentRouter_HighValueUrgentOrder() {
        // Input Scenarios: High value urgent order
        Order order = createValidOrder(new BigDecimal("1500"), Order.OrderPriority.URGENT);
        Map<String, Object> headers = new HashMap<>();
        headers.put("customer-department", "SALES");
        Message<Order> message = MessageBuilder.withPayload(order).copyHeaders(headers).build();
        
        Function<Message<Order>, String> router = departmentRouter.orderDepartmentRouter();
        
        // Expected Output: Should route to priority channel for high value urgent orders
        String result = router.apply(message);
        assertEquals("order-priority-channel", result);
    }

    @Test
    @DisplayName("Order Department Router - Regular Sales Order")
    void testOrderDepartmentRouter_RegularSalesOrder() {
        // Input Scenarios: Regular value normal priority order
        Order order = createValidOrder(new BigDecimal("200"), Order.OrderPriority.NORMAL);
        Map<String, Object> headers = new HashMap<>();
        headers.put("customer-department", "SALES");
        Message<Order> message = MessageBuilder.withPayload(order).copyHeaders(headers).build();
        
        Function<Message<Order>, String> router = departmentRouter.orderDepartmentRouter();
        
        // Expected Output: Should route to business channel
        String result = router.apply(message);
        assertEquals("order-business-channel", result);
    }

    @Test
    @DisplayName("Order Department Router - Null Customer Department")
    void testOrderDepartmentRouter_NullCustomerDepartment() {
        // Input Scenarios: Edge case with null customer department
        Order order = createValidOrder(new BigDecimal("300"), Order.OrderPriority.NORMAL);
        Message<Order> message = MessageBuilder.withPayload(order).build();
        
        Function<Message<Order>, String> router = departmentRouter.orderDepartmentRouter();
        
        // Expected Output: Should route to general channel
        String result = router.apply(message);
        assertEquals("order-general-channel", result);
    }

    @Test
    @DisplayName("Order Department Router - Exception Handling")
    void testOrderDepartmentRouter_ExceptionHandling() {
        // Error Handling: Testing with null message
        Function<Message<Order>, String> router = departmentRouter.orderDepartmentRouter();
        
        // Expected Output: Should handle exception and return fallback
        String result = router.apply(null);
        assertEquals("order-default-channel", result);
    }

    @ParameterizedTest
    @DisplayName("Order Department Router - Boundary Testing for Order Values")
    @CsvSource({
        "0, NORMAL, order-business-channel",
        "999.99, NORMAL, order-business-channel", 
        "1000, NORMAL, order-business-channel",
        "1000.01, NORMAL, order-business-channel",
        "1000, URGENT, order-business-channel",
        "1000.01, URGENT, order-priority-channel"
    })
    void testOrderDepartmentRouter_BoundaryValues(String amount, String priority, String expectedChannel) {
        // Boundary Testing: Testing edge values for order amounts and priorities
        Order order = createValidOrder(new BigDecimal(amount), Order.OrderPriority.valueOf(priority));
        Map<String, Object> headers = new HashMap<>();
        headers.put("customer-department", "SALES");
        Message<Order> message = MessageBuilder.withPayload(order).copyHeaders(headers).build();
        
        Function<Message<Order>, String> router = departmentRouter.orderDepartmentRouter();
        
        // Expected Output: Correct routing based on boundary conditions
        String result = router.apply(message);
        assertEquals(expectedChannel, result);
    }

    // ===============================================================================
    // MULTI-DESTINATION ROUTER TESTS
    // Function: Routes messages to multiple destinations based on department business rules
    // ===============================================================================

    @Test
    @DisplayName("Multi-Destination Router - Executive Department Multiple Destinations")
    void testMultiDestinationRouter_ExecutiveDepartment() {
        // Input Scenarios: Executive department user
        User user = createValidUser("EXECUTIVE", User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userMultiDestinationRouter();
        
        // Expected Output: Should include executive-specific destinations
        String result = router.apply(message);
        assertTrue(result.contains("user-executive-channel"));
        assertTrue(result.contains("executive-notifications"));
        assertTrue(result.contains("vip-processing"));
        assertTrue(result.contains("global-user-events"));
        assertTrue(result.contains("analytics-pipeline"));
    }

    @Test
    @DisplayName("Multi-Destination Router - IT Department Multiple Destinations")
    void testMultiDestinationRouter_ITDepartment() {
        // Input Scenarios: IT department user
        User user = createValidUser("IT", User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userMultiDestinationRouter();
        
        // Expected Output: Should include IT-specific destinations
        String result = router.apply(message);
        assertTrue(result.contains("user-tech-channel"));
        assertTrue(result.contains("it-security-notifications"));
        assertTrue(result.contains("system-admin-alerts"));
        assertTrue(result.contains("global-user-events"));
        assertTrue(result.contains("analytics-pipeline"));
    }

    @Test
    @DisplayName("Multi-Destination Router - Finance Department Multiple Destinations")
    void testMultiDestinationRouter_FinanceDepartment() {
        // Input Scenarios: Finance department user
        User user = createValidUser("FINANCE", User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userMultiDestinationRouter();
        
        // Expected Output: Should include finance-specific destinations
        String result = router.apply(message);
        assertTrue(result.contains("user-finance-channel"));
        assertTrue(result.contains("financial-compliance"));
        assertTrue(result.contains("audit-trail"));
        assertTrue(result.contains("global-user-events"));
        assertTrue(result.contains("analytics-pipeline"));
    }

    @Test
    @DisplayName("Multi-Destination Router - Global Destinations Always Included")
    void testMultiDestinationRouter_GlobalDestinations() {
        // Input Scenarios: Regular department user
        User user = createValidUser("HR", User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userMultiDestinationRouter();
        
        // Expected Output: Should always include global destinations
        String result = router.apply(message);
        assertTrue(result.contains("global-user-events"));
        assertTrue(result.contains("analytics-pipeline"));
        assertTrue(result.contains("user-operations-channel"));
    }

    // ===============================================================================
    // DYNAMIC ROUTER TESTS
    // Function: Routes messages dynamically based on multiple header factors
    // ===============================================================================

    @Test
    @DisplayName("Dynamic Router - Message Type and Department Headers")
    void testDynamicRouter_MessageTypeAndDepartment() {
        // Input Scenarios: Message with both type and department headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(MessageHeaders.EVENT_TYPE, "USER_CREATED");
        headers.put("department", "ENGINEERING");
        headers.put("priority", "HIGH");
        Message<Object> message = MessageBuilder.withPayload((Object) "test").copyHeaders(headers).build();
        
        Function<Message<Object>, String> router = departmentRouter.dynamicDepartmentRouter();
        
        // Expected Output: Should build dynamic destination with priority
        String result = router.apply(message);
        assertEquals("user_created-engineering-priority-channel", result);
    }

    @Test
    @DisplayName("Dynamic Router - Department Only Header")
    void testDynamicRouter_DepartmentOnly() {
        // Input Scenarios: Message with only department header
        Map<String, Object> headers = new HashMap<>();
        headers.put("department", "SALES");
        Message<Object> message = MessageBuilder.withPayload((Object) "test").copyHeaders(headers).build();
        
        Function<Message<Object>, String> router = departmentRouter.dynamicDepartmentRouter();
        
        // Expected Output: Should route to department channel
        String result = router.apply(message);
        assertEquals("dept-sales-channel", result);
    }

    @Test
    @DisplayName("Dynamic Router - Message Type Only Header")
    void testDynamicRouter_MessageTypeOnly() {
        // Input Scenarios: Message with only message type header
        Map<String, Object> headers = new HashMap<>();
        headers.put(MessageHeaders.EVENT_TYPE, "ORDER_PROCESSED");
        Message<Object> message = MessageBuilder.withPayload((Object) "test").copyHeaders(headers).build();
        
        Function<Message<Object>, String> router = departmentRouter.dynamicDepartmentRouter();
        
        // Expected Output: Should route to type channel
        String result = router.apply(message);
        assertEquals("type-order_processed-channel", result);
    }

    @Test
    @DisplayName("Dynamic Router - No Routing Headers")
    void testDynamicRouter_NoHeaders() {
        // Input Scenarios: Edge case with no routing headers
        Message<Object> message = MessageBuilder.withPayload((Object) "test").build();
        
        Function<Message<Object>, String> router = departmentRouter.dynamicDepartmentRouter();
        
        // Expected Output: Should route to default channel
        String result = router.apply(message);
        assertEquals("default-channel", result);
    }

    @ParameterizedTest
    @DisplayName("Dynamic Router - Priority Level Testing")
    @ValueSource(strings = {"HIGH", "URGENT", "high", "urgent", "LOW", "MEDIUM"})
    void testDynamicRouter_PriorityLevels(String priority) {
        // Boundary Testing: Different priority levels
        Map<String, Object> headers = new HashMap<>();
        headers.put(MessageHeaders.EVENT_TYPE, "TEST");
        headers.put("department", "IT");
        headers.put("priority", priority);
        Message<Object> message = MessageBuilder.withPayload((Object) "test").copyHeaders(headers).build();
        
        Function<Message<Object>, String> router = departmentRouter.dynamicDepartmentRouter();
        
        String result = router.apply(message);
        
        // Expected Output: High/Urgent priority should include priority in channel name
        if ("HIGH".equalsIgnoreCase(priority) || "URGENT".equalsIgnoreCase(priority)) {
            assertTrue(result.contains("priority"));
        }
        assertTrue(result.contains("test-it"));
    }

    @Test
    @DisplayName("Dynamic Router - Exception Handling")
    void testDynamicRouter_ExceptionHandling() {
        // Error Handling: Testing with null message
        Function<Message<Object>, String> router = departmentRouter.dynamicDepartmentRouter();
        
        // Expected Output: Should handle exception and return error channel
        String result = router.apply(null);
        assertEquals("error-channel", result);
    }

    // ===============================================================================
    // CONDITIONAL ROUTER TESTS
    // Function: Routes messages based on complex business conditions and user state
    // ===============================================================================

    @Test
    @DisplayName("Conditional Router - Inactive User")
    void testConditionalRouter_InactiveUser() {
        // Input Scenarios: Inactive user
        User user = createValidUser("SALES", User.UserStatus.INACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.conditionalDepartmentRouter();
        
        // Expected Output: Should route to inactive user channel
        String result = router.apply(message);
        assertEquals("inactive-user-channel", result);
    }

    @Test
    @DisplayName("Conditional Router - VIP User with Special Handling")
    void testConditionalRouter_VIPUserWithSpecialHandling() {
        // Input Scenarios: VIP user requiring special handling - but SUSPENDED users are treated as inactive first
        User user = createValidUser("EXECUTIVE", User.UserStatus.SUSPENDED);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.conditionalDepartmentRouter();
        
        // Expected Output: SUSPENDED users are routed to inactive channel first
        String result = router.apply(message);
        assertEquals("inactive-user-channel", result);
    }

    @Test
    @DisplayName("Conditional Router - VIP User Regular")
    void testConditionalRouter_VIPUserRegular() {
        // Input Scenarios: VIP user without special handling needs - but FINANCE is VIP and triggers special handling
        User user = createValidUser("FINANCE", User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.conditionalDepartmentRouter();
        
        // Expected Output: VIP departments trigger special handling
        String result = router.apply(message);
        assertEquals("vip-special-handling-channel", result);
    }

    @Test
    @DisplayName("Conditional Router - New User")
    void testConditionalRouter_NewUser() {
        // Input Scenarios: New user (created within 30 days)
        User user = createValidUser("HR", User.UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now().minusDays(15)); // 15 days ago
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.conditionalDepartmentRouter();
        
        // Expected Output: Should route to new user onboarding channel
        String result = router.apply(message);
        assertEquals("new-user-onboarding-channel", result);
    }

    @Test
    @DisplayName("Conditional Router - Special Handling Required")
    void testConditionalRouter_SpecialHandlingRequired() {
        // Input Scenarios: User requiring special handling - but PENDING users are treated as inactive first
        User user = createValidUser("HR", User.UserStatus.PENDING);
        user.setCreatedAt(LocalDateTime.now().minusDays(60)); // Not new user
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.conditionalDepartmentRouter();
        
        // Expected Output: PENDING users are routed to inactive channel first
        String result = router.apply(message);
        assertEquals("inactive-user-channel", result);
    }

    @Test
    @DisplayName("Conditional Router - Regular Active User")
    void testConditionalRouter_RegularActiveUser() {
        // Input Scenarios: Regular active user
        User user = createValidUser("MARKETING", User.UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now().minusDays(60)); // Not new user
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.conditionalDepartmentRouter();
        
        // Expected Output: Should route to normal department channel
        String result = router.apply(message);
        assertEquals("user-business-channel", result);
    }

    @Test
    @DisplayName("Conditional Router - Exception Handling")
    void testConditionalRouter_ExceptionHandling() {
        // Error Handling: Testing with null message
        Function<Message<User>, String> router = departmentRouter.conditionalDepartmentRouter();
        
        // Expected Output: Should handle exception and return error channel
        String result = router.apply(null);
        assertEquals("error-channel", result);
    }

    // ===============================================================================
    // BOUNDARY AND EDGE CASE TESTS
    // ===============================================================================

    @ParameterizedTest
    @DisplayName("Boundary Testing - Department Name Length Limits")
    @ValueSource(strings = {
        "A", // Single character
        "VERYLONGDEPARTMENTNAMETHATEXCEEDSNORMALLIMITS", // Very long name
        "DEPARTMENT_WITH_UNDERSCORES_AND_NUMBERS_123" // Special characters
    })
    void testBoundaryDepartmentNameLengths(String department) {
        // Boundary Testing: Testing extreme department name lengths
        User user = createValidUser(department, User.UserStatus.ACTIVE);
        Message<User> message = MessageBuilder.withPayload(user).build();
        
        Function<Message<User>, String> router = departmentRouter.userDepartmentRouter();
        
        // Expected Output: Should handle all department name lengths gracefully
        String result = router.apply(message);
        assertNotNull(result);
        assertTrue(result.endsWith("-channel"));
    }

    @Test
    @DisplayName("Edge Case - Extreme Order Values")
    void testEdgeCaseExtremeOrderValues() {
        // Boundary Testing: Testing with extreme order values
        Order order = createValidOrder(new BigDecimal("999999999999.99"), Order.OrderPriority.URGENT);
        Map<String, Object> headers = new HashMap<>();
        headers.put("customer-department", "FINANCE");
        Message<Order> message = MessageBuilder.withPayload(order).copyHeaders(headers).build();
        
        Function<Message<Order>, String> router = departmentRouter.orderDepartmentRouter();
        
        // Expected Output: Should handle extreme values and route to priority channel
        String result = router.apply(message);
        assertEquals("order-priority-channel", result);
    }

    // ===============================================================================
    // HELPER METHODS
    // ===============================================================================

    private User createValidUser(String department, User.UserStatus status) {
        User user = new User();
        user.setId("user-1");
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setDepartment(department);
        user.setStatus(status);
        user.setCreatedAt(LocalDateTime.now().minusDays(60)); // Default to old user
        return user;
    }

    private Order createValidOrder(BigDecimal amount, Order.OrderPriority priority) {
        Order order = new Order();
        order.setId("order-1");
        order.setUserId("user-100");
        order.setTotalAmount(amount);
        order.setPriority(priority);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setOrderDate(LocalDateTime.now());
        return order;
    }
} 