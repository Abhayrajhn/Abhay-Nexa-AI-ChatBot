package com.abhay.model.dto;

/**
 * Request DTO for creating a new conversation.
 * Example JSON: { "title": "My New Chat" }
 */
public class CreateConversationRequest {

    private String title;
    private Long userId;  // REQUIRED for memory system - identifies the user

    // Constructors
    public CreateConversationRequest() {
    }

    public CreateConversationRequest(String title) {
        this.title = title;
    }

    public CreateConversationRequest(String title, Long userId) {
        this.title = title;
        this.userId = userId;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
