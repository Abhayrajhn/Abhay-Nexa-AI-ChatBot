package com.abhay.model.dto;

/**
 * Request DTO for sending a message to a conversation.
 * Example JSON: { "content": "What is the capital of France?" }
 */
public class SendMessageRequest {

    private String content;
    private Long userId;  // REQUIRED for memory system - identifies the user

    // Constructors
    public SendMessageRequest() {
    }

    public SendMessageRequest(String content) {
        this.content = content;
    }

    public SendMessageRequest(String content, Long userId) {
        this.content = content;
        this.userId = userId;
    }

    // Getters and Setters
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
