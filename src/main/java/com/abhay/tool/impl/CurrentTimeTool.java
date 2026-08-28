package com.abhay.tool.impl;

import com.abhay.model.llm.ToolDefinition;
import com.abhay.tool.Tool;
import com.abhay.tool.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Current Time Tool
 * Returns the current date and time for a given timezone. Uses Java's ZonedDateTime with timezone support.
 * Example usage: User: "What time is it in Bangalore?" LLM calls: get_current_time({ "timezone": "Asia/Kolkata" }) Tool returns: { "time":
 * "2026-08-20 14:30:45", "timezone": "Asia/Kolkata", "day": "Wednesday" } LLM responds: "The current time in Bangalore is 2:30 PM on
 * Wednesday."
 * Timezone format: IANA timezone database (e.g., "America/New_York", "Asia/Tokyo")
 */
@Component
public class CurrentTimeTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getName() {
        return "get_current_time";
    }

    @Override
    public String getDescription() {
        return "Returns the current date and time for a given timezone or location. "
                + "Use standard IANA timezone identifiers (e.g., 'Asia/Kolkata', 'America/New_York', 'Europe/London'). "
                + "Defaults to UTC if no timezone is specified.";
    }

    @Override
    public ToolDefinition getDefinition() {
        // JSON Schema for the parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> timezoneProperty = new HashMap<>();
        timezoneProperty.put("type", "string");
        timezoneProperty.put("description",
                "IANA timezone identifier (e.g., 'Asia/Kolkata', 'America/New_York'). Defaults to UTC if not specified.");
        timezoneProperty.put("default", "UTC");
        properties.put("timezone", timezoneProperty);

        parameters.put("properties", properties);
        // timezone is optional (has default)

        return ToolDefinition.create(getName(), getDescription(), parameters);
    }

    @Override
    public String execute(String arguments) throws ToolExecutionException {
        try {
            // Parse JSON arguments
            JsonNode argsNode = objectMapper.readTree(arguments);

            // Get timezone (default to UTC if not provided)
            String timezoneStr = "UTC";
            if (argsNode.has("timezone") && !argsNode.get("timezone").isNull()) {
                timezoneStr = argsNode.get("timezone").asText();
            }

            // Validate and parse timezone
            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(timezoneStr);
            } catch (Exception e) {
                throw new ToolExecutionException(
                        "Invalid timezone: " + timezoneStr + ". Use IANA timezone identifiers (e.g., 'Asia/Kolkata', 'America/New_York').");
            }

            // Get current time in the specified timezone
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            // Format response
            Map<String, Object> response = new HashMap<>();
            response.put("time", now.format(FORMATTER));
            response.put("timezone", timezoneStr);
            response.put("day", now.getDayOfWeek().toString());
            response.put("iso8601", now.toString());  // Full ISO format for precision

            return objectMapper.writeValueAsString(response);

        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to get current time: " + e.getMessage(), e);
        }
    }
}
