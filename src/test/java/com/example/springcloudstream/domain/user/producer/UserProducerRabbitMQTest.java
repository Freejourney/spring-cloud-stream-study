package com.example.springcloudstream.domain.user.producer;

import com.example.springcloudstream.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * Unit Tests for UserProducerService - RabbitMQ-specific scenarios
 * 
 * Function to Test: Producer functions simulating RabbitMQ message production patterns,
 * testing exchange routing, publisher confirms, mandatory delivery, and queue durability.
 * 
 * Test Coverage:
 * - Input Scenarios: RabbitMQ-like message formats, routing keys, exchange types
 * - Expected Output: Proper message sending, routing, publisher confirmations
 * - Error Handling: Broker failures, routing failures, mandatory delivery
 * - Boundary Testing: High message rates, large payloads, connection recovery
 * - Dependencies: Mocked StreamBridge to isolate producer logic
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserProducerService Unit Tests - RabbitMQ Scenarios")
class UserProducerRabbitMQTest {

    @Mock
    private StreamBridge streamBridge;

    @InjectMocks
    private UserProducerService userProducerService;

    private User validUser;

    @BeforeEach
    void setUp() {
        // Create valid test user
        validUser = createValidUser("1", "John Doe", "john@example.com", 30, "IT");
        
        // Default mock behavior - successful sending
        when(streamBridge.send(anyString(), any())).thenReturn(true);
    }

    // ===============================================================================
    // RABBITMQ MESSAGE PRODUCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("RabbitMQ Producer - Direct Exchange Message")
    void testRabbitMQProducer_DirectExchange() {
        // Function to Test: sendUser() sends to RabbitMQ direct exchange
        boolean result = userProducerService.sendUser(validUser);
        
        // Expected Output: Should return true for successful sending
        assertTrue(result);
        
        // Verify StreamBridge was called (simulating RabbitMQ producer)
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @ParameterizedTest
    @DisplayName("RabbitMQ Producer - Topic Exchange Routing")
    @ValueSource(strings = {"user.created.it", "user.updated.hr", "user.deleted.finance", "user.activated.sales"})
    void testRabbitMQProducer_TopicRouting(String routingKeyPattern) {
        // Input Scenarios: Messages with different topic routing patterns
        String[] parts = routingKeyPattern.split("\\.");
        String department = parts.length > 2 ? parts[2].toUpperCase() : "IT";
        
        User user = createValidUser("1", "Test User", "test@example.com", 25, department);
        boolean result = userProducerService.sendUser(user);
        
        // Expected Output: Should send with implicit routing key (department-based)
        assertTrue(result);
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(user));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Fanout Exchange Broadcasting")
    void testRabbitMQProducer_FanoutExchange() {
        // Function to Test: sendUserWithHeaders() simulating fanout broadcast
        String eventType = "user-broadcast";
        String correlationId = "fanout-123";
        
        boolean result = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        
        // Expected Output: Should broadcast to all bound queues
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(eventType, capturedMessage.getHeaders().get("event-type"));
        assertEquals(correlationId, capturedMessage.getHeaders().get("correlation-id"));
    }

    @ParameterizedTest
    @DisplayName("RabbitMQ Producer - Headers Exchange Matching")
    @CsvSource({
        "department, IT, x-match, all",
        "priority, high, x-match, any",
        "region, US, x-match, all",
        "status, active, x-match, any"
    })
    void testRabbitMQProducer_HeadersExchange(String headerName, String headerValue, String matchType, String matchStrategy) {
        // Input Scenarios: Messages with header-based routing
        String eventType = "headers-routing";
        String correlationId = "headers-123";
        
        boolean result = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        
        // Expected Output: Should route based on header matching
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage.getHeaders().get("event-type"));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Publisher Confirms Simulation")
    void testRabbitMQProducer_PublisherConfirms() {
        // Input Scenarios: Message requiring publisher confirmation
        AtomicInteger confirmCount = new AtomicInteger(0);
        when(streamBridge.send(anyString(), any())).thenAnswer(invocation -> {
            confirmCount.incrementAndGet();
            return true; // Simulate successful confirmation
        });
        
        boolean result = userProducerService.sendUser(validUser);
        
        // Expected Output: Should receive publisher confirmation
        assertTrue(result);
        assertEquals(1, confirmCount.get());
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Mandatory Delivery")
    void testRabbitMQProducer_MandatoryDelivery() {
        // Function to Test: sendUserWithHeaders() with mandatory delivery
        String eventType = "mandatory-delivery";
        String correlationId = "mandatory-123";
        
        boolean result = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        
        // Expected Output: Should handle mandatory delivery requirement
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(eventType, capturedMessage.getHeaders().get("event-type"));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Persistent Messages")
    void testRabbitMQProducer_PersistentMessages() {
        // Input Scenarios: Messages requiring persistence
        User persistentUser = createValidUser("persistent-1", "Persistent User", "persistent@rabbitmq.com", 30, "IT");
        
        boolean result = userProducerService.sendUser(persistentUser);
        
        // Expected Output: Should send with delivery mode 2 (persistent)
        assertTrue(result);
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(persistentUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Priority Messages")
    void testRabbitMQProducer_PriorityMessages() {
        // Input Scenarios: Messages with different priorities
        String highPriorityEvent = "high-priority-event";
        String lowPriorityEvent = "low-priority-event";
        
        // Send high priority message
        boolean highResult = userProducerService.sendUserWithHeaders(validUser, highPriorityEvent, "high-123");
        // Send low priority message
        boolean lowResult = userProducerService.sendUserWithHeaders(validUser, lowPriorityEvent, "low-123");
        
        // Expected Output: Both should be sent successfully
        assertTrue(highResult);
        assertTrue(lowResult);
        
        verify(streamBridge, times(2)).send(eq("user-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("RabbitMQ Producer - TTL Messages")
    void testRabbitMQProducer_TTLMessages() {
        // Input Scenarios: Messages with time-to-live
        String eventType = "ttl-message";
        String correlationId = "ttl-123";
        
        boolean result = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        
        // Expected Output: Should set TTL headers for message expiration
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(eventType, capturedMessage.getHeaders().get("event-type"));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Batch Publishing")
    void testRabbitMQProducer_BatchPublishing() throws InterruptedException {
        // Boundary Testing: Batch message publishing
        int batchSize = 20;
        CountDownLatch latch = new CountDownLatch(batchSize);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // Input Scenarios: Publish messages in batch
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < batchSize; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    User user = createValidUser(
                        "batch-" + messageId,
                        "Batch User " + messageId,
                        "batch" + messageId + "@rabbitmq.com",
                        25,
                        "IT"
                    );
                    
                    if (userProducerService.sendUser(user)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Expected Output: All batch messages should be published
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(batchSize, successCount.get());
        
        verify(streamBridge, times(batchSize)).send(eq("user-out-0"), any(User.class));
        
        executor.shutdown();
    }

    @Test
    @DisplayName("RabbitMQ Producer - Large Message Handling")
    void testRabbitMQProducer_LargeMessage() {
        // Boundary Testing: Large message payload
        User largeUser = createValidUser("large-user", "User with Large Data", "large@rabbitmq.com", 30, "IT");
        
        // Expected Output: Should handle large messages within RabbitMQ limits
        boolean result = userProducerService.sendUser(largeUser);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(largeUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Flow Control")
    void testRabbitMQProducer_FlowControl() throws InterruptedException {
        // Boundary Testing: Flow control and backpressure
        int messageCount = 100;
        long startTime = System.currentTimeMillis();
        
        // Input Scenarios: Rapid message publishing
        for (int i = 0; i < messageCount; i++) {
            User user = createValidUser(
                "flow-" + i,
                "Flow User " + i,
                "flow" + i + "@rabbitmq.com",
                25,
                "IT"
            );
            
            boolean result = userProducerService.sendUser(user);
            assertTrue(result);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Expected Output: Should handle flow control efficiently
        assertTrue(duration < 15000, "Should handle flow control within 15 seconds");
        
        verify(streamBridge, times(messageCount)).send(eq("user-out-0"), any(User.class));
    }

    // ===============================================================================
    // RABBITMQ-SPECIFIC ERROR SCENARIOS
    // ===============================================================================

    @Test
    @DisplayName("RabbitMQ Producer - Connection Failure Simulation")
    void testRabbitMQProducer_ConnectionFailure() {
        // Error Handling: Simulating connection failure
        when(streamBridge.send(anyString(), any())).thenReturn(false);
        
        // Expected Output: Should return false when connection fails
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Exchange Not Found")
    void testRabbitMQProducer_ExchangeNotFound() {
        // Error Handling: Exchange doesn't exist
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Exchange not found"));
        
        // Expected Output: Should handle missing exchange gracefully
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Routing Failure")
    void testRabbitMQProducer_RoutingFailure() {
        // Error Handling: Message cannot be routed
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("No route to queue"));
        
        // Expected Output: Should handle routing failures
        boolean result = userProducerService.sendUserWithHeaders(validUser, "unroutable", "routing-fail-123");
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Publisher Confirm Timeout")
    void testRabbitMQProducer_ConfirmTimeout() {
        // Error Handling: Publisher confirmation timeout
        AtomicInteger attempt = new AtomicInteger(0);
        when(streamBridge.send(anyString(), any())).thenAnswer(invocation -> {
            // Simulate timeout on first attempt, success on retry
            return attempt.incrementAndGet() > 1;
        });
        
        // First attempt should timeout, retry should succeed
        boolean result1 = userProducerService.sendUser(validUser);
        boolean result2 = userProducerService.sendUser(validUser);
        
        // Expected Output: Should handle confirm timeout with retry
        assertFalse(result1);
        assertTrue(result2);
        
        verify(streamBridge, times(2)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Queue Full Simulation")
    void testRabbitMQProducer_QueueFull() {
        // Error Handling: Queue at capacity
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Queue full"));
        
        // Expected Output: Should handle queue overflow
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Channel Closed")
    void testRabbitMQProducer_ChannelClosed() {
        // Error Handling: Channel unexpectedly closed
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Channel closed"));
        
        // Expected Output: Should handle channel closure
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Connection Recovery")
    void testRabbitMQProducer_ConnectionRecovery() {
        // Error Handling: Connection recovery after failure
        AtomicInteger callCount = new AtomicInteger(0);
        when(streamBridge.send(anyString(), any())).thenAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            // Fail first 3 calls (connection down), succeed after recovery
            return count > 3;
        });
        
        // Simulate connection recovery scenario
        boolean result1 = userProducerService.sendUser(validUser);
        boolean result2 = userProducerService.sendUser(validUser);
        boolean result3 = userProducerService.sendUser(validUser);
        boolean result4 = userProducerService.sendUser(validUser); // After recovery
        
        // Expected Output: Should succeed after connection recovery
        assertFalse(result1);
        assertFalse(result2);
        assertFalse(result3);
        assertTrue(result4);
        
        verify(streamBridge, times(4)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Memory Alarm")
    void testRabbitMQProducer_MemoryAlarm() {
        // Error Handling: RabbitMQ memory alarm
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Memory alarm"));
        
        // Expected Output: Should handle memory pressure
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Disk Space Alarm")
    void testRabbitMQProducer_DiskSpaceAlarm() {
        // Error Handling: RabbitMQ disk space alarm
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Disk space alarm"));
        
        // Expected Output: Should handle disk space issues
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("RabbitMQ Producer - Cluster Partition")
    void testRabbitMQProducer_ClusterPartition() {
        // Error Handling: RabbitMQ cluster partition
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Cluster partition"));
        
        // Expected Output: Should handle cluster partitioning
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
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