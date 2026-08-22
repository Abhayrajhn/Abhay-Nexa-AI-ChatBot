package com.abhay.model.dto;

/**
 * Request DTO for updating a conversation.
 *
 * Used when the frontend wants to update the conversation title.
 */
public class UpdateConversationRequest {
    private String title;

    public UpdateConversationRequest() {
    }

    public UpdateConversationRequest(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
