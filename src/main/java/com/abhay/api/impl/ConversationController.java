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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation Controller Implementation. Implements the IConversationAPI interface and handles HTTP requests. Delegates business logic to
 * the service layer.
 */
@RestController
@CrossOrigin(origins = "*")  // Allow requests from any origin (for development)
public class ConversationController implements IConversationAPI {

    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);

    @Autowired
    private IConversationService conversationService;

    @Override
    public ResponseEntity<ConversationResponse> createConversation(CreateConversationRequest request) {
        logger.info("POST /api/conversations - Creating conversation with title: {} for userId: {}", request.getTitle(),
                request.getUserId());

        // Validate userId is provided
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId is required to create a conversation");
        }

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
        logger.info("POST /api/conversations/{}/messages - Sending message (non-streaming)", id);

        MessageResponse response = conversationService.sendMessage(id, request.getContent());

        return ResponseEntity.ok(response);
    }

    /**
     * Send a message and stream the AI response using Server-Sent Events. NEW STREAMING ENDPOINT How Server-Sent Events (SSE) work: 1.
     * Client makes a POST request to this endpoint 2. We create an SseEmitter and return it immediately 3. Spring keeps the HTTP connection
     * open 4. We send data through the emitter as it arrives from OpenAI 5. Client receives each chunk as a separate event 6. When done, we
     * close the connection SSE format: event: chunk data: Hello event: chunk data:  world event: done data: {"id": "123", ...}
     *
     * @param id
     *         Conversation ID
     * @param request
     *         Message content
     * @return SseEmitter for streaming response
     */
    @Override
    public SseEmitter sendMessageStream(Long id, SendMessageRequest request) {
        logger.info("POST /api/conversations/{}/messages/stream - Sending message (streaming) for userId: {}", id, request.getUserId());

        // Validate userId is provided
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId is required to send a message");
        }

        // Create SSE emitter with 5-minute timeout
        // This timeout is how long the connection can stay open
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);  // 5 minutes

        // Set up timeout handler
        emitter.onTimeout(() -> {
            logger.warn("SSE connection timed out for conversation {}", id);
            emitter.complete();
        });

        // Set up completion handler
        emitter.onCompletion(() -> {
            logger.info("SSE connection completed for conversation {}", id);
        });

        // Set up error handler
        emitter.onError((error) -> {
            logger.error("SSE connection error for conversation {}: {}", id, error.getMessage());
        });

        // Start streaming in a separate thread to avoid blocking the request
        // The service will handle sending chunks through the emitter
        new Thread(() -> {
            try {
                conversationService.sendMessageStream(id, request.getUserId(), request.getContent(), emitter);
            } catch (Exception e) {
                logger.error("Error in streaming thread: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        }).start();

        // Return the emitter immediately (connection stays open)
        return emitter;
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
     * Exception handler for ResourceNotFoundException. Converts ResourceNotFoundException to HTTP 404 with error message.
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
     * Exception handler for IllegalArgumentException (e.g., missing userId). Converts IllegalArgumentException to HTTP 400 with error
     * message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Bad request: {}", ex.getMessage());

        Map<String, String> error = new HashMap<>();
        error.put("error", "Bad Request");
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Exception handler for SecurityException (e.g., conversation doesn't belong to user). Converts SecurityException to HTTP 403 with
     * error message.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurityException(SecurityException ex) {
        logger.warn("Access denied: {}", ex.getMessage());

        Map<String, String> error = new HashMap<>();
        error.put("error", "Forbidden");
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Exception handler for generic exceptions. Converts any unhandled exception to HTTP 500.
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
