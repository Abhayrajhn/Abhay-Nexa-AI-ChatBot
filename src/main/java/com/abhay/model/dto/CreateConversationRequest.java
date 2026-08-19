package com.abhay.model.dto;

/**
 * Request DTO for creating a new conversation.
 *
 * Example JSON:
 * {
 *   "title": "My New Chat"
 * }
 */
public class CreateConversationRequest {
    private String title;

    // Constructors
    public CreateConversationRequest() {
    }

    public CreateConversationRequest(String title) {
        this.title = title;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
