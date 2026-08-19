package com.abhay.api.impl;

import com.abhay.api.IChatAPI;
import com.abhay.model.dto.ChatRequest;
import com.abhay.model.dto.ChatResponse;
import com.abhay.service.IChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat Controller Implementation.
 * Implements the IChatAPI interface and handles HTTP requests. Delegates business logic to the service layer.
 */
@RestController
@CrossOrigin(origins = "*")  // Allow requests from any origin (for development)
public class ChatController implements IChatAPI {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private IChatService chatService;

    @Override
    public ResponseEntity<ChatResponse> chat(ChatRequest request) {
        logger.info("Received chat request");

        try {
            // Validate request
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                logger.warn("Invalid request: message is null or empty");
                return ResponseEntity.badRequest().build();
            }

            // Process chat request through service
            ChatResponse response = chatService.chat(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing chat request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Nexa backend is running!");
    }
}
