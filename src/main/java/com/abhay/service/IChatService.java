package com.abhay.service;

import com.abhay.model.dto.ChatRequest;
import com.abhay.model.dto.ChatResponse;

/**
 * Chat Service Interface.
 *
 * Defines the contract for chat business logic operations.
 */
public interface IChatService {

    /**
     * Processes a chat request and returns the AI's response.
     *
     * @param request Contains user message and conversation history
     * @return ChatResponse with AI message and updated conversation history
     */
    ChatResponse chat(ChatRequest request);
}
