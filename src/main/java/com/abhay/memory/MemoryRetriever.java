package com.abhay.memory;

import com.abhay.entity.LongTermMemory;
import com.abhay.repository.LongTermMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MemoryRetriever loads relevant long-term memories for a given user and query.
 * Phase 1: Simple keyword-based retrieval from PostgreSQL Future: Vector embeddings for semantic search
 * Retrieval Strategy: 1. Extract keywords from user query 2. Score memories by relevance (keyword matches in key/value/tags) 3. Return top
 * N most relevant memories 4. Update access tracking for retrieved memories
 */
@Service
public class MemoryRetriever {

    private static final Logger logger = LoggerFactory.getLogger(MemoryRetriever.class);

    @Autowired
    private LongTermMemoryRepository memoryRepository;

    /**
     * Retrieve relevant memories for a user based on query keywords.
     *
     * @param userId
     *         The user ID
     * @param query
     *         The user's current query/message
     * @param maxResults
     *         Maximum memories to return
     * @return List of relevant memories, ordered by relevance
     */
    public List<LongTermMemory> retrieveRelevantMemories(Long userId, String query, int maxResults) {
        logger.info("Retrieving memories for userId={}, query='{}'", userId, query);

        // Extract keywords from query
        List<String> keywords = extractKeywords(query);
        logger.debug("Extracted keywords: {}", keywords);

        if (keywords.isEmpty()) {
            // No keywords - return most recently used memories
            List<LongTermMemory> recentMemories = memoryRepository.findTop10ByUser_IdOrderByLastAccessedAtDesc(userId);
            updateAccessCounts(recentMemories);
            return recentMemories.stream().limit(maxResults).collect(Collectors.toList());
        }

        // Retrieve all memories for user
        List<LongTermMemory> allMemories = memoryRepository.findByUser_Id(userId);

        if (allMemories.isEmpty()) {
            logger.info("No memories found for user {}", userId);
            return new ArrayList<>();
        }

        // Score each memory by relevance
        Map<LongTermMemory, Integer> scores = new HashMap<>();
        for (LongTermMemory memory : allMemories) {
            int score = calculateRelevanceScore(memory, keywords);
            if (score > 0) {
                scores.put(memory, score);
            }
        }

        // Sort by score (descending)
        List<LongTermMemory> relevantMemories = scores.entrySet().stream()
                .sorted(Map.Entry.<LongTermMemory, Integer> comparingByValue().reversed()).limit(maxResults).map(Map.Entry::getKey)
                .collect(Collectors.toList());

        logger.info("Found {} relevant memories", relevantMemories.size());

        // Update access counts
        updateAccessCounts(relevantMemories);

        return relevantMemories;
    }

    /**
     * Extract keywords from query text. Simple tokenization - split on whitespace and filter stopwords.
     */
    private List<String> extractKeywords(String query) {
        if (query == null || query.isEmpty()) {
            return new ArrayList<>();
        }

        // Simple stopwords list
        Set<String> stopwords = new HashSet<>(
                Arrays.asList("a", "an", "the", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does",
                        "did", "will", "would", "should", "can", "could", "may", "might", "must", "i", "you", "he", "she", "it", "we",
                        "they", "what", "which", "who", "when", "where", "why", "how", "this", "that", "these", "those", "to", "from", "in",
                        "on", "at", "by", "for", "with", "about", "as", "of", "and", "or", "but", "my", "your"));

        return Arrays.stream(query.toLowerCase().split("\\s+")).map(word -> word.replaceAll("[^a-z0-9]", ""))
                .filter(word -> word.length() > 2).filter(word -> !stopwords.contains(word)).distinct().collect(Collectors.toList());
    }

    /**
     * Calculate relevance score for a memory given keywords.
     * Scoring: - +10 points for keyword match in key - +5 points for keyword match in value - +3 points for keyword match in tags - +2
     * points for recent access (within last 7 days) - +1 point per access count (up to +10)
     */
    private int calculateRelevanceScore(LongTermMemory memory, List<String> keywords) {
        int score = 0;

        String keyLower = memory.getKey().toLowerCase();
        String valueLower = memory.getValue().toLowerCase();
        String tagsLower = memory.getTags() != null ? memory.getTags().toLowerCase() : "";

        for (String keyword : keywords) {
            if (keyLower.contains(keyword)) {
                score += 10;
            }
            if (valueLower.contains(keyword)) {
                score += 5;
            }
            if (tagsLower.contains(keyword)) {
                score += 3;
            }
        }

        // Boost recently accessed memories
        if (memory.getLastAccessedAt() != null) {
            long daysSinceAccess = java.time.temporal.ChronoUnit.DAYS.between(memory.getLastAccessedAt(), java.time.LocalDateTime.now());
            if (daysSinceAccess <= 7) {
                score += 2;
            }
        }

        // Boost frequently accessed memories (up to +10)
        score += Math.min(memory.getAccessCount(), 10);

        return score;
    }

    /**
     * Update access counts and timestamps for retrieved memories. This helps track which memories are most useful.
     */
    private void updateAccessCounts(List<LongTermMemory> memories) {
        for (LongTermMemory memory : memories) {
            memory.recordAccess();
            memoryRepository.save(memory);
        }
    }

    /**
     * Format memories as context string for LLM.
     * Output format: "User Profile (from memory): - programming_language: Python 3.11 - preferred_style: concise with code examples -
     * current_project: Django e-commerce site"
     */
    public String formatMemoriesAsContext(List<LongTermMemory> memories) {
        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("User Profile (from memory):\n");

        for (LongTermMemory memory : memories) {
            context.append("- ").append(memory.getKey()).append(": ").append(memory.getValue()).append("\n");
        }

        return context.toString();
    }

    /**
     * Get all memories for a user (for display/management).
     */
    public List<LongTermMemory> getAllUserMemories(Long userId) {
        return memoryRepository.findByUser_Id(userId);
    }

    /**
     * Get memories by type.
     */
    public List<LongTermMemory> getMemoriesByType(Long userId, String memoryType) {
        return memoryRepository.findByUser_IdAndMemoryType(userId, memoryType);
    }

    /**
     * Get memory count for a user.
     */
    public long getMemoryCount(Long userId) {
        return memoryRepository.countByUser_Id(userId);
    }
}
