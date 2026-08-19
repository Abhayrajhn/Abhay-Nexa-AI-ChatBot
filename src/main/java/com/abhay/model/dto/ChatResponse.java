package com.abhay.model.dto;

import com.abhay.model.llm.Message;

import java.util.List;

/**
 * Response DTO for the chat endpoint.
 *
 * The backend returns:
 * - message: The AI's response text
 * - conversationHistory: Complete conversation including the new exchange
 *
 * The frontend can store this conversationHistory and send it back
 * in the next request to maintain context.
 *
 * Example:
 * {
 *   "message": "The capital of France is Paris.",
 *   "conversationHistory": [
 *     {"role": "user", "content": "Hello"},
 *     {"role": "assistant", "content": "Hi! How can I help?"},
 *     {"role": "user", "content": "What is the capital of France?"},
 *     {"role": "assistant", "content": "The capital of France is Paris."}
 *   ]
 * }
 */
public class ChatResponse {
    private String message;
    private List<Message> conversationHistory;

    // Constructors
    public ChatResponse() {
    }

    public ChatResponse(String message, List<Message> conversationHistory) {
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
