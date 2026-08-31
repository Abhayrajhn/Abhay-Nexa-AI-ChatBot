package com.abhay.service;

import com.abhay.agent.AgentResult;
import com.abhay.agent.AgentRuntime;
import com.abhay.agent.AgentState;
import com.abhay.client.OpenAIClient;
import com.abhay.entity.Conversation;
import com.abhay.entity.Message;
import com.abhay.entity.User;
import com.abhay.entity.LongTermMemory;
import com.abhay.exception.ResourceNotFoundException;
import com.abhay.memory.MemoryExtractor;
import com.abhay.memory.MemoryRetriever;
import com.abhay.memory.WorkingMemory;
import com.abhay.model.dto.ConversationResponse;
import com.abhay.model.dto.CreateConversationRequest;
import com.abhay.model.dto.MessageResponse;
import com.abhay.model.llm.LLMResponse;
import com.abhay.model.llm.ToolCall;
import com.abhay.model.llm.ToolDefinition;
import com.abhay.planning.Plan;
import com.abhay.planning.PlanExecutor;
import com.abhay.planning.PlanStep;
import com.abhay.planning.Planner;
import com.abhay.repository.ConversationRepository;
import com.abhay.repository.MessageRepository;
import com.abhay.repository.UserRepository;
import com.abhay.repository.LongTermMemoryRepository;
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
import java.util.Optional;
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
    private Planner planner;

    @Autowired
    private PlanExecutor planExecutor;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LongTermMemoryRepository longTermMemoryRepository;

    @Autowired
    private MemoryExtractor memoryExtractor;

    @Autowired
    private MemoryRetriever memoryRetriever;

    @Autowired
    private AgentRuntime agentRuntime;

    @Value("${openai.system.message}")
    private String systemMessage;

    /**
     * Create a new conversation.
     */
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request) {
        logger.info("Creating new conversation with title: {} for userId: {}", request.getTitle(), request.getUserId());

        // Validate userId is provided
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId is required to create a conversation");
        }

        // Get user entity
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        // Create conversation and associate with user
        Conversation conversation = new Conversation(request.getTitle());
        conversation.setUser(user);
        Conversation saved = conversationRepository.save(conversation);

        logger.info("Created conversation {} for user {}", saved.getId(), user.getUsername());

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
    public void sendMessageStream(Long conversationId, Long userId, String content, SseEmitter emitter) {
        logger.info("Sending streaming message to conversation {} from user {}: {}", conversationId, userId, content);

        try {
            // 1. Validate conversation exists and belongs to user
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

            // 1b. Validate userId is provided
            if (userId == null) {
                throw new IllegalArgumentException("userId is required");
            }

            // 1c. Get user entity
            User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

            // 1d. Validate conversation belongs to user
            if (!conversation.getUser().getId().equals(userId)) {
                throw new SecurityException("Conversation does not belong to user");
            }

            logger.info("Processing message from user {} in conversation {}", user.getUsername(), conversationId);

            // 2. Save user message
            Message userMessage = new Message(Message.Role.USER, content);
            userMessage.setConversation(conversation);
            messageRepository.save(userMessage);
            logger.debug("Saved user message with id: {}", userMessage.getId());

            // 3. Retrieve conversation history (including the user message we just saved)
            List<Message> history = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
            logger.debug("Retrieved {} messages from conversation history", history.size());

            // 4. MEMORY INTEGRATION: Retrieve relevant long-term memories
            List<LongTermMemory> relevantMemories = memoryRetriever.retrieveRelevantMemories(userId, content, 10  // Max 10 memories
            );
            logger.info("Retrieved {} relevant memories for user {}", relevantMemories.size(), userId);

            // 5. Build message list for OpenAI with memory context
            List<com.abhay.model.llm.Message> llmMessages = buildLLMMessagesWithMemory(history, relevantMemories);

            // 6. Get available tools
            List<ToolDefinition> toolDefinitions = toolRegistry.getAllDefinitions();
            logger.info("Available tools: {}", toolRegistry.getToolCount());

            // 7. DECISION POINT: Which execution flow?
            // Priority: Agent Runtime > Planning > Tool Calling
            if (agentRuntime.needsAgentLoop(content)) {
                // AGENT RUNTIME FLOW (NEW)
                logger.info("Using AGENT RUNTIME flow for request (dynamic decision-making)");
                CompletableFuture.runAsync(() -> {
                    handleAgentRuntimeFlow(content, llmMessages, emitter, conversation, user);
                });
            } else if (planner.needsPlanning(content)) {
                // PLANNING FLOW
                logger.info("Using PLANNING flow for request (predetermined steps)");
                CompletableFuture.runAsync(() -> {
                    handlePlanningFlow(content, llmMessages, emitter, conversation, user);
                });
            } else {
                // TOOL CALLING FLOW (SIMPLE)
                logger.info("Using TOOL CALLING flow for request (simple or no tools)");
                handleToolCallingFlow(llmMessages, toolDefinitions, emitter, conversation, user);
            }

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
     * @param user
     *         User entity for memory extraction
     */
    private void handleToolExecution(List<com.abhay.model.llm.Message> llmMessages, List<ToolCall> toolCalls,
            List<ToolDefinition> toolDefinitions, SseEmitter emitter, Conversation conversation, User user) {
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
            saveAndCompleteResponse(finalContent, emitter, conversation, user,
                    llmMessages.stream().filter(m -> "user".equals(m.getRole())).reduce((first, second) -> second)
                            .map(com.abhay.model.llm.Message::getContent).orElse(""));

            // Note: Memory extraction now happens inside saveAndCompleteResponse

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
     * @param user
     *         User entity for memory extraction
     * @param userMessage
     *         The user's message content
     */
    private void saveAndCompleteResponse(String content, SseEmitter emitter, Conversation conversation, User user, String userMessage)
            throws IOException {

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

        // MEMORY INTEGRATION: Extract and store memories (async, non-blocking)
        CompletableFuture.runAsync(() -> {
            try {
                extractAndStoreMemories(userMessage, content, user, conversation.getId());
            } catch (Exception memEx) {
                logger.error("Failed to extract memories (non-critical): {}", memEx.getMessage());
            }
        });
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
     * Build LLM messages with memory context prepended. This is the memory-aware version that includes user's long-term memories.
     *
     * @param history
     *         Conversation history
     * @param memories
     *         Relevant long-term memories
     * @return List of messages including system prompt, memory context, and history
     */
    private List<com.abhay.model.llm.Message> buildLLMMessagesWithMemory(List<Message> history, List<LongTermMemory> memories) {
        List<com.abhay.model.llm.Message> llmMessages = new ArrayList<>();

        // 1. System message (as before)
        llmMessages.add(new com.abhay.model.llm.Message("system", systemMessage));

        // 2. Memory context
        if (memories != null && !memories.isEmpty()) {
            String memoryContext = memoryRetriever.formatMemoriesAsContext(memories);
            llmMessages.add(new com.abhay.model.llm.Message("system", memoryContext));
            logger.debug("Added memory context with {} memories", memories.size());
        }

        // 3. Conversation history (as before)
        for (Message msg : history) {
            llmMessages.add(new com.abhay.model.llm.Message(msg.getRole().toString().toLowerCase(), msg.getContent()));
        }

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

    /**
     * Handle planning flow for complex multi-step requests. Flow: 1. Generate plan using Planner 2. Execute plan using PlanExecutor 3.
     * Build context from plan results 4. Get final response from LLM 5. Stream final response to client 6. Save and complete
     */
    private void handlePlanningFlow(String content, List<com.abhay.model.llm.Message> llmMessages, SseEmitter emitter,
            Conversation conversation, User user) {
        try {
            logger.info("Starting planning flow for request: {}", content);

            // 1. Notify frontend: planning started
            emitter.send(SseEmitter.event().name("planning_start").data(Map.of("status", "Analyzing request and creating plan")));

            // 2. Generate plan
            Plan plan = planner.createPlan(content, llmMessages);
            logger.info("Plan created with {} steps", plan.getStepCount());

            // 3. Notify frontend: plan created
            emitter.send(SseEmitter.event().name("plan_created")
                    .data(Map.of("steps", plan.getStepCount(), "description", plan.getDescription())));

            // 4. Execute plan
            logger.info("Executing plan...");
            Map<String, Object> results = planExecutor.executePlan(plan);
            logger.info("Plan executed successfully");

            // 5. Notify frontend: execution complete
            emitter.send(SseEmitter.event().name("plan_executed").data(Map.of("status", "Plan executed successfully")));

            // 6. Build context for final response
            StringBuilder planContext = new StringBuilder();
            planContext.append("I executed a plan to complete your request. Here are the results:\n\n");

            for (PlanStep step : plan.getSteps()) {
                planContext.append("Step ").append(step.getStepNumber()).append(": ").append(step.getDescription()).append("\n");
                planContext.append("Tool used: ").append(step.getToolName()).append("\n");
                planContext.append("Result: ").append(step.getResult()).append("\n\n");
            }

            // 7. Get final response from LLM
            logger.info("Generating final natural language response...");
            llmMessages.add(new com.abhay.model.llm.Message("assistant", planContext.toString()));
            llmMessages.add(new com.abhay.model.llm.Message("system",
                    "Based on the plan execution results above, provide a clear, natural language response to the user's original request. "
                            + "Be concise and focus on answering what they asked."));

            String finalResponse = openAIClient.sendMessage(llmMessages);
            logger.info("Final response generated, length: {}", finalResponse.length());

            // 8. Stream final response (send as chunks for consistency)
            emitter.send(SseEmitter.event().name("chunk").data(finalResponse));

            // 9. Save and complete
            saveAndCompleteResponse(finalResponse, emitter, conversation, user, content);

            // Note: Memory extraction now happens inside saveAndCompleteResponse

        } catch (Exception e) {
            logger.error("Error during planning flow: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Planning failed: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                logger.error("Error sending error event: {}", ioException.getMessage());
                emitter.completeWithError(ioException);
            }
        }
    }

    /**
     * Handle agent runtime flow for dynamic decision-making. This flow implements the DECIDE → ACT → OBSERVE → DECIDE loop. The agent
     * iteratively: 1. Decides what to do next based on current state 2. Executes tools as needed 3. Observes the results 4. Decides again
     * based on new information 5. Repeats until task is complete or max iterations reached After the agent loop completes, we generate a
     * final natural language response and stream it to the frontend.
     *
     * @param userRequest
     *         The original user request
     * @param llmMessages
     *         Conversation context (history + memories)
     * @param emitter
     *         SSE emitter for streaming to frontend
     * @param conversation
     *         Database conversation entity
     * @param user
     *         User entity for memory extraction
     */
    private void handleAgentRuntimeFlow(String userRequest, List<com.abhay.model.llm.Message> llmMessages, SseEmitter emitter,
            Conversation conversation, User user) {
        try {
            logger.info("Starting AGENT RUNTIME flow for request: {}", userRequest);

            // Execute the agent loop
            // The agent will iterate through DECIDE → ACT → OBSERVE until complete
            AgentResult result = agentRuntime.executeAgentLoop(userRequest, llmMessages, emitter);

            if (!result.isSuccess()) {
                // Agent loop failed (max iterations or error)
                logger.error("Agent loop failed: {}", result.getErrorMessage());

                // Generate a safe fallback response
                String fallbackResponse = "I attempted to complete your request but encountered difficulties. " + "Here's what I tried:\n\n"
                        + result.getFinalState().getToolHistorySummary()
                        + "\n\nCould you please rephrase your request or break it into smaller steps?";

                // Stream the fallback response
                emitter.send(SseEmitter.event().name("chunk").data(fallbackResponse));

                // Save and complete
                saveAndCompleteResponse(fallbackResponse, emitter, conversation, user, userRequest);
                return;
            }

            // Agent loop succeeded!
            logger.info("Agent loop completed successfully after {} iterations", result.getIterationsUsed());

            // Now we need to generate the final natural language response
            // The agent has gathered all necessary information via tools
            // We'll ask the LLM to formulate a final answer based on the tool results

            // Build context for final response
            logger.info("Generating final natural language response...");

            // Add a summary of what the agent did
            String agentSummary = buildAgentExecutionSummary(result);
            llmMessages.add(new com.abhay.model.llm.Message("system", agentSummary));

            // Add instruction to generate final response
            llmMessages.add(new com.abhay.model.llm.Message("system",
                    "Based on the tool execution results above, provide a clear, natural language response to the user's original request. "
                            + "Be concise and focus on answering what they asked. "
                            + "Do not mention the internal tools or steps you used - just provide the answer."));

            // Get final response from LLM (non-streaming for now, can be enhanced later)
            String finalResponse = openAIClient.sendMessage(llmMessages);
            logger.info("Final response generated, length: {}", finalResponse.length());

            // Stream the final response to frontend
            emitter.send(SseEmitter.event().name("chunk").data(finalResponse));

            // Save and complete
            saveAndCompleteResponse(finalResponse, emitter, conversation, user, userRequest);

        } catch (Exception e) {
            logger.error("Error during agent runtime flow: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("Agent execution failed: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                logger.error("Error sending error event: {}", ioException.getMessage());
                emitter.completeWithError(ioException);
            }
        }
    }

    /**
     * Build a summary of agent execution for the final LLM call. This provides context about what tools were executed and their results.
     */
    private String buildAgentExecutionSummary(AgentResult result) {
        StringBuilder summary = new StringBuilder();
        summary.append("Agent execution completed. Here's what I did:\n\n");

        for (AgentState.ToolExecution exec : result.getFinalState().getToolExecutions()) {
            summary.append(String.format("- Called %s tool: %s\n", exec.getToolName(), exec.getResult()));
        }

        summary.append("\nNow provide a natural language response based on these results.");
        return summary.toString();
    }

    /**
     * Handle tool calling flow for simple requests. This is the EXISTING flow, extracted to a separate method.
     */
    private void handleToolCallingFlow(List<com.abhay.model.llm.Message> llmMessages, List<ToolDefinition> toolDefinitions,
            SseEmitter emitter, Conversation conversation, User user) {
        // Accumulate the complete response as we stream
        StringBuilder completeResponse = new StringBuilder();
        AtomicReference<List<ToolCall>> toolCallsRef = new AtomicReference<>();

        // Extract user message content for memory extraction later
        final String userMessageContent = llmMessages.stream().filter(m -> "user".equals(m.getRole())).reduce((first, second) -> second)
                .map(com.abhay.model.llm.Message::getContent).orElse("");

        // Call OpenAI API with streaming and tool support
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
                                handleToolExecution(llmMessages, toolCalls, toolDefinitions, emitter, conversation, user);
                            });
                        } else {
                            // NORMAL RESPONSE PATH (no tools needed)
                            logger.info("Streaming completed. Total response length: {}", completeResponse.length());
                            saveAndCompleteResponse(completeResponse.toString(), emitter, conversation, user, userMessageContent);
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
    }

    /**
     * Extract and store memories from conversation. This is called after the assistant response is generated. Uses MemoryExtractor to
     * identify memorable information and stores it in the database.
     *
     * @param userMessage
     *         The user's message
     * @param assistantResponse
     *         The assistant's response
     * @param user
     *         The user entity
     * @param conversationId
     *         The conversation ID
     */
    private void extractAndStoreMemories(String userMessage, String assistantResponse, User user, Long conversationId) {
        try {
            logger.info("Extracting memories from conversation {}", conversationId);

            List<LongTermMemory> extractedMemories = memoryExtractor.extractMemories(userMessage, assistantResponse, user, conversationId);

            if (!extractedMemories.isEmpty()) {
                logger.info("Storing {} new memories", extractedMemories.size());

                for (LongTermMemory memory : extractedMemories) {
                    // Check if memory with same key already exists
                    Optional<LongTermMemory> existing = longTermMemoryRepository.findByUser_IdAndKey(user.getId(), memory.getKey());

                    if (existing.isPresent()) {
                        // Update existing memory
                        LongTermMemory existingMemory = existing.get();
                        existingMemory.setValue(memory.getValue());
                        existingMemory.setConfidence(memory.getConfidence());
                        existingMemory.setTags(memory.getTags());
                        longTermMemoryRepository.save(existingMemory);
                        logger.info("Updated existing memory: {}", memory.getKey());
                    } else {
                        // Save new memory
                        longTermMemoryRepository.save(memory);
                        logger.info("Stored new memory: {}", memory.getKey());
                    }
                }
            } else {
                logger.debug("No memorable information found in this conversation");
            }

        } catch (Exception e) {
            logger.error("Failed to extract/store memories: {}", e.getMessage(), e);
            // Don't fail the request if memory extraction fails
        }
    }
}
