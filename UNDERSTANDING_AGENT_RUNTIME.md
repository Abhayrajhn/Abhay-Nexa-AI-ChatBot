# Understanding the Agent Runtime Loop

## The Big Picture: Why This Matters

You asked to understand how AI agents work from scratch. The Agent Runtime Loop is **the core concept** that transforms a chatbot into an agent.

### Chatbot vs Agent

**Chatbot:**
```
User: "What's 25 * 40?"
LLM: "It's 1000"
```

**Agent:**
```
User: "What's 25 * 40?"
Agent: "I need a calculator for this"
  → Calls calculator tool
  → Gets result: 1000
  → "The answer is 1000"
```

### Planning vs Agent Runtime

**Planning (What you had before):**
```
User: "Check weather in Bangalore. If raining, find umbrella."

Planner creates plan BEFORE execution:
  Step 1: weather("Bangalore")
  Step 2: search("umbrella")
  Step 3: Done

Problem: Step 2 ALWAYS executes, even if sunny!
```

**Agent Runtime (What we just built):**
```
User: "Check weather in Bangalore. If raining, find umbrella."

Iteration 1:
  Agent: "I should check weather first"
  Tool: weather("Bangalore")
  Result: "Sunny, 28°C"

Iteration 2:
  Agent: "It's sunny. No need for umbrella."
  Decision: FINAL_ANSWER

Response: "Weather is sunny at 28°C. You won't need an umbrella!"

Key Difference: Step 2 decision made AFTER seeing Step 1 result.
```

---

## The Core Concepts

### 1. Agent

An **agent** is a system that can:
- **Perceive** its environment (through observations)
- **Decide** what action to take
- **Act** (execute tools)
- **Learn** from results (observe outcomes)
- **Adapt** behavior based on new information

In NexaChat, the agent is implemented in `AgentRuntime.java`.

### 2. Agent State

**AgentState** is the agent's "working memory" during execution.

```java
AgentState {
    userRequest: "Original user request"
    iteration: 2
    toolExecutions: [
        {tool: "weather", result: "Rainy"},
        {tool: "search", result: "Umbrella options"}
    ]
    status: RUNNING
}
```

This is **NOT** long-term memory. It exists only during the current task.

Think of it like a scratchpad:
- Long-term memory = Your brain's permanent storage
- Agent state = A sticky note you're working with right now

### 3. Decision

At each iteration, the agent makes a **decision**:

```java
AgentDecision {
    decisionType: CALL_TOOL or FINAL_ANSWER
    toolName: "weather" (if CALL_TOOL)
    arguments: "{\"city\": \"Bangalore\"}" (if CALL_TOOL)
    reasoning: "I need to check weather first"
}
```

This is **structured output** from the LLM - not natural language.

### 4. Action

When the decision is `CALL_TOOL`, the agent **acts**:

```java
String result = toolExecutor.executeTool(
    decision.getToolName(),
    decision.getArguments()
);
```

This executes the tool through your existing `ToolExecutor` (no duplication!).

### 5. Observation

After acting, the agent **observes** the result:

```java
state.addToolExecution(toolName, arguments, result);
```

This observation becomes part of the context for the next decision.

### 6. Tool

A **tool** is an external capability the agent can use:

- `calculator` - Perform calculations
- `get_current_time` - Get time in timezone
- `get_conversation_stats` - Query database
- `weather` (hypothetical) - Check weather
- `search` (hypothetical) - Search information

Tools are registered in `ToolRegistry` and executed via `ToolExecutor`.

### 7. Tool Result

The output from a tool execution:

```json
{
  "temperature": 24,
  "condition": "Rainy",
  "city": "Bangalore"
}
```

This is what the agent observes and uses to make the next decision.

---

## The DECIDE → ACT → OBSERVE Loop

This is the heart of the agent system.

### Pseudocode

```
state = initialize(userRequest)
iteration = 0

while (iteration < MAX_ITERATIONS):
    iteration++
    
    // ===== DECIDE =====
    decision = ask_llm_what_to_do_next(state, context)
    
    if (decision == FINAL_ANSWER):
        break  // Task complete!
    
    // ===== ACT =====
    result = execute_tool(decision.toolName, decision.arguments)
    
    // ===== OBSERVE =====
    state.add_observation(result)
    context.add_tool_call_and_result(decision, result)
    
    // Loop continues with updated context...

// Generate final natural language response
response = ask_llm_for_final_answer(context)
return response
```

### Real Java Implementation

```java
// From AgentRuntime.java

AgentState state = new AgentState(userRequest);

while (state.getIteration() < maxIterations) {
    state.incrementIteration();
    
    // ===== DECIDE =====
    AgentDecision decision = makeDecision(context, state);
    
    if (decision.isFinalAnswer()) {
        state.setStatus(ExecutionStatus.COMPLETED);
        return AgentResult.success(state, iteration);
    }
    
    // ===== ACT =====
    String toolResult = toolExecutor.executeTool(
        decision.getToolName(),
        decision.getArguments()
    );
    
    // ===== OBSERVE =====
    state.addToolExecution(decision.getToolName(), 
                          decision.getArguments(), 
                          toolResult);
    
    addToolCallToContext(context, decision, toolResult);
}
```

---

## Memory Types in NexaChat

You now have THREE types of memory:

### 1. Conversation Memory (Message Table)

**What:** All messages in database
**Lifetime:** Permanent (until conversation deleted)
**Purpose:** Full chat history
**Storage:** PostgreSQL `messages` table

```sql
SELECT * FROM messages WHERE conversation_id = 1;
-- id | conversation_id | role | content | created_at
-- 1  | 1              | USER | "Hello" | 2026-08-31...
-- 2  | 1              | ASSISTANT | "Hi!" | 2026-08-31...
```

### 2. Long-Term Memory (LongTermMemory Table)

**What:** Facts, preferences, skills, context about the user
**Lifetime:** Permanent (cross-conversation)
**Purpose:** Remember user across conversations
**Storage:** PostgreSQL `long_term_memories` table

```sql
SELECT * FROM long_term_memories WHERE user_id = 1;
-- key: "programming_language" | value: "Java" | type: "fact"
-- key: "location" | value: "Bangalore" | type: "context"
```

### 3. Agent State (Working Memory)

**What:** Current task execution state
**Lifetime:** Current agent loop only (ephemeral)
**Purpose:** Track tool calls during one execution
**Storage:** In-memory only (not persisted)

```java
AgentState {
    userRequest: "Check weather..."
    iteration: 2
    toolExecutions: [...]
    status: RUNNING
}
```

---

## Comparison: All Three Execution Modes

You now have three ways to handle requests:

### 1. Tool Calling (Simple)

**When:** Simple requests, no dependencies
**Flow:** User → LLM → Tool (optional) → Response
**Example:** "What is 25 * 40?"

```
Iteration 1:
  LLM: "I'll use calculator"
  Tool: calculator("25 * 40") → 1000
  Response: "The answer is 1000"
```

### 2. Planning (Sequential)

**When:** Multi-step with known sequence
**Flow:** User → Generate Plan → Execute All Steps → Response
**Example:** "Convert 25°C to Fahrenheit then to Kelvin"

```
Plan Generation:
  Step 1: calculator("25 * 9/5 + 32") → 77°F
  Step 2: calculator("(77 - 32) * 5/9 + 273.15") → 298.15K

Plan Execution:
  Execute step 1 → 77
  Execute step 2 → 298.15
  
Response: "25°C = 77°F = 298.15K"
```

### 3. Agent Runtime (Conditional)

**When:** Conditional logic, dynamic decisions
**Flow:** User → Decide → Act → Observe → Decide → ... → Response
**Example:** "Check weather. If raining, find umbrella."

```
Iteration 1:
  DECIDE: "Check weather"
  ACT: weather("Bangalore")
  OBSERVE: "Sunny"

Iteration 2:
  DECIDE: "No umbrella needed"
  FINAL_ANSWER

Response: "Weather is sunny. No umbrella needed."
```

---

## The Complete Flow: Step by Step

Let's trace a complete request through the system:

### Request: "Check weather in Bangalore. If raining, find umbrella."

#### Step 1: Request Arrives

```http
POST /api/conversations/1/messages
{
  "content": "Check weather in Bangalore. If raining, find umbrella.",
  "userId": 1
}
```

#### Step 2: ConversationService Processes

```java
// Save user message
Message userMessage = new Message(Role.USER, content);
messageRepository.save(userMessage);

// Retrieve history
List<Message> history = messageRepository.findByConversation_Id(1);
```

#### Step 3: Long-Term Memory Retrieval

```java
List<LongTermMemory> memories = memoryRetriever.retrieveRelevantMemories(
    userId: 1,
    query: "Check weather in Bangalore...",
    limit: 10
);

// Returns:
// [{key: "location", value: "Bangalore", type: "context"}]
```

#### Step 4: Build LLM Context

```java
List<LLM.Message> llmMessages = [
    {role: "system", content: "You are a helpful AI assistant."},
    {role: "system", content: "What you know: location=Bangalore"},
    {role: "user", content: "Previous message..."},
    {role: "assistant", content: "Previous response..."},
    {role: "user", content: "Check weather in Bangalore..."}
];
```

#### Step 5: Flow Decision

```java
if (agentRuntime.needsAgentLoop(content)) {
    // ✓ TRUE (contains "if")
    handleAgentRuntimeFlow(...);
}
```

#### Step 6: Agent Loop Iteration 1

**DECIDE:**
```java
decision = makeDecision(context, state);
// Returns: AgentDecision{type=CALL_TOOL, tool="weather", args="{'city':'Bangalore'}"}
```

**ACT:**
```java
result = toolExecutor.executeTool("weather", "{'city':'Bangalore'}");
// Returns: "{'temperature': 24, 'condition': 'Rainy'}"
```

**OBSERVE:**
```java
state.addToolExecution("weather", args, result);
context.add(toolCallMessage);
context.add(toolResultMessage);
```

#### Step 7: Agent Loop Iteration 2

**DECIDE:**
```java
decision = makeDecision(context, state);
// LLM sees: "Weather is Rainy"
// Returns: AgentDecision{type=CALL_TOOL, tool="search", args="{'query':'umbrella'}"}
```

**ACT:**
```java
result = toolExecutor.executeTool("search", "{'query':'umbrella'}");
// Returns: "{'results': ['Compact umbrella $15', 'Golf umbrella $25']}"
```

**OBSERVE:**
```java
state.addToolExecution("search", args, result);
context.add(toolCallMessage);
context.add(toolResultMessage);
```

#### Step 8: Agent Loop Iteration 3

**DECIDE:**
```java
decision = makeDecision(context, state);
// LLM sees: All tool results
// Returns: AgentDecision{type=FINAL_ANSWER}
```

**Loop Exits:**
```java
return AgentResult.success(state, iterations=3);
```

#### Step 9: Generate Final Response

```java
// Add summary to context
String summary = "Agent execution completed. Tool calls: weather→Rainy, search→umbrellas";
context.add(new Message("system", summary));

// Get final natural language response
String finalResponse = openAIClient.sendMessage(context);
// Returns: "Weather in Bangalore is rainy at 24°C. Here are umbrella options:..."
```

#### Step 10: Stream to Frontend

```java
emitter.send(SseEmitter.event().name("chunk").data(finalResponse));
emitter.send(SseEmitter.event().name("done").data(messageResponse));
emitter.complete();
```

#### Step 11: Save to Database

```java
Message assistantMessage = new Message(Role.ASSISTANT, finalResponse);
messageRepository.save(assistantMessage);
```

#### Step 12: Memory Extraction (Async)

```java
CompletableFuture.runAsync(() -> {
    List<LongTermMemory> newMemories = memoryExtractor.extract(
        userMessage,
        assistantResponse,
        user
    );
    longTermMemoryRepository.saveAll(newMemories);
});
```

---

## Why This is Called an "Agent"

The key characteristics that make this an **agent**:

### 1. Autonomy
The agent decides what to do next without explicit instructions.

### 2. Reactivity
The agent responds to observations from the environment (tool results).

### 3. Pro-activeness
The agent takes initiative to achieve goals (calls tools when needed).

### 4. Social Ability
The agent interacts with external systems (tools, APIs, databases).

### 5. Adaptability
The agent changes behavior based on new information.

---

## What You've Learned

By implementing this, you now understand:

1. **DECIDE → ACT → OBSERVE** is the core agent loop
2. **Agent State** is temporary working memory
3. **Decisions** are structured output from LLM
4. **Tools** are external capabilities
5. **Observations** drive next decisions
6. **Planning** vs **Agent Runtime** differences
7. **Three types of memory** in an agent system
8. **Why iteration limits** prevent infinite loops
9. **How conditional logic** requires agent runtime
10. **The foundation** for autonomous AI systems

---

## Next Steps in Your Learning Journey

Now that you have the Agent Runtime Loop, you can build:

1. **RAG (Retrieval-Augmented Generation)**
   - Agent decides when to retrieve documents
   - Observes retrieval results
   - Decides whether to retrieve more or answer

2. **Multi-Agent Systems**
   - Multiple agents collaborating
   - One agent's output becomes another's input
   - Coordinated decision-making

3. **Self-Improving Agents**
   - Agent learns from successes/failures
   - Stores strategies in long-term memory
   - Improves over time

4. **Human-in-the-Loop Agents**
   - Agent requests human approval
   - Human provides feedback
   - Agent adapts based on feedback

5. **Autonomous Task Completion**
   - Complex multi-step tasks
   - Error recovery
   - Goal-driven behavior

---

## The Philosophy Behind Agents

Traditional programming:
```
if (condition) {
    action1();
} else {
    action2();
}
```

Agent programming:
```
decision = decide_what_to_do_based_on_state();
result = execute(decision);
state = observe(result);
decide_again_with_new_state();
```

**The shift:** From predetermined logic to adaptive decision-making.

This is why agents are powerful - they can handle situations not explicitly programmed.

---

## Congratulations! 🎉

You've successfully implemented an Agent Runtime Loop from scratch.

You now have:
- ✅ Tool calling
- ✅ Planning
- ✅ Agent runtime loop
- ✅ Long-term memory
- ✅ Conversation memory
- ✅ Streaming responses
- ✅ PostgreSQL persistence

Your NexaChat is now a **true AI agent system**, capable of dynamic decision-making and adaptive behavior.

This is the foundation that powers systems like:
- AutoGPT
- BabyAGI  
- LangChain Agents
- Crew AI
- OpenAI Assistants

But you built it yourself, so you **understand how it works**.

That's the real power of a learning project! 🚀
