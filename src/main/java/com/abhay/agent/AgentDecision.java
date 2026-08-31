package com.abhay.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a decision made by the agent at a specific iteration.
 * The LLM returns this structured decision to indicate what it wants to do next: - Call a tool (with tool name and arguments) - Provide
 * final answer (task complete)
 * Example JSON from LLM: { "decision_type": "CALL_TOOL", "tool_name": "weather", "arguments": "{\"city\": \"Bangalore\"}", "reasoning":
 * "Need to check current weather conditions" }
 * Or: { "decision_type": "FINAL_ANSWER", "reasoning": "I have all the information needed to respond" }
 */
public class AgentDecision {

    @JsonProperty("decision_type")
    private AgentDecisionType decisionType;

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("arguments")
    private String arguments;  // JSON string

    @JsonProperty("reasoning")
    private String reasoning;  // Why the agent made this decision (for debugging)

    // Constructors

    public AgentDecision() {
    }

    public AgentDecision(AgentDecisionType decisionType) {
        this.decisionType = decisionType;
    }

    public AgentDecision(AgentDecisionType decisionType, String toolName, String arguments) {
        this.decisionType = decisionType;
        this.toolName = toolName;
        this.arguments = arguments;
    }

    // Factory methods for convenience

    /**
     * Create a CALL_TOOL decision.
     */
    public static AgentDecision callTool(String toolName, String arguments) {
        return new AgentDecision(AgentDecisionType.CALL_TOOL, toolName, arguments);
    }

    /**
     * Create a FINAL_ANSWER decision.
     */
    public static AgentDecision finalAnswer() {
        return new AgentDecision(AgentDecisionType.FINAL_ANSWER);
    }

    // Getters and Setters

    public AgentDecisionType getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(AgentDecisionType decisionType) {
        this.decisionType = decisionType;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    // Utility methods

    public boolean isToolCall() {
        return decisionType == AgentDecisionType.CALL_TOOL;
    }

    public boolean isFinalAnswer() {
        return decisionType == AgentDecisionType.FINAL_ANSWER;
    }

    @Override
    public String toString() {
        if (isToolCall()) {
            return String.format("AgentDecision{type=CALL_TOOL, tool=%s, args=%s}", toolName,
                    arguments != null && arguments.length() > 50 ? arguments.substring(0, 50) + "..." : arguments);
        } else {
            return "AgentDecision{type=FINAL_ANSWER}";
        }
    }
}
