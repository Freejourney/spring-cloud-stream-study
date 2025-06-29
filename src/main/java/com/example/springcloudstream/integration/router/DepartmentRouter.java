package com.example.springcloudstream.integration.router;

import com.example.springcloudstream.domain.user.model.User;
import com.example.springcloudstream.domain.order.model.Order;
import com.example.springcloudstream.common.model.MessageHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;

/**
 * Department-Based Message Router
 * 
 * This class demonstrates content-based routing patterns based on department information:
 * - Route user messages to department-specific channels
 * - Route order messages based on customer department
 * - Dynamic routing with header inspection
 * - Conditional routing with business rules
 * - Multi-destination routing
 * 
 * Integration Patterns Demonstrated:
 * - Content-Based Router Pattern
 * - Recipient List Pattern
 * - Dynamic Router Pattern
 * - Message Filter Pattern
 * - Routing Slip Pattern
 * 
 * @author Spring Cloud Stream Tutorial
 * @version 1.0
 */
@Slf4j
@Component
public class DepartmentRouter {

    /**
     * User department router
     * Routes user messages to department-specific destinations
     * 
     * Pattern: Content-Based Router
     * Input: User message
     * Output: Routing destination name
     */
    @Bean
    public Function<Message<User>, String> userDepartmentRouter() {
        return message -> {
            try {
                User user = message.getPayload();
                String department = user.getDepartment();
                
                log.debug("🔀 Routing user message by department: {} -> {}", 
                    user.getId(), department);

                // Determine routing destination based on department
                String destination = determineUserDestination(department);
                
                // Add routing metadata to headers (for audit/monitoring)
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("routing-decision", "department-based");
                routingHeaders.put("routing-criteria", department);
                routingHeaders.put("routing-destination", destination);
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());
                routingHeaders.put("router-component", "department-router");

                // Create new message with routing headers for downstream processing
                Message<User> routedMessage = MessageBuilder
                    .withPayload(user)
                    .copyHeaders(routingHeaders)
                    .build();

                log.debug("✅ User routed to destination: {} (department: {})", 
                    destination, department);
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error routing user message by department", e);
                return "user-default-channel"; // Fallback destination
            }
        };
    }

    /**
     * Order department router
     * Routes order messages based on customer department
     * 
     * Pattern: Content-Based Router
     * Input: Order message  
     * Output: Routing destination name
     */
    @Bean
    public Function<Message<Order>, String> orderDepartmentRouter() {
        return message -> {
            try {
                Order order = message.getPayload();
                String customerDepartment = (String) message.getHeaders().get("customer-department");
                
                log.debug("🔀 Routing order message by customer department: {} -> {}", 
                    order.getId(), customerDepartment);

                // Determine routing destination based on customer department
                String destination = determineOrderDestination(customerDepartment, order);
                
                // Add routing metadata
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("routing-decision", "customer-department-based");
                routingHeaders.put("routing-criteria", customerDepartment);
                routingHeaders.put("routing-destination", destination);
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());
                routingHeaders.put("router-component", "department-router");
                routingHeaders.put("order-value-factor", order.getTotalAmount().toString());

                log.debug("✅ Order routed to destination: {} (customer department: {})", 
                    destination, customerDepartment);
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error routing order message by department", e);
                return "order-default-channel"; // Fallback destination
            }
        };
    }

    /**
     * Multi-destination department router
     * Routes messages to multiple destinations based on department rules
     * 
     * Pattern: Recipient List Pattern
     * Input: User message
     * Output: Comma-separated list of destinations
     */
    @Bean
    public Function<Message<User>, String> userMultiDestinationRouter() {
        return message -> {
            try {
                User user = message.getPayload();
                String department = user.getDepartment();
                
                log.debug("🔀 Multi-destination routing for user: {} (department: {})", 
                    user.getId(), department);

                // Build list of destinations based on business rules
                StringBuilder destinations = new StringBuilder();
                
                // Primary department destination
                destinations.append(determineUserDestination(department));
                
                // Additional destinations based on business rules
                if (isExecutiveDepartment(department)) {
                    destinations.append(",executive-notifications");
                    destinations.append(",vip-processing");
                }
                
                if (isITDepartment(department)) {
                    destinations.append(",it-security-notifications");
                    destinations.append(",system-admin-alerts");
                }
                
                if (isFinanceDepartment(department)) {
                    destinations.append(",financial-compliance");
                    destinations.append(",audit-trail");
                }
                
                // Global notifications for all users
                destinations.append(",global-user-events");
                destinations.append(",analytics-pipeline");

                String finalDestinations = destinations.toString();
                
                log.debug("✅ User routed to multiple destinations: {} -> {}", 
                    user.getId(), finalDestinations);
                
                return finalDestinations;

            } catch (Exception e) {
                log.error("💥 Error in multi-destination routing", e);
                return "user-default-channel"; // Fallback destination
            }
        };
    }

    /**
     * Dynamic department router with header inspection
     * Routes messages dynamically based on header content and department rules
     * 
     * Pattern: Dynamic Router Pattern
     * Input: Generic message
     * Output: Routing destination based on headers
     */
    @Bean
    public Function<Message<Object>, String> dynamicDepartmentRouter() {
        return message -> {
            try {
                // Extract routing information from headers
                String messageType = (String) message.getHeaders().get(MessageHeaders.EVENT_TYPE);
                String department = (String) message.getHeaders().get("department");
                String priority = (String) message.getHeaders().get("priority");
                String source = (String) message.getHeaders().get(MessageHeaders.SOURCE_SERVICE);
                
                log.debug("🔀 Dynamic routing: type={}, dept={}, priority={}, source={}", 
                    messageType, department, priority, source);

                // Dynamic routing logic based on multiple factors
                String destination = "default-channel";
                
                if (messageType != null && department != null) {
                    destination = buildDynamicDestination(messageType, department, priority);
                } else if (department != null) {
                    destination = "dept-" + department.toLowerCase() + "-channel";
                } else if (messageType != null) {
                    destination = "type-" + messageType.toLowerCase() + "-channel";
                }
                
                // Add routing metadata
                Map<String, Object> routingHeaders = new HashMap<>(message.getHeaders());
                routingHeaders.put("dynamic-routing-applied", true);
                routingHeaders.put("routing-factors", String.format("type=%s,dept=%s,priority=%s", 
                    messageType, department, priority));
                routingHeaders.put("routing-destination", destination);
                routingHeaders.put("routing-timestamp", System.currentTimeMillis());

                log.debug("✅ Dynamic routing completed: destination={}", destination);
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error in dynamic routing", e);
                return "error-channel"; // Error destination
            }
        };
    }

    /**
     * Conditional department router with business rules
     * Routes messages based on complex business conditions
     * 
     * Pattern: Message Filter + Content-Based Router
     * Input: User message
     * Output: Routing destination or filter result
     */
    @Bean
    public Function<Message<User>, String> conditionalDepartmentRouter() {
        return message -> {
            try {
                User user = message.getPayload();
                String department = user.getDepartment();
                
                log.debug("🔀 Conditional routing for user: {} (department: {})", 
                    user.getId(), department);

                // Business rule conditions
                boolean isVIPUser = isVIPDepartment(department);
                boolean isActiveUser = user.getStatus() == User.UserStatus.ACTIVE;
                boolean isNewUser = isNewUser(user);
                boolean requiresSpecialHandling = requiresSpecialHandling(user);
                
                // Conditional routing logic
                String destination;
                
                if (!isActiveUser) {
                    destination = "inactive-user-channel";
                } else if (isVIPUser && requiresSpecialHandling) {
                    destination = "vip-special-handling-channel";
                } else if (isVIPUser) {
                    destination = "vip-user-channel";
                } else if (isNewUser) {
                    destination = "new-user-onboarding-channel";
                } else if (requiresSpecialHandling) {
                    destination = "special-handling-channel";
                } else {
                    destination = determineUserDestination(department);
                }
                
                // Add condition metadata
                Map<String, Object> conditionHeaders = new HashMap<>(message.getHeaders());
                conditionHeaders.put("routing-conditions", String.format(
                    "vip=%s,active=%s,new=%s,special=%s", 
                    isVIPUser, isActiveUser, isNewUser, requiresSpecialHandling));
                conditionHeaders.put("routing-destination", destination);
                conditionHeaders.put("routing-timestamp", System.currentTimeMillis());

                log.debug("✅ Conditional routing completed: {} -> {} (conditions applied)", 
                    user.getId(), destination);
                
                return destination;

            } catch (Exception e) {
                log.error("💥 Error in conditional routing", e);
                return "error-channel";
            }
        };
    }

    // ===================
    // PRIVATE HELPER METHODS
    // ===================

    /**
     * Determine user destination based on department
     */
    private String determineUserDestination(String department) {
        if (department == null) {
            return "user-general-channel";
        }
        
        String dept = department.toUpperCase();
        return switch (dept) {
            case "ENGINEERING", "IT" -> "user-tech-channel";
            case "SALES", "MARKETING" -> "user-business-channel";
            case "HR", "OPERATIONS" -> "user-operations-channel";
            case "FINANCE", "ACCOUNTING" -> "user-finance-channel";
            case "EXECUTIVE", "MANAGEMENT" -> "user-executive-channel";
            default -> "user-general-channel";
        };
    }

    /**
     * Determine order destination based on customer department and order details
     */
    private String determineOrderDestination(String customerDepartment, Order order) {
        if (customerDepartment == null) {
            return "order-general-channel";
        }
        
        String dept = customerDepartment.toUpperCase();
        
        // Consider order value for routing decisions
        boolean isHighValue = order.getTotalAmount().compareTo(new java.math.BigDecimal("1000")) > 0;
        boolean isUrgent = order.getPriority() == Order.OrderPriority.URGENT;
        
        if (isHighValue && isUrgent) {
            return "order-priority-channel";
        }
        
        return switch (dept) {
            case "ENGINEERING", "IT" -> "order-tech-channel";
            case "SALES", "MARKETING" -> "order-business-channel";
            case "HR", "OPERATIONS" -> "order-operations-channel";
            case "FINANCE", "ACCOUNTING" -> "order-finance-channel";
            case "EXECUTIVE", "MANAGEMENT" -> "order-executive-channel";
            default -> "order-general-channel";
        };
    }

    /**
     * Build dynamic destination based on multiple factors
     */
    private String buildDynamicDestination(String messageType, String department, String priority) {
        StringBuilder destination = new StringBuilder();
        
        destination.append(messageType.toLowerCase()).append("-");
        destination.append(department.toLowerCase()).append("-");
        
        if ("HIGH".equalsIgnoreCase(priority) || "URGENT".equalsIgnoreCase(priority)) {
            destination.append("priority-");
        }
        
        destination.append("channel");
        
        return destination.toString();
    }

    /**
     * Check if department is executive level
     */
    private boolean isExecutiveDepartment(String department) {
        if (department == null) return false;
        String dept = department.toUpperCase();
        return dept.contains("EXECUTIVE") || dept.contains("MANAGEMENT") || dept.contains("CEO");
    }

    /**
     * Check if department is IT-related
     */
    private boolean isITDepartment(String department) {
        if (department == null) return false;
        String dept = department.toUpperCase();
        return dept.contains("IT") || dept.contains("ENGINEERING") || dept.contains("TECH");
    }

    /**
     * Check if department is finance-related
     */
    private boolean isFinanceDepartment(String department) {
        if (department == null) return false;
        String dept = department.toUpperCase();
        return dept.contains("FINANCE") || dept.contains("ACCOUNTING") || dept.contains("AUDIT");
    }

    /**
     * Check if department is VIP level
     */
    private boolean isVIPDepartment(String department) {
        return isExecutiveDepartment(department) || isFinanceDepartment(department);
    }

    /**
     * Check if user is new (created within last 30 days)
     */
    private boolean isNewUser(User user) {
        if (user.getCreatedAt() == null) return false;
        return user.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusDays(30));
    }

    /**
     * Check if user requires special handling
     */
    private boolean requiresSpecialHandling(User user) {
        // Business logic for special handling
        return isVIPDepartment(user.getDepartment()) || 
               user.getStatus() == User.UserStatus.SUSPENDED ||
               user.getStatus() == User.UserStatus.PENDING;
    }
} 