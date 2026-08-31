package com.abhay.agent;

/**
 * Custom exception for agent execution errors.
 * Thrown when the agent runtime encounters errors such as: - Maximum iterations exceeded - LLM returns invalid decision format - Critical
 * tool execution failures - Unexpected errors during agent loop
 */
public class AgentExecutionException extends RuntimeException {

    public AgentExecutionException(String message) {
        super(message);
    }

    public AgentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
