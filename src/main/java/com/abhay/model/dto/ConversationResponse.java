package com.abhay.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a conversation.
 *
 * Returns conversation details with or without messages.
 *
 * Example JSON:
 * {
 *   "id": 1,
 *   "title": "My Chat",
 *   "createdAt": "2026-08-19T12:00:00",
 *   "updatedAt": "2026-08-19T14:30:00",
 *   "messages": [...]
 * }
 */
public class ConversationResponse {
    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MessageResponse> messages;

    // Constructors
    public ConversationResponse() {
    }

    public ConversationResponse(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ConversationResponse(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt, List<MessageResponse> messages) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messages = messages;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<MessageResponse> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageResponse> messages) {
        this.messages = messages;
    }
}
