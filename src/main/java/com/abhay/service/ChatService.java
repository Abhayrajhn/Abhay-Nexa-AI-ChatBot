package com.abhay.service;

import com.abhay.client.OpenAIClient;
import com.abhay.model.dto.ChatRequest;
import com.abhay.model.dto.ChatResponse;
import com.abhay.model.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat Service Implementation.
 * This is where the business logic lives: 1. Receives user message and conversation history from the controller 2. Builds the complete
 * message array (system + history + new user message) 3. Calls OpenAI via the client 4. Returns the response with updated conversation
 * history
 * Key Concept: CONVERSATION CONTEXT - LLM APIs are STATELESS (they don't remember anything) - To maintain context, we send ALL previous
 * messages every time - The conversation history grows with each exchange - Later, you'll need to manage history size (token limits)
 */
@Service
public class ChatService implements IChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private OpenAIClient openAIClient;

    @Value("${openai.system.message}")
    private String systemMessage;

    @Override
    public ChatResponse chat(ChatRequest request) {
        logger.info("Processing chat request with message: {}", request.getMessage());

        // Build the complete message array for OpenAI
        List<Message> messages = buildMessages(request);

        // Send to OpenAI and get response
        String assistantResponse = openAIClient.sendMessage(messages);

        // Build updated conversation history
        List<Message> updatedHistory = buildUpdatedHistory(request.getConversationHistory(), request.getMessage(), assistantResponse);

        // Return response
        return new ChatResponse(assistantResponse, updatedHistory);
    }

    /**
     * Builds the complete message array to send to OpenAI.
     * Structure: 1. System message (instructions for AI behavior) 2. Previous conversation history (for context) 3. New user message
     */
    private List<Message> buildMessages(ChatRequest request) {
        List<Message> messages = new ArrayList<>();

        // 1. Add system message (always first)
        messages.add(new Message("system", systemMessage));

        // 2. Add conversation history (if exists)
        if (request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
            messages.addAll(request.getConversationHistory());
        }

        // 3. Add new user message
        messages.add(new Message("user", request.getMessage()));

        logger.debug("Built message array with {} messages", messages.size());
        return messages;
    }

    /**
     * Builds the updated conversation history to return to the frontend.
     * This includes: - Previous history (if any) - New user message - New assistant response
     * The frontend will send this back in the next request.
     */
    private List<Message> buildUpdatedHistory(List<Message> previousHistory, String userMessage, String assistantMessage) {
        List<Message> updatedHistory = new ArrayList<>();

        // Add previous history
        if (previousHistory != null && !previousHistory.isEmpty()) {
            updatedHistory.addAll(previousHistory);
        }

        // Add new user message
        updatedHistory.add(new Message("user", userMessage));

        // Add new assistant response
        updatedHistory.add(new Message("assistant", assistantMessage));

        return updatedHistory;
    }
}
