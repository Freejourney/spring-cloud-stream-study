package com.example.springcloudstream.integration.router;

import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.domain.user.model.User;
import com.example.springcloudstream.common.model.MessageHeaders;
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
import java.util.Arrays;

/**
 * Priority-Based Message Router
 * 
 * This class demonstrates priority-based routing patterns:
 * - Route messages by order priority levels
 * - Time-sensitive routing with SLA considerations
 * - Value-based priority routing
 * - Dynamic priority calculation and routing
 * - Priority escalation routing
 * 
 * Integration Patterns Demonstrated:
 * - Priority-Based Router Pattern
 * - Time-Based Router Pattern
 * - Value-Based Router Pattern
 * - Escalation Router Pattern
 * - Load Balancing Router Pattern
 * 
 * @author Spring Cloud Stream Tutorial
 * @version 1.0
 */
@Slf4j
@Component
public class PriorityRouter {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal PREMIUM_VALUE_THRESHOLD = new BigDecimal("5000");
    private static final int URGENT_SLA_HOURS = 4;
    private static final int HIGH_SLA_HOURS = 24;

    /**
     * Order priority router
     * Routes order messages based on priority levels
     * 
     * Pattern: Priority-Based Router
     * Input: Order message
     * Output: Priority-based destination
     */
    @Bean
    public Function<Message<Order>, String> orderPriorityRouter() {
        return message -> {
            try {
                Order order = message.getPayload();
                Order.OrderPriority priority = order.getPriority();
                
                log.debug("🔀 Routing order by priority: {} -> {}", 
                    order.getId(), priority);

                // Determine routing destination based on priority
                String destination = determineOrderPriorityDestination(priority, order);
                
                // Add priority routing metadata
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("routing-decision", "priority-based");
                routingHeaders.put("routing-criteria", priority.name());
                routingHeaders.put("routing-destination", destination);
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());
                routingHeaders.put("router-component", "priority-router");
                routingHeaders.put("order-value", order.getTotalAmount().toString());
                routingHeaders.put("sla-hours", calculateSLAHours(priority));

                log.debug("✅ Order routed by priority: {} -> {} (priority: {})", 
                    order.getId(), destination, priority);
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error routing order by priority", e);
                return "order-default-channel"; // Fallback destination
            }
        };
    }

    /**
     * Dynamic priority router with value consideration
     * Routes messages based on calculated priority considering multiple factors
     * 
     * Pattern: Value-Based Router + Dynamic Priority
     * Input: Order message
     * Output: Dynamically calculated priority destination
     */
    @Bean
    public Function<Message<Order>, String> dynamicPriorityRouter() {
        return message -> {
            try {
                Order order = message.getPayload();
                
                log.debug("🔀 Dynamic priority routing for order: {}", order.getId());

                // Calculate dynamic priority based on multiple factors
                String dynamicPriority = calculateDynamicPriority(order, message);
                String destination = determineDynamicPriorityDestination(dynamicPriority, order);
                
                // Add dynamic priority metadata
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("routing-decision", "dynamic-priority");
                routingHeaders.put("original-priority", order.getPriority().name());
                routingHeaders.put("calculated-priority", dynamicPriority);
                routingHeaders.put("routing-destination", destination);
                routingHeaders.put("priority-factors", buildPriorityFactors(order));
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());

                log.debug("✅ Dynamic priority routing: {} -> {} ({}->{})", 
                    order.getId(), destination, order.getPriority(), dynamicPriority);
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error in dynamic priority routing", e);
                return "order-default-channel";
            }
        };
    }

    /**
     * Time-sensitive priority router
     * Routes messages based on time sensitivity and SLA requirements
     * 
     * Pattern: Time-Based Router + SLA Router
     * Input: Order message
     * Output: Time-sensitive destination
     */
    @Bean
    public Function<Message<Order>, String> timeSensitivePriorityRouter() {
        return message -> {
            try {
                Order order = message.getPayload();
                LocalDateTime now = LocalDateTime.now();
                
                log.debug("🔀 Time-sensitive routing for order: {} at {}", 
                    order.getId(), now);

                // Calculate time-based routing
                String destination = calculateTimeSensitiveDestination(order, now);
                
                // Add time-sensitive metadata
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("routing-decision", "time-sensitive");
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());
                routingHeaders.put("routing-destination", destination);
                routingHeaders.put("order-age-minutes", calculateOrderAgeMinutes(order, now));
                routingHeaders.put("sla-breach-risk", calculateSLABreachRisk(order, now));
                routingHeaders.put("business-hours", isBusinessHours(now));

                log.debug("✅ Time-sensitive routing: {} -> {} (age: {} min)", 
                    order.getId(), destination, calculateOrderAgeMinutes(order, now));
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error in time-sensitive routing", e);
                return "order-default-channel";
            }
        };
    }

    /**
     * Priority escalation router
     * Routes messages that require escalation based on age and priority
     * 
     * Pattern: Escalation Router + Time-Based Router
     * Input: Order message
     * Output: Escalation destination
     */
    @Bean
    public Function<Message<Order>, String> priorityEscalationRouter() {
        return message -> {
            try {
                Order order = message.getPayload();
                LocalDateTime now = LocalDateTime.now();
                
                log.debug("🔀 Priority escalation routing for order: {}", order.getId());

                // Determine if escalation is needed
                boolean needsEscalation = needsEscalation(order, now);
                String escalationLevel = calculateEscalationLevel(order, now);
                String destination = determineEscalationDestination(escalationLevel, order);
                
                // Add escalation metadata
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("routing-decision", "escalation-based");
                routingHeaders.put("escalation-needed", needsEscalation);
                routingHeaders.put("escalation-level", escalationLevel);
                routingHeaders.put("routing-destination", destination);
                routingHeaders.put("escalation-reason", buildEscalationReason(order, now));
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());

                log.debug("✅ Escalation routing: {} -> {} (level: {}, escalation: {})", 
                    order.getId(), destination, escalationLevel, needsEscalation);
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error in escalation routing", e);
                return "order-escalation-error-channel";
            }
        };
    }

    /**
     * Load balancing priority router
     * Routes messages across multiple channels based on priority and load distribution
     * 
     * Pattern: Load Balancing Router + Priority Router
     * Input: Order message
     * Output: Load-balanced priority destination
     */
    @Bean
    public Function<Message<Order>, String> loadBalancedPriorityRouter() {
        return message -> {
            try {
                Order order = message.getPayload();
                Order.OrderPriority priority = order.getPriority();
                
                log.debug("🔀 Load-balanced priority routing for order: {} (priority: {})", 
                    order.getId(), priority);

                // Get available channels for this priority
                List<String> availableChannels = getAvailableChannelsForPriority(priority);
                
                // Select channel based on load balancing algorithm
                String selectedChannel = selectChannelForLoadBalancing(availableChannels, order);
                
                // Add load balancing metadata
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("routing-decision", "load-balanced-priority");
                routingHeaders.put("priority-level", priority.name());
                routingHeaders.put("available-channels", availableChannels.size());
                routingHeaders.put("selected-channel", selectedChannel);
                routingHeaders.put("load-balancing-algorithm", "round-robin-hash");
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());

                log.debug("✅ Load-balanced routing: {} -> {} (from {} options)", 
                    order.getId(), selectedChannel, availableChannels.size());
                
                return selectedChannel;

            } catch (Exception e) {
                log.error("💥 Error in load-balanced priority routing", e);
                return "order-default-channel";
            }
        };
    }

    // ===================
    // PRIVATE HELPER METHODS
    // ===================

    /**
     * Determine order priority destination
     */
    private String determineOrderPriorityDestination(Order.OrderPriority priority, Order order) {
        // Consider both priority and order value
        boolean isHighValue = order.getTotalAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0;
        boolean isPremiumValue = order.getTotalAmount().compareTo(PREMIUM_VALUE_THRESHOLD) > 0;
        
        return switch (priority) {
            case URGENT -> isPremiumValue ? "order-urgent-premium-channel" : "order-urgent-channel";
            case HIGH -> isHighValue ? "order-high-value-channel" : "order-high-channel";
            case NORMAL -> isHighValue ? "order-normal-value-channel" : "order-normal-channel";
            case LOW -> "order-low-channel";
        };
    }

    /**
     * Calculate dynamic priority based on multiple factors
     */
    private String calculateDynamicPriority(Order order, Message<Order> message) {
        int priorityScore = 0;
        
        // Base priority score
        priorityScore += switch (order.getPriority()) {
            case URGENT -> 100;
            case HIGH -> 75;
            case NORMAL -> 50;
            case LOW -> 25;
        };
        
        // Value-based adjustment
        if (order.getTotalAmount().compareTo(PREMIUM_VALUE_THRESHOLD) > 0) {
            priorityScore += 50;
        } else if (order.getTotalAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            priorityScore += 25;
        }
        
        // Age-based adjustment
        long ageMinutes = calculateOrderAgeMinutes(order, LocalDateTime.now());
        if (ageMinutes > 240) { // 4 hours
            priorityScore += 30;
        } else if (ageMinutes > 120) { // 2 hours
            priorityScore += 15;
        }
        
        // Customer department factor
        String customerDept = (String) message.getHeaders().get("customer-department");
        if ("EXECUTIVE".equalsIgnoreCase(customerDept) || "VIP".equalsIgnoreCase(customerDept)) {
            priorityScore += 20;
        }
        
        // Convert score to priority level
        if (priorityScore >= 150) return "CRITICAL";
        if (priorityScore >= 120) return "URGENT";
        if (priorityScore >= 80) return "HIGH";
        if (priorityScore >= 50) return "NORMAL";
        return "LOW";
    }

    /**
     * Determine dynamic priority destination
     */
    private String determineDynamicPriorityDestination(String dynamicPriority, Order order) {
        return switch (dynamicPriority) {
            case "CRITICAL" -> "order-critical-channel";
            case "URGENT" -> "order-urgent-dynamic-channel";
            case "HIGH" -> "order-high-dynamic-channel";
            case "NORMAL" -> "order-normal-dynamic-channel";
            case "LOW" -> "order-low-dynamic-channel";
            default -> "order-default-channel";
        };
    }

    /**
     * Calculate time-sensitive destination
     */
    private String calculateTimeSensitiveDestination(Order order, LocalDateTime now) {
        long ageMinutes = calculateOrderAgeMinutes(order, now);
        boolean isBusinessHours = isBusinessHours(now);
        Order.OrderPriority priority = order.getPriority();
        
        // SLA breach risk calculation
        String slaRisk = calculateSLABreachRisk(order, now);
        
        if ("HIGH".equals(slaRisk)) {
            return "order-sla-breach-risk-channel";
        } else if ("MEDIUM".equals(slaRisk) && priority == Order.OrderPriority.URGENT) {
            return "order-urgent-time-sensitive-channel";
        } else if (!isBusinessHours && priority == Order.OrderPriority.URGENT) {
            return "order-after-hours-urgent-channel";
        } else if (ageMinutes > 480) { // 8 hours
            return "order-aged-channel";
        } else {
            return determineOrderPriorityDestination(priority, order);
        }
    }

    /**
     * Check if escalation is needed
     */
    private boolean needsEscalation(Order order, LocalDateTime now) {
        long ageMinutes = calculateOrderAgeMinutes(order, now);
        Order.OrderPriority priority = order.getPriority();
        
        return switch (priority) {
            case URGENT -> ageMinutes > URGENT_SLA_HOURS * 60;
            case HIGH -> ageMinutes > HIGH_SLA_HOURS * 60;
            case NORMAL -> ageMinutes > 72 * 60; // 3 days
            case LOW -> ageMinutes > 168 * 60; // 1 week
        };
    }

    /**
     * Calculate escalation level
     */
    private String calculateEscalationLevel(Order order, LocalDateTime now) {
        if (!needsEscalation(order, now)) {
            return "NONE";
        }
        
        long ageMinutes = calculateOrderAgeMinutes(order, now);
        Order.OrderPriority priority = order.getPriority();
        
        if (priority == Order.OrderPriority.URGENT && ageMinutes > URGENT_SLA_HOURS * 60 * 2) {
            return "CRITICAL";
        } else if (ageMinutes > 1440) { // 24 hours
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }

    /**
     * Determine escalation destination
     */
    private String determineEscalationDestination(String escalationLevel, Order order) {
        return switch (escalationLevel) {
            case "CRITICAL" -> "order-critical-escalation-channel";
            case "HIGH" -> "order-high-escalation-channel";
            case "MEDIUM" -> "order-medium-escalation-channel";
            case "NONE" -> determineOrderPriorityDestination(order.getPriority(), order);
            default -> "order-escalation-default-channel";
        };
    }

    /**
     * Get available channels for priority level
     */
    private List<String> getAvailableChannelsForPriority(Order.OrderPriority priority) {
        return switch (priority) {
            case URGENT -> Arrays.asList(
                "order-urgent-channel-1", 
                "order-urgent-channel-2", 
                "order-urgent-channel-3"
            );
            case HIGH -> Arrays.asList(
                "order-high-channel-1", 
                "order-high-channel-2"
            );
            case NORMAL -> Arrays.asList(
                "order-normal-channel-1", 
                "order-normal-channel-2", 
                "order-normal-channel-3",
                "order-normal-channel-4"
            );
            case LOW -> Arrays.asList(
                "order-low-channel-1"
            );
        };
    }

    /**
     * Select channel for load balancing
     */
    private String selectChannelForLoadBalancing(List<String> channels, Order order) {
        if (channels.isEmpty()) {
            return "order-default-channel";
        }
        
        // Simple hash-based load balancing
        int hash = order.getId().hashCode();
        int index = Math.abs(hash) % channels.size();
        return channels.get(index);
    }

    /**
     * Calculate order age in minutes
     */
    private long calculateOrderAgeMinutes(Order order, LocalDateTime now) {
        if (order.getOrderDate() == null) return 0;
        return java.time.Duration.between(order.getOrderDate(), now).toMinutes();
    }

    /**
     * Calculate SLA hours for priority
     */
    private int calculateSLAHours(Order.OrderPriority priority) {
        return switch (priority) {
            case URGENT -> URGENT_SLA_HOURS;
            case HIGH -> HIGH_SLA_HOURS;
            case NORMAL -> 72; // 3 days
            case LOW -> 168; // 1 week
        };
    }

    /**
     * Calculate SLA breach risk
     */
    private String calculateSLABreachRisk(Order order, LocalDateTime now) {
        long ageMinutes = calculateOrderAgeMinutes(order, now);
        int slaHours = calculateSLAHours(order.getPriority());
        long slaMinutes = slaHours * 60L;
        
        double riskPercentage = (double) ageMinutes / slaMinutes;
        
        if (riskPercentage >= 1.0) return "BREACHED";
        if (riskPercentage >= 0.8) return "HIGH";
        if (riskPercentage >= 0.6) return "MEDIUM";
        return "LOW";
    }

    /**
     * Check if current time is business hours
     */
    private boolean isBusinessHours(LocalDateTime now) {
        int hour = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue();
        
        // Monday-Friday, 9 AM - 6 PM
        return dayOfWeek >= 1 && dayOfWeek <= 5 && hour >= 9 && hour < 18;
    }

    /**
     * Build priority factors description
     */
    private String buildPriorityFactors(Order order) {
        return String.format("originalPriority=%s,value=%s,items=%d", 
            order.getPriority(), 
            order.getTotalAmount(), 
            order.getItems() != null ? order.getItems().size() : 0);
    }

    /**
     * Build escalation reason
     */
    private String buildEscalationReason(Order order, LocalDateTime now) {
        long ageMinutes = calculateOrderAgeMinutes(order, now);
        int slaHours = calculateSLAHours(order.getPriority());
        
        return String.format("Order age %d minutes exceeds SLA of %d hours for priority %s", 
            ageMinutes, slaHours, order.getPriority());
    }
} 