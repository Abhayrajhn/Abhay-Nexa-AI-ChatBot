package com.abhay.memory;

import com.abhay.client.OpenAIClient;
import com.abhay.entity.LongTermMemory;
import com.abhay.entity.User;
import com.abhay.model.llm.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MemoryExtractor uses LLM to identify information worth remembering. Security: LLM returns structured JSON, backend validates before
 * storing. The LLM does NOT directly write to the database. Process: 1. Analyze user message and assistant response 2. Use LLM to extract
 * memorable facts/preferences/context 3. Parse structured JSON response 4. Validate each memory (reject secrets, enforce limits) 5. Return
 * validated memories (caller saves to DB)
 */
@Service
public class MemoryExtractor {

    private static final Logger logger = LoggerFactory.getLogger(MemoryExtractor.class);

    @Autowired
    private OpenAIClient openAIClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Secrets/sensitive keywords to reject
    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList("password", "api_key", "secret", "token", "credential", "ssn",
            "credit_card", "bank", "private_key", "apikey", "api key", "access_token", "auth_token", "bearer");

    /**
     * Extract memorable information from a user's message.
     *
     * @param userMessage
     *         The user's message text
     * @param assistantResponse
     *         The assistant's response (for context)
     * @param user
     *         The user entity
     * @param conversationId
     *         Source conversation ID
     * @return List of validated LongTermMemory objects (NOT yet saved to DB)
     */
    public List<LongTermMemory> extractMemories(String userMessage, String assistantResponse, User user, Long conversationId) {
        logger.info("Extracting memories from user message (userId={})", user.getId());

        try {
            // Build extraction prompt
            String extractionPrompt = buildExtractionPrompt();

            // Build messages for LLM
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", extractionPrompt));
            messages.add(new Message("user", "User said: " + userMessage));
            if (assistantResponse != null && !assistantResponse.isEmpty()) {
                messages.add(new Message("assistant", assistantResponse));
            }
            messages.add(new Message("user", "Extract any memorable information from the user's message. "
                    + "Return JSON array of memories or empty array if nothing memorable."));

            // Call LLM
            String llmResponse = openAIClient.sendMessage(messages);
            logger.info("========== MEMORY EXTRACTION RESPONSE ==========");
            logger.info("Raw LLM Response: {}", llmResponse);
            logger.info("================================================");

            // Parse JSON response
            List<LongTermMemory> memories = parseMemoriesFromLLM(llmResponse, user, conversationId);

            // Validate each memory
            List<LongTermMemory> validMemories = new ArrayList<>();
            for (LongTermMemory memory : memories) {
                if (validateMemory(memory)) {
                    validMemories.add(memory);
                } else {
                    logger.warn("Rejected memory: key={}, reason=validation failed", memory.getKey());
                }
            }

            logger.info("Extracted {} valid memories (rejected {})", validMemories.size(), memories.size() - validMemories.size());

            return validMemories;

        } catch (Exception e) {
            logger.error("Failed to extract memories: {}", e.getMessage(), e);
            return new ArrayList<>();  // Return empty list on error, don't fail the request
        }
    }

    /**
     * Build the system prompt for memory extraction.
     */
    private String buildExtractionPrompt() {
        return """
                You are a memory extraction system. Your job is to identify information worth remembering about the user.
                
                Extract information in these categories:
                1. FACT - Objective information (e.g., "uses Python 3.11", "works at Acme Corp")
                2. PREFERENCE - User preferences (e.g., "prefers concise explanations", "likes dark mode")
                3. CONTEXT - Current situation (e.g., "building e-commerce site", "learning Kubernetes")
                4. SKILL - User expertise (e.g., "expert in Django", "beginner in React")
                
                DO NOT extract:
                - Passwords, API keys, tokens, secrets, credentials
                - Temporary information ("I'm hungry", "it's raining")
                - Trivial facts that won't be useful later
                - Every detail of the conversation
                - Anything that looks like sensitive data
                
                Return a JSON array of memories:
                [
                  {
                    "type": "fact",
                    "key": "programming_language",
                    "value": "Python",
                    "tags": ["python", "programming", "language"],
                    "confidence": 1.0
                  },
                  {
                    "type": "preference",
                    "key": "explanation_style",
                    "value": "concise with code examples",
                    "tags": ["communication", "style"],
                    "confidence": 0.9
                  }
                ]
                
                If nothing is memorable, return empty array: []
                
                Be selective - only extract information that would be useful in future conversations.
                Keys should be snake_case (e.g., "programming_language", not "Programming Language").
                Tags should be lowercase single words.
                """;
    }

    /**
     * Parse LLM response into LongTermMemory objects.
     */
    private List<LongTermMemory> parseMemoriesFromLLM(String llmResponse, User user, Long conversationId) {
        List<LongTermMemory> memories = new ArrayList<>();

        try {
            // Extract JSON from response (might be wrapped in markdown)
            String jsonStr = llmResponse.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            // Parse JSON array
            JsonNode root = objectMapper.readTree(jsonStr);

            if (!root.isArray()) {
                logger.warn("LLM returned non-array response: {}", jsonStr);
                return memories;
            }

            // Parse each memory
            for (JsonNode node : root) {
                try {
                    LongTermMemory memory = new LongTermMemory();
                    memory.setUser(user);
                    memory.setMemoryType(node.get("type").asText());
                    memory.setKey(node.get("key").asText());
                    memory.setValue(node.get("value").asText());
                    memory.setSource("conversation_" + conversationId);

                    // Optional fields
                    if (node.has("tags") && node.get("tags").isArray()) {
                        List<String> tags = new ArrayList<>();
                        for (JsonNode tag : node.get("tags")) {
                            tags.add(tag.asText());
                        }
                        memory.setTagsFromList(tags);
                    }

                    if (node.has("confidence")) {
                        memory.setConfidence(node.get("confidence").asDouble());
                    } else {
                        memory.setConfidence(1.0);
                    }

                    memories.add(memory);

                } catch (Exception e) {
                    logger.warn("Failed to parse memory node: {}", node.toString(), e);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse LLM response as JSON: {}", llmResponse, e);
        }

        return memories;
    }

    /**
     * Validate a memory before storing. Security checks: - No sensitive keywords in key or value - Reasonable length limits - Valid memory
     * type
     */
    private boolean validateMemory(LongTermMemory memory) {
        // Check required fields
        if (memory.getKey() == null || memory.getKey().isEmpty()) {
            logger.warn("Memory missing key");
            return false;
        }

        if (memory.getValue() == null || memory.getValue().isEmpty()) {
            logger.warn("Memory missing value");
            return false;
        }

        if (memory.getMemoryType() == null || memory.getMemoryType().isEmpty()) {
            logger.warn("Memory missing type");
            return false;
        }

        // Check valid types
        List<String> validTypes = Arrays.asList("fact", "preference", "context", "skill");
        if (!validTypes.contains(memory.getMemoryType().toLowerCase())) {
            logger.warn("Invalid memory type: {}", memory.getMemoryType());
            return false;
        }

        // Check length limits
        if (memory.getKey().length() > 255) {
            logger.warn("Memory key too long: {} chars", memory.getKey().length());
            return false;
        }

        if (memory.getValue().length() > 1000) {
            logger.warn("Memory value too long: {} chars", memory.getValue().length());
            return false;
        }

        // Check for sensitive keywords (security)
        String combinedText = (memory.getKey() + " " + memory.getValue()).toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (combinedText.contains(keyword)) {
                logger.warn("Memory contains sensitive keyword '{}': rejected", keyword);
                return false;
            }
        }

        return true;
    }
}
