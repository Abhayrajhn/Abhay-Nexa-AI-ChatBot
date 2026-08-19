package com.abhay.model.llm;

/**
 * Represents a single message in the conversation.
 * OpenAI expects messages with "role" and "content" fields.
 *
 * Roles:
 * - "system": Instructions for the AI's behavior
 * - "user": Messages from the human user
 * - "assistant": Messages from the AI
 */
public class Message {
    private String role;
    private String content;

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
}
