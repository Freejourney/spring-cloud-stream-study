package com.example.springcloudstream.integration.transformer;

import com.example.springcloudstream.domain.user.model.User;
import com.example.springcloudstream.domain.user.model.UserEvent;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Unit Tests for UserTransformer
 * 
 * Function to Test: User message transformation patterns including enrichment,
 * normalization, event conversion, legacy format transformation, and audit trails.
 * 
 * Test Coverage:
 * - Input Scenarios: Various user data formats, different message headers, edge cases
 * - Expected Output: Properly transformed messages with correct headers and payloads
 * - Error Handling: Invalid data, null values, transformation failures
 * - Boundary Testing: Large messages, special characters, different formats
 * - Dependencies: No external dependencies, isolated transformation testing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserTransformer Integration Tests")
class UserTransformerTest {

    @InjectMocks
    private UserTransformer userTransformer;

    private User validUser;
    private User executiveUser;
    private User newUser;
    private Message<User> validMessage;

    @BeforeEach
    void setUp() {
        // Create valid test user
        validUser = User.builder()
                .id("USER-001")
                .name("John Doe")
                .email("john.doe@example.com")
                .age(30)
                .department("Engineering")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();

        // Create executive user
        executiveUser = User.builder()
                .id("USER-EXEC-001")
                .name("Jane Smith")
                .email("jane.smith@example.com")
                .age(45)
                .department("Executive")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(365))
                .build();

        // Create new user
        newUser = User.builder()
                .id("USER-NEW-001")
                .name("Bob Wilson")
                .email("bob.wilson@example.com")
                .age(25)
                .department("Marketing")
                .status(User.UserStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // Create valid message
        validMessage = MessageBuilder
                .withPayload(validUser)
                .setHeader(MessageHeaders.EVENT_TYPE, "USER_CREATED")
                .setHeader(MessageHeaders.SOURCE_SERVICE, "user-service")
                .setHeader(MessageHeaders.CORRELATION_ID, "CORR-001")
                .build();
    }

    // ===============================================================================
    // USER ENRICHMENT TRANSFORMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("User Enricher - Successful Enrichment")
    void testUserEnricher_SuccessfulEnrichment() {
        // Function to Test: userEnricher() enriches user messages with metadata
        Function<Message<User>, Message<User>> enricher = userTransformer.userEnricher();
        
        // Input Scenarios: Valid user message for enrichment
        Message<User> enrichedMessage = enricher.apply(validMessage);
        
        // Expected Output: Message should be enriched with additional headers
        assertNotNull(enrichedMessage);
        assertEquals(validUser, enrichedMessage.getPayload());
        
        // Verify enrichment headers
        assertTrue(enrichedMessage.getHeaders().containsKey("enrichment-timestamp"));
        assertEquals("1.0", enrichedMessage.getHeaders().get("enrichment-version"));
        assertEquals("enriched", enrichedMessage.getHeaders().get("processing-stage"));
        assertEquals("engineering", enrichedMessage.getHeaders().get("user-domain"));
        assertTrue(enrichedMessage.getHeaders().containsKey("user-tier"));
        assertEquals("user-enrichment", enrichedMessage.getHeaders().get("transformation-applied"));
    }

    @ParameterizedTest
    @DisplayName("User Enricher - Different User Tiers")
    @CsvSource({
        "Executive, VIP",
        "Engineering, PREMIUM", 
        "Marketing, STANDARD",
        "Sales, STANDARD",
        "Support, BASIC"
    })
    void testUserEnricher_DifferentUserTiers(String department, String expectedTier) {
        // Input Scenarios: Users from different departments
        User user = User.builder()
                .id("USER-TIER-TEST")
                .name("Test User")
                .email("test@example.com")
                .age(30)
                .department(department)
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> message = MessageBuilder.withPayload(user).build();
        Function<Message<User>, Message<User>> enricher = userTransformer.userEnricher();
        
        Message<User> enrichedMessage = enricher.apply(message);
        
        // Expected Output: User tier should be calculated based on department
        assertEquals(expectedTier, enrichedMessage.getHeaders().get("user-tier"));
    }

    @Test
    @DisplayName("User Enricher - Exception Handling")
    void testUserEnricher_ExceptionHandling() {
        // Error Handling: Test with user that has null department to trigger edge case
        User userWithNullDepartment = User.builder()
                .id("USER-NULL-DEPT")
                .name("Test User")
                .email("test@example.com")
                .age(30)
                .department(null)  // Null department should be handled gracefully
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
                
        Message<User> message = MessageBuilder.withPayload(userWithNullDepartment).build();
        Function<Message<User>, Message<User>> enricher = userTransformer.userEnricher();
        
        // Expected Output: Should handle null department gracefully
        assertDoesNotThrow(() -> {
            Message<User> enrichedMessage = enricher.apply(message);
            assertNotNull(enrichedMessage);
            assertEquals("general", enrichedMessage.getHeaders().get("user-domain"));
            assertEquals("STANDARD", enrichedMessage.getHeaders().get("user-tier"));
        });
    }

    // ===============================================================================
    // USER NORMALIZATION TRANSFORMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("User Normalizer - Successful Normalization")
    void testUserNormalizer_SuccessfulNormalization() {
        // Function to Test: userNormalizer() normalizes user data format
        User unnormalizedUser = User.builder()
                .id("  user-001  ")  // Extra spaces
                .name("  JOHN DOE  ")  // Mixed case with spaces
                .email("  JOHN.DOE@EXAMPLE.COM  ")  // Uppercase with spaces
                .age(30)
                .department("Engineering")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> message = MessageBuilder.withPayload(unnormalizedUser).build();
        Function<Message<User>, Message<User>> normalizer = userTransformer.userNormalizer();
        
        // Input Scenarios: User with unnormalized data
        Message<User> normalizedMessage = normalizer.apply(message);
        
        // Expected Output: User data should be normalized
        User normalizedUser = normalizedMessage.getPayload();
        assertNotNull(normalizedUser);
        assertEquals("user-001", normalizedUser.getId());  // Trimmed and lowercase
        assertEquals("John Doe", normalizedUser.getName());  // Proper case
        assertEquals("john.doe@example.com", normalizedUser.getEmail());  // Lowercase email
        
        // Verify normalization headers
        assertTrue(normalizedMessage.getHeaders().containsKey("normalization-timestamp"));
        assertEquals("id,name,email", normalizedMessage.getHeaders().get("normalization-rules-applied"));
        assertEquals("normalized", normalizedMessage.getHeaders().get("processing-stage"));
        assertEquals("user-normalization", normalizedMessage.getHeaders().get("transformation-applied"));
    }

    @ParameterizedTest
    @DisplayName("User Normalizer - Different Name Formats")
    @ValueSource(strings = {
        "john doe",
        "JOHN DOE", 
        "John DOE",
        "jOhN dOe",
        "  john   doe  "
    })
    void testUserNormalizer_DifferentNameFormats(String inputName) {
        // Input Scenarios: Various name formatting issues
        User user = User.builder()
                .id("USER-NAME-TEST")
                .name(inputName)
                .email("test@example.com")
                .age(30)
                .department("Engineering")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> message = MessageBuilder.withPayload(user).build();
        Function<Message<User>, Message<User>> normalizer = userTransformer.userNormalizer();
        
        Message<User> normalizedMessage = normalizer.apply(message);
        
        // Expected Output: All name formats should be normalized to "John Doe"
        assertEquals("John Doe", normalizedMessage.getPayload().getName());
    }

    // ===============================================================================
    // USER TO EVENT TRANSFORMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("User to UserEvent - USER_CREATED Event")
    void testUserToUserEvent_UserCreatedEvent() {
        // Function to Test: userToUserEvent() converts User to UserEvent
        Message<User> message = MessageBuilder
                .withPayload(validUser)
                .setHeader("event-type", "USER_CREATED")
                .build();

        Function<Message<User>, Message<UserEvent>> transformer = userTransformer.userToUserEvent();
        
        // Input Scenarios: User creation event
        Message<UserEvent> eventMessage = transformer.apply(message);
        
        // Expected Output: Should create USER_CREATED event
        assertNotNull(eventMessage);
        UserEvent userEvent = eventMessage.getPayload();
        assertNotNull(userEvent);
        assertEquals("USER_CREATED", userEvent.getEventType().name());
        assertEquals(validUser.getId(), userEvent.getUser().getId());
        
        // Verify event headers
        assertEquals("USER_CREATED", eventMessage.getHeaders().get(MessageHeaders.EVENT_TYPE));
        assertEquals("user-transformer", eventMessage.getHeaders().get(MessageHeaders.SOURCE_SERVICE));
        assertTrue(eventMessage.getHeaders().containsKey(MessageHeaders.CORRELATION_ID));
        assertEquals(validUser.getDepartment(), eventMessage.getHeaders().get("user-department"));
        assertEquals("user-to-event", eventMessage.getHeaders().get("transformation-applied"));
    }

    @ParameterizedTest
    @DisplayName("User to UserEvent - Different Event Types")
    @ValueSource(strings = {"USER_CREATED", "USER_UPDATED", "USER_DELETED"})
    void testUserToUserEvent_DifferentEventTypes(String eventType) {
        // Input Scenarios: Different types of user events
        Message<User> message = MessageBuilder
                .withPayload(validUser)
                .setHeader("event-type", eventType)
                .build();

        Function<Message<User>, Message<UserEvent>> transformer = userTransformer.userToUserEvent();
        
        Message<UserEvent> eventMessage = transformer.apply(message);
        
        // Expected Output: Should create appropriate event type
        assertEquals(eventType, eventMessage.getPayload().getEventType().name());
        assertEquals(eventType, eventMessage.getHeaders().get(MessageHeaders.EVENT_TYPE));
    }

    // ===============================================================================
    // LEGACY FORMAT TRANSFORMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("Legacy Format Transformer - Successful Conversion")
    void testLegacyFormatTransformer_SuccessfulConversion() {
        // Function to Test: userToLegacyFormat() converts to legacy format
        Function<Message<User>, Message<Map<String, Object>>> transformer = 
                userTransformer.userToLegacyFormat();
        
        // Input Scenarios: Modern user format for legacy conversion
        Message<Map<String, Object>> legacyMessage = transformer.apply(validMessage);
        
        // Expected Output: Should convert to legacy format
        assertNotNull(legacyMessage);
        Map<String, Object> legacyData = legacyMessage.getPayload();
        
        // Verify legacy format structure
        assertEquals("USER-001", legacyData.get("user_id"));
        assertEquals("JOHN DOE", legacyData.get("user_name"));  // Uppercase in legacy
        assertEquals("john.doe@example.com", legacyData.get("email_address"));
        assertEquals("ENG", legacyData.get("dept_code"));  // Department mapped to code
        assertEquals("A", legacyData.get("status_code"));  // ACTIVE -> A
        
        // Verify legacy headers
        assertEquals("legacy-v1", legacyMessage.getHeaders().get("legacy-format"));
        assertEquals("user-transformer", legacyMessage.getHeaders().get("legacy-source"));
        assertEquals("legacy-conversion", legacyMessage.getHeaders().get("transformation-applied"));
    }

    @ParameterizedTest
    @DisplayName("Legacy Format Transformer - Department Code Mapping")
    @CsvSource({
        "Engineering, ENG",
        "Marketing, MKT",
        "Sales, SLS", 
        "Finance, FIN",
        "IT, ITD",
        "Executive, EXC",
        "Unknown Department, GEN"
    })
    void testLegacyFormatTransformer_DepartmentMapping(String department, String expectedCode) {
        // Input Scenarios: Different departments for code mapping
        User user = User.builder()
                .id("USER-DEPT-TEST")
                .name("Test User")
                .email("test@example.com")
                .age(30)
                .department(department)
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> message = MessageBuilder.withPayload(user).build();
        Function<Message<User>, Message<Map<String, Object>>> transformer = 
                userTransformer.userToLegacyFormat();
        
        Message<Map<String, Object>> legacyMessage = transformer.apply(message);
        
        // Expected Output: Department should be mapped to correct legacy code
        assertEquals(expectedCode, legacyMessage.getPayload().get("dept_code"));
    }

    // ===============================================================================
    // AUDIT TRAIL TRANSFORMER TESTS
    // ===============================================================================

    @Test
    @DisplayName("User Audit Trail - Successful Audit Addition")
    void testUserAuditTrail_SuccessfulAuditAddition() {
        // Function to Test: userAuditTrail() adds audit information
        Function<Message<User>, Message<User>> auditTransformer = userTransformer.userAuditTrail();
        
        // Input Scenarios: User message requiring audit trail
        Message<User> auditedMessage = auditTransformer.apply(validMessage);
        
        // Expected Output: Should add comprehensive audit headers
        assertNotNull(auditedMessage);
        assertEquals(validUser, auditedMessage.getPayload());
        
        // Verify audit headers
        assertTrue(auditedMessage.getHeaders().containsKey("audit-timestamp"));
        assertEquals("system", auditedMessage.getHeaders().get("audit-user"));
        assertEquals("message-processed", auditedMessage.getHeaders().get("audit-action"));
        assertEquals("user-transformer", auditedMessage.getHeaders().get("audit-source"));
        assertTrue(auditedMessage.getHeaders().containsKey("audit-trail-id"));
        assertTrue(auditedMessage.getHeaders().containsKey("processing-node"));
        assertEquals("audit-trail", auditedMessage.getHeaders().get("transformation-applied"));
        
        // Verify message path tracking
        String messagePath = (String) auditedMessage.getHeaders().get("message-path");
        assertTrue(messagePath.contains("user-transformer"));
    }

    @Test
    @DisplayName("User Audit Trail - Message Path Tracking")
    void testUserAuditTrail_MessagePathTracking() {
        // Function to Test: Message path accumulation across transformations
        Message<User> messageWithPath = MessageBuilder
                .fromMessage(validMessage)
                .setHeader("message-path", "original-source -> previous-transformer")
                .build();

        Function<Message<User>, Message<User>> auditTransformer = userTransformer.userAuditTrail();
        
        // Input Scenarios: Message with existing path
        Message<User> auditedMessage = auditTransformer.apply(messageWithPath);
        
        // Expected Output: Should append to existing message path
        String messagePath = (String) auditedMessage.getHeaders().get("message-path");
        assertTrue(messagePath.contains("original-source"));
        assertTrue(messagePath.contains("previous-transformer"));
        assertTrue(messagePath.contains("user-transformer"));
    }

    // ===============================================================================
    // TRANSFORMATION CHAIN TESTS
    // ===============================================================================

    @Test
    @DisplayName("Transformation Chain - Complete Processing Pipeline")
    void testTransformationChain_CompleteProcessingPipeline() {
        // Integration Test: Complete transformation pipeline
        Function<Message<User>, Message<User>> enricher = userTransformer.userEnricher();
        Function<Message<User>, Message<User>> normalizer = userTransformer.userNormalizer();
        Function<Message<User>, Message<User>> auditTrail = userTransformer.userAuditTrail();
        
        // Create user with formatting issues for normalization
        User unnormalizedUser = User.builder()
                .id("  user-chain-001  ")
                .name("  test user  ")
                .email("  TEST.USER@EXAMPLE.COM  ")
                .age(30)
                .department("Engineering")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> originalMessage = MessageBuilder.withPayload(unnormalizedUser).build();
        
        // Execute transformation chain
        Message<User> enrichedMessage = enricher.apply(originalMessage);
        Message<User> normalizedMessage = normalizer.apply(enrichedMessage);
        Message<User> finalMessage = auditTrail.apply(normalizedMessage);
        
        // Expected Output: Should have all transformations applied
        // Verify normalized payload
        User finalUser = finalMessage.getPayload();
        assertEquals("user-chain-001", finalUser.getId());
        assertEquals("Test User", finalUser.getName());
        assertEquals("test.user@example.com", finalUser.getEmail());
        
        // Verify all transformation headers are present
        assertTrue(finalMessage.getHeaders().containsKey("enrichment-timestamp"));
        assertTrue(finalMessage.getHeaders().containsKey("normalization-timestamp"));
        assertTrue(finalMessage.getHeaders().containsKey("audit-timestamp"));
        assertEquals("engineering", finalMessage.getHeaders().get("user-domain"));
        assertTrue(finalMessage.getHeaders().containsKey("user-tier"));
        assertTrue(finalMessage.getHeaders().containsKey("audit-trail-id"));
    }

    // ===============================================================================
    // ERROR HANDLING AND EDGE CASES
    // ===============================================================================

    @Test
    @DisplayName("Transformer Error Handling - Invalid User Data")
    void testTransformerErrorHandling_InvalidUserData() {
        // Error Handling: Missing required fields should be handled gracefully
        User invalidUser = User.builder()
                .id("INVALID-USER")
                // Missing name and email
                .age(30)
                .department("Engineering")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> invalidMessage = MessageBuilder.withPayload(invalidUser).build();
        Function<Message<User>, Message<User>> normalizer = userTransformer.userNormalizer();
        
        // Expected Output: Should handle missing fields gracefully
        Message<User> result = normalizer.apply(invalidMessage);
        assertNotNull(result);
        // Normalizer should handle null/missing fields without throwing exceptions
    }

    @ParameterizedTest
    @DisplayName("Transformer Performance - Large User Names")
    @ValueSource(ints = {50, 100, 200})
    void testTransformerPerformance_LargeUserNames(int nameLength) {
        // Boundary Testing: Large user names
        String largeName = "A".repeat(nameLength);
        User largeNameUser = User.builder()
                .id("USER-LARGE-NAME")
                .name(largeName)
                .email("large.name@example.com")
                .age(30)
                .department("Engineering")
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> message = MessageBuilder.withPayload(largeNameUser).build();
        Function<Message<User>, Message<User>> enricher = userTransformer.userEnricher();
        
        // Expected Output: Should handle large names without performance issues
        Message<User> result = enricher.apply(message);
        assertNotNull(result);
        assertEquals(largeName, result.getPayload().getName());
    }

    @Test
    @DisplayName("Transformer Null Safety - Null Department Handling")
    void testTransformerNullSafety_NullDepartmentHandling() {
        // Error Handling: Null department should be handled gracefully
        User nullDeptUser = User.builder()
                .id("USER-NULL-DEPT")
                .name("No Department User")
                .email("nodept@example.com")
                .age(30)
                .department(null)
                .status(User.UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Message<User> message = MessageBuilder.withPayload(nullDeptUser).build();
        Function<Message<User>, Message<User>> enricher = userTransformer.userEnricher();
        
        // Expected Output: Should handle null department gracefully
        Message<User> result = enricher.apply(message);
        assertNotNull(result);
        assertEquals("general", result.getHeaders().get("user-domain"));
    }
} 