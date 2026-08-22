package com.abhay.api;

import com.abhay.model.dto.ConversationResponse;
import com.abhay.model.dto.CreateConversationRequest;
import com.abhay.model.dto.UpdateConversationRequest;
import com.abhay.model.dto.MessageResponse;
import com.abhay.model.dto.SendMessageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Conversation API Interface - Defines the contract for conversation management endpoints.
 * This interface separates the API contract from the implementation, making it easier to maintain, test, and potentially create alternative
 * implementations.
 */
@RequestMapping("/api/conversations")
public interface IConversationAPI {

    /**
     * Create a new conversation.
     *
     * @param request
     *         Contains conversation title
     * @return ResponseEntity with ConversationResponse (201 Created)
     */
    @PostMapping
    ResponseEntity<ConversationResponse> createConversation(@RequestBody CreateConversationRequest request);

    /**
     * Get all conversations (without messages for performance).
     *
     * @return ResponseEntity with list of ConversationResponse (200 OK)
     */
    @GetMapping
    ResponseEntity<List<ConversationResponse>> getAllConversations();

    /**
     * Get a single conversation with all its messages.
     *
     * @param id
     *         Conversation ID
     * @return ResponseEntity with ConversationResponse including messages (200 OK)
     */
    @GetMapping("/{id}")
    ResponseEntity<ConversationResponse> getConversation(@PathVariable Long id);

    /**
     * Delete a conversation (cascade deletes all messages).
     *
     * @param id
     *         Conversation ID
     * @return ResponseEntity with no content (204 No Content)
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteConversation(@PathVariable Long id);

    /**
     * Update a conversation's title.
     *
     * @param id
     *         Conversation ID
     * @param request
     *         Contains new title
     * @return ResponseEntity with updated ConversationResponse (200 OK)
     */
    @PutMapping("/{id}")
    ResponseEntity<ConversationResponse> updateConversation(@PathVariable Long id, @RequestBody UpdateConversationRequest request);

    /**
     * Send a message to a conversation and get AI response.
     *
     * @param id
     *         Conversation ID
     * @param request
     *         Contains message content
     * @return ResponseEntity with MessageResponse (assistant's response) (200 OK)
     */
    @PostMapping("/{id}/messages")
    ResponseEntity<MessageResponse> sendMessage(@PathVariable Long id, @RequestBody SendMessageRequest request);

    /**
     * Get all messages for a conversation.
     *
     * @param id
     *         Conversation ID
     * @return ResponseEntity with list of MessageResponse (200 OK)
     */
    @GetMapping("/{id}/messages")
    ResponseEntity<List<MessageResponse>> getMessages(@PathVariable Long id);
}
