package com.abhay.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ApprovalRequest entities.
 * Provides database access for approval requests.
 */
@Repository
public interface ApprovalRepository extends JpaRepository<ApprovalRequest, String> {

    /**
     * Find all approval requests for a specific user.
     *
     * @param userId
     *         The user ID
     * @return List of approval requests
     */
    List<ApprovalRequest> findByUser_Id(Long userId);

    /**
     * Find all pending approval requests for a user.
     *
     * @param userId
     *         The user ID
     * @param status
     *         The status to filter by
     * @return List of pending approval requests
     */
    List<ApprovalRequest> findByUser_IdAndStatus(Long userId, ApprovalStatus status);

    /**
     * Find approval request by ID and user ID (for security).
     *
     * @param id
     *         The approval request ID
     * @param userId
     *         The user ID
     * @return Optional approval request
     */
    Optional<ApprovalRequest> findByIdAndUser_Id(String id, Long userId);
}
