package com.example.springcloudstream.domain.user.consumer;

import com.example.springcloudstream.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit Tests for UserConsumerService
 * 
 * Function to Test: Consumer functions that process user messages, including userConsumer(), 
 * userWithHeadersConsumer(), departmentalUserConsumer(), and userTransformer() that perform 
 * message consumption, header processing, and user data transformation.
 * 
 * Test Coverage:
 * - Input Scenarios: Valid users, invalid users, null values, edge cases
 * - Expected Output: Proper message processing, transformation results, side effects
 * - Error Handling: Graceful handling of null values, invalid data, processing failures
 * - Boundary Testing: Extreme values, edge cases, concurrent processing
 * - Dependencies: Isolated testing without external dependencies
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserConsumerService Unit Tests")
class UserConsumerServiceTest {

    @InjectMocks
    private UserConsumerService userConsumerService;

    private User validUser;
    private User invalidUser;

    @BeforeEach
    void setUp() {
        // Create valid test user
        validUser = createValidUser("1", "John Doe", "john@example.com", 30, "IT");
        
        // Create invalid test user (with problematic data)
        invalidUser = new User();
        invalidUser.setId(""); // Empty ID
        invalidUser.setName(""); // Empty name  
        invalidUser.setEmail("invalid-email"); // Invalid email format
        invalidUser.setAge(-5); // Invalid age
        invalidUser.setDepartment(""); // Empty department
        invalidUser.setStatus(null); // Null status
        invalidUser.setCreatedAt(null); // Null timestamp
    }

    // ===============================================================================
    // USER CONSUMER FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("User Consumer - Valid User Processing")
    void testUserConsumer_ValidUser() {
        // Function to Test: userConsumer() processes valid user messages
        Consumer<User> consumer = userConsumerService.userConsumer();
        int initialCount = userConsumerService.getProcessedCount();
        
        // Input Scenarios: Valid user with all fields populated
        assertDoesNotThrow(() -> consumer.accept(validUser));
        
        // Expected Output: Processing count should increase by 1
        assertEquals(initialCount + 1, userConsumerService.getProcessedCount());
    }

    @ParameterizedTest
    @DisplayName("User Consumer - Various Valid Departments")
    @ValueSource(strings = {"IT", "HR", "FINANCE", "SALES", "MARKETING", "OPERATIONS", "EXECUTIVE"})
    void testUserConsumer_ValidDepartments(String department) {
        // Input Scenarios: Users from different valid departments
        User user = createValidUser("1", "Test User", "test@example.com", 25, department);
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Expected Output: Should process users from all departments
        assertDoesNotThrow(() -> consumer.accept(user));
        assertTrue(userConsumerService.getProcessedCount() > 0);
    }

    @ParameterizedTest
    @DisplayName("User Consumer - Various User Ages")
    @ValueSource(ints = {18, 25, 35, 45, 55, 65, 75})
    void testUserConsumer_ValidAges(int age) {
        // Input Scenarios: Users with different valid ages
        User user = createValidUser("1", "Test User", "test@example.com", age, "IT");
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Expected Output: Should process users of all valid ages
        assertDoesNotThrow(() -> consumer.accept(user));
        assertTrue(userConsumerService.getProcessedCount() > 0);
    }

    @ParameterizedTest
    @DisplayName("User Consumer - Various User Statuses")
    @EnumSource(User.UserStatus.class)
    void testUserConsumer_AllStatuses(User.UserStatus status) {
        // Input Scenarios: Users with different status values
        User user = createValidUser("1", "Test User", "test@example.com", 30, "IT");
        user.setStatus(status);
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Expected Output: Should process users with any status
        assertDoesNotThrow(() -> consumer.accept(user));
        assertTrue(userConsumerService.getProcessedCount() > 0);
    }

    @Test
    @DisplayName("User Consumer - Null User Error Handling")
    void testUserConsumer_NullUser() {
        // Error Handling: Testing with null user
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Expected Output: Should throw IllegalArgumentException for null user
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            consumer.accept(null));
        
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("User message cannot be null", exception.getCause().getMessage());
    }

    @Test
    @DisplayName("User Consumer - Malformed User Error Handling")
    void testUserConsumer_MalformedUser() {
        // Error Handling: Testing with user having invalid data
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Expected Output: Should still process (business logic may handle invalid data)
        assertDoesNotThrow(() -> consumer.accept(invalidUser));
        assertTrue(userConsumerService.getProcessedCount() > 0);
    }

    @Test
    @DisplayName("User Consumer - Concurrent Processing")
    void testUserConsumer_ConcurrentProcessing() throws InterruptedException {
        // Boundary Testing: Testing concurrent message processing
        Consumer<User> consumer = userConsumerService.userConsumer();
        int threadCount = 10;
        int messagesPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * messagesPerThread);
        AtomicInteger exceptions = new AtomicInteger(0);
        
        // Input Scenarios: Multiple threads processing messages concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    try {
                        User user = createValidUser(
                            String.valueOf(threadId * messagesPerThread + j),
                            "User " + threadId + "-" + j,
                            "user" + threadId + j + "@example.com",
                            25 + j,
                            "IT"
                        );
                        consumer.accept(user);
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        // Expected Output: All messages should be processed without exceptions
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(0, exceptions.get());
        assertEquals(threadCount * messagesPerThread, userConsumerService.getProcessedCount());
        
        executor.shutdown();
    }

    // ===============================================================================
    // USER WITH HEADERS CONSUMER FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("User With Headers Consumer - Valid Message")
    void testUserWithHeadersConsumer_ValidMessage() {
        // Function to Test: userWithHeadersConsumer() processes messages with headers
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Message with user payload and headers
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("event-type", "user-created")
            .setHeader("correlation-id", "test-123")
            .setHeader("source-service", "user-service")
            .build();
        
        // Expected Output: Should process message with headers successfully
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @ParameterizedTest
    @DisplayName("User With Headers Consumer - Various Event Types")
    @ValueSource(strings = {"user-created", "user-updated", "user-deleted", "user-activated", "user-deactivated"})
    void testUserWithHeadersConsumer_EventTypes(String eventType) {
        // Input Scenarios: Messages with different event types
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("event-type", eventType)
            .setHeader("correlation-id", "test-123")
            .build();
        
        // Expected Output: Should handle all event types
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("User With Headers Consumer - Missing Headers")
    void testUserWithHeadersConsumer_MissingHeaders() {
        // Error Handling: Testing message without required headers
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        Message<User> message = MessageBuilder.withPayload(validUser).build();
        
        // Expected Output: Should handle missing headers gracefully
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("User With Headers Consumer - Null Message Payload")
    void testUserWithHeadersConsumer_NullPayload() {
        // Error Handling: Testing message creation with null payload
        // Spring MessageBuilder prevents null payloads at framework level
        
        // Expected Output: MessageBuilder should throw IllegalArgumentException for null payload
        assertThrows(IllegalArgumentException.class, () -> {
            MessageBuilder.withPayload((User) null)
                .setHeader("event-type", "user-created")
                .build();
        });
    }

    @Test
    @DisplayName("User With Headers Consumer - Boundary Header Values")
    void testUserWithHeadersConsumer_BoundaryHeaders() {
        // Boundary Testing: Testing with extreme header values
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Headers with boundary values
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("event-type", "") // Empty string
            .setHeader("correlation-id", "a".repeat(1000)) // Very long string
            .setHeader("source-service", "service-with-very-long-name-that-exceeds-normal-limits")
            .setHeader("custom-header", null) // Null header value
            .build();
        
        // Expected Output: Should handle boundary header values
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    // ===============================================================================
    // DEPARTMENTAL USER CONSUMER FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Departmental User Consumer - Valid Departmental Message")
    void testDepartmentalUserConsumer_ValidMessage() {
        // Function to Test: departmentalUserConsumer() processes department-specific messages
        Consumer<Message<User>> consumer = userConsumerService.departmentalUserConsumer();
        
        // Input Scenarios: Message with department-specific headers
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("department", "IT")
            .setHeader("event-type", "departmental-user-created")
            .build();
        
        // Expected Output: Should process departmental message successfully
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @ParameterizedTest
    @DisplayName("Departmental User Consumer - Various Departments")
    @CsvSource({
        "IT, user-created",
        "HR, user-updated", 
        "FINANCE, user-activated",
        "SALES, user-deactivated",
        "MARKETING, user-deleted",
        "OPERATIONS, user-transferred"
    })
    void testDepartmentalUserConsumer_DepartmentEvents(String department, String eventType) {
        // Input Scenarios: Different department and event type combinations
        Consumer<Message<User>> consumer = userConsumerService.departmentalUserConsumer();
        User user = createValidUser("1", "Test User", "test@example.com", 30, department);
        
        Message<User> message = MessageBuilder.withPayload(user)
            .setHeader("department", department)
            .setHeader("event-type", eventType)
            .build();
        
        // Expected Output: Should handle all department-event combinations
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("Departmental User Consumer - Missing Department Header")
    void testDepartmentalUserConsumer_MissingDepartment() {
        // Error Handling: Testing message without department header
        Consumer<Message<User>> consumer = userConsumerService.departmentalUserConsumer();
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("event-type", "user-created")
            .build();
        
        // Expected Output: Should handle missing department header gracefully
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    // ===============================================================================
    // USER TRANSFORMER FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("User Transformer - Valid User Transformation")
    void testUserTransformer_ValidUser() {
        // Function to Test: userTransformer() transforms user messages
        Function<User, User> transformer = userConsumerService.userTransformer();
        
        // Input Scenarios: Valid user object
        User result = transformer.apply(validUser);
        
        // Expected Output: Should return transformed user
        assertNotNull(result);
        assertEquals(validUser.getId(), result.getId());
        assertNotNull(result.getName());
        assertEquals(validUser.getName().toUpperCase(), result.getName()); // Name should be uppercase
        assertEquals(validUser.getEmail().toLowerCase(), result.getEmail()); // Email should be lowercase
    }

    @Test
    @DisplayName("User Transformer - Various Input Users")
    void testUserTransformer_VariousUsers() {
        // Input Scenarios: Different user configurations
        Function<User, User> transformer = userConsumerService.userTransformer();
        
        String[] departments = {"IT", "HR", "FINANCE", "SALES"};
        User.UserStatus[] statuses = {User.UserStatus.ACTIVE, User.UserStatus.INACTIVE, User.UserStatus.PENDING};
        
        for (String dept : departments) {
            for (User.UserStatus status : statuses) {
                User user = createValidUser("1", "Test User", "test@example.com", 30, dept);
                user.setStatus(status);
                
                // Expected Output: Should transform all user combinations
                User result = transformer.apply(user);
                assertNotNull(result);
                assertEquals(user.getId(), result.getId());
                assertEquals(dept, result.getDepartment());
                assertEquals(status, result.getStatus());
            }
        }
    }

    @Test
    @DisplayName("User Transformer - Null User Error Handling")
    void testUserTransformer_NullUser() {
        // Error Handling: Testing with null user
        Function<User, User> transformer = userConsumerService.userTransformer();
        
        // Expected Output: Should throw RuntimeException for null user
        assertThrows(RuntimeException.class, () -> transformer.apply(null));
    }

    @Test
    @DisplayName("User Transformer - Boundary Values")
    void testUserTransformer_BoundaryValues() {
        // Boundary Testing: Testing with extreme user values
        Function<User, User> transformer = userConsumerService.userTransformer();
        
        // Input Scenarios: User with valid boundary values (avoiding empty name which would throw exception)
        User boundaryUser = new User();
        boundaryUser.setId("a".repeat(255)); // Very long ID
        boundaryUser.setName("A"); // Single character name (valid)
        boundaryUser.setEmail("x@y.z"); // Minimal email
        boundaryUser.setAge(0); // Minimum age
        boundaryUser.setDepartment("X"); // Single character department
        boundaryUser.setStatus(User.UserStatus.ACTIVE);
        boundaryUser.setCreatedAt(LocalDateTime.now());
        
        // Expected Output: Should handle boundary values and transform correctly
        User result = transformer.apply(boundaryUser);
        assertNotNull(result);
        assertEquals(boundaryUser.getId(), result.getId());
        assertEquals("A", result.getName()); // Name should remain "A" when uppercased
    }

    @Test
    @DisplayName("User Transformer - Performance Testing")
    void testUserTransformer_Performance() {
        // Boundary Testing: Testing transformer performance with multiple users
        Function<User, User> transformer = userConsumerService.userTransformer();
        int userCount = 1000;
        
        long startTime = System.currentTimeMillis();
        
        // Input Scenarios: Large number of users for performance testing
        for (int i = 0; i < userCount; i++) {
            User user = createValidUser(
                String.valueOf(i),
                "User " + i,
                "user" + i + "@example.com",
                25 + (i % 40),
                i % 2 == 0 ? "IT" : "HR"
            );
            
            User result = transformer.apply(user);
            assertNotNull(result);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Expected Output: Should process all users within reasonable time
        assertTrue(duration < 5000, "Should process " + userCount + " users within 5 seconds");
    }

    // ===============================================================================
    // HELPER METHODS
    // ===============================================================================

    private User createValidUser(String id, String name, String email, int age, String department) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setAge(age);
        user.setDepartment(department);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
} 