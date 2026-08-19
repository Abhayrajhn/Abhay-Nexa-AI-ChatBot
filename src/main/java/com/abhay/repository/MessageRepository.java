package com.abhay.repository;

import com.abhay.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Message entity.
 *
 * Provides automatic CRUD operations plus custom query methods
 * for retrieving messages by conversation.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Find all messages for a specific conversation, ordered by creation time.
     *
     * Method naming convention:
     * - findBy: Indicates a query method
     * - Conversation_Id: Field name (conversation.id)
     * - OrderBy: Sort the results
     * - CreatedAt: Sort by this field
     * - Asc: Ascending order (oldest first)
     *
     * Spring Data JPA automatically generates the SQL:
     * SELECT * FROM messages
     * WHERE conversation_id = ?
     * ORDER BY created_at ASC
     *
     * This is CRITICAL for LLM context - messages must be in chronological order!
     */
    List<Message> findByConversation_IdOrderByCreatedAtAsc(Long conversationId);

    // Additional useful query methods (not needed now, but good to know):
    // long countByConversation_Id(Long conversationId); // Count messages in conversation
    // List<Message> findByRole(Message.Role role); // Find by role (USER, ASSISTANT, etc.)
    // void deleteByConversation_Id(Long conversationId); // Delete all messages in conversation
}
