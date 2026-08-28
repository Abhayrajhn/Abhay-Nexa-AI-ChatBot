package com.abhay.model.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a tool definition sent to OpenAI's API. When we make a request to OpenAI, we include a "tools" array that describes what tools
 * are available. This helps the LLM understand: - What tools exist - What each tool does - What parameters each tool accepts OpenAI uses
 * this information to decide when and how to call tools. Example JSON sent to OpenAI: { "type": "function", "function": { "name":
 * "calculator", "description": "Performs mathematical calculations", "parameters": { "type": "object", "properties": { "expression": {
 * "type": "string", "description": "The mathematical expression to evaluate" } }, "required": ["expression"] } } } The LLM then generates
 * arguments that match this schema when it wants to call the tool.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolDefinition {

    // Always "function" for OpenAI's current API
    private String type = "function";

    // The function definition (name, description, parameters)
    private FunctionDefinition function;

    // Constructors
    public ToolDefinition() {
    }

    public ToolDefinition(FunctionDefinition function) {
        this.function = function;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public void setFunction(FunctionDefinition function) {
        this.function = function;
    }

    /**
     * Inner class: FunctionDefinition Contains the actual tool specification: - name: Unique identifier for the tool - description: Helps
     * the LLM decide when to use it - parameters: JSON Schema defining what arguments are valid - strict: Whether to enforce strict schema
     * validation (optional)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionDefinition {

        private String name;
        private String description;

        // JSON Schema as a Map (allows flexible schema definitions)
        private Map<String, Object> parameters;

        // Optional: strict mode for parameter validation
        private Boolean strict;

        // Constructors
        public FunctionDefinition() {
        }

        public FunctionDefinition(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }

        public Boolean getStrict() {
            return strict;
        }

        public void setStrict(Boolean strict) {
            this.strict = strict;
        }
    }

    /**
     * Helper method to create a tool definition with common defaults.
     *
     * @param name
     *         Tool name
     * @param description
     *         Tool description
     * @param parameters
     *         JSON Schema for parameters
     * @return A new ToolDefinition
     */
    public static ToolDefinition create(String name, String description, Map<String, Object> parameters) {
        FunctionDefinition function = new FunctionDefinition(name, description, parameters);
        return new ToolDefinition(function);
    }
}
