package com.abhay.agent;

/**
 * The final result of an agent execution.
 * After the agent loop completes (either by reaching FINAL_ANSWER or max iterations), this object contains: - Whether execution was
 * successful - The final state (including all tool executions) - Number of iterations used - Error message if failed
 * This is returned by AgentRuntime.executeAgentLoop() and used by ConversationService to determine how to proceed (generate final response
 * or handle error).
 */
public class AgentResult {

    /**
     * Whether the agent successfully completed the task.
     */
    private boolean success;

    /**
     * The final agent state (includes all tool executions).
     */
    private AgentState finalState;

    /**
     * Number of iterations the agent used.
     */
    private int iterationsUsed;

    /**
     * Error message if execution failed.
     */
    private String errorMessage;

    // Constructors

    public AgentResult() {
    }

    public AgentResult(boolean success, AgentState finalState, int iterationsUsed) {
        this.success = success;
        this.finalState = finalState;
        this.iterationsUsed = iterationsUsed;
    }

    // Factory methods

    /**
     * Create a successful result.
     */
    public static AgentResult success(AgentState finalState, int iterationsUsed) {
        return new AgentResult(true, finalState, iterationsUsed);
    }

    /**
     * Create a failed result.
     */
    public static AgentResult failure(AgentState finalState, int iterationsUsed, String errorMessage) {
        AgentResult result = new AgentResult(false, finalState, iterationsUsed);
        result.setErrorMessage(errorMessage);
        return result;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public AgentState getFinalState() {
        return finalState;
    }

    public void setFinalState(AgentState finalState) {
        this.finalState = finalState;
    }

    public int getIterationsUsed() {
        return iterationsUsed;
    }

    public void setIterationsUsed(int iterationsUsed) {
        this.iterationsUsed = iterationsUsed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("AgentResult{success=true, iterations=%d, toolCalls=%d}", iterationsUsed,
                    finalState != null ? finalState.getToolExecutions().size() : 0);
        } else {
            return String.format("AgentResult{success=false, iterations=%d, error=%s}", iterationsUsed, errorMessage);
        }
    }
}
