package com.abhay.api.impl;

import com.abhay.api.IConversationAPI;
import com.abhay.exception.ResourceNotFoundException;
import com.abhay.model.dto.ConversationResponse;
import com.abhay.model.dto.CreateConversationRequest;
import com.abhay.model.dto.UpdateConversationRequest;
import com.abhay.model.dto.MessageResponse;
import com.abhay.model.dto.SendMessageRequest;
import com.abhay.service.IConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation Controller Implementation.
 *
 * Implements the IConversationAPI interface and handles HTTP requests.
 * Delegates business logic to the service layer.
 */
@RestController
@CrossOrigin(origins = "*")  // Allow requests from any origin (for development)
public class ConversationController implements IConversationAPI {

    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);

    @Autowired
    private IConversationService conversationService;

    @Override
    public ResponseEntity<ConversationResponse> createConversation(CreateConversationRequest request) {
        logger.info("POST /api/conversations - Creating conversation with title: {}", request.getTitle());

        ConversationResponse response = conversationService.createConversation(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<ConversationResponse>> getAllConversations() {
        logger.info("GET /api/conversations - Fetching all conversations");

        List<ConversationResponse> conversations = conversationService.getAllConversations();

        return ResponseEntity.ok(conversations);
    }

    @Override
    public ResponseEntity<ConversationResponse> getConversation(Long id) {
        logger.info("GET /api/conversations/{} - Fetching conversation", id);

        ConversationResponse response = conversationService.getConversationById(id);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteConversation(Long id) {
        logger.info("DELETE /api/conversations/{} - Deleting conversation", id);

        conversationService.deleteConversation(id);

        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MessageResponse> sendMessage(Long id, SendMessageRequest request) {
        logger.info("POST /api/conversations/{}/messages - Sending message", id);

        MessageResponse response = conversationService.sendMessage(id, request.getContent());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<MessageResponse>> getMessages(Long id) {
        logger.info("GET /api/conversations/{}/messages - Fetching messages", id);

        List<MessageResponse> messages = conversationService.getMessages(id);

        return ResponseEntity.ok(messages);
    }

    @Override
    public ResponseEntity<ConversationResponse> updateConversation(Long id, UpdateConversationRequest request) {
        logger.info("PUT /api/conversations/{} - Updating conversation title to: {}", id, request.getTitle());

        ConversationResponse response = conversationService.updateConversationTitle(id, request.getTitle());

        return ResponseEntity.ok(response);
    }

    /**
     * Exception handler for ResourceNotFoundException.
     *
     * Converts ResourceNotFoundException to HTTP 404 with error message.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());

        Map<String, String> error = new HashMap<>();
        error.put("error", "Not Found");
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Exception handler for generic exceptions.
     *
     * Converts any unhandled exception to HTTP 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: ", ex);

        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
