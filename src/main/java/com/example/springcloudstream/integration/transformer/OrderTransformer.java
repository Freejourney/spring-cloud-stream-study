package com.example.springcloudstream.integration.transformer;

import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.common.model.MessageHeaders;
import com.example.springcloudstream.common.util.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Order Message Transformer
 * 
 * This class demonstrates various message transformation patterns for order domain:
 * - Order data enrichment with business calculations
 * - Order summary creation for analytics
 * - Priority-based transformations
 * - Multi-format message generation
 * 
 * Integration Patterns Demonstrated:
 * - Message Translator Pattern
 * - Content Enricher Pattern  
 * - Splitter Pattern (simplified)
 * - Content-Based transformation
 * 
 * @author Spring Cloud Stream Tutorial
 * @version 1.0
 */
@Slf4j
@Component
public class OrderTransformer {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.08"); // 8% tax rate
    private static final BigDecimal SHIPPING_THRESHOLD = new BigDecimal("100.00");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("9.99");

    /**
     * Order financial enrichment transformer
     * Enriches order with calculated financial data (tax, shipping, discounts)
     * 
     * Pattern: Content Enricher
     * Input: Order message
     * Output: Financially enriched Order message
     */
    @Bean
    public Function<Message<Order>, Message<Order>> orderFinancialEnricher() {
        return message -> {
            try {
                Order order = message.getPayload();
                log.debug("💰 Enriching order with financial calculations: {}", order.getId());

                // Calculate financial details
                BigDecimal subtotal = order.getTotalAmount();
                BigDecimal tax = calculateTax(subtotal);
                BigDecimal shipping = calculateShipping(subtotal, order);
                BigDecimal discount = calculateDiscount(order);
                BigDecimal finalTotal = subtotal.add(tax).add(shipping).subtract(discount);

                // Add financial calculation headers
                Map<String, Object> enrichedHeaders = new HashMap<>(message.getHeaders());
                enrichedHeaders.put("order-subtotal", subtotal.toString());
                enrichedHeaders.put("order-tax-amount", tax.toString());
                enrichedHeaders.put("order-shipping-cost", shipping.toString());
                enrichedHeaders.put("order-discount-amount", discount.toString());
                enrichedHeaders.put("order-final-total", finalTotal.toString());
                enrichedHeaders.put("financial-calculation-timestamp", System.currentTimeMillis());
                enrichedHeaders.put("transformation-applied", "financial-enrichment");
                enrichedHeaders.put("tax-rate-applied", TAX_RATE.toString());
                enrichedHeaders.put("free-shipping-eligible", shipping.equals(BigDecimal.ZERO) ? "true" : "false");

                Message<Order> enrichedMessage = MessageBuilder
                    .withPayload(order)
                    .copyHeaders(enrichedHeaders)
                    .build();

                log.debug("✅ Financial enrichment completed: {} -> Total: ${}", 
                    order.getId(), finalTotal);
                
                return enrichedMessage;

            } catch (Exception e) {
                log.error("💥 Error enriching order with financial calculations", e);
                throw new RuntimeException("Order financial enrichment failed", e);
            }
        };
    }

    /**
     * Order to event descriptions splitter
     * Splits order into multiple event descriptions for different systems
     * 
     * Pattern: Splitter Pattern (simplified)
     * Input: Order message
     * Output: List of event descriptions
     */
    @Bean
    public Function<Message<Order>, Message<List<String>>> orderEventSplitter() {
        return message -> {
            try {
                Order order = message.getPayload();
                log.debug("🔄 Splitting order into multiple events: {}", order.getId());

                List<String> events = new ArrayList<>();
                String correlationId = MessageUtils.generateCorrelationId();

                // Generate different events based on order status and conditions
                switch (order.getStatus()) {
                    case PENDING -> {
                        events.add("ORDER_CREATED:" + order.getId());
                        events.add("INVENTORY_CHECK_REQUESTED:" + order.getId());
                        events.add("PAYMENT_REQUESTED:" + order.getId());
                    }
                    case CONFIRMED -> {
                        events.add("ORDER_CONFIRMED:" + order.getId());
                        events.add("FULFILLMENT_REQUESTED:" + order.getId());
                    }
                    case PROCESSING -> {
                        events.add("ORDER_PROCESSING_STARTED:" + order.getId());
                        if (order.getPriority() == Order.OrderPriority.URGENT) {
                            events.add("PRIORITY_PROCESSING_REQUESTED:" + order.getId());
                        }
                    }
                    case SHIPPED -> {
                        events.add("ORDER_SHIPPED:" + order.getId());
                        events.add("TRACKING_NOTIFICATION_REQUESTED:" + order.getId());
                    }
                    case DELIVERED -> {
                        events.add("ORDER_DELIVERED:" + order.getId());
                        events.add("CUSTOMER_SURVEY_REQUESTED:" + order.getId());
                    }
                    case CANCELLED -> {
                        events.add("ORDER_CANCELLED:" + order.getId());
                        events.add("REFUND_REQUESTED:" + order.getId());
                    }
                }

                // Add analytics event for all orders
                events.add("ANALYTICS_DATA_REQUESTED:" + order.getId());

                // Create message with event list
                Map<String, Object> splitHeaders = new HashMap<>(message.getHeaders());
                splitHeaders.put("split-correlation-id", correlationId);
                splitHeaders.put("split-event-count", events.size());
                splitHeaders.put("split-timestamp", System.currentTimeMillis());
                splitHeaders.put("transformation-applied", "order-event-split");
                splitHeaders.put("original-order-id", order.getId());

                Message<List<String>> splitMessage = MessageBuilder
                    .withPayload(events)
                    .copyHeaders(splitHeaders)
                    .build();

                log.debug("✅ Order split into {} events: {}", events.size(), order.getId());
                return splitMessage;

            } catch (Exception e) {
                log.error("💥 Error splitting order into events", e);
                throw new RuntimeException("Order event splitting failed", e);
            }
        };
    }

    /**
     * Order analytics transformer
     * Transforms order data into analytics-friendly format
     * 
     * Pattern: Message Translator Pattern
     * Input: Order message
     * Output: Analytics data message (Map format)
     */
    @Bean
    public Function<Message<Order>, Message<Map<String, Object>>> orderAnalyticsTransformer() {
        return message -> {
            try {
                Order order = message.getPayload();
                log.debug("📊 Transforming order to analytics format: {}", order.getId());

                // Create analytics data structure
                Map<String, Object> analytics = new HashMap<>();
                
                // Basic order data
                analytics.put("order_id", order.getId());
                analytics.put("customer_id", order.getUserId());
                analytics.put("order_timestamp", order.getOrderDate());
                analytics.put("order_amount", order.getTotalAmount());
                analytics.put("order_status", order.getStatus().name());
                analytics.put("order_priority", order.getPriority().name());
                
                // Derived analytics fields
                analytics.put("order_value_category", categorizeOrderValue(order.getTotalAmount()));
                analytics.put("payment_method", order.getPaymentMethod() != null ? 
                    order.getPaymentMethod().toString() : "UNKNOWN");
                
                // Time-based analytics
                LocalDateTime now = LocalDateTime.now();
                analytics.put("processing_hour", now.getHour());
                analytics.put("processing_day_of_week", now.getDayOfWeek().name());
                analytics.put("processing_month", now.getMonth().name());
                analytics.put("processing_quarter", calculateQuarter(now));
                
                // Business metrics
                analytics.put("is_high_value", order.getTotalAmount().compareTo(
                    new BigDecimal("1000")) > 0);
                analytics.put("is_urgent_priority", 
                    order.getPriority() == Order.OrderPriority.URGENT);
                analytics.put("shipping_region", extractShippingRegion(order));
                
                // Add analytics headers
                Map<String, Object> analyticsHeaders = new HashMap<>();
                analyticsHeaders.put("analytics-format", "order-v1");
                analyticsHeaders.put("analytics-timestamp", System.currentTimeMillis());
                analyticsHeaders.put("data-retention-days", 365);
                analyticsHeaders.put("analytics-category", "order-transaction");
                analyticsHeaders.put("transformation-applied", "order-analytics");
                analyticsHeaders.put(MessageHeaders.CORRELATION_ID, 
                    message.getHeaders().get(MessageHeaders.CORRELATION_ID));

                Message<Map<String, Object>> analyticsMessage = MessageBuilder
                    .withPayload(analytics)
                    .copyHeaders(analyticsHeaders)
                    .build();

                log.debug("✅ Analytics transformation completed: {} -> category: {}", 
                    order.getId(), analytics.get("order_value_category"));
                return analyticsMessage;

            } catch (Exception e) {
                log.error("💥 Error transforming order to analytics format", e);
                throw new RuntimeException("Order analytics transformation failed", e);
            }
        };
    }

    /**
     * Order audit transformer
     * Adds comprehensive audit trail to order messages
     * 
     * Pattern: Message History Pattern
     * Input: Order message
     * Output: Order message with complete audit trail
     */
    @Bean
    public Function<Message<Order>, Message<Order>> orderAuditTransformer() {
        return message -> {
            try {
                Order order = message.getPayload();
                log.debug("📋 Adding comprehensive audit trail to order: {}", order.getId());

                // Add comprehensive audit headers
                Map<String, Object> auditHeaders = new HashMap<>(message.getHeaders());
                auditHeaders.put("audit-timestamp", System.currentTimeMillis());
                auditHeaders.put("audit-user", "order-transformer");
                auditHeaders.put("audit-action", "order-transformation");
                auditHeaders.put("audit-source", "order-transformer");
                
                // Order-specific audit data
                auditHeaders.put("audit-order-id", order.getId());
                auditHeaders.put("audit-customer-id", order.getUserId());
                auditHeaders.put("audit-order-value", order.getTotalAmount().toString());
                auditHeaders.put("audit-order-status", order.getStatus().name());
                auditHeaders.put("audit-order-priority", order.getPriority().name());
                
                // Compliance audit data
                auditHeaders.put("gdpr-compliant", "true");
                auditHeaders.put("pci-compliant", "true");
                auditHeaders.put("data-classification", "business-sensitive");
                auditHeaders.put("retention-policy", "7-years");
                
                // Processing audit trail
                String currentPath = (String) auditHeaders.getOrDefault("message-path", "");
                auditHeaders.put("message-path", currentPath + " -> order-transformer");
                auditHeaders.put("transformation-applied", "order-audit");

                Message<Order> auditMessage = MessageBuilder
                    .withPayload(order)
                    .copyHeaders(auditHeaders)
                    .build();

                log.debug("✅ Comprehensive audit trail added to order: {}", order.getId());
                return auditMessage;

            } catch (Exception e) {
                log.error("💥 Error adding audit trail to order message", e);
                throw new RuntimeException("Order audit transformation failed", e);
            }
        };
    }

    // ===================
    // PRIVATE HELPER METHODS
    // ===================

    /**
     * Calculate tax amount
     */
    private BigDecimal calculateTax(BigDecimal subtotal) {
        return subtotal.multiply(TAX_RATE).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate shipping cost
     */
    private BigDecimal calculateShipping(BigDecimal subtotal, Order order) {
        if (subtotal.compareTo(SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO; // Free shipping for orders over threshold
        }
        if (order.getPriority() == Order.OrderPriority.URGENT) {
            return SHIPPING_COST.multiply(new BigDecimal("2")); // Express shipping
        }
        return SHIPPING_COST;
    }

    /**
     * Calculate discount amount
     */
    private BigDecimal calculateDiscount(Order order) {
        // Business logic for discounts
        if (order.getTotalAmount().compareTo(new BigDecimal("500")) > 0) {
            return order.getTotalAmount().multiply(new BigDecimal("0.05")); // 5% discount
        }
        return BigDecimal.ZERO;
    }

    /**
     * Categorize order value for analytics
     */
    private String categorizeOrderValue(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("1000")) > 0) return "HIGH";
        if (amount.compareTo(new BigDecimal("500")) > 0) return "MEDIUM";
        if (amount.compareTo(new BigDecimal("100")) > 0) return "STANDARD";
        return "LOW";
    }

    /**
     * Calculate quarter from date
     */
    private String calculateQuarter(LocalDateTime date) {
        int month = date.getMonthValue();
        return "Q" + ((month - 1) / 3 + 1);
    }

    /**
     * Extract shipping region
     */
    private String extractShippingRegion(Order order) {
        if (order.getShippingAddress() != null && order.getShippingAddress().getState() != null) {
            return mapStateToRegion(order.getShippingAddress().getState());
        }
        return "UNKNOWN";
    }

    /**
     * Map state to region
     */
    private String mapStateToRegion(String state) {
        // Simplified region mapping
        return switch (state.toUpperCase()) {
            case "CA", "OR", "WA", "NV", "AZ" -> "WEST";
            case "NY", "NJ", "CT", "MA", "PA" -> "NORTHEAST";
            case "TX", "OK", "LA", "AR" -> "SOUTH";
            case "IL", "IN", "OH", "MI", "WI" -> "MIDWEST";
            default -> "OTHER";
        };
    }
} 