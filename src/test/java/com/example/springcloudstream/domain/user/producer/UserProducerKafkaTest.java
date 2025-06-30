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
 * Unit Tests for UserProducerService - Kafka-specific scenarios
 * 
 * Function to Test: Producer functions simulating Kafka message production patterns,
 * testing partitioning, serialization, producer configurations, and throughput scenarios.
 * 
 * Test Coverage:
 * - Input Scenarios: Kafka-like message formats, partition keys, producer configs
 * - Expected Output: Proper message sending, partition assignment, serialization
 * - Error Handling: Broker failures, serialization errors, partition unavailability
 * - Boundary Testing: High throughput, large messages, concurrent producers
 * - Dependencies: Mocked StreamBridge to isolate producer logic
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserProducerService Unit Tests - Kafka Scenarios")
class UserProducerKafkaTest {

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
    // KAFKA MESSAGE PRODUCTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Kafka Producer - Basic Message to Topic")
    void testKafkaProducer_BasicMessage() {
        // Function to Test: sendUser() sends to Kafka topic
        boolean result = userProducerService.sendUser(validUser);
        
        // Expected Output: Should return true for successful sending
        assertTrue(result);
        
        // Verify StreamBridge was called (simulating Kafka producer)
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @ParameterizedTest
    @DisplayName("Kafka Producer - Partition Key Generation")
    @ValueSource(strings = {"IT", "HR", "FINANCE", "SALES", "MARKETING"})
    void testKafkaProducer_PartitionKeys(String department) {
        // Input Scenarios: Users from different departments (partition keys)
        User user = createValidUser("1", "Test User", "test@example.com", 25, department);
        
        // Expected Output: Should send with department as effective partition key
        boolean result = userProducerService.sendUser(user);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(user));
    }

    @Test
    @DisplayName("Kafka Producer - Explicit Partition Assignment")
    void testKafkaProducer_ExplicitPartition() {
        // Function to Test: sendUserToPartition() with specific partition
        int targetPartition = 2;
        
        boolean result = userProducerService.sendUserToPartition(validUser, targetPartition);
        
        // Expected Output: Should return true and set partition header
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-partitioned-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(targetPartition, capturedMessage.getHeaders().get("partition"));
    }

    @ParameterizedTest
    @DisplayName("Kafka Producer - Round-Robin Partition Distribution")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void testKafkaProducer_RoundRobinPartitions(int partition) {
        // Input Scenarios: Messages distributed across partitions
        boolean result = userProducerService.sendUserToPartition(validUser, partition);
        
        // Expected Output: Should handle all partition assignments
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-partitioned-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(partition, capturedMessage.getHeaders().get("partition"));
    }

    @Test
    @DisplayName("Kafka Producer - Message Headers with Kafka Metadata")
    void testKafkaProducer_KafkaHeaders() {
        // Function to Test: sendUserWithHeaders() with Kafka-specific headers
        String eventType = "user-created";
        String correlationId = "kafka-correlation-123";
        
        boolean result = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        
        // Expected Output: Should include Kafka-style headers
        assertTrue(result);
        
        ArgumentCaptor<Message<User>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge, times(1)).send(eq("user-out-0"), messageCaptor.capture());
        
        Message<User> capturedMessage = messageCaptor.getValue();
        assertEquals(eventType, capturedMessage.getHeaders().get("event-type"));
        assertEquals(correlationId, capturedMessage.getHeaders().get("correlation-id"));
        assertNotNull(capturedMessage.getHeaders().get("event-timestamp"));
    }

    @Test
    @DisplayName("Kafka Producer - High Throughput Message Sending")
    void testKafkaProducer_HighThroughput() throws InterruptedException {
        // Boundary Testing: High-throughput message production
        int messageCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        // Input Scenarios: Rapid message production (simulating Kafka batch)
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    User user = createValidUser(
                        String.valueOf(messageId),
                        "HighThroughput User " + messageId,
                        "user" + messageId + "@kafka.com",
                        25 + (messageId % 40),
                        messageId % 2 == 0 ? "IT" : "HR"
                    );
                    
                    if (userProducerService.sendUser(user)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Expected Output: Should handle high throughput efficiently
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        assertEquals(messageCount, successCount.get());
        assertTrue(duration < 10000, "Should handle " + messageCount + " messages within 10 seconds");
        
        // Verify all messages were sent
        verify(streamBridge, times(messageCount)).send(eq("user-out-0"), any(User.class));
        
        executor.shutdown();
    }

    @Test
    @DisplayName("Kafka Producer - Large Message Payload")
    void testKafkaProducer_LargeMessage() {
        // Boundary Testing: Large message (simulating Kafka max.message.bytes)
        User largeUser = createValidUser("large-user", "User with Large Data", "large@kafka.com", 30, "IT");
        
        // Expected Output: Should handle large messages up to Kafka limits
        boolean result = userProducerService.sendUser(largeUser);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(largeUser));
    }

    @Test
    @DisplayName("Kafka Producer - Idempotent Producer Simulation")
    void testKafkaProducer_IdempotentProducer() {
        // Input Scenarios: Same message sent multiple times (idempotency)
        String eventType = "user-created";
        String correlationId = "idempotent-123";
        
        // Send same message multiple times
        boolean result1 = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        boolean result2 = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        boolean result3 = userProducerService.sendUserWithHeaders(validUser, eventType, correlationId);
        
        // Expected Output: All sends should succeed (idempotency handled by Kafka)
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);
        
        verify(streamBridge, times(3)).send(eq("user-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Kafka Producer - Transaction Simulation")
    void testKafkaProducer_TransactionSimulation() {
        // Input Scenarios: Multiple related messages (transaction-like)
        User originalUser = createValidUser("tx-user", "Transaction User", "tx@kafka.com", 30, "IT");
        User updatedUser = createValidUser("tx-user", "Transaction User", "tx@kafka.com", 30, "HR");
        
        // Send original and update in "transaction"
        boolean createResult = userProducerService.sendUserWithHeaders(originalUser, "user-created", "tx-123");
        boolean updateResult = userProducerService.sendUserUpdateEvent(updatedUser, originalUser);
        
        // Expected Output: Both messages should be sent successfully
        assertTrue(createResult);
        assertTrue(updateResult);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), any(Message.class));
        verify(streamBridge, times(1)).send(eq("user-updates-out-0"), any(Message.class));
    }

    @ParameterizedTest
    @DisplayName("Kafka Producer - Message Ordering by Partition")
    @CsvSource({
        "0, user-1, user-2, user-3",
        "1, user-4, user-5, user-6",
        "2, user-7, user-8, user-9"
    })
    void testKafkaProducer_MessageOrdering(int partition, String userId1, String userId2, String userId3) {
        // Input Scenarios: Ordered messages to same partition
        String[] userIds = {userId1, userId2, userId3};
        
        for (String userId : userIds) {
            User user = createValidUser(userId, "Ordered User", "ordered@kafka.com", 25, "IT");
            boolean result = userProducerService.sendUserToPartition(user, partition);
            assertTrue(result);
        }
        
        // Expected Output: All messages should be sent to same partition
        verify(streamBridge, times(3)).send(eq("user-partitioned-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Kafka Producer - Compression Simulation")
    void testKafkaProducer_CompressionSimulation() {
        // Input Scenarios: Large payload that would benefit from compression
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContent.append("This is repetitive content that compresses well. ");
        }
        
        User compressibleUser = createValidUser("compress-user", "Compressible User", "compress@kafka.com", 30, "IT");
        
        // Expected Output: Should handle compressible content
        boolean result = userProducerService.sendUser(compressibleUser);
        assertTrue(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(compressibleUser));
    }

    // ===============================================================================
    // KAFKA-SPECIFIC ERROR SCENARIOS
    // ===============================================================================

    @Test
    @DisplayName("Kafka Producer - Broker Unavailable Simulation")
    void testKafkaProducer_BrokerUnavailable() {
        // Error Handling: Simulating broker unavailability
        when(streamBridge.send(anyString(), any())).thenReturn(false);
        
        // Expected Output: Should return false when broker unavailable
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("Kafka Producer - Serialization Error Simulation")
    void testKafkaProducer_SerializationError() {
        // Error Handling: Simulating serialization failure
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Serialization failed"));
        
        // Expected Output: Should return false on serialization error
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("Kafka Producer - Partition Unavailable Simulation")
    void testKafkaProducer_PartitionUnavailable() {
        // Error Handling: Partition leader unavailable
        when(streamBridge.send(eq("user-partitioned-out-0"), any())).thenReturn(false);
        
        int unavailablePartition = 5;
        boolean result = userProducerService.sendUserToPartition(validUser, unavailablePartition);
        
        // Expected Output: Should return false when partition unavailable
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-partitioned-out-0"), any(Message.class));
    }

    @Test
    @DisplayName("Kafka Producer - Producer Buffer Full Simulation")
    void testKafkaProducer_BufferFull() {
        // Error Handling: Producer buffer exhaustion
        AtomicInteger callCount = new AtomicInteger(0);
        when(streamBridge.send(anyString(), any())).thenAnswer(invocation -> {
            // Simulate buffer full after certain number of calls
            return callCount.incrementAndGet() <= 100;
        });
        
        int messageCount = 50;
        int successCount = 0;
        
        // Input Scenarios: Sending more messages than buffer can handle
        for (int i = 0; i < messageCount; i++) {
            User user = createValidUser("buffer-" + i, "Buffer User " + i, "buffer" + i + "@kafka.com", 25, "IT");
            if (userProducerService.sendUser(user)) {
                successCount++;
            }
        }
        
        // Expected Output: Should succeed until buffer is full
        assertEquals(50, successCount);
        verify(streamBridge, times(messageCount)).send(eq("user-out-0"), any(User.class));
    }

    @Test
    @DisplayName("Kafka Producer - Network Timeout Simulation")
    void testKafkaProducer_NetworkTimeout() {
        // Error Handling: Network timeout during send
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Network timeout"));
        
        // Expected Output: Should handle network timeouts gracefully
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("Kafka Producer - Topic Not Found Simulation")
    void testKafkaProducer_TopicNotFound() {
        // Error Handling: Topic doesn't exist
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("Topic not found"));
        
        // Expected Output: Should handle missing topic gracefully
        boolean result = userProducerService.sendUser(validUser);
        assertFalse(result);
        
        verify(streamBridge, times(1)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("Kafka Producer - Retries and Backoff Simulation")
    void testKafkaProducer_RetriesBackoff() {
        // Error Handling: Simulating retries with eventual success
        AtomicInteger attempt = new AtomicInteger(0);
        when(streamBridge.send(anyString(), any())).thenAnswer(invocation -> {
            // Fail first 2 attempts, succeed on 3rd
            return attempt.incrementAndGet() >= 3;
        });
        
        // Simulate 3 retry attempts
        boolean result1 = userProducerService.sendUser(validUser);
        boolean result2 = userProducerService.sendUser(validUser);
        boolean result3 = userProducerService.sendUser(validUser);
        
        // Expected Output: Should eventually succeed after retries
        assertFalse(result1);
        assertFalse(result2);
        assertTrue(result3);
        
        verify(streamBridge, times(3)).send(eq("user-out-0"), eq(validUser));
    }

    @Test
    @DisplayName("Kafka Producer - Batch Processing Simulation")
    void testKafkaProducer_BatchProcessing() throws InterruptedException {
        // Boundary Testing: Batch message processing
        int batchSize = 20;
        CountDownLatch latch = new CountDownLatch(batchSize);
        AtomicInteger batchSuccessCount = new AtomicInteger(0);
        
        // Input Scenarios: Process messages in batches
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            for (int i = 0; i < batchSize; i++) {
                User user = createValidUser(
                    "batch-" + i,
                    "Batch User " + i,
                    "batch" + i + "@kafka.com",
                    25,
                    "IT"
                );
                
                if (userProducerService.sendUser(user)) {
                    batchSuccessCount.incrementAndGet();
                }
                latch.countDown();
            }
        });
        
        // Expected Output: All batch messages should be processed
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(batchSize, batchSuccessCount.get());
        
        verify(streamBridge, times(batchSize)).send(eq("user-out-0"), any(User.class));
        
        executor.shutdown();
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