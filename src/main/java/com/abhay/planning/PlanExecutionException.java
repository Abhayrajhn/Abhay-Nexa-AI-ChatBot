package com.abhay.planning;

/**
 * Exception thrown when plan execution fails.
 *
 * This can happen when:
 * - A step's dependencies are not satisfied
 * - A tool execution fails
 * - Variable substitution fails
 * - Plan validation fails
 */
public class PlanExecutionException extends RuntimeException {

    public PlanExecutionException(String message) {
        super(message);
    }

    public PlanExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
