package com.abhay.memory;

import java.util.*;

/**
 * Working Memory represents the agent's current task execution state.
 * This is NOT persisted to the database. It exists only during request processing and is used to track: - What task is being executed -
 * What steps have been completed - What variables/results are available - What the current goal is
 * Think of this as the agent's "short-term memory" or "scratch pad" while working.
 * Usage: 1. Create WorkingMemory when starting a task 2. Update it during execution (add variables, mark steps complete) 3. Pass it to
 * Planner and PlanExecutor for context 4. Discard it after task completes
 */
public class WorkingMemory {

    private String taskDescription;              // What task are we doing?
    private String currentGoal;                  // What are we doing RIGHT NOW?
    private Map<String, Object> variables;       // Results from completed steps
    private List<String> completedSteps;         // What we've already done
    private List<String> pendingSteps;           // What we still need to do
    private Map<String, Object> context;         // Additional context/observations
    private List<String> observations;           // Notes about what we've seen

    public WorkingMemory(String taskDescription) {
        this.taskDescription = taskDescription;
        this.variables = new HashMap<>();
        this.completedSteps = new ArrayList<>();
        this.pendingSteps = new ArrayList<>();
        this.context = new HashMap<>();
        this.observations = new ArrayList<>();
    }

    // Current goal tracking
    public void setCurrentGoal(String goal) {
        this.currentGoal = goal;
    }

    public String getCurrentGoal() {
        return currentGoal;
    }

    // Step tracking
    public void markStepComplete(String stepDescription) {
        completedSteps.add(stepDescription);
        pendingSteps.remove(stepDescription);
    }

    public void addPendingStep(String stepDescription) {
        if (!pendingSteps.contains(stepDescription)) {
            pendingSteps.add(stepDescription);
        }
    }

    // Variable management (for step results)
    public void addVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }

    public Map<String, Object> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    // Context management (for additional info)
    public void addContext(String key, Object value) {
        context.put(key, value);
    }

    public Object getContext(String key) {
        return context.get(key);
    }

    public Map<String, Object> getAllContext() {
        return Collections.unmodifiableMap(context);
    }

    // Observations (for agent reasoning)
    public void addObservation(String observation) {
        observations.add(observation);
    }

    public List<String> getObservations() {
        return Collections.unmodifiableList(observations);
    }

    // Summary for logging/debugging
    public String summarize() {
        return String.format("WorkingMemory{task='%s', currentGoal='%s', completed=%d, pending=%d, variables=%d}", taskDescription,
                currentGoal, completedSteps.size(), pendingSteps.size(), variables.size());
    }

    // Getters
    public String getTaskDescription() {
        return taskDescription;
    }

    public List<String> getCompletedSteps() {
        return Collections.unmodifiableList(completedSteps);
    }

    public List<String> getPendingSteps() {
        return Collections.unmodifiableList(pendingSteps);
    }
}
