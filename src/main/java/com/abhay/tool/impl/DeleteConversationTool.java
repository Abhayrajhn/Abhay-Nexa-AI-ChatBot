package com.abhay.tool.impl;

import com.abhay.entity.Conversation;
import com.abhay.exception.ResourceNotFoundException;
import com.abhay.model.llm.ToolDefinition;
import com.abhay.repository.ConversationRepository;
import com.abhay.tool.Tool;
import com.abhay.tool.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Delete Conversation Tool
 * Permanently deletes a conversation and all its messages from the database.
 * IMPORTANT: This tool requires human approval before execution.
 * Security: - The tool itself does NOT check approval - The Agent Runtime checks requiresApproval() before execution - Only approved
 * requests reach the execute() method - User ownership should be verified by the approval system
 * Flow: 1. LLM decides to delete a conversation 2. Agent Runtime detects requiresApproval = true 3. Creates ApprovalRequest (PENDING) 4.
 * Pauses execution 5. User approves/rejects 6. If approved: execute() is called 7. If rejected: execute() is never called
 * Example usage: User: "Delete my conversation called Trip Planning" LLM calls: delete_conversation({ "conversationId": "123" }) System:
 * Asks user for approval User: Approves Tool executes: Deletes conversation 123 Tool returns: { "success": true, "message": "Deleted
 * conversation 'Trip Planning'" }
 */
@Component
public class DeleteConversationTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(DeleteConversationTool.class);

    @Autowired
    private ConversationRepository conversationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "delete_conversation";
    }

    @Override
    public String getDescription() {
        return "Permanently deletes a conversation and all its messages. " + "This action cannot be undone. Use with caution. "
                + "Requires conversation ID.";
    }

    @Override
    public ToolDefinition getDefinition() {
        // JSON Schema for the parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // conversationId parameter
        Map<String, Object> conversationIdProperty = new HashMap<>();
        conversationIdProperty.put("type", "string");
        conversationIdProperty.put("description", "The ID of the conversation to delete");
        properties.put("conversationId", conversationIdProperty);

        parameters.put("properties", properties);
        parameters.put("required", new String[] { "conversationId" });

        return ToolDefinition.create(getName(), getDescription(), parameters);
    }

    @Override
    public boolean requiresApproval() {
        // CRITICAL: Deletion is destructive and requires human approval
        return true;
    }

    @Override
    public String execute(String arguments) throws ToolExecutionException {
        logger.info("Executing delete_conversation with arguments: {}", arguments);

        try {
            // Parse arguments
            JsonNode root = objectMapper.readTree(arguments);

            // Support both "conversationId" (camelCase) and "conversation_id" (snake_case)
            // LLMs sometimes prefer snake_case
            String conversationIdStr = null;
            if (root.has("conversationId")) {
                conversationIdStr = root.get("conversationId").asText();
            } else if (root.has("conversation_id")) {
                conversationIdStr = root.get("conversation_id").asText();
            } else {
                throw new ToolExecutionException("Missing required parameter: conversationId (or conversation_id)");
            }

            Long conversationId;

            try {
                conversationId = Long.parseLong(conversationIdStr);
            } catch (NumberFormatException e) {
                throw new ToolExecutionException("Invalid conversationId: must be a number");
            }

            // Check if conversation exists
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

            String conversationTitle = conversation.getTitle();
            logger.info("Deleting conversation: id={}, title={}", conversationId, conversationTitle);

            // Delete the conversation (cascades to messages due to CascadeType.ALL)
            conversationRepository.deleteById(conversationId);

            logger.info("Successfully deleted conversation {}", conversationId);

            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("conversationId", conversationId);
            response.put("conversationTitle", conversationTitle);
            response.put("message", "Successfully deleted conversation '" + conversationTitle + "'");

            return objectMapper.writeValueAsString(response);

        } catch (ResourceNotFoundException e) {
            // Conversation not found
            logger.warn("Conversation not found: {}", e.getMessage());
            throw new ToolExecutionException("Conversation not found: " + e.getMessage());

        } catch (ToolExecutionException e) {
            // Re-throw tool execution exceptions
            throw e;

        } catch (Exception e) {
            // Unexpected error
            logger.error("Error deleting conversation: {}", e.getMessage(), e);
            throw new ToolExecutionException("Failed to delete conversation: " + e.getMessage());
        }
    }
}
