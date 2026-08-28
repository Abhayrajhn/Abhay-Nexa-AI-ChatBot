package com.abhay.tool;

/**
 * Exception thrown when a tool execution fails.
 * This exception is used to communicate tool errors back to the LLM. The LLM will see the error message and can explain it to the user.
 * Examples: - Calculator: "Invalid expression: division by zero" - Time: "Invalid timezone: XYZ" - Database: "Query failed: connection
 * timeout"
 * The error message should be: - Clear and specific - User-friendly (the LLM will relay it) - Not expose sensitive information - Not
 * include stack traces (those go to logs)
 */
public class ToolExecutionException extends Exception {

    /**
     * Creates a new ToolExecutionException with the given message.
     *
     * @param message
     *         The error message describing what went wrong
     */
    public ToolExecutionException(String message) {
        super(message);
    }

    /**
     * Creates a new ToolExecutionException with a message and cause.
     *
     * @param message
     *         The error message describing what went wrong
     * @param cause
     *         The underlying exception that caused this error
     */
    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
