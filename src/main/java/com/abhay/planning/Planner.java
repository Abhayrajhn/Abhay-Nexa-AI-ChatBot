package com.abhay.planning;

import com.abhay.client.OpenAIClient;
import com.abhay.model.llm.Message;
import com.abhay.model.llm.ToolDefinition;
import com.abhay.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates execution plans using LLM. The Planner: 1. Analyzes user requests to determine if planning is needed 2. Uses OpenAI to generate
 * structured JSON plans 3. Validates that all tools in the plan exist in ToolRegistry 4. Returns a Plan object ready for execution
 * Security: The planner can ONLY select tools from ToolRegistry. It cannot create new tools or execute arbitrary code.
 */
@Component
public class Planner {

    private static final Logger logger = LoggerFactory.getLogger(Planner.class);

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Determines if a user request needs planning. Planning is needed when: - Request has sequential steps ("then", "after that",
     * "first...second") - Request has dependencies (one operation depends on another's result) - Request contains "and" connecting multiple
     * distinct operations
     *
     * @param userRequest
     *         The user's message
     * @return true if planning is needed, false for simple tool calling
     */
    public boolean needsPlanning(String userRequest) {
        String lower = userRequest.toLowerCase();

        // Keywords that suggest multi-step operations
        boolean hasSequentialKeywords =
                lower.contains(" then ") || lower.contains(" and then ") || lower.contains(" after that ") || lower.contains(" after ") || (
                        lower.contains("first") && (lower.contains("second") || lower.contains("then"))) || lower.contains("step 1")
                        || lower.contains("step 2") ||
                        // "Get X and convert" pattern suggests dependency
                        (lower.contains("convert") && (lower.contains("get") || lower.contains("find")));

        // Check for "and" connecting distinct operations (not just parameters)
        // e.g., "Calculate X and tell me Y" suggests two operations
        boolean hasMultipleOperations = false;
        if (lower.contains(" and ")) {
            String[] parts = lower.split(" and ");
            if (parts.length >= 2) {
                // Check if both parts have verbs (suggests two operations)
                String[] actionVerbs = { "calculate", "tell", "get", "find", "convert", "show", "give" };
                int verbCount = 0;
                for (String part : parts) {
                    for (String verb : actionVerbs) {
                        if (part.contains(verb)) {
                            verbCount++;
                            break;
                        }
                    }
                }
                hasMultipleOperations = verbCount >= 2;
            }
        }

        if (hasSequentialKeywords || hasMultipleOperations) {
            logger.info("Planning needed: detected sequential keywords or multiple operations");
            return true;
        }

        // Future enhancement: Use LLM to decide more intelligently
        logger.info("Planning not needed: simple request");
        return false;
    }

    /**
     * Creates a plan for the given user request. Uses LLM to analyze the request and generate a structured plan.
     *
     * @param userRequest
     *         The user's message
     * @param conversationHistory
     *         Previous messages for context (optional)
     * @return A Plan object ready for execution
     */
    public Plan createPlan(String userRequest, List<Message> conversationHistory) {
        logger.info("Creating plan for request: {}", userRequest);

        try {
            // 1. Build planning prompt
            String planningPrompt = buildPlanningPrompt();

            // 2. Create messages for LLM
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", planningPrompt));
            messages.add(new Message("user", userRequest));

            // 3. Call OpenAI to generate plan
            String planJson = openAIClient.sendMessage(messages);
            logger.info("Received plan JSON from LLM");
            logger.debug("Plan JSON: {}", planJson);

            // 4. Parse JSON into Plan object
            Plan plan = parsePlanJson(planJson);

            // 5. Validate plan
            validatePlan(plan);

            logger.info("Plan created successfully with {} steps", plan.getStepCount());
            return plan;

        } catch (Exception e) {
            logger.error("Failed to create plan: {}", e.getMessage(), e);
            throw new PlanExecutionException("Failed to create plan: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the system prompt for plan generation.
     */
    private String buildPlanningPrompt() {
        // Get available tools
        List<ToolDefinition> tools = toolRegistry.getAllDefinitions();

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a task planner. Your job is to break down user requests into sequential steps.\n\n");
        prompt.append("Available tools with their EXACT parameter schemas:\n\n");

        for (ToolDefinition tool : tools) {
            prompt.append("Tool: ").append(tool.getFunction().getName()).append("\n");
            prompt.append("Description: ").append(tool.getFunction().getDescription()).append("\n");
            try {
                prompt.append("Parameters: ").append(objectMapper.writeValueAsString(tool.getFunction().getParameters())).append("\n\n");
            } catch (Exception e) {
                prompt.append("Parameters: ").append(tool.getFunction().getParameters().toString()).append("\n\n");
            }
        }

        prompt.append("\nYour task:\n");
        prompt.append("1. Analyze the user's request\n");
        prompt.append("2. Break it into sequential steps\n");
        prompt.append("3. Each step must use ONE tool from the available tools\n");
        prompt.append("4. Use the EXACT parameter names from each tool's schema above\n");
        prompt.append("5. If a step depends on a previous step's result, use outputVariable and dependsOn\n");
        prompt.append("6. Use {{variable_name}} in arguments to reference previous step results\n\n");

        prompt.append("Return ONLY a JSON object in this exact format:\n");
        prompt.append("{\n");
        prompt.append("  \"description\": \"Brief description of what this plan does\",\n");
        prompt.append("  \"steps\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"stepNumber\": 1,\n");
        prompt.append("      \"description\": \"What this step does\",\n");
        prompt.append("      \"toolName\": \"calculator\",\n");
        prompt.append("      \"arguments\": \"{\\\"expression\\\": \\\"25 * 40\\\"}\",\n");
        prompt.append("      \"outputVariable\": \"result\",\n");
        prompt.append("      \"dependsOn\": []\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("CRITICAL rules:\n");
        prompt.append("- Use ONLY tools from the available tools list above\n");
        prompt.append("- Use EXACT parameter names from each tool's schema (e.g., calculator uses 'expression', not 'param')\n");
        prompt.append("- arguments MUST be a valid JSON string (properly escaped)\n");
        prompt.append("- For calculator tool, ALWAYS use: {\\\"expression\\\": \\\"math here\\\"}\n");
        prompt.append("- For get_current_time tool, ALWAYS use: {\\\"timezone\\\": \\\"timezone here\\\"}\n");
        prompt.append("- outputVariable is optional (only if result is needed by later steps)\n");
        prompt.append("- dependsOn is optional (array of step numbers this step depends on)\n");
        prompt.append("- Use {{variable_name}} in arguments to reference outputs from previous steps\n");
        prompt.append("- stepNumber must start at 1 and increment by 1\n");
        prompt.append("- Return ONLY the JSON object, no explanation or markdown formatting\n");

        return prompt.toString();
    }

    /**
     * Parses the LLM's JSON response into a Plan object.
     */
    private Plan parsePlanJson(String planJson) {
        try {
            // Extract JSON if wrapped in markdown code blocks
            planJson = planJson.trim();
            if (planJson.startsWith("```json")) {
                planJson = planJson.substring(7);
            }
            if (planJson.startsWith("```")) {
                planJson = planJson.substring(3);
            }
            if (planJson.endsWith("```")) {
                planJson = planJson.substring(0, planJson.length() - 3);
            }
            planJson = planJson.trim();

            // Parse JSON
            JsonNode root = objectMapper.readTree(planJson);

            Plan plan = new Plan();

            if (root.has("description")) {
                plan.setDescription(root.get("description").asText());
            }

            JsonNode stepsNode = root.get("steps");
            if (stepsNode == null || !stepsNode.isArray()) {
                throw new IllegalArgumentException("Plan JSON must contain 'steps' array");
            }

            for (JsonNode stepNode : stepsNode) {
                PlanStep step = new PlanStep();

                // Required fields
                if (!stepNode.has("stepNumber") || !stepNode.has("toolName") || !stepNode.has("arguments")) {
                    throw new IllegalArgumentException("Each step must have stepNumber, toolName, and arguments");
                }

                step.setStepNumber(stepNode.get("stepNumber").asInt());
                step.setToolName(stepNode.get("toolName").asText());
                step.setArguments(stepNode.get("arguments").asText());

                // Optional fields
                if (stepNode.has("description") && !stepNode.get("description").isNull()) {
                    step.setDescription(stepNode.get("description").asText());
                }

                if (stepNode.has("outputVariable") && !stepNode.get("outputVariable").isNull()) {
                    String varName = stepNode.get("outputVariable").asText();
                    if (!varName.isEmpty()) {
                        step.setOutputVariable(varName);
                    }
                }

                if (stepNode.has("dependsOn") && stepNode.get("dependsOn").isArray()) {
                    List<Integer> deps = new ArrayList<>();
                    for (JsonNode depNode : stepNode.get("dependsOn")) {
                        deps.add(depNode.asInt());
                    }
                    if (!deps.isEmpty()) {
                        step.setDependsOn(deps);
                    }
                }

                plan.addStep(step);
            }

            return plan;

        } catch (Exception e) {
            logger.error("Failed to parse plan JSON: {}", e.getMessage(), e);
            throw new PlanExecutionException("Failed to parse plan JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the plan can be executed. Checks: - All tools exist in ToolRegistry - Dependencies are valid (reference existing
     * steps) - Step numbers are sequential starting from 1
     */
    private void validatePlan(Plan plan) {
        if (plan.getStepCount() == 0) {
            throw new IllegalArgumentException("Plan validation failed: Plan has no steps");
        }

        for (int i = 0; i < plan.getStepCount(); i++) {
            PlanStep step = plan.getStep(i);

            // Check step number is correct
            if (step.getStepNumber() != i + 1) {
                throw new IllegalArgumentException(
                        "Plan validation failed: Step numbers must be sequential starting from 1. Expected " + (i + 1) + " but got "
                                + step.getStepNumber());
            }

            // Check tool exists in registry
            if (!toolRegistry.hasTool(step.getToolName())) {
                throw new IllegalArgumentException(
                        "Plan validation failed: Unknown tool '" + step.getToolName() + "' in step " + step.getStepNumber()
                                + ". Available tools: " + toolRegistry.getToolNames());
            }

            // Check dependencies are valid
            if (step.getDependsOn() != null) {
                for (Integer depStepNum : step.getDependsOn()) {
                    if (depStepNum < 1 || depStepNum >= step.getStepNumber()) {
                        throw new IllegalArgumentException(
                                "Plan validation failed: Invalid dependency in step " + step.getStepNumber() + " -> step " + depStepNum
                                        + ". Dependencies must reference earlier steps.");
                    }
                }
            }
        }

        logger.info("Plan validation passed");
    }
}
