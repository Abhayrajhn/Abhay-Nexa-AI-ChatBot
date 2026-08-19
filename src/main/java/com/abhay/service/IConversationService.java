package com.abhay.service;

import com.abhay.model.dto.ConversationResponse;
import com.abhay.model.dto.CreateConversationRequest;
import com.abhay.model.dto.MessageResponse;

import java.util.List;

/**
 * Conversation Service Interface.
 *
 * Defines the contract for conversation management business logic.
 * This allows for easy testing with mocks and potential alternative implementations.
 */
public interface IConversationService {

    /**
     * Create a new conversation.
     *
     * @param request Contains conversation title
     * @return ConversationResponse with created conversation details
     */
    ConversationResponse createConversation(CreateConversationRequest request);

    /**
     * Get all conversations (without messages for performance).
     *
     * @return List of ConversationResponse
     */
    List<ConversationResponse> getAllConversations();

    /**
     * Get a single conversation with all its messages.
     *
     * @param id Conversation ID
     * @return ConversationResponse with messages
     * @throws com.abhay.exception.ResourceNotFoundException if conversation not found
     */
    ConversationResponse getConversationById(Long id);

    /**
     * Delete a conversation (cascade deletes all messages).
     *
     * @param id Conversation ID
     * @throws com.abhay.exception.ResourceNotFoundException if conversation not found
     */
    void deleteConversation(Long id);

    /**
     * Send a message to a conversation and get AI response.
     *
     * This method:
     * 1. Retrieves conversation history from database
     * 2. Saves the user's message
     * 3. Builds context for the LLM
     * 4. Calls OpenAI API
     * 5. Saves the assistant's response
     * 6. Returns the assistant message
     *
     * @param conversationId Conversation ID
     * @param content User message content
     * @return MessageResponse with assistant's response
     * @throws com.abhay.exception.ResourceNotFoundException if conversation not found
     */
    MessageResponse sendMessage(Long conversationId, String content);

    /**
     * Get all messages for a conversation.
     *
     * @param conversationId Conversation ID
     * @return List of MessageResponse in chronological order
     * @throws com.abhay.exception.ResourceNotFoundException if conversation not found
     */
    List<MessageResponse> getMessages(Long conversationId);
}
