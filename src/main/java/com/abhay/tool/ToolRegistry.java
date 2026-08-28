package com.abhay.tool;

import com.abhay.model.llm.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry that manages all available tools in the application. This is a Spring-managed component that automatically discovers all Tool
 * beans at startup and provides methods to access them. How Auto-Discovery Works: 1. Spring finds all beans that implement the Tool
 * interface 2. Constructor injection provides them as a List<Tool> 3. We register each tool by its name in a Map 4. Tools are now available
 * for the LLM to call Example: - CalculatorTool is annotated with @Component - Spring creates an instance and adds it to the List<Tool> -
 * ToolRegistry registers it: tools.put("calculator", calculatorTool) - LLM can now request "calculator" tool Security Benefits: - Whitelist
 * approach: only registered tools can be called - No dynamic loading of arbitrary classes - Each tool is explicitly defined and approved
 * Usage: - ToolExecutor uses this to find and execute tools - ConversationService uses this to get tool definitions for OpenAI
 */
@Component
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    // Map of tool name -> tool instance
    private final Map<String, Tool> tools = new HashMap<>();

    /**
     * Constructor with auto-discovery via Spring dependency injection. Spring automatically provides all beans that implement Tool
     * interface. We register each one by its name.
     *
     * @param toolList
     *         All Tool beans found by Spring
     */
    @Autowired
    public ToolRegistry(List<Tool> toolList) {
        logger.info("Initializing ToolRegistry with {} tools", toolList.size());

        for (Tool tool : toolList) {
            String name = tool.getName();

            if (tools.containsKey(name)) {
                logger.error("Duplicate tool name detected: {}. Each tool must have a unique name.", name);
                throw new IllegalStateException("Duplicate tool name: " + name);
            }

            tools.put(name, tool);
            logger.info("Registered tool: {} - {}", name, tool.getDescription());
        }

        logger.info("ToolRegistry initialized successfully with tools: {}", tools.keySet());
    }

    /**
     * Returns all tool definitions for sending to OpenAI. The LLM needs to know what tools are available before it can request them. This
     * method collects all tool definitions and returns them as a list. These definitions are included in every OpenAI API request.
     *
     * @return List of all tool definitions
     */
    public List<ToolDefinition> getAllDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();

        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }

        logger.debug("Returning {} tool definitions", definitions.size());
        return definitions;
    }

    /**
     * Gets a specific tool by name. Used by ToolExecutor when the LLM requests a tool.
     *
     * @param name
     *         The tool name (e.g., "calculator")
     * @return The tool instance
     * @throws IllegalArgumentException
     *         if the tool doesn't exist
     */
    public Tool getTool(String name) {
        Tool tool = tools.get(name);

        if (tool == null) {
            logger.error("Tool not found: {}. Available tools: {}", name, tools.keySet());
            throw new IllegalArgumentException("Unknown tool: " + name);
        }

        return tool;
    }

    /**
     * Checks if a tool with the given name exists.
     *
     * @param name
     *         The tool name to check
     * @return true if the tool exists, false otherwise
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Returns the names of all registered tools. Useful for debugging and logging.
     *
     * @return Set of all tool names
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * Returns the total number of registered tools.
     *
     * @return The tool count
     */
    public int getToolCount() {
        return tools.size();
    }
}
