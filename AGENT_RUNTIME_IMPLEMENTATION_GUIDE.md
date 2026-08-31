# Agent Runtime Loop Implementation Guide

## Overview

This document explains the **Agent Runtime Loop** implementation in NexaChat - a dynamic decision-making system that enables the AI to decide, act, observe, and decide again based on results.

---

## What is the Agent Runtime Loop?

The Agent Runtime Loop is a system where the AI:

1. **DECIDE**: Looks at the current state and decides what to do next
2. **ACT**: Executes a tool if needed
3. **OBSERVE**: Records the result
4. **DECIDE**: Makes the next decision based on observations
5. **Repeat**: Until the task is complete

---

## Key Difference: Planning vs Agent Runtime

### Planning (Existing)
```
User: "Check weather in Bangalore. If raining, find umbrella."

Planner generates ALL steps upfront:
  Step 1: Call weather("Bangalore")
  Step 2: Call search("umbrella")
  Step 3: Done

Problem: Step 2 always executes even if NOT raining.
The plan is FIXED before execution.
```

### Agent Runtime (New)
```
User: "Check weather in Bangalore. If raining, find umbrella."

Iteration 1:
  DECIDE: "I need to check weather first"
  ACT: Call weather("Bangalore")
  OBSERVE: Result = "Sunny, 28°C"

Iteration 2:
  DECIDE: "It's sunny, not raining. No need to search for umbrella."
  FINAL_ANSWER: "Weather is sunny at 28°C. You won't need an umbrella today."

Key: Step 2 decision is made AFTER observing Step 1 result.
```

---

## Architecture

### Package Structure

```
com.abhay.agent/
├── AgentDecisionType.java      // Enum: CALL_TOOL, FINAL_ANSWER
├── AgentDecision.java           // Structured decision from LLM
├── AgentState.java              // Temporary execution state
├── AgentResult.java             // Final result of agent loop
├── AgentExecutionException.java // Custom exception
└── AgentRuntime.java            // Core orchestrator
```

### Core Classes

#### 1. AgentDecisionType
```java
public enum AgentDecisionType {
    CALL_TOOL,      // Agent needs to execute a tool
    FINAL_ANSWER    // Agent has enough information
}
```

#### 2. AgentDecision
```java
public class AgentDecision {
    private AgentDecisionType decisionType;
    private String toolName;        // Only for CALL_TOOL
    private String arguments;       // Only for CALL_TOOL
    private String reasoning;       // Why this decision
}
```

Example JSON from LLM:
```json
{
  "decision_type": "CALL_TOOL",
  "tool_name": "weather",
  "arguments": "{\"city\": \"Bangalore\"}",
  "reasoning": "Need to check current weather conditions"
}
```

#### 3. AgentState
```java
public class AgentState {
    private String userRequest;
    private int iteration;
    private List<ToolExecution> toolExecutions;
    private ExecutionStatus status;
    private LocalDateTime startTime;
}
```

This is **temporary working memory** - exists only during the current execution.

#### 4. AgentRuntime
The core orchestrator that implements the DECIDE → ACT → OBSERVE loop.

---

## Execution Flow

### Flow Diagram

```
User Request
    ↓
ConversationService.sendMessageStream()
    ↓
Retrieve: History + Long-Term Memories
    ↓
Decision Point: Which flow?
    ├─→ needsAgentLoop() → YES
    │       ↓
    │   handleAgentRuntimeFlow()
    │       ↓
    │   AgentRuntime.executeAgentLoop()
    │       ↓
    │   ┌─────────────────────────────┐
    │   │  Agent Loop (max 10 iter)  │
    │   │                             │
    │   │  Iteration 1:               │
    │   │    DECIDE → ACT → OBSERVE   │
    │   │                             │
    │   │  Iteration 2:               │
    │   │    DECIDE → ACT → OBSERVE   │
    │   │                             │
    │   │  Iteration N:               │
    │   │    DECIDE → FINAL_ANSWER    │
    │   └─────────────────────────────┘
    │       ↓
    │   Generate final natural language response
    │       ↓
    │   Stream to frontend
    │
    ├─→ needsPlanning() → YES
    │       ↓
    │   handlePlanningFlow()
    │
    └─→ Otherwise
            ↓
        handleToolCallingFlow()
```

### Detailed Agent Loop

```java
AgentState state = new AgentState(userRequest);

while (iteration < maxIterations) {
    iteration++;
    
    // ===== DECIDE =====
    // Build decision prompt with:
    // - User request
    // - Tool history
    // - Available tools
    // - Instructions
    
    AgentDecision decision = makeDecision(context, state);
    
    if (decision.isFinalAnswer()) {
        // Task complete!
        return AgentResult.success(state, iteration);
    }
    
    // ===== ACT =====
    String result = toolExecutor.executeTool(
        decision.getToolName(),
        decision.getArguments()
    );
    
    // ===== OBSERVE =====
    state.addToolExecution(decision, result);
    context.add(toolCallMessage);
    context.add(toolResultMessage);
    
    // Loop continues...
}

// Max iterations reached
return AgentResult.failure(state, maxIterations, "Max iterations reached");
```

---

## When to Use Each Flow?

### Use Agent Runtime When:
- Request has **conditional logic**: "if", "based on", "depending on"
- Example: "Check weather. If raining, find umbrella."
- Example: "Search for X. Based on results, do Y."

### Use Planning When:
- Request has **predetermined sequential steps**
- Example: "Convert 25°C to Fahrenheit then to Kelvin"
- Example: "Calculate X, then use it for Y"

### Use Tool Calling When:
- Simple request with **no dependencies**
- Example: "What is 25 * 40?"
- Example: "What time is it in Bangalore?"

---

## Configuration

### application.properties

```properties
# Agent Runtime Configuration
agent.max.iterations=10
```

This prevents infinite loops. The agent stops after 10 iterations if it hasn't reached FINAL_ANSWER.

---

## Integration with Existing Systems

### 1. Long-Term Memory
```java
// Memory is retrieved BEFORE agent loop
List<LongTermMemory> memories = memoryRetriever.retrieveRelevantMemories(userId, userRequest);
List<Message> context = buildLLMMessagesWithMemory(history, memories);

// Agent loop uses this context
agentRuntime.executeAgentLoop(userRequest, context, emitter);
```

### 2. Tool System
```java
// Agent Runtime REUSES existing ToolExecutor
// No duplication, same security validation
String result = toolExecutor.executeTool(toolName, arguments);
```

### 3. Streaming
```java
// Agent loop runs synchronously
// Final response streams to frontend (existing code)
String finalResponse = openAIClient.sendMessage(context);
emitter.send(event("chunk").data(finalResponse));
```

---

## SSE Events for Frontend

The agent runtime sends these Server-Sent Events:

| Event Name | Data | Description |
|------------|------|-------------|
| `agent_loop_start` | `{maxIterations: 10}` | Agent loop started |
| `agent_decision` | `{iteration: 1, decisionType: "CALL_TOOL", toolName: "weather"}` | LLM made a decision |
| `agent_tool_executed` | `{iteration: 1, tool: "weather", result: "..."}` | Tool executed |
| `agent_loop_complete` | `{iterations: 3, status: "completed"}` | Loop completed successfully |
| `agent_loop_max_iterations` | `{iterations: 10, message: "..."}` | Max iterations reached |
| `agent_loop_error` | `{iteration: 2, error: "..."}` | Error during execution |
| `chunk` | `"text chunk"` | Final response streaming |
| `done` | `{MessageResponse}` | Completion |

---

## Error Handling

### 1. Tool Errors
```java
// Tool returns error JSON
{"error": "Weather service unavailable"}

// Agent observes the error
// LLM decides what to do next:
// - Retry?
// - Use another tool?
// - Explain to user?
```

### 2. Max Iterations Reached
```java
// Agent couldn't complete in 10 iterations
// System returns safe fallback:
"I attempted to complete your request but encountered difficulties.
Here's what I tried: [tool history]
Could you please rephrase your request?"
```

### 3. LLM Parse Errors
```java
// LLM returns invalid JSON
// System throws AgentExecutionException
// ConversationService catches and returns error to user
```

---

## Example Execution Trace

### User Request
"Check the weather in Bangalore. If it is raining, search for an umbrella."

### Execution Log

```
==================== AGENT RUNTIME LOOP STARTED ====================
User request: Check the weather in Bangalore. If it is raining, search for an umbrella.
Max iterations: 10

---------- Agent Iteration 1 ----------
DECIDE: Asking LLM for next decision...
DECIDE: LLM decided → AgentDecision{type=CALL_TOOL, tool=weather, args={"city": "Bangalore"}}
ACT: Executing tool 'weather' with arguments: {"city": "Bangalore"}
ACT: Tool executed. Result: {"temperature": 24, "condition": "Rainy", "humidity": 85}
OBSERVE: Added tool result to agent state. Total tool calls: 1

---------- Agent Iteration 2 ----------
DECIDE: Asking LLM for next decision...
DECIDE: LLM decided → AgentDecision{type=CALL_TOOL, tool=search, args={"query": "umbrella"}}
ACT: Executing tool 'search' with arguments: {"query": "umbrella"}
ACT: Tool executed. Result: {"results": ["Compact umbrella - $15", "Golf umbrella - $25"]}
OBSERVE: Added tool result to agent state. Total tool calls: 2

---------- Agent Iteration 3 ----------
DECIDE: Asking LLM for next decision...
DECIDE: LLM decided → AgentDecision{type=FINAL_ANSWER}
DECIDE: Agent chose FINAL_ANSWER. Task complete.

==================== AGENT RUNTIME LOOP COMPLETED ====================

Generating final natural language response...
Final response generated, length: 156

Final Response:
"The weather in Bangalore is currently rainy at 24°C with 85% humidity. 
Since it's raining, I found some umbrella options for you:
- Compact umbrella for $15
- Golf umbrella for $25"
```

---

## Testing Scenarios

### Test 1: Simple Question (No Agent Loop)
```
User: "What is Java?"
Flow: Tool Calling (no tools needed, direct LLM response)
Expected: Normal response, no agent loop
```

### Test 2: Single Tool (No Agent Loop)
```
User: "What is 25 * 40?"
Flow: Tool Calling (calculator tool)
Expected: One tool call, direct response
```

### Test 3: Conditional Logic (Agent Loop Triggered)
```
User: "Check weather in Bangalore. If raining, find umbrella."
Flow: Agent Runtime
Expected:
  - Iteration 1: Weather tool → "Rainy"
  - Iteration 2: Search tool → "Umbrella results"
  - Iteration 3: FINAL_ANSWER
```

### Test 4: Tool Error
```
User: "Check weather in XYZ123" (invalid city)
Flow: Agent Runtime
Expected:
  - Iteration 1: Weather tool → {"error": "Invalid city"}
  - Iteration 2: LLM explains error to user
  - FINAL_ANSWER
```

### Test 5: Max Iterations
```
Create a scenario where agent keeps requesting more tools
Expected:
  - After 10 iterations: Loop stops
  - Fallback response with tool history
```

---

## Key Design Principles

1. **Reuse Existing Systems**: Agent Runtime uses existing ToolExecutor, ToolRegistry, Memory systems
2. **No Duplication**: One tool execution path (ToolExecutor)
3. **Security**: Only registered tools can be called
4. **Prevent Infinite Loops**: Max iterations limit
5. **Graceful Degradation**: Fallback responses on errors
6. **Observability**: Comprehensive logging + SSE events
7. **Keep Streaming**: Final response continues to stream

---

## Comparison: Three Execution Modes

| Feature | Tool Calling | Planning | Agent Runtime |
|---------|-------------|----------|---------------|
| Decision Making | One shot | Upfront | Iterative |
| Tool Dependency | Independent | Predetermined | Dynamic |
| Use Case | Simple | Sequential | Conditional |
| Example | "Calculate X" | "Convert X→Y→Z" | "If X then Y" |
| Iterations | 1 | 1 (multi-step) | N (up to 10) |

---

## Future Enhancements

1. **Stream Agent Reasoning**: Stream internal decisions to frontend
2. **Tool Result Caching**: Cache tool results within conversation
3. **Multi-Modal Agents**: Support image/file inputs
4. **Agent Collaboration**: Multiple agents working together
5. **Human-in-the-Loop**: Request user approval before tool execution
6. **Learning from Outcomes**: Store successful agent strategies

---

## Troubleshooting

### Agent Loop Never Reaches FINAL_ANSWER
- Check: Are tools returning proper results?
- Check: Is LLM understanding the task?
- Check: Max iterations too low?
- Solution: Review logs, increase max iterations

### Agent Calls Wrong Tool
- Check: Tool descriptions in ToolRegistry
- Check: Decision prompt clarity
- Solution: Improve tool descriptions

### Max Iterations Reached Too Quickly
- Check: Is the request too complex?
- Check: Are tools failing?
- Solution: Break request into smaller tasks

---

## Summary

The Agent Runtime Loop enables **dynamic decision-making** in NexaChat:

✅ DECIDE → ACT → OBSERVE → DECIDE cycle
✅ Conditional logic support ("if X then Y")
✅ Integration with existing tools and memory
✅ Iteration limits prevent infinite loops
✅ Comprehensive logging and SSE events
✅ Graceful error handling

This is the foundation for building true AI agents that can reason, act, and adapt based on observations.
