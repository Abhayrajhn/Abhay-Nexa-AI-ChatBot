package com.abhay.repository;

import com.abhay.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Conversation entity.
 *
 * By extending JpaRepository, Spring Data JPA automatically provides:
 * - save(entity) - Insert or update
 * - findById(id) - Find by primary key
 * - findAll() - Get all records
 * - deleteById(id) - Delete by primary key
 * - count() - Count total records
 * - existsById(id) - Check if exists
 *
 * JpaRepository<Conversation, Long>:
 * - Conversation: The entity type
 * - Long: The type of the primary key (id field)
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Spring Data JPA provides all basic CRUD operations automatically
    // No implementation needed!

    // You can add custom query methods here if needed
    // Examples (not needed for Phase 2, but good to know):
    // List<Conversation> findByTitleContaining(String keyword);
    // List<Conversation> findByCreatedAtAfter(LocalDateTime date);
    // List<Conversation> findTop10ByOrderByCreatedAtDesc();
}
