package com.abhay.tool;

import com.abhay.model.llm.ToolDefinition;

/**
 * Core interface for all tools that can be called by the LLM.
 *
 * A "tool" is an external capability that the LLM can request when it needs to:
 * - Perform calculations (Calculator)
 * - Access real-time information (Current Time)
 * - Query databases (Conversation Stats)
 * - Future: Search the web, run code, interact with APIs
 *
 * KEY CONCEPT: Tool Calling vs Agent
 *
 * Tool Calling (what we're implementing here):
 * - LLM decides it needs a tool
 * - Requests the tool with arguments
 * - We execute the tool
 * - Send result back to LLM
 * - LLM generates final response
 * - Single-turn interaction
 *
 * Agent (future implementation):
 * - Multi-turn loop: Decide → Act → Observe → Repeat
 * - Memory across interactions
 * - Planning and reasoning
 * - Autonomous behavior
 *
 * Tool calling is the FOUNDATION for agents. You can't build an agent
 * without first implementing tool calling.
 *
 * How Tool Calling Works:
 *
 * User: "What is 25 * 40?"
 *   ↓
 * LLM: "I need the calculator tool"
 *   ↓
 * System: Executes calculator.execute("{\"expression\": \"25 * 40\"}")
 *   ↓
 * Calculator: Returns "{\"result\": 1000}"
 *   ↓
 * LLM: "The result is 1000"
 *   ↓
 * User sees: "25 * 40 equals 1000"
 *
 * Security:
 * - Only registered tools can be called
 * - Arguments are validated
 * - No arbitrary code execution
 * - Each tool controls what it can do
 */
public interface Tool {

    /**
     * Returns the unique name of this tool.
     * This name is used by the LLM to request the tool.
     *
     * Examples: "calculator", "get_current_time", "get_conversation_stats"
     *
     * Naming conventions:
     * - lowercase
     * - underscores for spaces
     * - descriptive but concise
     *
     * @return The tool name
     */
    String getName();

    /**
     * Returns a human-readable description of what this tool does.
     *
     * This description is sent to the LLM so it can decide when to use the tool.
     * Be specific and clear - the quality of this description directly affects
     * how well the LLM uses the tool.
     *
     * Good: "Performs mathematical calculations including +, -, *, /, %, parentheses"
     * Bad: "Does math"
     *
     * @return The tool description
     */
    String getDescription();

    /**
     * Returns the complete tool definition including parameter schema.
     *
     * This is sent to OpenAI's API as part of the request. It includes:
     * - Tool name
     * - Description
     * - Parameters (JSON Schema defining what arguments this tool accepts)
     *
     * The LLM uses this schema to generate valid arguments.
     *
     * @return The tool definition
     */
    ToolDefinition getDefinition();

    /**
     * Executes this tool with the given arguments.
     *
     * The arguments come from the LLM as a JSON string.
     * For example: "{\"expression\": \"25 * 40\"}"
     *
     * This method should:
     * 1. Parse the JSON arguments
     * 2. Validate the arguments
     * 3. Perform the tool's operation
     * 4. Return the result as a JSON string
     *
     * Example:
     * Input:  "{\"expression\": \"25 * 40\"}"
     * Output: "{\"result\": 1000}"
     *
     * Error Handling:
     * - Throw ToolExecutionException for errors
     * - Include helpful error messages
     * - The LLM will see the error and can explain it to the user
     *
     * @param arguments JSON string containing the tool arguments
     * @return JSON string containing the tool result
     * @throws ToolExecutionException if the tool execution fails
     */
    String execute(String arguments) throws ToolExecutionException;
}
