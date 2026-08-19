package com.abhay.api;

import com.abhay.model.dto.ChatRequest;
import com.abhay.model.dto.ChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Chat API Interface - Defines the contract for chat endpoints.
 * This interface separates the API contract from the implementation, making it easier to maintain and test.
 */
@RequestMapping("/api")
public interface IChatAPI {

    /**
     * Main chat endpoint.
     * Receives a user message and optional conversation history, sends it to the LLM, and returns the AI's response.
     *
     * @param request
     *         Contains user message and conversation history
     * @return ResponseEntity with ChatResponse containing AI message and updated history
     */
    @PostMapping("/chat")
    ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request);

    /**
     * Health check endpoint.
     * Used to verify that the backend service is running.
     *
     * @return ResponseEntity with health status message
     */
    @GetMapping("/health")
    ResponseEntity<String> health();
}
