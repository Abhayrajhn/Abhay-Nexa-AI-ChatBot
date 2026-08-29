package com.abhay.repository;

import com.abhay.entity.LongTermMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for LongTermMemory entity. Provides queries for retrieving user memories by various criteria.
 */
@Repository
public interface LongTermMemoryRepository extends JpaRepository<LongTermMemory, Long> {

    /**
     * Find all memories for a specific user.
     *
     * @param userId
     *         The user ID
     * @return List of all memories for the user
     */
    List<LongTermMemory> findByUser_Id(Long userId);

    /**
     * Find memories by user and type.
     *
     * @param userId
     *         The user ID
     * @param memoryType
     *         The memory type (fact, preference, context, skill)
     * @return List of memories matching the criteria
     */
    List<LongTermMemory> findByUser_IdAndMemoryType(Long userId, String memoryType);

    /**
     * Find a specific memory by user and key.
     *
     * @param userId
     *         The user ID
     * @param key
     *         The memory key
     * @return Optional containing the memory if found
     */
    Optional<LongTermMemory> findByUser_IdAndKey(Long userId, String key);

    /**
     * Find memories by user where tags contain any of the given keywords. Simple substring match for Phase 1 (no full-text search).
     *
     * @param userId
     *         The user ID
     * @param keyword1
     *         First keyword to search for
     * @param keyword2
     *         Second keyword to search for
     * @param keyword3
     *         Third keyword to search for
     * @return List of memories matching any keyword
     */
    @Query("SELECT m FROM LongTermMemory m WHERE m.user.id = :userId "
            + "AND (m.tags LIKE %:keyword1% OR m.tags LIKE %:keyword2% OR m.tags LIKE %:keyword3%)")
    List<LongTermMemory> findByUserIdAndTagsContaining(@Param("userId") Long userId, @Param("keyword1") String keyword1,
            @Param("keyword2") String keyword2, @Param("keyword3") String keyword3);

    /**
     * Find memories by user and key prefix (for related memories). Example: key="python%" matches "python_version", "python_framework",
     * etc.
     *
     * @param userId
     *         The user ID
     * @param keyPrefix
     *         The key prefix to match
     * @return List of memories with matching key prefix
     */
    List<LongTermMemory> findByUser_IdAndKeyStartingWith(Long userId, String keyPrefix);

    /**
     * Count total memories for a user.
     *
     * @param userId
     *         The user ID
     * @return Number of memories
     */
    long countByUser_Id(Long userId);

    /**
     * Delete a specific memory by user and key.
     *
     * @param userId
     *         The user ID
     * @param key
     *         The memory key
     */
    void deleteByUser_IdAndKey(Long userId, String key);

    /**
     * Find most recently accessed memories. Useful for "recent context" retrieval.
     *
     * @param userId
     *         The user ID
     * @return List of up to 10 most recently accessed memories
     */
    List<LongTermMemory> findTop10ByUser_IdOrderByLastAccessedAtDesc(Long userId);

    /**
     * Find most frequently accessed memories. Useful for identifying important memories.
     *
     * @param userId
     *         The user ID
     * @return List of up to 10 most frequently accessed memories
     */
    List<LongTermMemory> findTop10ByUser_IdOrderByAccessCountDesc(Long userId);
}
