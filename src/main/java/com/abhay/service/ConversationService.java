package com.abhay.service;

import com.abhay.client.OpenAIClient;
import com.abhay.entity.Conversation;
import com.abhay.entity.Message;
import com.abhay.exception.ResourceNotFoundException;
import com.abhay.model.dto.ConversationResponse;
import com.abhay.model.dto.CreateConversationRequest;
import com.abhay.model.dto.MessageResponse;
import com.abhay.model.llm.LLMResponse;
import com.abhay.model.llm.ToolCall;
import com.abhay.model.llm.ToolDefinition;
import com.abhay.repository.ConversationRepository;
import com.abhay.repository.MessageRepository;
import com.abhay.tool.ToolExecutor;
import com.abhay.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Conversation Service Implementation. Implements IConversationService and handles all conversation management business logic. This service
 * coordinates between repositories and the OpenAI client to manage conversations, messages, and LLM interactions.
 */
@Service
public class ConversationService implements IConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolExecutor toolExecutor;

    @Value("${openai.system.message}")
    private String systemMessage;

    /**
     * Create a new conversation.
     */
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request) {
        logger.info("Creating new conversation with title: {}", request.getTitle());

        Conversation conversation = new Conversation(request.getTitle());
        Conversation saved = conversationRepository.save(conversation);

        return mapToConversationResponse(saved, false);
    }

    /**
     * Get all conversations (without messages for performance).
     */
    public List<ConversationResponse> getAllConversations() {
        logger.info("Fetching all conversations");

        List<Conversation> conversations = conversationRepository.findAll();

        return conversations.stream().map(conv -> mapToConversationResponse(conv, false)).collect(Collectors.toList());
    }

    /**
     * Get a single conversation with all its messages.
     */
    public ConversationResponse getConversationById(Long id) {
        logger.info("Fetching conversation with id: {}", id);

        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", id));

        return mapToConversationResponse(conversation, true);
    }

    /**
     * Delete a conversation (cascade deletes all messages).
     */
    @Transactional
    public void deleteConversation(Long id) {
        logger.info("Deleting conversation with id: {}", id);

        if (!conversationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Conversation", "id", id);
        }

        conversationRepository.deleteById(id);
    }

    /**
     * Update a conversation's title.
     */
    @Transactional
    public ConversationResponse updateConversationTitle(Long id, String title) {
        logger.info("Updating conversation {} with new title: {}", id, title);

        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", id));

        conversation.setTitle(title);
        Conversation updated = conversationRepository.save(conversation);

        logger.info("Successfully updated conversation {}", id);

        return mapToConversationResponse(updated, false);
    }

    /**
     * Send a message to a conversation and get AI response. NON-STREAMING VERSION (original method - kept for compatibility) This is the
     * core method that: 1. Retrieves conversation history from database 2. Saves the user's message 3. Builds context for the LLM 4. Calls
     * OpenAI API 5. Saves the assistant's response 6. Returns the assistant message
     */
    @Transactional
    public MessageResponse sendMessage(Long conversationId, String content) {
        logger.info("Sending message to conversation {}: {}", conversationId, content);

        // 1. Validate conversation exists
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

        // 2. Save user message
        Message userMessage = new Message(Message.Role.USER, content);
        userMessage.setConversation(conversation);
        messageRepository.save(userMessage);
        logger.debug("Saved user message with id: {}", userMessage.getId());

        // 3. Retrieve conversation history (including the user message we just saved)
        List<Message> history = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        logger.debug("Retrieved {} messages from conversation history", history.size());

        // 4. Build message list for OpenAI (transform entities to LLM format)
        List<com.abhay.model.llm.Message> llmMessages = buildLLMMessages(history);

        // 5. Call OpenAI API
        String assistantResponse = openAIClient.sendMessage(llmMessages);
        logger.info("Received response from OpenAI");

        // 6. Save assistant message
        Message assistantMessage = new Message(Message.Role.ASSISTANT, assistantResponse);
        assistantMessage.setConversation(conversation);
        Message savedAssistant = messageRepository.save(assistantMessage);
        logger.debug("Saved assistant message with id: {}", savedAssistant.getId());

        // 7. Return assistant message as DTO
        return mapToMessageResponse(savedAssistant);
    }

    /**
     * Send a message to a conversation and stream the AI response. STREAMING VERSION WITH TOOL CALLING SUPPORT Flow: 1. Save user message
     * 2. Build conversation history 3. Call OpenAI with tool definitions (streaming) 4. If LLM returns tool_calls: - Execute each tool -
     * Send results back to OpenAI - Get final response 5. If LLM returns text: - Stream text to client 6. Save assistant response
     *
     * @param conversationId
     *         The conversation to send the message to
     * @param content
     *         The user's message content
     * @param emitter
     *         The SSE emitter to send chunks to the client
     */
    @Transactional
    public void sendMessageStream(Long conversationId, String content, SseEmitter emitter) {
        logger.info("Sending streaming message to conversation {}: {}", conversationId, content);

        try {
            // 1. Validate conversation exists
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

            // 2. Save user message
            Message userMessage = new Message(Message.Role.USER, content);
            userMessage.setConversation(conversation);
            messageRepository.save(userMessage);
            logger.debug("Saved user message with id: {}", userMessage.getId());

            // 3. Retrieve conversation history (including the user message we just saved)
            List<Message> history = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
            logger.debug("Retrieved {} messages from conversation history", history.size());

            // 4. Build message list for OpenAI (transform entities to LLM format)
            List<com.abhay.model.llm.Message> llmMessages = buildLLMMessages(history);

            // 5. Get available tools
            List<ToolDefinition> toolDefinitions = toolRegistry.getAllDefinitions();
            logger.info("Available tools: {}", toolRegistry.getToolCount());

            // 6. Accumulate the complete response as we stream
            StringBuilder completeResponse = new StringBuilder();
            AtomicReference<List<ToolCall>> toolCallsRef = new AtomicReference<>();

            // 7. Call OpenAI API with streaming and tool support
            openAIClient.sendMessageStream(llmMessages, toolDefinitions,
                    // onChunk: Called for each chunk of text
                    chunk -> {
                        try {
                            // Accumulate the chunk
                            completeResponse.append(chunk);

                            // Send the chunk to the client via SSE
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));

                            logger.debug("Sent chunk of length: {}", chunk.length());
                        } catch (IOException e) {
                            logger.error("Error sending chunk to client: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    },
                    // onToolCalls: Called when LLM requests tools
                    toolCalls -> {
                        logger.info("LLM requested {} tools", toolCalls.size());
                        toolCallsRef.set(toolCalls);
                    },
                    // onComplete: Called when streaming finishes
                    () -> {
                        try {
                            List<ToolCall> toolCalls = toolCallsRef.get();

                            if (toolCalls != null && !toolCalls.isEmpty()) {
                                // TOOL EXECUTION PATH
                                // Run in separate thread to avoid blocking reactive context
                                logger.info("Handling tool execution for {} tool calls", toolCalls.size());
                                CompletableFuture.runAsync(() -> {
                                    handleToolExecution(llmMessages, toolCalls, toolDefinitions, emitter, conversation);
                                });
                            } else {
                                // NORMAL RESPONSE PATH (no tools needed)
                                logger.info("Streaming completed. Total response length: {}", completeResponse.length());
                                saveAndCompleteResponse(completeResponse.toString(), emitter, conversation);
                            }

                        } catch (Exception e) {
                            logger.error("Error completing streaming: {}", e.getMessage(), e);
                            emitter.completeWithError(e);
                        }
                    },
                    // onError: Called if an error occurs during streaming
                    error -> {
                        try {
                            logger.error("Error during streaming: {}", error.getMessage(), error);

                            // Send error event to client
                            emitter.send(SseEmitter.event().name("error").data("Failed to get response from AI: " + error.getMessage()));

                            emitter.completeWithError(error);
                        } catch (IOException e) {
                            logger.error("Error sending error event: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    });

        } catch (Exception e) {
            logger.error("Error initiating streaming: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Failed to initiate streaming: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                logger.error("Error sending error event: {}", ioException.getMessage());
                emitter.completeWithError(ioException);
            }
        }
    }

    /**
     * Handle tool execution when LLM requests tools. Flow: 1. Notify client that tools are executing 2. Add assistant message with
     * tool_calls to conversation history 3. Execute each tool 4. Add tool result messages to conversation history 5. Call OpenAI again with
     * tool results 6. Get final response and stream to client
     *
     * @param llmMessages
     *         Current conversation history
     * @param toolCalls
     *         List of tool calls from LLM
     * @param toolDefinitions
     *         Available tool definitions
     * @param emitter
     *         SSE emitter for client communication
     * @param conversation
     *         Database conversation entity
     */
    private void handleToolExecution(List<com.abhay.model.llm.Message> llmMessages, List<ToolCall> toolCalls,
            List<ToolDefinition> toolDefinitions, SseEmitter emitter, Conversation conversation) {
        try {
            // 1. Notify frontend: tools are executing
            emitter.send(SseEmitter.event().name("tool_execution_start").data(Map.of("count", toolCalls.size())));

            // 2. Add assistant message with tool_calls to history
            com.abhay.model.llm.Message assistantMessage = new com.abhay.model.llm.Message();
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(null);  // No content when calling tools
            assistantMessage.setToolCalls(toolCalls);
            llmMessages.add(assistantMessage);

            logger.info("Added assistant message with {} tool calls to history", toolCalls.size());

            // 3. Execute each tool
            for (ToolCall call : toolCalls) {
                String toolName = call.getFunction().getName();
                String arguments = call.getFunction().getArguments();

                logger.info("Executing tool: {} with id: {}", toolName, call.getId());

                // Notify frontend: executing this tool
                Map<String, String> toolInfo = new HashMap<>();
                toolInfo.put("tool", toolName);
                toolInfo.put("id", call.getId());
                emitter.send(SseEmitter.event().name("tool_execution").data(toolInfo));

                // Execute tool
                String result = toolExecutor.executeTool(toolName, arguments);
                logger.info("Tool {} executed. Result length: {}", toolName, result.length());

                // Add tool result message to history
                com.abhay.model.llm.Message toolMessage = new com.abhay.model.llm.Message();
                toolMessage.setRole("tool");
                toolMessage.setToolCallId(call.getId());
                toolMessage.setContent(result);
                llmMessages.add(toolMessage);

                logger.debug("Added tool result message to history for tool: {}", toolName);
            }

            // 4. Notify frontend: tools execution complete
            emitter.send(SseEmitter.event().name("tool_execution_complete").data("All tools executed successfully"));

            logger.info("All tools executed. Getting final response from OpenAI...");

            // 5. Get final response from OpenAI (with tool results)
            LLMResponse finalResponse = openAIClient.sendMessageWithToolResults(llmMessages, toolDefinitions);

            // 6. Extract final answer
            String finalContent = finalResponse.getChoices().get(0).getMessage().getContent();
            logger.info("Received final response from OpenAI. Length: {}", finalContent.length());

            // 7. Save and complete
            saveAndCompleteResponse(finalContent, emitter, conversation);

        } catch (Exception e) {
            logger.error("Error during tool execution: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Tool execution failed: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                logger.error("Error sending error event: {}", ioException.getMessage());
                emitter.completeWithError(ioException);
            }
        }
    }

    /**
     * Save assistant response and complete SSE connection.
     *
     * @param content
     *         Assistant's response content
     * @param emitter
     *         SSE emitter
     * @param conversation
     *         Database conversation entity
     */
    private void saveAndCompleteResponse(String content, SseEmitter emitter, Conversation conversation) throws IOException {

        // Save the complete assistant message to database
        Message assistantMessage = new Message(Message.Role.ASSISTANT, content);
        assistantMessage.setConversation(conversation);
        Message savedAssistant = messageRepository.save(assistantMessage);
        logger.info("Saved complete assistant message with id: {}", savedAssistant.getId());

        // Send completion event with the saved message
        MessageResponse response = mapToMessageResponse(savedAssistant);
        emitter.send(SseEmitter.event().name("done").data(response));

        // Close the SSE connection
        emitter.complete();
        logger.info("SSE connection closed successfully");
    }

    /**
     * Get all messages for a conversation.
     */
    public List<MessageResponse> getMessages(Long conversationId) {
        logger.info("Fetching messages for conversation: {}", conversationId);

        // Validate conversation exists
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Conversation", "id", conversationId);
        }

        List<Message> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);

        return messages.stream().map(this::mapToMessageResponse).collect(Collectors.toList());
    }

    // ==================== Private Helper Methods ====================

    /**
     * Convert database Message entities to OpenAI LLM message format. Database entity: - Role: Enum (USER, ASSISTANT, SYSTEM) - Has
     * conversation reference, ID, timestamps OpenAI format: - role: String ("user", "assistant", "system") - content: String - No other
     * fields
     */
    private List<com.abhay.model.llm.Message> buildLLMMessages(List<Message> historyEntities) {
        List<com.abhay.model.llm.Message> llmMessages = new ArrayList<>();

        // Add system message first (instructions for AI behavior)
        llmMessages.add(new com.abhay.model.llm.Message("system", systemMessage));

        // Add conversation history
        for (Message entity : historyEntities) {
            String role = entity.getRole().name().toLowerCase(); // USER → "user"
            llmMessages.add(new com.abhay.model.llm.Message(role, entity.getContent()));
        }

        logger.debug("Built LLM message array with {} messages (including system message)", llmMessages.size());
        return llmMessages;
    }

    /**
     * Map Conversation entity to ConversationResponse DTO.
     *
     * @param includeMessages
     *         - If true, fetch and include all messages
     */
    private ConversationResponse mapToConversationResponse(Conversation entity, boolean includeMessages) {
        ConversationResponse response = new ConversationResponse(entity.getId(), entity.getTitle(), entity.getCreatedAt(),
                entity.getUpdatedAt());

        if (includeMessages) {
            List<Message> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(entity.getId());
            List<MessageResponse> messageResponses = messages.stream().map(this::mapToMessageResponse).collect(Collectors.toList());
            response.setMessages(messageResponses);
        } else {
            response.setMessages(new ArrayList<>());
        }

        return response;
    }

    /**
     * Map Message entity to MessageResponse DTO. Note: Does NOT include conversation reference (prevents circular reference).
     */
    private MessageResponse mapToMessageResponse(Message entity) {
        return new MessageResponse(entity.getId(), entity.getRole().name(),  // Enum → String
                entity.getContent(), entity.getCreatedAt());
    }
}
