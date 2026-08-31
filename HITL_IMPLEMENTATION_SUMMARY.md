# Human-in-the-Loop (HITL) Implementation Summary

## Overview
Successfully implemented Human-in-the-Loop approval system for NexaChat agent. The agent can now pause before executing destructive actions and wait for human approval.

## What Was Implemented

### 1. Tool Approval Metadata (`Tool.java`)
- Added `requiresApproval()` method to Tool interface
- Default: `false` (safe by default)
- Tools that modify data override to return `true`

### 2. Delete Conversation Tool (`DeleteConversationTool.java`)
- Implements the example destructive action
- Requires approval (`requiresApproval() = true`)
- Uses existing ConversationRepository to delete conversations
- Returns success confirmation with conversation details

### 3. Approval Request Entity (`ApprovalRequest.java`)
- Stores approval requests in database
- Fields:
  - `id` (UUID) - unique identifier
  - `user` - owner of the approval
  - `conversationId` - context
  - `toolName` - immutable tool name
  - `toolArguments` - immutable arguments (JSON)
  - `status` - PENDING/APPROVED/REJECTED
  - `agentStateJson` - serialized agent state for resumption
  - `createdAt`, `updatedAt` - timestamps

### 4. Approval Status Enum (`ApprovalStatus.java`)
- `PENDING` - waiting for user decision
- `APPROVED` - user approved, tool will execute
- `REJECTED` - user rejected, tool will NOT execute

### 5. Approval Repository (`ApprovalRepository.java`)
- JPA repository for ApprovalRequest
- Methods:
  - `findByUser_Id()` - all approvals for user
  - `findByUser_IdAndStatus()` - filter by status
  - `findByIdAndUser_Id()` - security check

### 6. Agent Runtime Integration (`AgentRuntime.java`)
- Modified `executeAgentLoop()` to accept User and conversationId
- Added approval check between DECIDE and ACT phases
- Flow:
  1. LLM decides to use a tool
  2. Check if `tool.requiresApproval()` is true
  3. If yes: Create ApprovalRequest, save to DB, send SSE event, return `AgentResult.pendingApproval()`
  4. If no: Execute tool normally
- Agent state is serialized to JSON for resumption

### 7. Agent Result Extensions (`AgentResult.java`)
- Added `pendingApproval` boolean flag
- Added `approvalId` string field
- Factory method: `pendingApproval(AgentState, int iterations, String approvalId)`

### 8. Approval REST APIs (`ApprovalController.java`)
- **GET /api/approvals?userId={userId}**
  - Lists pending approvals for user
  - Returns: id, toolName, toolArguments, conversationId, createdAt, status
  - Does NOT expose agentStateJson (security)

- **POST /api/approvals/{id}/approve**
  - Approves a request
  - Security: Verifies user owns the approval
  - Security: Status must be PENDING
  - Returns: SSE stream for agent execution
  - Triggers agent resumption

- **POST /api/approvals/{id}/reject**
  - Rejects a request
  - Security: Verifies user owns the approval
  - Security: Status must be PENDING
  - Returns: SSE stream with rejection message
  - Does NOT execute tool

### 9. Approval Service (`ApprovalService.java`)
- **`resumeAfterApproval(approvalId, emitter)`**
  - Loads ApprovalRequest from database
  - Deserializes AgentState
  - Executes approved tool
  - Adds tool result to state
  - Generates final response (simplified for now)
  - Saves assistant message
  - Streams result to frontend

- **`handleRejection(approvalId, emitter)`**
  - Generates polite rejection message
  - Saves assistant message
  - Streams to frontend
  - Does NOT execute tool

### 10. Conversation Service Integration (`ConversationService.java`)
- Modified `handleAgentRuntimeFlow()` to pass User and conversationId
- Added check for `result.isPendingApproval()`
- If pending: Don't save message or complete emitter (approval flow handles it)
- If success: Continue normal flow

## Security Features

1. **Immutable Tool Data**: Frontend cannot modify toolName or toolArguments in approval request
2. **User Ownership**: User can only approve/reject their own requests
3. **Status Validation**: Approval can only be used once (PENDING → APPROVED/REJECTED)
4. **Server-Side Verification**: All checks happen server-side, never trust frontend
5. **Single-Use Approval**: Once approved/rejected, status cannot change

## Database Schema

```sql
CREATE TABLE approval_requests (
    id VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    tool_arguments TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    agent_state_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## SSE Events

### Agent Pauses for Approval
```javascript
event: approval_required
data: {
  "approvalId": "uuid-here",
  "toolName": "delete_conversation",
  "toolArguments": "{\"conversationId\": \"123\"}",
  "conversationId": 123
}
```

### After Approval Granted
```javascript
event: tool_execution_approved
data: {"tool": "delete_conversation"}

event: tool_execution_complete
data: {"tool": "delete_conversation", "result": "{...}"}

event: chunk
data: "Action completed successfully..."

event: done
data: {"message": "..."}
```

### After Rejection
```javascript
event: chunk
data: "I understand. I won't execute the 'delete_conversation' action..."

event: done
data: {"message": "..."}
```

## Testing the Implementation

### 1. Start Application
```bash
mvn spring-boot:run
```

### 2. Create a Conversation
```bash
curl -X POST http://localhost:8081/api/conversations \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "title": "Test Conversation"}'
```

### 3. Ask Agent to Delete
```bash
curl -X POST http://localhost:8081/api/conversations/1/messages/stream \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "content": "Delete conversation 2"}'
```

Expected: Agent pauses, sends `approval_required` event

### 4. List Pending Approvals
```bash
curl http://localhost:8081/api/approvals?userId=1
```

### 5. Approve
```bash
curl -X POST http://localhost:8081/api/approvals/{approvalId}/approve \
  -H "Content-Type: application/json" \
  -d '{"userId": 1}'
```

Expected: Tool executes, conversation deleted

### 6. Or Reject
```bash
curl -X POST http://localhost:8081/api/approvals/{approvalId}/reject \
  -H "Content-Type: application/json" \
  -d '{"userId": 1}'
```

Expected: Tool does NOT execute, polite rejection message

## What's Left (Frontend)

### Task #39: React Approval UI Component
- Listen for `approval_required` SSE event
- Display modal/dialog with:
  - Tool name (human-readable)
  - What it will do (e.g., "Delete conversation 'My Chat'")
  - Approve button (green)
  - Reject button (red)
- On approve: POST to `/api/approvals/{id}/approve`
- On reject: POST to `/api/approvals/{id}/reject`
- Listen for response stream (SSE)

### Example React Component Structure
```javascript
// In ChatWindow.jsx, listen for SSE events:
eventSource.addEventListener('approval_required', (event) => {
  const data = JSON.parse(event.data);
  setApprovalRequest(data); // Show modal
});

// ApprovalModal.jsx
<Modal>
  <h3>Action Requires Approval</h3>
  <p>The agent wants to: {toolName}</p>
  <p>{description}</p>
  <Button onClick={approve}>Approve</Button>
  <Button onClick={reject}>Reject</Button>
</Modal>

// On approve, connect to new SSE stream:
const response = await fetch(`/api/approvals/${id}/approve`, {
  method: 'POST',
  body: JSON.stringify({userId})
});
// Handle SSE events from response
```

## Architecture Diagram

```
User Request
    ↓
ConversationService
    ↓
AgentRuntime.executeAgentLoop()
    ↓
DECIDE (LLM chooses tool)
    ↓
CHECK APPROVAL (tool.requiresApproval()?)
    ↓
  YES                           NO
    ↓                            ↓
CREATE ApprovalRequest     EXECUTE TOOL
    ↓                            ↓
SAVE to DB               OBSERVE result
    ↓                            ↓
SEND SSE event           Continue loop
    ↓
PAUSE (return pendingApproval)
    ↓
[USER DECISION]
    ↓
APPROVE or REJECT
    ↓
ApprovalController
    ↓
ApprovalService.resumeAfterApproval()
    ↓
EXECUTE TOOL
    ↓
GENERATE RESPONSE
    ↓
SAVE & STREAM
```

## Key Design Decisions

1. **Pause at Runtime**: Agent pauses during execution, not before
2. **State Serialization**: AgentState serialized as JSON for flexibility
3. **Single-Use Approval**: Prevents replay attacks
4. **SSE for Approval**: Uses same streaming mechanism as chat
5. **Security First**: All validation server-side, immutable tool data
6. **Simple Example**: DELETE as the example destructive action

## Future Enhancements

1. **Full Agent Loop Resumption**: Currently generates simple response, could resume full agent loop
2. **Approval Expiration**: Auto-reject after X minutes
3. **Approval Notifications**: Email/push when approval needed
4. **Approval History**: Track all approvals for audit
5. **Batch Approvals**: Approve multiple actions at once
6. **Conditional Approvals**: "Always allow this action for this user"
7. **Approval Chains**: Multiple approvers for critical actions

## Files Modified/Created

### Created
- `src/main/java/com/abhay/approval/ApprovalStatus.java`
- `src/main/java/com/abhay/approval/ApprovalRequest.java`
- `src/main/java/com/abhay/approval/ApprovalRepository.java`
- `src/main/java/com/abhay/tool/impl/DeleteConversationTool.java`
- `src/main/java/com/abhay/controller/ApprovalController.java`
- `src/main/java/com/abhay/service/ApprovalService.java`

### Modified
- `src/main/java/com/abhay/tool/Tool.java` - Added `requiresApproval()`
- `src/main/java/com/abhay/agent/AgentResult.java` - Added pendingApproval support
- `src/main/java/com/abhay/agent/AgentRuntime.java` - Added approval checking
- `src/main/java/com/abhay/service/ConversationService.java` - Added pending approval handling

## Compilation Status
✅ All files compile successfully
✅ Application starts successfully
✅ Database schema created
✅ All 4 tools registered (calculator, get_current_time, get_conversation_stats, delete_conversation)
✅ Ready for frontend integration

## Next Steps
1. Implement React approval UI component (Task #39)
2. Test end-to-end flow (Task #40)
3. Deploy and monitor in production
