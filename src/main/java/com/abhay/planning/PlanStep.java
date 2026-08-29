package com.abhay.planning;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single step in a plan. Each step: - Calls one tool from the ToolRegistry - Can depend on previous steps - Can store its
 * result in a named variable - Tracks execution status Example: Step 1: calculator("25 * 40") → stores result in "product" Step 2:
 * calculator("{{product}} + 100") → depends on Step 1
 */
public class PlanStep {

    private int stepNumber;           // 1, 2, 3, ...
    private String description;       // Human-readable description
    private String toolName;          // Which tool to call (must exist in ToolRegistry)
    private String arguments;         // JSON arguments for the tool
    private String outputVariable;    // Optional: variable name to store result
    private List<Integer> dependsOn;  // Optional: step numbers this depends on
    private String result;            // Result after execution (null before)
    private boolean executed;         // Has this step been executed?

    public PlanStep() {
        this.dependsOn = new ArrayList<>();
        this.executed = false;
    }

    public PlanStep(int stepNumber, String toolName, String arguments) {
        this();
        this.stepNumber = stepNumber;
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public PlanStep(int stepNumber, String description, String toolName, String arguments) {
        this(stepNumber, toolName, arguments);
        this.description = description;
    }

    /**
     * Check if this step can execute (all dependencies satisfied). A step can execute if: - It has no dependencies, OR - All steps it
     * depends on have been executed
     */
    public boolean canExecute(Plan plan) {
        if (dependsOn == null || dependsOn.isEmpty()) {
            return true;  // No dependencies
        }

        // Check all dependencies are executed
        for (Integer depStepNum : dependsOn) {
            // Convert 1-based step number to 0-based index
            int index = depStepNum - 1;

            if (index < 0 || index >= plan.getStepCount()) {
                return false;  // Invalid dependency
            }

            PlanStep depStep = plan.getStep(index);
            if (!depStep.isExecuted()) {
                return false;  // Dependency not satisfied
            }
        }

        return true;
    }

    /**
     * Add a dependency on another step.
     */
    public void addDependency(int stepNumber) {
        if (dependsOn == null) {
            dependsOn = new ArrayList<>();
        }
        if (!dependsOn.contains(stepNumber)) {
            dependsOn.add(stepNumber);
        }
    }

    /**
     * Check if this step has dependencies.
     */
    public boolean hasDependencies() {
        return dependsOn != null && !dependsOn.isEmpty();
    }

    // Getters and setters
    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getOutputVariable() {
        return outputVariable;
    }

    public void setOutputVariable(String outputVariable) {
        this.outputVariable = outputVariable;
    }

    public List<Integer> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<Integer> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    @Override
    public String toString() {
        return "PlanStep{" + "stepNumber=" + stepNumber + ", description='" + description + '\'' + ", toolName='" + toolName + '\''
                + ", executed=" + executed + ", hasDependencies=" + hasDependencies() + '}';
    }
}
