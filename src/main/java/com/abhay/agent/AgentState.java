package com.abhay.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporary state for a single agent execution. This is NOT long-term memory. It only exists during the current agent loop execution.
 * AgentState tracks: - The original user request - Current iteration number - History of tool calls and their results - Execution status -
 * Start time (for timeout detection) Think of this as the agent's "working memory" or "scratch pad" for the current task. Example state
 * during execution: { userRequest: "Check weather in Bangalore. If raining, find umbrella." iteration: 2 toolCalls: [ {tool: "weather",
 * args: {...}, result: "Rainy, 24°C", timestamp: ...} ] status: RUNNING }
 */
public class AgentState {

    /**
     * The original user request that triggered this agent execution.
     */
    private String userRequest;

    /**
     * Current iteration number (starts at 0).
     */
    private int iteration;

    /**
     * History of tool calls made during this execution. Each entry contains: tool name, arguments, result, timestamp.
     */
    private List<ToolExecution> toolExecutions;

    /**
     * Current execution status.
     */
    private ExecutionStatus status;

    /**
     * When this agent execution started.
     */
    private LocalDateTime startTime;

    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        MAX_ITERATIONS_REACHED
    }

    // Constructors

    public AgentState() {
        this.iteration = 0;
        this.toolExecutions = new ArrayList<>();
        this.status = ExecutionStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }

    public AgentState(String userRequest) {
        this();
        this.userRequest = userRequest;
    }

    // Methods for managing state

    /**
     * Record a tool execution (call + result).
     */
    public void addToolExecution(String toolName, String arguments, String result) {
        ToolExecution execution = new ToolExecution(iteration, toolName, arguments, result, LocalDateTime.now());
        toolExecutions.add(execution);
    }

    /**
     * Increment the iteration counter.
     */
    public void incrementIteration() {
        this.iteration++;
    }

    /**
     * Get the most recent tool execution result.
     */
    public String getLastToolResult() {
        if (toolExecutions.isEmpty()) {
            return null;
        }
        return toolExecutions.get(toolExecutions.size() - 1).getResult();
    }

    /**
     * Get all tool execution history as a formatted string. Useful for adding to LLM context.
     */
    public String getToolHistorySummary() {
        if (toolExecutions.isEmpty()) {
            return "No tools executed yet.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Tool execution history:\n");
        for (ToolExecution exec : toolExecutions) {
            summary.append(String.format("- [Iteration %d] %s: %s\n", exec.getIteration(), exec.getToolName(),
                    exec.getResult().length() > 100 ? exec.getResult().substring(0, 100) + "..." : exec.getResult()));
        }
        return summary.toString();
    }

    // Getters and Setters

    public String getUserRequest() {
        return userRequest;
    }

    public void setUserRequest(String userRequest) {
        this.userRequest = userRequest;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public List<ToolExecution> getToolExecutions() {
        return toolExecutions;
    }

    public void setToolExecutions(List<ToolExecution> toolExecutions) {
        this.toolExecutions = toolExecutions;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * Inner class representing a single tool execution.
     */
    public static class ToolExecution {

        private int iteration;
        private String toolName;
        private String arguments;
        private String result;
        private LocalDateTime timestamp;

        public ToolExecution(int iteration, String toolName, String arguments, String result, LocalDateTime timestamp) {
            this.iteration = iteration;
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
            this.timestamp = timestamp;
        }

        // Getters

        public int getIteration() {
            return iteration;
        }

        public String getToolName() {
            return toolName;
        }

        public String getArguments() {
            return arguments;
        }

        public String getResult() {
            return result;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}
