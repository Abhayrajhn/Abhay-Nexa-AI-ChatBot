package com.abhay.planning;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents a multi-step execution plan.
 * A plan consists of ordered steps that execute sequentially. Steps can depend on previous steps' results.
 * Example: Plan: "Calculate 25 * 40 and tell me the time in Bangalore" Step 1: calculator("25 * 40") Step 2:
 * get_current_time("Asia/Kolkata")
 */
public class Plan {

    private String id;
    private String description;
    private List<PlanStep> steps;

    public Plan() {
        this.id = UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
    }

    public Plan(String description) {
        this();
        this.description = description;
    }

    /**
     * Add a step to the plan. Steps are executed in the order they are added.
     */
    public void addStep(PlanStep step) {
        steps.add(step);
    }

    /**
     * Get step by index (0-based).
     */
    public PlanStep getStep(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new IndexOutOfBoundsException("Step index " + index + " out of bounds (size: " + steps.size() + ")");
        }
        return steps.get(index);
    }

    /**
     * Check if all steps are executed.
     */
    public boolean isComplete() {
        return steps.stream().allMatch(PlanStep::isExecuted);
    }

    /**
     * Get all executed steps.
     */
    public List<PlanStep> getExecutedSteps() {
        return steps.stream().filter(PlanStep::isExecuted).collect(Collectors.toList());
    }

    /**
     * Get all pending (not yet executed) steps.
     */
    public List<PlanStep> getPendingSteps() {
        return steps.stream().filter(step -> !step.isExecuted()).collect(Collectors.toList());
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PlanStep> steps) {
        this.steps = steps;
    }

    public int getStepCount() {
        return steps.size();
    }

    @Override
    public String toString() {
        return "Plan{" + "id='" + id + '\'' + ", description='" + description + '\'' + ", steps=" + steps.size() + ", complete="
                + isComplete() + '}';
    }
}
