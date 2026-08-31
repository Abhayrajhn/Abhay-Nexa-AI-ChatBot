package com.abhay.approval;

/**
 * Status of an approval request.
 *
 * Lifecycle:
 * PENDING → APPROVED → (tool executes)
 * PENDING → REJECTED → (tool never executes)
 *
 * Security:
 * - An approval can only be used once
 * - Once APPROVED or REJECTED, status cannot change
 * - Status transitions are enforced by ApprovalService
 */
public enum ApprovalStatus {
    /**
     * Waiting for user decision.
     * Initial state when approval request is created.
     */
    PENDING,

    /**
     * User approved the action.
     * Tool will be executed.
     */
    APPROVED,

    /**
     * User rejected the action.
     * Tool will NOT be executed.
     */
    REJECTED
}
