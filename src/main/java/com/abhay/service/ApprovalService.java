package com.abhay.service;

import com.abhay.agent.AgentRuntime;
import com.abhay.agent.AgentState;
import com.abhay.approval.ApprovalRequest;
import com.abhay.approval.ApprovalRepository;
import com.abhay.approval.ApprovalStatus;
import com.abhay.entity.Conversation;
import com.abhay.entity.Message;
import com.abhay.model.llm.ToolCall;
import com.abhay.repository.ConversationRepository;
import com.abhay.repository.MessageRepository;
import com.abhay.tool.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * Service for handling approval lifecycle and agent resumption.
 * Responsibilities: - Resume agent execution after approval is granted - Execute the approved tool - Continue agent loop from paused state
 * - Handle rejection (notify user, don't execute) - Update conversation with results
 * Flow after approval: 1. Load ApprovalRequest from database 2. Verify status is APPROVED 3. Deserialize AgentState 4. Execute the approved
 * tool 5. Add tool result to AgentState 6. Resume agent loop (may iterate more or complete) 7. Generate final response 8. Save to database
 */
@Service
public class ApprovalService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalService.class);

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Resume agent execution after approval is granted.
     * This is called after the user approves an approval request. It executes the approved tool and continues the agent loop.
     *
     * @param approvalId
     *         Approval request ID
     * @param emitter
     *         SSE emitter for streaming updates to frontend
     * @throws Exception
     *         if resumption fails
     */
    @Transactional
    public void resumeAfterApproval(String approvalId, SseEmitter emitter) throws Exception {
        logger.info("Resuming agent execution after approval: {}", approvalId);

        // Load approval request
        ApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new RuntimeException("Approval request not found: " + approvalId));

        // Verify status is APPROVED
        if (!approval.isApproved()) {
            throw new IllegalStateException("Approval request is not approved: " + approval.getStatus());
        }

        // Load conversation
        Conversation conversation = conversationRepository.findById(approval.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + approval.getConversationId()));

        // Deserialize agent state
        AgentState state = objectMapper.readValue(approval.getAgentStateJson(), AgentState.class);
        logger.info("Deserialized agent state: {} iterations, {} tool executions", state.getIteration(), state.getToolExecutions().size());

        // Execute the approved tool
        logger.info("Executing approved tool: {} with arguments: {}", approval.getToolName(), approval.getToolArguments());

        try {
            // Notify frontend: tool execution starting
            emitter.send(SseEmitter.event().name("tool_execution_approved").data(Map.of("tool", approval.getToolName())));

            // Execute tool
            String toolResult = toolExecutor.executeTool(approval.getToolName(), approval.getToolArguments());
            logger.info("Tool execution result: {}", toolResult);

            // Add tool execution to agent state
            state.addToolExecution(approval.getToolName(), approval.getToolArguments(), toolResult);

            // Notify frontend: tool execution complete
            emitter.send(
                    SseEmitter.event().name("tool_execution_complete").data(Map.of("tool", approval.getToolName(), "result", toolResult)));

            // TODO: Continue agent loop if needed
            // For now, we'll just generate a final response based on the tool result
            // In a full implementation, we'd call agentRuntime.resumeAgentLoop(state, emitter)

            // Generate final response
            String finalResponse = generateFinalResponse(state);

            // Stream final response
            emitter.send(SseEmitter.event().name("chunk").data(finalResponse));

            // Save assistant message
            Message assistantMessage = new Message(Message.Role.ASSISTANT, finalResponse);
            assistantMessage.setConversation(conversation);
            messageRepository.save(assistantMessage);

            // Notify frontend: done
            emitter.send(SseEmitter.event().name("done").data(Map.of("message", finalResponse)));
            emitter.complete();

            logger.info("Agent resumption completed successfully");

        } catch (Exception e) {
            logger.error("Failed to execute approved tool: {}", e.getMessage(), e);

            // Send error to frontend
            emitter.send(SseEmitter.event().name("error").data(Map.of("error", "Failed to execute approved action: " + e.getMessage())));
            emitter.completeWithError(e);

            throw e;
        }
    }

    /**
     * Handle rejection of an approval request.
     * Notifies the user that the action was rejected and will not be executed.
     *
     * @param approvalId
     *         Approval request ID
     * @param emitter
     *         SSE emitter for streaming updates
     * @throws IOException
     *         if sending SSE fails
     */
    @Transactional
    public void handleRejection(String approvalId, SseEmitter emitter) throws IOException {
        logger.info("Handling rejection for approval: {}", approvalId);

        // Load approval request
        ApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new RuntimeException("Approval request not found: " + approvalId));

        // Verify status is REJECTED
        if (!approval.isRejected()) {
            throw new IllegalStateException("Approval request is not rejected: " + approval.getStatus());
        }

        // Load conversation
        Conversation conversation = conversationRepository.findById(approval.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + approval.getConversationId()));

        // Generate rejection message
        String rejectionMessage = String.format(
                "I understand. I won't execute the '%s' action. Is there anything else I can help you with?", approval.getToolName());

        // Stream rejection message
        emitter.send(SseEmitter.event().name("chunk").data(rejectionMessage));

        // Save assistant message
        Message assistantMessage = new Message(Message.Role.ASSISTANT, rejectionMessage);
        assistantMessage.setConversation(conversation);
        messageRepository.save(assistantMessage);

        // Notify frontend: done
        emitter.send(SseEmitter.event().name("done").data(Map.of("message", rejectionMessage)));
        emitter.complete();

        logger.info("Rejection handled successfully");
    }

    /**
     * Generate a final response based on agent state.
     * Enhanced version that creates a natural, informative response based on the tool execution result.
     *
     * @param state
     *         Agent state with tool execution history
     * @return Final response message
     */
    private String generateFinalResponse(AgentState state) {
        // Safety check: ensure we have tool executions
        if (state.getToolExecutions() == null || state.getToolExecutions().isEmpty()) {
            logger.warn("No tool executions found in agent state, returning generic message");
            return "✅ Action completed successfully.";
        }

        // Get the last tool execution (the one we just approved)
        String lastToolName = state.getToolExecutions().get(state.getToolExecutions().size() - 1).getToolName();
        String lastToolResult = state.getToolExecutions().get(state.getToolExecutions().size() - 1).getResult();

        // Safety check: ensure tool result is not null
        if (lastToolResult == null) {
            logger.warn("Tool result is null, returning generic message");
            return String.format("✅ Action completed successfully. The %s operation has been executed.", lastToolName);
        }

        // Parse the tool result to create a better response
        try {
            if (lastToolName.equals("delete_conversation")) {
                // Parse the JSON result from delete_conversation tool
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> result = mapper.readValue(lastToolResult, Map.class);

                String conversationTitle = (String) result.get("conversationTitle");
                Object conversationId = result.get("conversationId");

                if (conversationTitle != null && !conversationTitle.isEmpty()) {
                    return String.format(
                            "✅ Successfully deleted the conversation '%s' (ID: %s). All messages in that conversation have been permanently removed.",
                            conversationTitle, conversationId);
                } else {
                    return String.format("✅ Successfully deleted conversation #%s and all its messages.", conversationId);
                }
            }

            // For other tools, provide generic success message
            return String.format("✅ Action completed successfully. The %s operation has been executed.", lastToolName);

        } catch (Exception e) {
            logger.error("Error parsing tool result: {}", e.getMessage());
            // Fallback to generic message if parsing fails
            return String.format("✅ Action completed successfully. The %s operation has been executed.", lastToolName);
        }
    }
}
