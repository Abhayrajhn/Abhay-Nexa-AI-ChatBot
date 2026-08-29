package com.abhay.planning;

import com.abhay.tool.ToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes plans step-by-step.
 * The PlanExecutor: 1. Executes steps in order 2. Checks dependencies before each step 3. Substitutes variables from previous steps 4. Uses
 * existing ToolExecutor (no duplication) 5. Returns all results
 * Security: Only executes tools via ToolExecutor, which validates against ToolRegistry.
 */
@Component
public class PlanExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PlanExecutor.class);

    @Autowired
    private ToolExecutor toolExecutor;  // REUSE existing executor

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes a plan step-by-step.
     * Steps execute sequentially. If a step has dependencies, they must be satisfied before the step can execute.
     * Variables from previous steps are substituted into later step arguments using {{variable_name}} syntax.
     *
     * @param plan
     *         The plan to execute
     * @return Map of variable names to their values
     */
    public Map<String, Object> executePlan(Plan plan) {
        logger.info("Executing plan: {} with {} steps", plan.getDescription(), plan.getStepCount());

        Map<String, Object> variables = new HashMap<>();

        for (PlanStep step : plan.getSteps()) {
            logger.info("Executing step {}: {}", step.getStepNumber(), step.getDescription());

            // 1. Check dependencies
            if (!step.canExecute(plan)) {
                throw new PlanExecutionException(
                        "Step " + step.getStepNumber() + " cannot execute: dependencies not satisfied. " + "Depends on steps: "
                                + step.getDependsOn());
            }

            // 2. Substitute variables in arguments
            String resolvedArgs = substituteVariables(step.getArguments(), variables);
            logger.info("Step {} resolved arguments: {}", step.getStepNumber(), resolvedArgs);

            // 3. Execute tool via existing ToolExecutor (REUSE!)
            String result = toolExecutor.executeTool(step.getToolName(), resolvedArgs);
            logger.info("Step {} result: {}", step.getStepNumber(), result.length() > 200 ? result.substring(0, 200) + "..." : result);

            // 4. Store result if variable name provided
            if (step.getOutputVariable() != null && !step.getOutputVariable().isEmpty()) {
                Object value = extractValue(result);
                variables.put(step.getOutputVariable(), value);
                logger.info("Stored variable '{}' = {}", step.getOutputVariable(), value);
            }

            // 5. Mark step as executed
            step.setResult(result);
            step.setExecuted(true);
        }

        logger.info("Plan execution completed successfully. Variables: {}", variables.keySet());
        return variables;
    }

    /**
     * Substitutes variable placeholders in arguments.
     * Example: arguments: "{\"expression\": \"{{celsius_temp}} * 9/5 + 32\"}" variables: {celsius_temp: 28} result: "{\"expression\": \"28
     * * 9/5 + 32\"}"
     * Pattern: {{variable_name}}
     */
    private String substituteVariables(String arguments, Map<String, Object> variables) {
        if (variables.isEmpty()) {
            return arguments;
        }

        String result = arguments;

        // Pattern: {{variable_name}} where variable_name is alphanumeric + underscore
        Pattern pattern = Pattern.compile("\\{\\{(\\w+)\\}\\}");
        Matcher matcher = pattern.matcher(arguments);

        while (matcher.find()) {
            String varName = matcher.group(1);
            if (variables.containsKey(varName)) {
                Object value = variables.get(varName);
                String valueStr = String.valueOf(value);
                result = result.replace("{{" + varName + "}}", valueStr);
                logger.debug("Substituted {{{}}} with {}", varName, valueStr);
            } else {
                logger.warn("Variable {{{}}} referenced but not found in context", varName);
            }
        }

        return result;
    }

    /**
     * Extracts a meaningful value from a tool result.
     * Tool results are JSON. This extracts the primary value for storage.
     * Examples: - {"result": 1000} -> 1000 - {"temperature": 28, "unit": "C"} -> 28 - {"time": "14:30:00", "timezone": "Asia/Kolkata"} ->
     * "14:30:00"
     * If no clear primary value, returns the full JSON string.
     */
    private Object extractValue(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson);

            // Check for common result fields
            if (root.has("result")) {
                return getNodeValue(root.get("result"));
            }
            if (root.has("temperature")) {
                return getNodeValue(root.get("temperature"));
            }
            if (root.has("value")) {
                return getNodeValue(root.get("value"));
            }
            if (root.has("time")) {
                return root.get("time").asText();
            }
            if (root.has("count")) {
                return getNodeValue(root.get("count"));
            }

            // If single field, return it
            if (root.size() == 1) {
                return getNodeValue(root.fields().next().getValue());
            }

            // If error field, return full JSON for context
            if (root.has("error")) {
                return resultJson;
            }

            // Otherwise return the full JSON
            return resultJson;

        } catch (Exception e) {
            logger.warn("Failed to parse result JSON, returning as-is: {}", e.getMessage());
            return resultJson;
        }
    }

    /**
     * Extracts the appropriate Java type from a JsonNode.
     */
    private Object getNodeValue(JsonNode node) {
        if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isNull()) {
            return null;
        } else {
            return node.asText();
        }
    }

    /**
     * Get a summary of plan execution results.
     * Useful for logging or passing to the final LLM call.
     */
    public String getExecutionSummary(Plan plan) {
        StringBuilder summary = new StringBuilder();
        summary.append("Plan: ").append(plan.getDescription()).append("\n\n");

        for (PlanStep step : plan.getSteps()) {
            summary.append("Step ").append(step.getStepNumber()).append(": ");
            summary.append(step.getDescription()).append("\n");
            summary.append("Tool: ").append(step.getToolName()).append("\n");

            if (step.isExecuted()) {
                summary.append("Result: ").append(step.getResult()).append("\n");
            } else {
                summary.append("Status: Not executed\n");
            }

            summary.append("\n");
        }

        return summary.toString();
    }
}
