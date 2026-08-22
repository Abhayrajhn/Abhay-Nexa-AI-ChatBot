package com.abhay.service;

import com.abhay.client.OpenAIClient;
import com.abhay.entity.Conversation;
import com.abhay.entity.Message;
import com.abhay.exception.ResourceNotFoundException;
import com.abhay.model.dto.ConversationResponse;
import com.abhay.model.dto.CreateConversationRequest;
import com.abhay.model.dto.MessageResponse;
import com.abhay.repository.ConversationRepository;
import com.abhay.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversation Service Implementation.
 * Implements IConversationService and handles all conversation management business logic. This service coordinates between repositories and
 * the OpenAI client to manage conversations, messages, and LLM interactions.
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
     * Send a message to a conversation and get AI response. This is the core method that: 1. Retrieves conversation history from database
     * 2. Saves the user's message 3. Builds context for the LLM 4. Calls OpenAI API 5. Saves the assistant's response 6. Returns the
     * assistant message
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
