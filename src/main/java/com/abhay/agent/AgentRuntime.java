package com.abhay.agent;

import com.abhay.client.OpenAIClient;
import com.abhay.model.llm.Message;
import com.abhay.model.llm.ToolDefinition;
import com.abhay.tool.ToolExecutor;
import com.abhay.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent Runtime - Core orchestrator for the DECIDE → ACT → OBSERVE → DECIDE loop. This is the heart of the agent system. Unlike Planning
 * (which generates all steps upfront), the Agent Runtime makes dynamic decisions based on observations. Core Loop: 1. DECIDE: LLM looks at
 * current state and decides next action 2. ACT: If tool call, execute the tool 3. OBSERVE: Add tool result to state 4. DECIDE: LLM decides
 * again based on new information 5. Repeat until FINAL_ANSWER or max iterations Example execution: User: "Check weather in Bangalore. If
 * raining, find umbrella." Iteration 1: DECIDE: LLM → "I need to check weather first" Decision: CALL_TOOL(weather, Bangalore) ACT: Execute
 * weather tool OBSERVE: Result = "Rainy, 24°C" Iteration 2: DECIDE: LLM → "It's raining, so I should search for umbrellas" Decision:
 * CALL_TOOL(search, umbrella) ACT: Execute search tool OBSERVE: Result = "Search results..." Iteration 3: DECIDE: LLM → "I have all
 * information needed" Decision: FINAL_ANSWER → Exit loop, generate final response Key Features: - Dynamic decision making (next step
 * depends on previous results) - Iteration limits (prevents infinite loops) - Tool error handling (LLM decides how to recover) - SSE
 * notifications (frontend sees agent progress)
 */
@Component
public class AgentRuntime {

    private static final Logger logger = LoggerFactory.getLogger(AgentRuntime.class);

    @Value("${agent.max.iterations:10}")
    private int maxIterations;

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Determines if a user request needs the agent runtime loop. Agent runtime is needed when the request has conditional logic where the
     * next action depends on the result of the previous action. Keywords that suggest agent loop: - "if" (conditional) - "based on"
     * (dependency) - "depending on" (conditional) - "check...then" (sequence with dependency) Examples: - "Check weather. If raining, find
     * umbrella." → TRUE (conditional) - "Calculate 25 * 40" → FALSE (simple, no dependency) - "Get time in Bangalore and New York" → FALSE
     * (independent, use planning or tool calling)
     *
     * @param userRequest
     *         The user's message
     * @return true if agent loop is needed
     */
    public boolean needsAgentLoop(String userRequest) {
        String lower = userRequest.toLowerCase();

        // Conditional keywords
        boolean hasConditional =
                lower.contains("if ") || lower.contains(" if ") || lower.contains("based on ") || lower.contains("depending on ")
                        || lower.contains("according to ") || (lower.contains("check") && lower.contains("then"));

        if (hasConditional) {
            logger.info("Agent loop needed: detected conditional logic");
            return true;
        }

        // Future: Could use LLM to make this decision more intelligently
        logger.info("Agent loop not needed: no conditional logic detected");
        return false;
    }

    /**
     * Execute the agent runtime loop. This is the main method that runs the DECIDE → ACT → OBSERVE cycle.
     *
     * @param userRequest
     *         The original user request
     * @param context
     *         Conversation context (history + memories)
     * @param emitter
     *         SSE emitter for frontend notifications
     * @return AgentResult with final state and success status
     */
    public AgentResult executeAgentLoop(String userRequest, List<Message> context, SseEmitter emitter) {
        logger.info("==================== AGENT RUNTIME LOOP STARTED ====================");
        logger.info("User request: {}", userRequest);
        logger.info("Max iterations: {}", maxIterations);

        // Initialize agent state
        AgentState state = new AgentState(userRequest);

        try {
            // Notify frontend: agent loop started
            emitter.send(SseEmitter.event().name("agent_loop_start").data(Map.of("maxIterations", maxIterations)));

        } catch (IOException e) {
            logger.error("Failed to send agent_loop_start event: {}", e.getMessage());
        }

        // Main agent loop
        while (state.getIteration() < maxIterations) {
            state.incrementIteration();
            int currentIteration = state.getIteration();

            logger.info("---------- Agent Iteration {} ----------", currentIteration);

            try {
                // ===== DECIDE =====
                // Ask LLM: "What should I do next?"
                logger.info("DECIDE: Asking LLM for next decision...");

                AgentDecision decision = makeDecision(context, state);
                logger.info("DECIDE: LLM decided → {}", decision);

                // Notify frontend of decision
                try {
                    emitter.send(SseEmitter.event().name("agent_decision")
                            .data(Map.of("iteration", currentIteration, "decisionType", decision.getDecisionType().name(), "toolName",
                                    decision.getToolName() != null ? decision.getToolName() : "")));
                } catch (IOException e) {
                    logger.error("Failed to send agent_decision event: {}", e.getMessage());
                }

                // Check decision type
                if (decision.isFinalAnswer()) {
                    // Task complete!
                    logger.info("DECIDE: Agent chose FINAL_ANSWER. Task complete.");
                    state.setStatus(AgentState.ExecutionStatus.COMPLETED);

                    try {
                        emitter.send(SseEmitter.event().name("agent_loop_complete")
                                .data(Map.of("iterations", currentIteration, "status", "completed")));
                    } catch (IOException e) {
                        logger.error("Failed to send agent_loop_complete event: {}", e.getMessage());
                    }

                    logger.info("==================== AGENT RUNTIME LOOP COMPLETED ====================");
                    return AgentResult.success(state, currentIteration);
                }

                // ===== ACT =====
                // Execute the tool
                logger.info("ACT: Executing tool '{}' with arguments: {}", decision.getToolName(), decision.getArguments());

                String toolResult = toolExecutor.executeTool(decision.getToolName(), decision.getArguments());

                logger.info("ACT: Tool executed. Result: {}",
                        toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult);

                // Notify frontend of tool execution
                try {
                    emitter.send(SseEmitter.event().name("agent_tool_executed")
                            .data(Map.of("iteration", currentIteration, "tool", decision.getToolName(), "result",
                                    toolResult.length() > 100 ? toolResult.substring(0, 100) + "..." : toolResult)));
                } catch (IOException e) {
                    logger.error("Failed to send agent_tool_executed event: {}", e.getMessage());
                }

                // ===== OBSERVE =====
                // Add tool execution to state
                state.addToolExecution(decision.getToolName(), decision.getArguments(), toolResult);
                logger.info("OBSERVE: Added tool result to agent state. Total tool calls: {}", state.getToolExecutions().size());

                // Add to conversation context for next iteration
                // The LLM needs to see what happened
                addToolCallToContext(context, decision, toolResult);

            } catch (Exception e) {
                logger.error("Error during agent iteration {}: {}", currentIteration, e.getMessage(), e);

                // Decide whether to continue or fail
                // For now, we fail on any error during iteration
                state.setStatus(AgentState.ExecutionStatus.FAILED);

                try {
                    emitter.send(SseEmitter.event().name("agent_loop_error")
                            .data(Map.of("iteration", currentIteration, "error", e.getMessage())));
                } catch (IOException ioException) {
                    logger.error("Failed to send agent_loop_error event: {}", ioException.getMessage());
                }

                logger.info("==================== AGENT RUNTIME LOOP FAILED ====================");
                return AgentResult.failure(state, currentIteration, e.getMessage());
            }
        }

        // Max iterations reached without FINAL_ANSWER
        logger.warn("Max iterations ({}) reached without FINAL_ANSWER", maxIterations);
        state.setStatus(AgentState.ExecutionStatus.MAX_ITERATIONS_REACHED);

        try {
            emitter.send(SseEmitter.event().name("agent_loop_max_iterations")
                    .data(Map.of("iterations", maxIterations, "message", "Agent could not complete task in allowed iterations")));
        } catch (IOException e) {
            logger.error("Failed to send agent_loop_max_iterations event: {}", e.getMessage());
        }

        logger.info("==================== AGENT RUNTIME LOOP MAX ITERATIONS ====================");
        return AgentResult.failure(state, maxIterations, "Agent could not complete the task within " + maxIterations + " iterations");
    }

    /**
     * Ask LLM to make a decision: CALL_TOOL or FINAL_ANSWER? The LLM receives: - Current conversation context - Tool execution history from
     * this agent loop - Available tools - Instructions on how to decide Returns structured decision.
     */
    private AgentDecision makeDecision(List<Message> context, AgentState state) {
        // Build decision prompt
        List<Message> decisionMessages = new ArrayList<>(context);

        // Add agent-specific instructions
        String decisionPrompt = buildDecisionPrompt(state);
        decisionMessages.add(new Message("system", decisionPrompt));

        // Call LLM
        String llmResponse = openAIClient.sendMessage(decisionMessages);

        logger.debug("LLM decision response: {}", llmResponse);

        // Parse decision from LLM response
        return parseDecision(llmResponse);
    }

    /**
     * Build the prompt that instructs the LLM how to make decisions.
     */
    private String buildDecisionPrompt(AgentState state) {
        List<ToolDefinition> tools = toolRegistry.getAllDefinitions();

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI agent making decisions to complete a task.\n\n");
        prompt.append("USER'S ORIGINAL REQUEST:\n");
        prompt.append(state.getUserRequest()).append("\n\n");

        // Show tool execution history
        if (!state.getToolExecutions().isEmpty()) {
            prompt.append("TOOLS YOU'VE ALREADY EXECUTED:\n");
            for (AgentState.ToolExecution exec : state.getToolExecutions()) {
                prompt.append(String.format("- %s: %s\n", exec.getToolName(), exec.getResult()));
            }
            prompt.append("\n");
        }

        prompt.append("AVAILABLE TOOLS:\n");
        for (ToolDefinition tool : tools) {
            prompt.append(String.format("- %s: %s\n", tool.getFunction().getName(), tool.getFunction().getDescription()));
        }
        prompt.append("\n");

        prompt.append("YOUR TASK:\n");
        prompt.append("Decide what to do next. You have TWO options:\n\n");
        prompt.append("1. CALL_TOOL - If you need more information or need to perform an action\n");
        prompt.append("2. FINAL_ANSWER - If you have enough information to answer the user\n\n");

        prompt.append("CRITICAL RULES:\n");
        prompt.append("- For ANY calculation (even simple math), you MUST use the calculator tool\n");
        prompt.append("- For time/date queries, you MUST use the get_current_time tool\n");
        prompt.append("- For conversation stats, you MUST use the get_conversation_stats tool\n");
        prompt.append("- Do NOT do calculations in your head - ALWAYS call the calculator tool\n");
        prompt.append("- Only choose FINAL_ANSWER when you have called ALL necessary tools\n\n");

        prompt.append("Return ONLY a JSON object in this EXACT format:\n\n");
        prompt.append("Option 1 - Call a tool:\n");
        prompt.append("{\n");
        prompt.append("  \"decision_type\": \"CALL_TOOL\",\n");
        prompt.append("  \"tool_name\": \"weather\",\n");
        prompt.append("  \"arguments\": \"{\\\"city\\\": \\\"Bangalore\\\"}\",\n");
        prompt.append("  \"reasoning\": \"I need to check the weather first\"\n");
        prompt.append("}\n\n");

        prompt.append("Option 2 - Provide final answer:\n");
        prompt.append("{\n");
        prompt.append("  \"decision_type\": \"FINAL_ANSWER\",\n");
        prompt.append("  \"reasoning\": \"I have all the information needed to respond\"\n");
        prompt.append("}\n\n");

        prompt.append("JSON FORMAT RULES:\n");
        prompt.append("- Return ONLY the JSON, no explanation before or after\n");
        prompt.append("- Use double quotes for JSON strings\n");
        prompt.append("- Escape quotes in arguments field (\\\")\n");
        prompt.append("- decision_type must be exactly \"CALL_TOOL\" or \"FINAL_ANSWER\"\n");
        prompt.append("- If CALL_TOOL, tool_name and arguments are required\n");
        prompt.append("- Use only tools from the available tools list above\n\n");

        prompt.append("EXAMPLE DECISION MAKING:\n");
        prompt.append("User: \"Calculate 10 + 5\"\n");
        prompt.append("Correct: {\"decision_type\": \"CALL_TOOL\", \"tool_name\": \"calculator\", \"arguments\": \"{\\\"expression\\\": \\\"10 + 5\\\"}\"}\n");
        prompt.append("Wrong: {\"decision_type\": \"FINAL_ANSWER\"} (you must call calculator first!)\n\n");

        prompt.append("User: \"Get time. If after 6PM, calculate 24-18\"\n");
        prompt.append("Step 1: {\"decision_type\": \"CALL_TOOL\", \"tool_name\": \"get_current_time\", ...}\n");
        prompt.append("Step 2: {\"decision_type\": \"CALL_TOOL\", \"tool_name\": \"calculator\", ...} (based on result)\n");
        prompt.append("Step 3: {\"decision_type\": \"FINAL_ANSWER\"}\n");

        return prompt.toString();
    }

    /**
     * Parse the LLM's response into an AgentDecision.
     */
    private AgentDecision parseDecision(String llmResponse) {
        try {
            // Clean up response (remove markdown wrappers if present)
            String jsonStr = llmResponse.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            // Parse JSON
            JsonNode root = objectMapper.readTree(jsonStr);

            // Extract decision_type
            if (!root.has("decision_type")) {
                throw new IllegalArgumentException("LLM response missing 'decision_type' field");
            }

            String decisionTypeStr = root.get("decision_type").asText();
            AgentDecisionType decisionType;

            if ("CALL_TOOL".equals(decisionTypeStr)) {
                decisionType = AgentDecisionType.CALL_TOOL;
            } else if ("FINAL_ANSWER".equals(decisionTypeStr)) {
                decisionType = AgentDecisionType.FINAL_ANSWER;
            } else {
                throw new IllegalArgumentException("Invalid decision_type: " + decisionTypeStr);
            }

            AgentDecision decision = new AgentDecision(decisionType);

            // Extract reasoning if present
            if (root.has("reasoning")) {
                decision.setReasoning(root.get("reasoning").asText());
            }

            // If CALL_TOOL, extract tool_name and arguments
            if (decisionType == AgentDecisionType.CALL_TOOL) {
                if (!root.has("tool_name") || !root.has("arguments")) {
                    throw new IllegalArgumentException("CALL_TOOL decision missing tool_name or arguments");
                }

                decision.setToolName(root.get("tool_name").asText());
                decision.setArguments(root.get("arguments").asText());
            }

            return decision;

        } catch (Exception e) {
            logger.error("Failed to parse LLM decision: {}", e.getMessage(), e);
            logger.error("LLM response was: {}", llmResponse);
            throw new AgentExecutionException("Failed to parse LLM decision: " + e.getMessage(), e);
        }
    }

    /**
     * Add tool call and result to conversation context. The LLM needs to see: 1. That it called a tool (assistant message with tool call
     * info) 2. What the tool returned (tool result message) This follows the same pattern as the existing tool calling flow.
     */
    private void addToolCallToContext(List<Message> context, AgentDecision decision, String toolResult) {
        // Add assistant message (tool call)
        Message assistantMsg = new Message("assistant",
                String.format("I'm calling the %s tool with arguments: %s", decision.getToolName(), decision.getArguments()));
        context.add(assistantMsg);

        // Add tool result message
        Message toolMsg = new Message("user", String.format("Tool '%s' returned: %s", decision.getToolName(), toolResult));
        context.add(toolMsg);
    }

    /**
     * Get the configured maximum iterations.
     */
    public int getMaxIterations() {
        return maxIterations;
    }
}
