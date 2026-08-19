package com.abhay.model.dto;

/**
 * Request DTO for sending a message to a conversation.
 *
 * Example JSON:
 * {
 *   "content": "What is the capital of France?"
 * }
 */
public class SendMessageRequest {
    private String content;

    // Constructors
    public SendMessageRequest() {
    }

    public SendMessageRequest(String content) {
        this.content = content;
    }

    // Getters and Setters
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
