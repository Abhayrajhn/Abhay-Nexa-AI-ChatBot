package com.abhay.model.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a single message in the conversation.
 * OpenAI expects messages with "role" and "content" fields.
 *
 * Roles:
 * - "system": Instructions for the AI's behavior
 * - "user": Messages from the human user
 * - "assistant": Messages from the AI
 * - "tool": Results from tool executions (NEW for tool calling)
 *
 * Tool Calling Support:
 * - toolCalls: Present when role="assistant" and LLM wants to call tools
 * - toolCallId: Present when role="tool" to link result back to the request
 */
public class Message {
    private String role;
    private String content;

    // Tool calling fields (optional)
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    // Constructors
    public Message() {
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // Getters and Setters
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
