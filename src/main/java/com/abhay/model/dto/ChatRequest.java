package com.abhay.model.dto;

import com.abhay.model.llm.Message;

import java.util.List;

/**
 * Request DTO for the chat endpoint.
 *
 * The frontend sends:
 * - message: The new user message
 * - conversationHistory: Previous messages (optional, can be null/empty for first message)
 *
 * Example:
 * {
 *   "message": "What is the capital of France?",
 *   "conversationHistory": [
 *     {"role": "user", "content": "Hello"},
 *     {"role": "assistant", "content": "Hi! How can I help?"}
 *   ]
 * }
 */
public class ChatRequest {
    private String message;
    private List<Message> conversationHistory;

    // Constructors
    public ChatRequest() {
    }

    public ChatRequest(String message, List<Message> conversationHistory) {
        this.message = message;
        this.conversationHistory = conversationHistory;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Message> getConversationHistory() {
        return conversationHistory;
    }

    public void setConversationHistory(List<Message> conversationHistory) {
        this.conversationHistory = conversationHistory;
    }
}
