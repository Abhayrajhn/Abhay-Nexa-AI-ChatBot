package com.abhay.approval;

import com.abhay.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Represents a request for human approval before executing a tool. When the Agent Runtime detects that a tool requires approval: 1. Creates
 * an ApprovalRequest with PENDING status 2. Saves it to the database 3. Pauses agent execution 4. Returns approval info to frontend The
 * approval request contains: - Original tool name (immutable) - Original tool arguments (immutable) - User who owns this request -
 * Conversation context - Agent state for resumption Security: - Frontend CANNOT modify tool name or arguments - Only the owning user can
 * approve/reject - Approval can only be used once (PENDING → APPROVED/REJECTED) - All data is verified server-side
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    /**
     * Unique identifier (UUID). Used in approval/reject API calls.
     */
    @Id
    private String id;

    /**
     * User who owns this approval request. Only this user can approve/reject it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Conversation where this approval was requested.
     */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /**
     * Name of the tool that requires approval. Example: "delete_conversation" IMMUTABLE: Frontend cannot modify this.
     */
    @Column(name = "tool_name", nullable = false)
    private String toolName;

    /**
     * Tool arguments as JSON string. Example: "{\"conversationId\": \"123\"}" IMMUTABLE: Frontend cannot modify this.
     */
    @Column(name = "tool_arguments", nullable = false, columnDefinition = "TEXT")
    private String toolArguments;

    /**
     * Current status of this approval request. PENDING → APPROVED or REJECTED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    /**
     * Agent state at the time of approval request. Contains: - User request - Iteration number - Tool execution history - Current status
     * This allows the agent to resume from where it paused. Stored as JSON for flexibility.
     */
    @Column(name = "agent_state_json", columnDefinition = "TEXT")
    private String agentStateJson;

    /**
     * When this approval request was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When this approval request was last updated. Updated when status changes (PENDING → APPROVED/REJECTED).
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors

    public ApprovalRequest() {
    }

    public ApprovalRequest(String id, User user, Long conversationId, String toolName, String toolArguments) {
        this.id = id;
        this.user = user;
        this.conversationId = conversationId;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
        this.status = ApprovalStatus.PENDING;
    }

    // Lifecycle callbacks

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Business methods

    /**
     * Check if this approval is still pending.
     */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    /**
     * Check if this approval was approved.
     */
    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    /**
     * Check if this approval was rejected.
     */
    public boolean isRejected() {
        return status == ApprovalStatus.REJECTED;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolArguments() {
        return toolArguments;
    }

    public void setToolArguments(String toolArguments) {
        this.toolArguments = toolArguments;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public String getAgentStateJson() {
        return agentStateJson;
    }

    public void setAgentStateJson(String agentStateJson) {
        this.agentStateJson = agentStateJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
