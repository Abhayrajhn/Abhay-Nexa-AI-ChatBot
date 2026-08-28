package com.abhay.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Executor that safely runs tools with validation and error handling. This component is the bridge between the LLM's tool requests and the
 * actual tool implementations. It provides: 1. Validation: Ensures the tool exists and arguments are valid 2. Safe Execution: Catches
 * exceptions and converts them to error messages 3. Logging: Tracks all tool executions for debugging 4. Security: Only executes registered
 * tools (whitelist approach) Flow: LLM requests tool "calculator" with args '{"expression": "25 * 40"}' ↓
 * ToolExecutor.executeTool("calculator", '{"expression": "25 * 40"}') ↓ Validate: Does "calculator" tool exist? ✓ ↓ Get tool from registry
 * ↓ Execute: tool.execute('{"expression": "25 * 40"}') ↓ Return result: '{"result": 1000}' Error Handling: - Unknown tool → Error JSON -
 * Invalid arguments → Error JSON - Tool execution fails → Error JSON - Never throws exceptions (returns error as JSON instead) This ensures
 * the LLM always gets a response, even if it's an error. The LLM can then explain the error to the user in natural language.
 */
@Component
public class ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;

    @Autowired
    public ToolExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Executes a tool with the given arguments. This is the main entry point for tool execution. It: 1. Validates the tool exists 2.
     * Executes the tool 3. Returns the result 4. Handles errors gracefully
     *
     * @param toolName
     *         The name of the tool to execute (e.g., "calculator")
     * @param arguments
     *         JSON string containing the arguments
     * @return JSON string containing the result or error
     */
    public String executeTool(String toolName, String arguments) {
        logger.info("Executing tool: {} with arguments: {}", toolName,
                arguments.length() > 100 ? arguments.substring(0, 100) + "..." : arguments);

        try {
            // Validate tool exists
            if (!toolRegistry.hasTool(toolName)) {
                String error = buildErrorResponse("Unknown tool: " + toolName + ". Available tools: " + toolRegistry.getToolNames());
                logger.error("Tool not found: {}. Available: {}", toolName, toolRegistry.getToolNames());
                return error;
            }

            // Validate arguments are not null or empty
            if (arguments == null || arguments.trim().isEmpty()) {
                String error = buildErrorResponse("Tool arguments cannot be empty");
                logger.error("Empty arguments provided for tool: {}", toolName);
                return error;
            }

            // Get the tool
            Tool tool = toolRegistry.getTool(toolName);

            // Execute the tool
            long startTime = System.currentTimeMillis();
            String result = tool.execute(arguments);
            long executionTime = System.currentTimeMillis() - startTime;

            logger.info("Tool {} executed successfully in {}ms. Result: {}", toolName, executionTime,
                    result.length() > 200 ? result.substring(0, 200) + "..." : result);

            return result;

        } catch (ToolExecutionException e) {
            // Tool-specific error (expected)
            String error = buildErrorResponse(e.getMessage());
            logger.warn("Tool execution failed for {}: {}", toolName, e.getMessage());
            return error;

        } catch (Exception e) {
            // Unexpected error
            String error = buildErrorResponse("Tool execution failed: " + e.getMessage());
            logger.error("Unexpected error executing tool {}: {}", toolName, e.getMessage(), e);
            return error;
        }
    }

    /**
     * Builds a standardized error response as JSON. Format: {"error": "error message"} This ensures the LLM always gets valid JSON, even
     * for errors. The LLM can parse this and explain the error to the user.
     *
     * @param errorMessage
     *         The error message
     * @return JSON string with the error
     */
    private String buildErrorResponse(String errorMessage) {
        // Escape quotes in error message for valid JSON
        String escapedMessage = errorMessage.replace("\"", "\\\"");
        return String.format("{\"error\": \"%s\"}", escapedMessage);
    }

    /**
     * Validates that a JSON string is well-formed (basic check). This is a simple validation - tools should do more thorough validation of
     * their specific argument schemas.
     *
     * @param json
     *         The JSON string to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        String trimmed = json.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
