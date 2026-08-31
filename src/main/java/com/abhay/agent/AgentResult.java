package com.abhay.agent;

/**
 * The final result of an agent execution. After the agent loop completes (either by reaching FINAL_ANSWER, max iterations, or requiring
 * approval), this object contains: - Whether execution was successful - Whether approval is pending - The final state (including all tool
 * executions) - Number of iterations used - Error message if failed - Approval ID if pending approval This is returned by
 * AgentRuntime.executeAgentLoop() and used by ConversationService to determine how to proceed (generate final response, handle error, or
 * wait for approval).
 */
public class AgentResult {

    /**
     * Whether the agent successfully completed the task.
     */
    private boolean success;

    /**
     * Whether the agent is waiting for approval. If true, execution is paused pending user decision.
     */
    private boolean pendingApproval;

    /**
     * Approval request ID if pendingApproval is true.
     */
    private String approvalId;

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
        this.pendingApproval = false;
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

    /**
     * Create a pending approval result. The agent has paused and is waiting for user decision.
     */
    public static AgentResult pendingApproval(AgentState finalState, int iterationsUsed, String approvalId) {
        AgentResult result = new AgentResult(true, finalState, iterationsUsed);
        result.setPendingApproval(true);
        result.setApprovalId(approvalId);
        return result;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isPendingApproval() {
        return pendingApproval;
    }

    public void setPendingApproval(boolean pendingApproval) {
        this.pendingApproval = pendingApproval;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
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
        if (pendingApproval) {
            return String.format("AgentResult{pendingApproval=true, approvalId=%s, iterations=%d}", approvalId, iterationsUsed);
        } else if (success) {
            return String.format("AgentResult{success=true, iterations=%d, toolCalls=%d}", iterationsUsed,
                    finalState != null ? finalState.getToolExecutions().size() : 0);
        } else {
            return String.format("AgentResult{success=false, iterations=%d, error=%s}", iterationsUsed, errorMessage);
        }
    }
}
