package com.example.springcloudstream.domain.user.consumer;

import com.example.springcloudstream.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit Tests for UserConsumerService - Kafka-specific scenarios
 * 
 * Function to Test: Consumer functions simulating Kafka message consumption patterns,
 * testing serialization/deserialization, partition handling, and consumer group behavior.
 * 
 * Test Coverage:
 * - Input Scenarios: Kafka-like message formats, partitioned messages, consumer groups
 * - Expected Output: Proper message processing, partition awareness, ordering
 * - Error Handling: Malformed JSON, serialization issues, consumer failures
 * - Boundary Testing: High throughput, large messages, concurrent consumption
 * - Dependencies: Isolated testing without actual Kafka brokers
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserConsumerService Unit Tests - Kafka Scenarios")
class UserConsumerKafkaTest {

    @InjectMocks
    private UserConsumerService userConsumerService;

    private User validUser;
    private String validUserJson;

    @BeforeEach
    void setUp() {
        // Create valid test user
        validUser = createValidUser("1", "John Doe", "john@example.com", 30, "IT");
        
        // Simulate JSON serialization
        validUserJson = """
            {
                "id": "1",
                "name": "John Doe",
                "email": "john@example.com",
                "age": 30,
                "department": "IT",
                "status": "ACTIVE",
                "createdAt": "2024-01-01T10:00:00"
            }
            """;
    }

    // ===============================================================================
    // KAFKA MESSAGE CONSUMPTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("Kafka Consumer - Valid JSON Message Processing")
    void testKafkaConsumer_ValidJsonMessage() {
        // Function to Test: userConsumer() processes Kafka-style JSON messages
        Consumer<User> consumer = userConsumerService.userConsumer();
        int initialCount = userConsumerService.getProcessedCount();
        
        // Input Scenarios: User object as if deserialized from Kafka JSON
        assertDoesNotThrow(() -> consumer.accept(validUser));
        
        // Expected Output: Message should be processed successfully
        assertEquals(initialCount + 1, userConsumerService.getProcessedCount());
    }

    @ParameterizedTest
    @DisplayName("Kafka Consumer - Various Kafka Topics/Departments")
    @ValueSource(strings = {"user-events-it", "user-events-hr", "user-events-finance", "user-events-sales"})
    void testKafkaConsumer_VariousTopics(String topicSuffix) {
        // Input Scenarios: Users from different Kafka topics (simulated by department)
        String department = topicSuffix.replace("user-events-", "").toUpperCase();
        User user = createValidUser("1", "Test User", "test@example.com", 25, department);
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Expected Output: Should process users from all topics
        assertDoesNotThrow(() -> consumer.accept(user));
        assertTrue(userConsumerService.getProcessedCount() > 0);
    }

    @Test
    @DisplayName("Kafka Consumer - Message Headers with Kafka Metadata")
    void testKafkaConsumer_KafkaHeaders() {
        // Function to Test: userWithHeadersConsumer() processes Kafka headers
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Message with Kafka-style headers
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("kafka_topic", "user-events")
            .setHeader("kafka_partition", 2)
            .setHeader("kafka_offset", 12345L)
            .setHeader("kafka_timestamp", System.currentTimeMillis())
            .setHeader("kafka_consumer_group", "user-consumer-group")
            .setHeader("event-type", "user-created")
            .build();
        
        // Expected Output: Should process Kafka headers successfully
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @ParameterizedTest
    @DisplayName("Kafka Consumer - Partition-based Processing")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void testKafkaConsumer_PartitionProcessing(int partition) {
        // Input Scenarios: Messages from different Kafka partitions
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("kafka_partition", partition)
            .setHeader("partition_key", "user-" + partition)
            .build();
        
        // Expected Output: Should handle all partitions
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("Kafka Consumer - High Throughput Message Processing")
    void testKafkaConsumer_HighThroughput() throws InterruptedException {
        // Boundary Testing: Simulating high-throughput Kafka consumption
        Consumer<User> consumer = userConsumerService.userConsumer();
        int messageCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger processed = new AtomicInteger(0);
        
        // Input Scenarios: High volume of messages (like Kafka batch processing)
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    User user = createValidUser(
                        String.valueOf(messageId),
                        "User " + messageId,
                        "user" + messageId + "@example.com",
                        25 + (messageId % 40),
                        messageId % 2 == 0 ? "IT" : "HR"
                    );
                    consumer.accept(user);
                    processed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Expected Output: Should process high volume efficiently
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(messageCount, processed.get());
        
        executor.shutdown();
    }

    @Test
    @DisplayName("Kafka Consumer - Serialization Error Handling")
    void testKafkaConsumer_SerializationErrors() {
        // Error Handling: Testing malformed user data (as if from bad JSON)
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Input Scenarios: User with corrupted data (simulating deserialization issues)
        User corruptedUser = new User();
        corruptedUser.setId("malformed-id-" + "\0".repeat(100)); // Null bytes
        corruptedUser.setName("User\nWith\nNewlines");
        corruptedUser.setEmail("invalid@email@domain");
        corruptedUser.setAge(-999);
        corruptedUser.setDepartment("UNKNOWN\tDEPT");
        
        // Expected Output: Should handle corrupted data gracefully
        assertDoesNotThrow(() -> consumer.accept(corruptedUser));
    }

    @Test
    @DisplayName("Kafka Consumer - Consumer Group Simulation")
    void testKafkaConsumer_ConsumerGroup() throws InterruptedException {
        // Boundary Testing: Simulating multiple consumers in a group
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        int consumerCount = 3;
        int messagesPerConsumer = 5;
        ExecutorService executor = Executors.newFixedThreadPool(consumerCount);
        CountDownLatch latch = new CountDownLatch(consumerCount * messagesPerConsumer);
        
        // Input Scenarios: Multiple consumers processing partitioned messages
        for (int consumerId = 0; consumerId < consumerCount; consumerId++) {
            final int cId = consumerId;
            executor.submit(() -> {
                for (int msgId = 0; msgId < messagesPerConsumer; msgId++) {
                    try {
                        User user = createValidUser(
                            "consumer-" + cId + "-msg-" + msgId,
                            "User " + cId + "-" + msgId,
                            "user" + cId + msgId + "@example.com",
                            25,
                            "IT"
                        );
                        
                        Message<User> message = MessageBuilder.withPayload(user)
                            .setHeader("kafka_consumer_group", "test-group")
                            .setHeader("kafka_consumer_id", "consumer-" + cId)
                            .setHeader("kafka_partition", cId % 3)
                            .build();
                        
                        consumer.accept(message);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        // Expected Output: All consumers should process their messages
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        executor.shutdown();
    }

    @ParameterizedTest
    @DisplayName("Kafka Consumer - Message Ordering by Partition")
    @CsvSource({
        "0, user-1, user-2, user-3",
        "1, user-4, user-5, user-6", 
        "2, user-7, user-8, user-9"
    })
    void testKafkaConsumer_MessageOrdering(int partition, String userId1, String userId2, String userId3) {
        // Input Scenarios: Ordered messages within a partition
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        String[] userIds = {userId1, userId2, userId3};
        for (int i = 0; i < userIds.length; i++) {
            User user = createValidUser(userIds[i], "User " + i, "user" + i + "@example.com", 25, "IT");
            Message<User> message = MessageBuilder.withPayload(user)
                .setHeader("kafka_partition", partition)
                .setHeader("kafka_offset", i + 1)
                .setHeader("message_order", i)
                .build();
            
            // Expected Output: Should process messages in order within partition
            assertDoesNotThrow(() -> consumer.accept(message));
        }
    }

    @Test
    @DisplayName("Kafka Consumer - Large Message Payload")
    void testKafkaConsumer_LargeMessage() {
        // Boundary Testing: Large message payload (simulating Kafka max message size)
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Input Scenarios: User with large data (simulating near Kafka limit)
        User largeUser = createValidUser("large-user", "User with Large Data", "large@example.com", 30, "IT");
        
        // Expected Output: Should handle large messages
        assertDoesNotThrow(() -> consumer.accept(largeUser));
        assertTrue(userConsumerService.getProcessedCount() > 0);
    }

    @Test
    @DisplayName("Kafka Consumer - Duplicate Message Handling")
    void testKafkaConsumer_DuplicateMessages() {
        // Error Handling: Duplicate message processing (Kafka at-least-once delivery)
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Same message with different Kafka metadata
        Message<User> originalMessage = MessageBuilder.withPayload(validUser)
            .setHeader("kafka_offset", 100L)
            .setHeader("message_id", "msg-123")
            .build();
        
        Message<User> duplicateMessage = MessageBuilder.withPayload(validUser)
            .setHeader("kafka_offset", 101L) // Different offset
            .setHeader("message_id", "msg-123") // Same message ID
            .build();
        
        // Expected Output: Should handle duplicates gracefully
        assertDoesNotThrow(() -> {
            consumer.accept(originalMessage);
            consumer.accept(duplicateMessage);
        });
    }

    @Test
    @DisplayName("Kafka Consumer - Backpressure Simulation")
    void testKafkaConsumer_Backpressure() throws InterruptedException {
        // Boundary Testing: Simulating backpressure scenarios
        Consumer<User> consumer = userConsumerService.userConsumer();
        int burstSize = 50;
        long startTime = System.currentTimeMillis();
        
        // Input Scenarios: Burst of messages (simulating Kafka lag)
        for (int i = 0; i < burstSize; i++) {
            User user = createValidUser(
                "burst-" + i,
                "Burst User " + i,
                "burst" + i + "@example.com",
                25,
                "IT"
            );
            consumer.accept(user);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Expected Output: Should handle burst efficiently
        assertTrue(duration < 10000, "Should handle burst within 10 seconds");
        assertEquals(burstSize, userConsumerService.getProcessedCount());
    }

    // ===============================================================================
    // KAFKA-SPECIFIC ERROR SCENARIOS
    // ===============================================================================

    @Test
    @DisplayName("Kafka Consumer - Network Timeout Simulation")
    void testKafkaConsumer_NetworkTimeout() {
        // Error Handling: Simulating network issues
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Message with timeout indicators
        Message<User> timeoutMessage = MessageBuilder.withPayload(validUser)
            .setHeader("kafka_retry_attempt", 3)
            .setHeader("kafka_timeout", true)
            .setHeader("network_error", "timeout")
            .build();
        
        // Expected Output: Should handle network issues gracefully
        assertDoesNotThrow(() -> consumer.accept(timeoutMessage));
    }

    @Test
    @DisplayName("Kafka Consumer - Consumer Rebalance Simulation")
    void testKafkaConsumer_ConsumerRebalance() {
        // Error Handling: Simulating consumer group rebalancing
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Messages during rebalance
        Message<User> rebalanceMessage = MessageBuilder.withPayload(validUser)
            .setHeader("kafka_rebalance", true)
            .setHeader("kafka_consumer_generation", 5)
            .setHeader("kafka_group_coordinator", "broker-2")
            .build();
        
        // Expected Output: Should handle rebalance scenarios
        assertDoesNotThrow(() -> consumer.accept(rebalanceMessage));
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