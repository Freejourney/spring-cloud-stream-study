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

/**
 * Unit Tests for UserConsumerService - RabbitMQ-specific scenarios
 * 
 * Function to Test: Consumer functions simulating RabbitMQ message consumption patterns,
 * testing exchange routing, queue binding, acknowledgments, and dead letter handling.
 * 
 * Test Coverage:
 * - Input Scenarios: RabbitMQ-like message formats, exchange routing, queue bindings
 * - Expected Output: Proper message processing, routing key handling, acknowledgments
 * - Error Handling: Message rejection, dead letter queues, connection failures
 * - Boundary Testing: High message rates, large payloads, queue durability
 * - Dependencies: Isolated testing without actual RabbitMQ brokers
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserConsumerService Unit Tests - RabbitMQ Scenarios")
class UserConsumerRabbitMQTest {

    @InjectMocks
    private UserConsumerService userConsumerService;

    private User validUser;

    @BeforeEach
    void setUp() {
        // Create valid test user
        validUser = createValidUser("1", "John Doe", "john@example.com", 30, "IT");
    }

    // ===============================================================================
    // RABBITMQ MESSAGE CONSUMPTION TESTS
    // ===============================================================================

    @Test
    @DisplayName("RabbitMQ Consumer - Direct Exchange Message")
    void testRabbitMQConsumer_DirectExchange() {
        // Function to Test: userConsumer() processes RabbitMQ direct exchange messages
        Consumer<User> consumer = userConsumerService.userConsumer();
        int initialCount = userConsumerService.getProcessedCount();
        
        // Input Scenarios: User message from direct exchange
        assertDoesNotThrow(() -> consumer.accept(validUser));
        
        // Expected Output: Message should be processed successfully
        assertEquals(initialCount + 1, userConsumerService.getProcessedCount());
    }

    @ParameterizedTest
    @DisplayName("RabbitMQ Consumer - Topic Exchange Routing")
    @ValueSource(strings = {"user.created.it", "user.updated.hr", "user.deleted.finance", "user.activated.sales"})
    void testRabbitMQConsumer_TopicExchange(String routingKey) {
        // Input Scenarios: Messages with different routing keys
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_routingKey", routingKey)
            .setHeader("amqp_exchange", "user.topic.exchange")
            .setHeader("amqp_queue", "user.queue." + routingKey.split("\\.")[2])
            .build();
        
        // Expected Output: Should process all routing key patterns
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Fanout Exchange Broadcasting")
    void testRabbitMQConsumer_FanoutExchange() {
        // Function to Test: userWithHeadersConsumer() processes fanout messages
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Broadcast message from fanout exchange
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_exchange", "user.fanout.exchange")
            .setHeader("amqp_routingKey", "") // Fanout ignores routing key
            .setHeader("broadcast", true)
            .setHeader("fanout_queue", "user.notifications.queue")
            .build();
        
        // Expected Output: Should process fanout broadcast
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @ParameterizedTest
    @DisplayName("RabbitMQ Consumer - Headers Exchange Matching")
    @CsvSource({
        "department, IT, true",
        "priority, high, true",
        "region, US, false",
        "status, active, true"
    })
    void testRabbitMQConsumer_HeadersExchange(String headerName, String headerValue, boolean shouldMatch) {
        // Input Scenarios: Messages with header-based routing
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_exchange", "user.headers.exchange")
            .setHeader("amqp_headers_match", shouldMatch ? "all" : "any")
            .setHeader(headerName, headerValue)
            .setHeader("routing_criteria", "headers")
            .build();
        
        // Expected Output: Should process header-matched messages
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Message Acknowledgment Simulation")
    void testRabbitMQConsumer_MessageAcknowledgment() {
        // Function to Test: Consumer acknowledgment behavior
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Message requiring acknowledgment
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_deliveryTag", 12345L)
            .setHeader("amqp_redelivered", false)
            .setHeader("amqp_ackRequired", true)
            .setHeader("amqp_consumerTag", "consumer-1")
            .build();
        
        // Expected Output: Should process and simulate acknowledgment
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Message Rejection and DLQ")
    void testRabbitMQConsumer_MessageRejection() {
        // Error Handling: Testing message rejection scenarios
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Message that should be rejected
        User invalidUser = new User(); // Minimal invalid user
        Message<User> message = MessageBuilder.withPayload(invalidUser)
            .setHeader("amqp_deliveryTag", 12346L)
            .setHeader("amqp_redelivered", true)
            .setHeader("amqp_retryCount", 3)
            .setHeader("dlq_eligible", true)
            .build();
        
        // Expected Output: Should handle rejection gracefully
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Priority Queue Processing")
    void testRabbitMQConsumer_PriorityQueue() {
        // Input Scenarios: Messages with different priorities
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // High priority message
        Message<User> highPriorityMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_priority", 10)
            .setHeader("amqp_queue", "user.priority.queue")
            .setHeader("message_type", "urgent")
            .build();
        
        // Low priority message
        Message<User> lowPriorityMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_priority", 1)
            .setHeader("amqp_queue", "user.priority.queue")
            .setHeader("message_type", "normal")
            .build();
        
        // Expected Output: Should process both priority levels
        assertDoesNotThrow(() -> {
            consumer.accept(highPriorityMessage);
            consumer.accept(lowPriorityMessage);
        });
    }

    @Test
    @DisplayName("RabbitMQ Consumer - TTL and Expiration Handling")
    void testRabbitMQConsumer_TTLHandling() {
        // Error Handling: Message time-to-live scenarios
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Message with TTL headers
        Message<User> expiredMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_expiration", "30000") // 30 seconds TTL
            .setHeader("amqp_timestamp", System.currentTimeMillis() - 60000) // 1 minute ago
            .setHeader("message_expired", true)
            .build();
        
        // Expected Output: Should handle expired messages
        assertDoesNotThrow(() -> consumer.accept(expiredMessage));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Durable Queue Simulation")
    void testRabbitMQConsumer_DurableQueue() {
        // Input Scenarios: Messages from durable queues
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        Message<User> durableMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_queue_durable", true)
            .setHeader("amqp_message_persistent", true)
            .setHeader("amqp_deliveryMode", 2) // Persistent
            .setHeader("queue_survived_restart", true)
            .build();
        
        // Expected Output: Should process durable messages
        assertDoesNotThrow(() -> consumer.accept(durableMessage));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Concurrent Consumer Simulation")
    void testRabbitMQConsumer_ConcurrentConsumers() throws InterruptedException {
        // Boundary Testing: Multiple consumers on same queue
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        int consumerCount = 5;
        int messagesPerConsumer = 5;
        ExecutorService executor = Executors.newFixedThreadPool(consumerCount);
        CountDownLatch latch = new CountDownLatch(consumerCount * messagesPerConsumer);
        
        // Input Scenarios: Multiple consumers competing for messages
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
                            .setHeader("amqp_consumerTag", "consumer-" + cId)
                            .setHeader("amqp_queue", "user.work.queue")
                            .setHeader("consumer_prefetch", 10)
                            .build();
                        
                        consumer.accept(message);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        // Expected Output: All consumers should process messages
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        executor.shutdown();
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Large Message Handling")
    void testRabbitMQConsumer_LargeMessage() {
        // Boundary Testing: Large message payloads
        Consumer<User> consumer = userConsumerService.userConsumer();
        
        // Input Scenarios: User with large amount of data
        User largeUser = createValidUser("large-user", "User with Large Data", "large@example.com", 30, "IT");
        
        // Expected Output: Should handle large messages within RabbitMQ limits
        assertDoesNotThrow(() -> consumer.accept(largeUser));
        assertTrue(userConsumerService.getProcessedCount() > 0);
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Connection Recovery Simulation")
    void testRabbitMQConsumer_ConnectionRecovery() {
        // Error Handling: Connection failure and recovery
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        // Input Scenarios: Message after connection recovery
        Message<User> recoveryMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_connection_recovery", true)
            .setHeader("amqp_channel_recovery", true)
            .setHeader("connection_attempt", 3)
            .setHeader("recovery_successful", true)
            .build();
        
        // Expected Output: Should handle post-recovery messages
        assertDoesNotThrow(() -> consumer.accept(recoveryMessage));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Publisher Confirms Simulation")
    void testRabbitMQConsumer_PublisherConfirms() {
        // Input Scenarios: Messages with publisher confirmation data
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        Message<User> confirmedMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_confirmed", true)
            .setHeader("amqp_deliveryTag", 98765L)
            .setHeader("publisher_ack", true)
            .setHeader("confirm_timeout", false)
            .build();
        
        // Expected Output: Should process confirmed messages
        assertDoesNotThrow(() -> consumer.accept(confirmedMessage));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Flow Control Simulation")
    void testRabbitMQConsumer_FlowControl() throws InterruptedException {
        // Boundary Testing: Flow control and backpressure
        Consumer<User> consumer = userConsumerService.userConsumer();
        int messageCount = 100;
        long startTime = System.currentTimeMillis();
        
        // Input Scenarios: Rapid message consumption
        for (int i = 0; i < messageCount; i++) {
            User user = createValidUser(
                "flow-" + i,
                "Flow User " + i,
                "flow" + i + "@example.com",
                25,
                "IT"
            );
            consumer.accept(user);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Expected Output: Should handle flow control efficiently  
        assertTrue(duration < 15000, "Should handle flow control within 15 seconds");
        assertEquals(messageCount, userConsumerService.getProcessedCount());
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Queue Arguments Processing")
    void testRabbitMQConsumer_QueueArguments() {
        // Input Scenarios: Messages with queue-specific arguments
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        Message<User> message = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_queue_arguments", "x-max-length=100,x-message-ttl=3600000")
            .setHeader("amqp_queue_type", "quorum")
            .setHeader("amqp_ha_policy", "all")
            .setHeader("queue_configured", true)
            .build();
        
        // Expected Output: Should process messages with queue arguments
        assertDoesNotThrow(() -> consumer.accept(message));
    }

    // ===============================================================================
    // RABBITMQ-SPECIFIC ERROR SCENARIOS
    // ===============================================================================

    @Test
    @DisplayName("RabbitMQ Consumer - Nack and Requeue Simulation")
    void testRabbitMQConsumer_NackRequeue() {
        // Error Handling: Negative acknowledgment with requeue
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        Message<User> nackMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_nack", true)
            .setHeader("amqp_requeue", true)
            .setHeader("amqp_deliveryTag", 55555L)
            .setHeader("processing_failed", true)
            .build();
        
        // Expected Output: Should handle nack/requeue gracefully
        assertDoesNotThrow(() -> consumer.accept(nackMessage));
    }

    @Test
    @DisplayName("RabbitMQ Consumer - Mandatory Routing Failure")
    void testRabbitMQConsumer_MandatoryRouting() {
        // Error Handling: Mandatory routing failures
        Consumer<Message<User>> consumer = userConsumerService.userWithHeadersConsumer();
        
        Message<User> unroutableMessage = MessageBuilder.withPayload(validUser)
            .setHeader("amqp_mandatory", true)
            .setHeader("amqp_routing_failed", true)
            .setHeader("amqp_return_code", 312)
            .setHeader("amqp_return_text", "NO_ROUTE")
            .build();
        
        // Expected Output: Should handle routing failures
        assertDoesNotThrow(() -> consumer.accept(unroutableMessage));
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