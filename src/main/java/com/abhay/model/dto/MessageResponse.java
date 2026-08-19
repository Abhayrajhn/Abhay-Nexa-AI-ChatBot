package com.abhay.model.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for a message.
 *
 * Example JSON:
 * {
 *   "id": 1,
 *   "role": "USER",
 *   "content": "What is the capital of France?",
 *   "createdAt": "2026-08-19T12:00:00"
 * }
 */
public class MessageResponse {
    private Long id;
    private String role;
    private String content;
    private LocalDateTime createdAt;

    // Constructors
    public MessageResponse() {
    }

    public MessageResponse(Long id, String role, String content, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
