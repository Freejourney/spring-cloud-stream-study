package com.example.springcloudstream.common.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

/**
 * Date Time Utilities
 * 
 * Comprehensive utility class for date and time operations in Spring Cloud Stream applications.
 * Provides common date/time formatting, parsing, conversion, and calculation methods.
 * 
 * @author Spring Cloud Stream Study
 * @version 1.0
 * @since 2024-01-01
 */
public final class DateTimeUtils {

    // Private constructor to prevent instantiation
    private DateTimeUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Common date/time formatters
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;
    
    // Custom formatters
    public static final DateTimeFormatter READABLE_DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter READABLE_DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter READABLE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter COMPACT_DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    // Common time zones
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    public static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    // ==================== CURRENT TIME METHODS ====================

    /**
     * Get current timestamp as LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Get current timestamp in UTC
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC_ZONE);
    }

    /**
     * Get current timestamp as epoch milliseconds
     */
    public static long nowAsEpochMilli() {
        return Instant.now().toEpochMilli();
    }

    /**
     * Get current timestamp as Instant
     */
    public static Instant nowAsInstant() {
        return Instant.now();
    }

    /**
     * Get current date
     */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Get current time
     */
    public static LocalTime nowTime() {
        return LocalTime.now();
    }

    // ==================== FORMATTING METHODS ====================

    /**
     * Format LocalDateTime to ISO string
     */
    public static String formatToIsoString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_DATE_TIME_FORMATTER) : null;
    }

    /**
     * Format LocalDateTime to readable string
     */
    public static String formatToReadableString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(READABLE_DATE_TIME_FORMATTER) : null;
    }

    /**
     * Format LocalDate to ISO string
     */
    public static String formatDateToIsoString(LocalDate date) {
        return date != null ? date.format(ISO_DATE_FORMATTER) : null;
    }

    /**
     * Format LocalTime to string
     */
    public static String formatTimeToString(LocalTime time) {
        return time != null ? time.format(READABLE_TIME_FORMATTER) : null;
    }

    /**
     * Format to compact string (for filenames, IDs, etc.)
     */
    public static String formatToCompactString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(COMPACT_DATE_TIME_FORMATTER) : null;
    }

    /**
     * Format with custom pattern
     */
    public static String formatWithPattern(LocalDateTime dateTime, String pattern) {
        if (dateTime == null || pattern == null) {
            return null;
        }
        try {
            return dateTime.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pattern: " + pattern, e);
        }
    }

    // ==================== PARSING METHODS ====================

    /**
     * Parse ISO string to LocalDateTime
     */
    public static LocalDateTime parseFromIsoString(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeString, ISO_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ISO date time format: " + dateTimeString, e);
        }
    }

    /**
     * Parse readable string to LocalDateTime
     */
    public static LocalDateTime parseFromReadableString(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeString, READABLE_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid readable date time format: " + dateTimeString, e);
        }
    }

    /**
     * Parse with custom pattern
     */
    public static LocalDateTime parseWithPattern(String dateTimeString, String pattern) {
        if (dateTimeString == null || pattern == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Failed to parse '" + dateTimeString + 
                "' with pattern '" + pattern + "'", e);
        }
    }

    /**
     * Parse epoch milliseconds to LocalDateTime
     */
    public static LocalDateTime parseFromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), SYSTEM_ZONE);
    }

    /**
     * Parse epoch milliseconds to LocalDateTime in UTC
     */
    public static LocalDateTime parseFromEpochMilliUtc(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), UTC_ZONE);
    }

    // ==================== CONVERSION METHODS ====================

    /**
     * Convert LocalDateTime to epoch milliseconds
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(SYSTEM_ZONE).toInstant().toEpochMilli() : 0;
    }

    /**
     * Convert LocalDateTime to epoch milliseconds in UTC
     */
    public static long toEpochMilliUtc(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(UTC_ZONE).toInstant().toEpochMilli() : 0;
    }

    /**
     * Convert LocalDateTime to Instant
     */
    public static Instant toInstant(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(SYSTEM_ZONE).toInstant() : null;
    }

    /**
     * Convert between time zones
     */
    public static LocalDateTime convertTimeZone(LocalDateTime dateTime, ZoneId fromZone, ZoneId toZone) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(fromZone).withZoneSameInstant(toZone).toLocalDateTime();
    }

    // ==================== CALCULATION METHODS ====================

    /**
     * Calculate duration between two LocalDateTime instances
     */
    public static Duration calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return Duration.ZERO;
        }
        return Duration.between(start, end);
    }

    /**
     * Calculate duration in milliseconds
     */
    public static long calculateDurationMillis(LocalDateTime start, LocalDateTime end) {
        return calculateDuration(start, end).toMillis();
    }

    /**
     * Calculate duration in seconds
     */
    public static long calculateDurationSeconds(LocalDateTime start, LocalDateTime end) {
        return calculateDuration(start, end).getSeconds();
    }

    /**
     * Calculate duration in minutes
     */
    public static long calculateDurationMinutes(LocalDateTime start, LocalDateTime end) {
        return calculateDuration(start, end).toMinutes();
    }

    /**
     * Calculate duration in hours
     */
    public static long calculateDurationHours(LocalDateTime start, LocalDateTime end) {
        return calculateDuration(start, end).toHours();
    }

    /**
     * Calculate days between two dates
     */
    public static long calculateDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * Add duration to LocalDateTime
     */
    public static LocalDateTime addDuration(LocalDateTime dateTime, Duration duration) {
        return dateTime != null && duration != null ? dateTime.plus(duration) : dateTime;
    }

    /**
     * Subtract duration from LocalDateTime
     */
    public static LocalDateTime subtractDuration(LocalDateTime dateTime, Duration duration) {
        return dateTime != null && duration != null ? dateTime.minus(duration) : dateTime;
    }

    /**
     * Add days to LocalDateTime
     */
    public static LocalDateTime addDays(LocalDateTime dateTime, long days) {
        return dateTime != null ? dateTime.plusDays(days) : null;
    }

    /**
     * Add hours to LocalDateTime
     */
    public static LocalDateTime addHours(LocalDateTime dateTime, long hours) {
        return dateTime != null ? dateTime.plusHours(hours) : null;
    }

    /**
     * Add minutes to LocalDateTime
     */
    public static LocalDateTime addMinutes(LocalDateTime dateTime, long minutes) {
        return dateTime != null ? dateTime.plusMinutes(minutes) : null;
    }

    // ==================== VALIDATION METHODS ====================

    /**
     * Check if date is in the past
     */
    public static boolean isInPast(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isBefore(LocalDateTime.now());
    }

    /**
     * Check if date is in the future
     */
    public static boolean isInFuture(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isAfter(LocalDateTime.now());
    }

    /**
     * Check if date is today
     */
    public static boolean isToday(LocalDate date) {
        return date != null && date.equals(LocalDate.now());
    }

    /**
     * Check if two dates are the same day
     */
    public static boolean isSameDay(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return dateTime1.toLocalDate().equals(dateTime2.toLocalDate());
    }

    /**
     * Check if date is within range
     */
    public static boolean isWithinRange(LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {
        if (dateTime == null || start == null || end == null) {
            return false;
        }
        return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get start of day
     */
    public static LocalDateTime getStartOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    /**
     * Get end of day
     */
    public static LocalDateTime getEndOfDay(LocalDate date) {
        return date != null ? date.atTime(23, 59, 59, 999999999) : null;
    }

    /**
     * Get start of month
     */
    public static LocalDate getStartOfMonth(LocalDate date) {
        return date != null ? date.withDayOfMonth(1) : null;
    }

    /**
     * Get end of month
     */
    public static LocalDate getEndOfMonth(LocalDate date) {
        return date != null ? date.withDayOfMonth(date.lengthOfMonth()) : null;
    }

    /**
     * Get readable time ago string
     */
    public static String getTimeAgoString(LocalDateTime pastTime) {
        if (pastTime == null) {
            return "unknown";
        }

        Duration duration = Duration.between(pastTime, LocalDateTime.now());
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return seconds + " seconds ago";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes ago";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " hours ago";
        } else {
            return (seconds / 86400) + " days ago";
        }
    }

    /**
     * Create business day calculator (excluding weekends)
     */
    public static LocalDate addBusinessDays(LocalDate startDate, int businessDays) {
        if (startDate == null) {
            return null;
        }

        LocalDate date = startDate;
        int daysAdded = 0;

        while (daysAdded < businessDays) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                daysAdded++;
            }
        }

        return date;
    }

    /**
     * Get age from birth date
     */
    public static int calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
} 