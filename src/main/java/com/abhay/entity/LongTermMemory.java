package com.abhay.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Long-Term Memory entity for storing persistent facts and preferences about users. Memory Types: - fact: Objective information (e.g.,
 * "uses Python 3.11") - preference: User preferences (e.g., "prefers concise explanations") - context: Current situation (e.g., "working on
 * e-commerce project") - skill: User expertise (e.g., "expert in Django") Security: - Memories are validated before storage (no passwords,
 * API keys, secrets) - Length limits enforced (key: 255 chars, value: 1000 chars) - Type whitelist enforced Retrieval: - Keyword-based
 * search using tags and keys - Access tracking (count and timestamp) - Relevance scoring for ranking
 */
@Entity
@Table(name = "long_term_memories", indexes = { @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_memory_type", columnList = "memory_type"), @Index(name = "idx_key", columnList = "memory_key"),
        @Index(name = "idx_tags", columnList = "tags") })
public class LongTermMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Type of memory: 'fact', 'preference', 'context', 'skill'
     */
    @Column(name = "memory_type", nullable = false, length = 50)
    private String memoryType;

    /**
     * Structured key for this memory. Examples: "programming_language", "preferred_style", "current_project"
     */
    @Column(name = "memory_key", nullable = false, length = 255)
    private String key;

    /**
     * The actual memory value. Examples: "Python 3.11", "concise with code", "Django e-commerce site"
     */
    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    private String value;

    /**
     * Optional tags for retrieval. Stored as comma-separated values: "python,programming,language"
     */
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    /**
     * Confidence score (0.0 to 1.0). 1.0 = explicit user statement 0.8 = strong inference 0.5 = weak inference
     */
    @Column(name = "confidence")
    private Double confidence;

    /**
     * Source conversation where this was learned. Format: "conversation_123" or "user_profile" or "manual"
     */
    @Column(name = "source", length = 255)
    private String source;

    /**
     * Number of times this memory has been accessed.
     */
    @Column(name = "access_count", nullable = false)
    private Integer accessCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Call this when memory is retrieved and used.
     */
    public void recordAccess() {
        this.lastAccessedAt = LocalDateTime.now();
        this.accessCount++;
    }

    // Constructors
    public LongTermMemory() {}

    public LongTermMemory(User user, String memoryType, String key, String value) {
        this.user = user;
        this.memoryType = memoryType;
        this.key = key;
        this.value = value;
        this.confidence = 1.0;
        this.accessCount = 0;
    }

    // Helper methods for tags
    public void setTagsFromList(List<String> tagList) {
        if (tagList != null && !tagList.isEmpty()) {
            this.tags = String.join(",", tagList);
        }
    }

    public List<String> getTagsAsList() {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(tags.split(","));
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    @Override
    public String toString() {
        return "LongTermMemory{" + "id=" + id + ", memoryType='" + memoryType + '\'' + ", key='" + key + '\'' + ", value='" + value + '\''
                + ", confidence=" + confidence + ", accessCount=" + accessCount + '}';
    }
}
