package com.abhay.controller;

import com.abhay.approval.ApprovalRequest;
import com.abhay.approval.ApprovalStatus;
import com.abhay.approval.ApprovalRepository;
import com.abhay.entity.User;
import com.abhay.repository.UserRepository;
import com.abhay.service.ApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST API for managing approval requests. Endpoints: - GET /api/approvals?userId={userId} - List all pending approvals for user - POST
 * /api/approvals/{id}/approve - Approve a request - POST /api/approvals/{id}/reject - Reject a request Security: - User can only
 * approve/reject their own requests - Approval request ID and user ID must match - Status must be PENDING (cannot approve/reject twice) -
 * All data validated server-side
 */
@RestController
@RequestMapping("/api/approvals")
@CrossOrigin(origins = "*")
public class ApprovalController {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalController.class);

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApprovalService approvalService;

    /**
     * Get all pending approval requests for a user.
     *
     * @param userId
     *         User ID (required query parameter)
     * @return List of pending approval requests
     */
    @GetMapping
    public ResponseEntity<?> getPendingApprovals(@RequestParam Long userId) {
        try {
            if (userId == null) {
                return ResponseEntity.status(400).body(Map.of("error", "userId is required"));
            }

            User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

            List<ApprovalRequest> pendingApprovals = approvalRepository.findByUser_IdAndStatus(user.getId(), ApprovalStatus.PENDING);

            // Map to response DTOs (don't expose agentStateJson to frontend)
            List<Map<String, Object>> response = pendingApprovals.stream().map(approval -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", approval.getId());
                map.put("toolName", approval.getToolName());
                map.put("toolArguments", approval.getToolArguments());
                map.put("conversationId", approval.getConversationId());
                map.put("createdAt", approval.getCreatedAt().toString());
                map.put("status", approval.getStatus().toString());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get pending approvals: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to retrieve approvals"));
        }
    }

    /**
     * Approve an approval request and resume agent execution. Security checks: - User must own this approval request - Status must be
     * PENDING Returns SSE stream for agent execution updates.
     *
     * @param approvalId
     *         Approval request ID
     * @param userId
     *         User ID (required in request body)
     * @return SseEmitter for streaming agent execution
     */
    @PostMapping("/{id}/approve")
    public SseEmitter approveRequest(@PathVariable("id") String approvalId, @RequestBody Map<String, Long> body) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        try {
            Long userId = body.get("userId");
            if (userId == null) {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "userId is required")));
                emitter.completeWithError(new IllegalArgumentException("userId is required"));
                return emitter;
            }

            User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

            // Security: Verify user owns this approval request
            Optional<ApprovalRequest> optionalApproval = approvalRepository.findByIdAndUser_Id(approvalId, user.getId());

            if (optionalApproval.isEmpty()) {
                logger.warn("Approval request not found or access denied: {}", approvalId);
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "Approval request not found")));
                emitter.completeWithError(new RuntimeException("Approval request not found"));
                return emitter;
            }

            ApprovalRequest approval = optionalApproval.get();

            // Security: Check status is PENDING
            if (!approval.isPending()) {
                logger.warn("Approval request already processed: {} (status: {})", approvalId, approval.getStatus());
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "Approval already processed")));
                emitter.completeWithError(new IllegalStateException("Approval already processed"));
                return emitter;
            }

            // Update status to APPROVED
            approval.setStatus(ApprovalStatus.APPROVED);
            approvalRepository.save(approval);

            logger.info("Approval request approved: {}", approvalId);

            // Resume agent execution asynchronously
            new Thread(() -> {
                try {
                    approvalService.resumeAfterApproval(approvalId, emitter);
                } catch (Exception e) {
                    logger.error("Failed to resume agent after approval: {}", e.getMessage(), e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("error", e.getMessage())));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("Failed to send error event: {}", ex.getMessage());
                    }
                }
            }).start();

            return emitter;

        } catch (Exception e) {
            logger.error("Failed to approve request: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "Failed to approve request")));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("Failed to send error event: {}", ex.getMessage());
            }
            return emitter;
        }
    }

    /**
     * Reject an approval request. Security checks: - User must own this approval request - Status must be PENDING Returns SSE stream for
     * rejection message.
     *
     * @param approvalId
     *         Approval request ID
     * @param userId
     *         User ID (required in request body)
     * @return SseEmitter for streaming rejection message
     */
    @PostMapping("/{id}/reject")
    public SseEmitter rejectRequest(@PathVariable("id") String approvalId, @RequestBody Map<String, Long> body) {
        SseEmitter emitter = new SseEmitter(60_000L); // 1 minute timeout

        try {
            Long userId = body.get("userId");
            if (userId == null) {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "userId is required")));
                emitter.completeWithError(new IllegalArgumentException("userId is required"));
                return emitter;
            }

            User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

            // Security: Verify user owns this approval request
            Optional<ApprovalRequest> optionalApproval = approvalRepository.findByIdAndUser_Id(approvalId, user.getId());

            if (optionalApproval.isEmpty()) {
                logger.warn("Approval request not found or access denied: {}", approvalId);
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "Approval request not found")));
                emitter.completeWithError(new RuntimeException("Approval request not found"));
                return emitter;
            }

            ApprovalRequest approval = optionalApproval.get();

            // Security: Check status is PENDING
            if (!approval.isPending()) {
                logger.warn("Approval request already processed: {} (status: {})", approvalId, approval.getStatus());
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "Approval already processed")));
                emitter.completeWithError(new IllegalStateException("Approval already processed"));
                return emitter;
            }

            // Update status to REJECTED
            approval.setStatus(ApprovalStatus.REJECTED);
            approvalRepository.save(approval);

            logger.info("Approval request rejected: {}", approvalId);

            // Handle rejection asynchronously
            new Thread(() -> {
                try {
                    approvalService.handleRejection(approvalId, emitter);
                } catch (Exception e) {
                    logger.error("Failed to handle rejection: {}", e.getMessage(), e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("error", e.getMessage())));
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        logger.error("Failed to send error event: {}", ex.getMessage());
                    }
                }
            }).start();

            return emitter;

        } catch (Exception e) {
            logger.error("Failed to reject request: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("error", "Failed to reject request")));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                logger.error("Failed to send error event: {}", ex.getMessage());
            }
            return emitter;
        }
    }
}
