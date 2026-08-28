package com.abhay.model.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a tool call request from OpenAI.
 * When the LLM decides it needs to use a tool, it returns a response with tool_calls instead of regular content. Each tool call contains: -
 * An ID (to track this specific call) - The function name and arguments
 * Example JSON from OpenAI: { "id": "call_abc123", "type": "function", "function": { "name": "calculator", "arguments": "{\"expression\":
 * \"25 * 40\"}" } }
 * The LLM can request multiple tools in one response. Each gets a unique ID so we can send results back correctly.
 * Flow: 1. LLM returns tool_calls with IDs and arguments 2. We execute each tool 3. We send results back with matching IDs 4. LLM generates
 * final response using the results
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    // Unique identifier for this tool call (e.g., "call_abc123")
    private String id;

    // Always "function" for OpenAI's current API
    private String type;

    // The function to call (name and arguments)
    private FunctionCall function;

    // Constructors
    public ToolCall() {
    }

    public ToolCall(String id, String type, FunctionCall function) {
        this.id = id;
        this.type = type;
        this.function = function;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionCall getFunction() {
        return function;
    }

    public void setFunction(FunctionCall function) {
        this.function = function;
    }

    /**
     * Inner class: FunctionCall
     * Contains the actual function request: - name: Which tool to call - arguments: JSON string with the parameters
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {

        private String name;

        // Arguments as a JSON string (not parsed yet)
        // Example: "{\"expression\": \"25 * 40\"}"
        private String arguments;

        // Constructors
        public FunctionCall() {
        }

        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }

    @Override
    public String toString() {
        return "ToolCall{" + "id='" + id + '\'' + ", type='" + type + '\'' + ", function=" + (function != null
                ? function.getName()
                : "null") + '}';
    }
}
