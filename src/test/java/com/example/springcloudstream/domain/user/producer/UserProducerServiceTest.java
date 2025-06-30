package com.example.springcloudstream.domain.user.producer;

import com.example.springcloudstream.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for UserProducerService
 * 
 * Function to Test: Producer functions that send user messages, including sendUser(), 
 * sendUserWithHeaders(), sendUserToPartition(), and sendUserUpdateEvent() that perform 
 * message sending, header processing, and user data publishing.
 * 
 * Test Coverage:
 * - Input Scenarios: Valid users, invalid users, null values, edge cases
 * - Expected Output: Proper message sending, header inclusion, return values
 * - Error Handling: Graceful handling of null values, invalid data, sending failures
 * - Boundary Testing: Extreme values, edge cases, concurrent sending
 * - Dependencies: Mocked StreamBridge to isolate producer logic
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserProducerService Unit Tests")
class UserProducerServiceTest {

    @Mock
    private StreamBridge streamBridge;

    @InjectMocks
    private UserProducerService userProducerService;

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
        
        // Default mock behavior - successful sending
        when(streamBridge.send(anyString(), any())).thenReturn(true);
    }

    // ===============================================================================
    // SEND USER FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send User - Valid User")
    void testSendUser_ValidUser() {
        // Function to Test: sendUser() sends valid user messages
        // Input Scenarios: Valid user with all fields populated
        boolean result = userProducerService.sendUser(validUser);
        
        // Expected Output: Should return true for successful sending
        assertTrue(result);
        
        // Verify StreamBridge was called with correct parameters
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @ParameterizedTest
    @DisplayName("Send User - Various Valid Departments")
    @ValueSource(strings = {"IT", "HR", "FINANCE", "SALES", "MARKETING", "OPERATIONS", "EXECUTIVE"})
    void testSendUser_ValidDepartments(String department) {
        // Input Scenarios: Users from different valid departments
        User user = createValidUser("1", "Test User", "test@example.com", 25, department);
        
        // Expected Output: Should send users from all departments
        boolean result = userProducerService.sendUser(user);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(user));
    }

    @ParameterizedTest
    @DisplayName("Send User - Various User Ages")
    @ValueSource(ints = {18, 25, 35, 45, 55, 65, 75})
    void testSendUser_ValidAges(int age) {
        // Input Scenarios: Users with different valid ages
        User user = createValidUser("1", "Test User", "test@example.com", age, "IT");
        
        // Expected Output: Should send users of all valid ages
        boolean result = userProducerService.sendUser(user);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(user));
    }

    @ParameterizedTest
    @DisplayName("Send User - Various User Statuses")
    @EnumSource(User.UserStatus.class)
    void testSendUser_AllStatuses(User.UserStatus status) {
        // Input Scenarios: Users with different status values
        User user = createValidUser("1", "Test User", "test@example.com", 30, "IT");
        user.setStatus(status);
        
        // Expected Output: Should send users with any status
        boolean result = userProducerService.sendUser(user);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(user));
    }

    @Test
    @DisplayName("Send User - Null User Error Handling")
    void testSendUser_NullUser() {
        // Error Handling: Testing with null user
        // Expected Output: Should return false for null user
        boolean result = userProducerService.sendUser(null);
        assertFalse(result);
        
        // Verify StreamBridge was not called
        verify(streamBridge, never()).send(anyString(), any());
    }

    @Test
    @DisplayName("Send User - Malformed User")
    void testSendUser_MalformedUser() {
        // Error Handling: Testing with user having invalid data
        // Expected Output: Should still attempt to send (validation is business logic)
        boolean result = userProducerService.sendUser(invalidUser);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(invalidUser));
    }

    @Test
    @DisplayName("Send User - StreamBridge Failure")
    void testSendUser_StreamBridgeFailure() {
        // Error Handling: Testing when StreamBridge fails
        when(streamBridge.send(anyString(), any())).thenReturn(false);
        
        // Expected Output: Should return false when sending fails
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("Send User - StreamBridge Exception")
    void testSendUser_StreamBridgeException() {
        // Error Handling: Testing when StreamBridge throws exception
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Send failed"));
        
        // Expected Output: Should return false when exception occurs
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("Send User - Concurrent Sending")
    void testSendUser_ConcurrentSending() throws InterruptedException {
        // Boundary Testing: Testing concurrent message sending
        int threadCount = 10;
        int messagesPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * messagesPerThread);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // Input Scenarios: Multiple threads sending messages concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    User user = createValidUser(
                        String.valueOf(threadId * messagesPerThread + j),
                        "User " + threadId + "-" + j,
                        "user" + threadId + j + "@example.com",
                        25 + j,
                        "IT"
                    );
                    
                    if (userProducerService.sendUser(user)) {
                        successCount.incrementAndGet();
                    }
                    latch.countDown();
                }
            });
        }
        
        // Expected Output: All messages should be sent successfully
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(threadCount * messagesPerThread, successCount.get());
        
        // Verify total invocations
        verify(streamBridge, times(threadCount * messagesPerThread)).send(eq("user-out-0"), any(User.class));
        
        executor.shutdown();
    }

    // ===============================================================================
    // SEND USER WITH HEADERS FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send User With Headers - Valid Parameters")
    void testSendUserWithHeaders_ValidParameters() {
        // Function to Test: sendUserWithHeaders() sends user messages with headers
        String eventType = "user-created";
        String correlationId = "test-correlation-123";
        
        // Input Scenarios: Valid user with headers
        boolean result = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        
        // Expected Output: Should return true for successful sending
        assertTrue(result);
        
        // Capture and verify the message sent
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(validUser, capturedMessage.getPayload());
        assertEquals(eventType, capturedMessage.getHeaders().get("event-type"));
        assertEquals(correlationId, capturedMessage.getHeaders().get("correlation-id"));
        assertEquals("user-service", capturedMessage.getHeaders().get("source-service"));
        assertEquals(validUser.getDepartment(), capturedMessage.getHeaders().get("user-department"));
        assertNotNull(capturedMessage.getHeaders().get("event-timestamp"));
    }

    @ParameterizedTest
    @DisplayName("Send User With Headers - Various Event Types")
    @ValueSource(strings = {"user-created", "user-updated", "user-deleted", "user-activated", "user-deactivated"})
    void testSendUserWithHeaders_EventTypes(String eventType) {
        // Input Scenarios: Different event types
        String correlationId = "test-123";
        
        // Expected Output: Should handle all event types
        boolean result = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(eventType, capturedMessage.getHeaders().get("event-type"));
    }

    @Test
    @DisplayName("Send User With Headers - Null User Error Handling")
    void testSendUserWithHeaders_NullUser() {
        // Error Handling: Testing with null user
        // Expected Output: Should return false for null user
        boolean result = userProducerService.sendUserWithHeaders(null, "user-created", "test-123");
        assertFalse(result);
        
        verify(streamBridge, never()).send(anyString(), any());
    }

    @Test
    @DisplayName("Send User With Headers - Null Parameters")
    void testSendUserWithHeaders_NullParameters() {
        // Error Handling: Testing with null event type and correlation ID
        // Expected Output: Should still send (headers may be null)
        boolean result = userProducerService.sendUserWithHeaders(validUser, null, null);
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertNull(capturedMessage.getHeaders().get("event-type"));
        assertNull(capturedMessage.getHeaders().get("correlation-id"));
    }

    @Test
    @DisplayName("Send User With Headers - Boundary Header Values")
    void testSendUserWithHeaders_BoundaryValues() {
        // Boundary Testing: Testing with extreme header values
        String longEventType = "event-type-" + "x".repeat(1000);
        String longCorrelationId = "correlation-" + "y".repeat(2000);
        
        // Expected Output: Should handle long header values
        boolean result = userProducerService.sendUserWithHeaders(validUser, longEventType, longCorrelationId);
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(longEventType, capturedMessage.getHeaders().get("event-type"));
        assertEquals(longCorrelationId, capturedMessage.getHeaders().get("correlation-id"));
    }

    // ===============================================================================
    // SEND USER TO PARTITION FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send User To Partition - Valid Partition")
    void testSendUserToPartition_ValidPartition() {
        // Function to Test: sendUserToPartition() sends user to specific partition
        int partition = 2;
        
        // Input Scenarios: Valid user and partition number
        boolean result = userProducerService.sendUserToPartition(validUser, partition);
        
        // Expected Output: Should return true for successful sending
        assertTrue(result);
        
        // Capture and verify the message sent
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-partitioned-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(validUser, capturedMessage.getPayload());
        assertEquals(partition, capturedMessage.getHeaders().get("partition"));
        assertEquals("user-created", capturedMessage.getHeaders().get("event-type"));
    }

    @ParameterizedTest
    @DisplayName("Send User To Partition - Various Partition Numbers")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 10, 15, 99})
    void testSendUserToPartition_VariousPartitions(int partition) {
        // Input Scenarios: Different partition numbers
        // Expected Output: Should handle all partition numbers
        boolean result = userProducerService.sendUserToPartition(validUser, partition);
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-partitioned-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(partition, capturedMessage.getHeaders().get("partition"));
    }

    @ParameterizedTest
    @DisplayName("Send User To Partition - Boundary Partition Values")
    @ValueSource(ints = {-1, 0, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void testSendUserToPartition_BoundaryPartitions(int partition) {
        // Boundary Testing: Testing with extreme partition values
        // Expected Output: Should handle boundary partition values
        boolean result = userProducerService.sendUserToPartition(validUser, partition);
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-partitioned-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(partition, capturedMessage.getHeaders().get("partition"));
    }

    @Test
    @DisplayName("Send User To Partition - Null User Error Handling")
    void testSendUserToPartition_NullUser() {
        // Error Handling: Testing with null user
        // Expected Output: Should return false for null user  
        boolean result = userProducerService.sendUserToPartition(null, 2);
        assertFalse(result);
        
        verify(streamBridge, never()).send(anyString(), any());
    }

    // ===============================================================================
    // SEND USER UPDATE EVENT FUNCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Send User Update Event - Valid Update")
    void testSendUserUpdateEvent_ValidUpdate() {
        // Function to Test: sendUserUpdateEvent() sends user update events
        User originalUser = createValidUser("1", "John Doe", "john@example.com", 30, "IT");
        User updatedUser = createValidUser("1", "John Doe", "john@example.com", 30, "HR");
        
        // Input Scenarios: Original and updated user with department change
        boolean result = userProducerService.sendUserUpdateEvent(updatedUser, originalUser);
        
        // Expected Output: Should return true for successful sending
        assertTrue(result);
        
        // Capture and verify the message sent
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-updates-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(updatedUser, capturedMessage.getPayload());
        assertEquals("user-updated", capturedMessage.getHeaders().get("event-type"));
        assertEquals("IT", capturedMessage.getHeaders().get("original-department"));
        assertEquals("HR", capturedMessage.getHeaders().get("new-department"));
        assertEquals(true, capturedMessage.getHeaders().get("department-changed"));
    }

    @ParameterizedTest
    @DisplayName("Send User Update Event - Various Department Changes")
    @CsvSource({
        "IT, HR, true",
        "HR, FINANCE, true",
        "FINANCE, IT, true",
        "SALES, SALES, false",
        "MARKETING, MARKETING, false"
    })
    void testSendUserUpdateEvent_DepartmentChanges(String originalDept, String newDept, boolean changed) {
        // Input Scenarios: Different department change scenarios
        User originalUser = createValidUser("1", "Test User", "test@example.com", 30, originalDept);
        User updatedUser = createValidUser("1", "Test User", "test@example.com", 30, newDept);
        
        // Expected Output: Should detect department changes correctly
        boolean result = userProducerService.sendUserUpdateEvent(updatedUser, originalUser);
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-updates-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(originalDept, capturedMessage.getHeaders().get("original-department"));
        assertEquals(newDept, capturedMessage.getHeaders().get("new-department"));
        assertEquals(changed, capturedMessage.getHeaders().get("department-changed"));
    }

    @Test
    @DisplayName("Send User Update Event - Null Users Error Handling")
    void testSendUserUpdateEvent_NullUsers() {
        // Error Handling: Testing with null users
        User validUser = createValidUser("1", "Test User", "test@example.com", 30, "IT");
        
        // Test null updated user
        boolean result1 = userProducerService.sendUserUpdateEvent(null, validUser);
        assertFalse(result1);
        
        // Test null original user
        boolean result2 = userProducerService.sendUserUpdateEvent(validUser, null);
        assertFalse(result2);
        
        // Test both null
        boolean result3 = userProducerService.sendUserUpdateEvent(null, null);
        assertFalse(result3);
        
        verify(streamBridge, never()).send(anyString(), any());
    }

    @Test
    @DisplayName("Send User Update Event - Exception Handling")
    void testSendUserUpdateEvent_ExceptionHandling() {
        // Error Handling: Testing when StreamBridge throws exception
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Send failed"));
        
        User originalUser = createValidUser("1", "Test User", "test@example.com", 30, "IT");
        User updatedUser = createValidUser("1", "Test User", "test@example.com", 30, "HR");
        
        // Expected Output: Should return false when exception occurs
        boolean result = userProducerService.sendUserUpdateEvent(updatedUser, originalUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-updates-out-0"), any(Message.class));
    }

    // ===============================================================================
    // PERFORMANCE AND LOAD TESTING
    // ===============================================================================

    @Test
    @DisplayName("Performance Testing - High Volume User Sending")
    void testPerformance_HighVolumeUserSending() {
        // Boundary Testing: Testing performance with high volume
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
            
            boolean result = userProducerService.sendUser(user);
            assertTrue(result);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Expected Output: Should send all users within reasonable time
        assertTrue(duration < 5000, "Should send " + userCount + " users within 5 seconds");
        
        // Verify all users were sent
        verify(streamBridge, times(userCount)).send(eq("user-out-0"), any(User.class));
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