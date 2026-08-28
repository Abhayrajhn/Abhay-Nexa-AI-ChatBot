package com.abhay.service;

import com.abhay.model.dto.ConversationResponse;
import com.abhay.model.dto.CreateConversationRequest;
import com.abhay.model.dto.MessageResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Conversation Service Interface. Defines the contract for conversation management operations.
 */
public interface IConversationService {

    /**
     * Create a new conversation.
     */
    ConversationResponse createConversation(CreateConversationRequest request);

    /**
     * Get all conversations (without messages).
     */
    List<ConversationResponse> getAllConversations();

    /**
     * Get a single conversation with all its messages.
     */
    ConversationResponse getConversationById(Long id);

    /**
     * Delete a conversation.
     */
    void deleteConversation(Long id);

    /**
     * Update a conversation's title.
     */
    ConversationResponse updateConversationTitle(Long id, String title);

    /**
     * Send a message to a conversation and get AI response. NON-STREAMING VERSION
     */
    MessageResponse sendMessage(Long conversationId, String content);

    /**
     * Send a message to a conversation and stream the AI response. STREAMING VERSION
     */
    void sendMessageStream(Long conversationId, String content, SseEmitter emitter);

    /**
     * Get all messages for a conversation.
     */
    List<MessageResponse> getMessages(Long conversationId);
}
