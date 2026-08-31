package com.abhay.agent;

/**
 * Types of decisions an agent can make during execution.
 * The agent runtime loop operates on a simple decision model: - CALL_TOOL: The agent needs to execute a tool to gather more information -
 * FINAL_ANSWER: The agent has enough information to provide the final response
 * This enum represents the possible decisions the LLM can make at each iteration.
 */
public enum AgentDecisionType {
    /**
     * Agent decides to call a tool. The agent needs to execute a tool to: - Gather information (e.g., weather data) - Perform calculations
     * - Query databases - Take any action that requires external capabilities
     */
    CALL_TOOL,

    /**
     * Agent decides the task is complete. The agent has enough information to: - Answer the user's question - Complete the requested task -
     * Provide a final response
     * This signals the end of the agent loop.
     */
    FINAL_ANSWER
}
