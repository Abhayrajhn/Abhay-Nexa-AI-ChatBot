package com.abhay.tool.impl;

import com.abhay.model.llm.ToolDefinition;
import com.abhay.repository.ConversationRepository;
import com.abhay.repository.MessageRepository;
import com.abhay.tool.Tool;
import com.abhay.tool.ToolExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Conversation Statistics Tool Retrieves statistics about conversations from the database. This is a READ-ONLY tool - no database
 * modifications allowed. Example usage: User: "How many conversations do I have?" LLM calls: get_conversation_stats({}) Tool returns: {
 * "total_conversations": 5, "total_messages": 42 } LLM responds: "You have 5 conversations with a total of 42 messages." SECURITY: Only
 * uses JPA repository count methods - no SQL execution.
 */
@Component
public class ConversationStatsTool implements Tool {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "get_conversation_stats";
    }

    @Override
    public String getDescription() {
        return "Retrieves statistics about conversations including total count and message count. "
                + "This tool provides a summary of the user's conversation history.";
    }

    @Override
    public ToolDefinition getDefinition() {
        // JSON Schema for the parameters (no parameters needed)
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", new HashMap<>());  // Empty properties - no params needed

        return ToolDefinition.create(getName(), getDescription(), parameters);
    }

    @Override
    public String execute(String arguments) throws ToolExecutionException {
        try {
            // Query database for statistics
            long totalConversations = conversationRepository.count();
            long totalMessages = messageRepository.count();

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("total_conversations", totalConversations);
            response.put("total_messages", totalMessages);

            // Calculate average messages per conversation (avoid division by zero)
            if (totalConversations > 0) {
                double avgMessagesPerConversation = (double) totalMessages / totalConversations;
                response.put("avg_messages_per_conversation", Math.round(avgMessagesPerConversation * 100.0) / 100.0);
            } else {
                response.put("avg_messages_per_conversation", 0);
            }

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            throw new ToolExecutionException("Failed to retrieve conversation statistics: " + e.getMessage(), e);
        }
    }
}
