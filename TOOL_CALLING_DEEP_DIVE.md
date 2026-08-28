# Tool Calling - Complete Deep Dive

## 🎯 Table of Contents

1. [High-Level Overview](#high-level-overview)
2. [How AI Decides Which Tool to Use](#how-ai-decides-which-tool-to-use)
3. [Complete Request Flow](#complete-request-flow)
4. [Code Walkthrough - Step by Step](#code-walkthrough-step-by-step)
5. [JSON Payloads at Each Step](#json-payloads-at-each-step)
6. [Streaming with Tool Calls](#streaming-with-tool-calls)
7. [Multi-Turn Conversation Flow](#multi-turn-conversation-flow)

---

## 🔍 High-Level Overview

### The Journey of: "What is 25 * 40?"

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. USER TYPES MESSAGE                                           │
│    "What is 25 * 40?"                                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. SPRING BOOT RECEIVES REQUEST                                │
│    POST /api/conversations/31/messages/stream                  │
│    Body: {"content": "What is 25 * 40?"}                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. CONVERSATIONSERVICE PREPARES REQUEST                        │
│    - Saves user message to database                            │
│    - Loads conversation history                                │
│    - Gets tool definitions from ToolRegistry                   │
│    - Builds request for OpenAI                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. SENDS TO OPENAI WITH TOOLS                                  │
│    POST https://api.openai.com/v1/chat/completions            │
│    {                                                            │
│      "model": "gpt-4o-mini",                                  │
│      "messages": [                                             │
│        {"role": "system", "content": "You are Nexa AI"},      │
│        {"role": "user", "content": "What is 25 * 40?"}       │
│      ],                                                         │
│      "tools": [                                                │
│        {                                                       │
│          "type": "function",                                  │
│          "function": {                                        │
│            "name": "calculator",                             │
│            "description": "Performs math calculations...",   │
│            "parameters": {                                    │
│              "type": "object",                               │
│              "properties": {                                 │
│                "expression": {                               │
│                  "type": "string",                           │
│                  "description": "Math expression to eval"    │
│                }                                             │
│              },                                               │
│              "required": ["expression"]                       │
│            }                                                  │
│          }                                                    │
│        },                                                      │
│        ... (time tool, stats tool)                           │
│      ],                                                         │
│      "stream": true                                           │
│    }                                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 5. OPENAI AI BRAIN PROCESSES REQUEST                           │
│                                                                 │
│    AI Reasoning (internal to OpenAI):                         │
│    ┌─────────────────────────────────────────────┐          │
│    │ Input: "What is 25 * 40?"                  │          │
│    │                                              │          │
│    │ AI thinks:                                   │          │
│    │ - This is a math question                   │          │
│    │ - I could guess, but might be wrong         │          │
│    │ - I have a "calculator" tool available      │          │
│    │ - The tool description says it can help    │          │
│    │ - I should use it!                          │          │
│    │                                              │          │
│    │ Decision: CALL calculator("25 * 40")       │          │
│    └─────────────────────────────────────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 6. OPENAI STREAMS BACK TOOL CALL (NOT TEXT!)                  │
│    Stream of SSE events:                                       │
│                                                                 │
│    event: chunk                                                │
│    data: {"choices":[{"delta":{"tool_calls":[{               │
│           "index":0,                                           │
│           "id":"call_abc123",                                 │
│           "type":"function",                                   │
│           "function":{"name":"calculator","arguments":""}     │
│         }]}}]}                                                 │
│                                                                 │
│    event: chunk                                                │
│    data: {"choices":[{"delta":{"tool_calls":[{               │
│           "index":0,                                           │
│           "function":{"arguments":"{\""}                      │
│         }]}}]}                                                 │
│                                                                 │
│    event: chunk                                                │
│    data: {"choices":[{"delta":{"tool_calls":[{               │
│           "index":0,                                           │
│           "function":{"arguments":"expression"}               │
│         }]}}]}                                                 │
│                                                                 │
│    ... (many more chunks building up JSON)                    │
│                                                                 │
│    event: chunk                                                │
│    data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}│
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 7. OPENAICLIENT ACCUMULATES FRAGMENTS                         │
│    ToolCallAccumulator merges all fragments:                  │
│    {                                                            │
│      "id": "call_abc123",                                     │
│      "type": "function",                                       │
│      "function": {                                             │
│        "name": "calculator",                                  │
│        "arguments": "{\"expression\":\"25 * 40\"}"           │
│      }                                                          │
│    }                                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 8. DETECTS finish_reason="tool_calls"                         │
│    OpenAIClient calls: onToolCalls(completedToolCalls)        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 9. CONVERSATIONSERVICE HANDLES TOOL EXECUTION                  │
│    CompletableFuture.runAsync(() -> {                         │
│      handleToolExecution(...)                                 │
│    })                                                           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 10. TOOLEXECUTOR EXECUTES THE TOOL                            │
│     String toolName = "calculator"                            │
│     String arguments = "{\"expression\":\"25 * 40\"}"         │
│                                                                 │
│     Tool tool = toolRegistry.getTool("calculator")            │
│     String result = tool.execute(arguments)                   │
│                                                                 │
│     CalculatorTool.execute():                                 │
│     - Parses: {"expression": "25 * 40"}                      │
│     - Evaluates: 25 * 40 = 1000                              │
│     - Returns: {"result": 1000, "expression": "25 * 40"}     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 11. BUILD CONVERSATION HISTORY WITH TOOL RESULT                │
│     messages = [                                               │
│       {"role": "system", "content": "You are Nexa AI"},       │
│       {"role": "user", "content": "What is 25 * 40?"},       │
│       {"role": "assistant", "tool_calls": [{                  │
│         "id": "call_abc123",                                  │
│         "function": {                                          │
│           "name": "calculator",                               │
│           "arguments": "{\"expression\":\"25 * 40\"}"        │
│         }                                                       │
│       }]},                                                      │
│       {"role": "tool", "tool_call_id": "call_abc123",        │
│        "content": "{\"result\":1000,\"expression\":\"25*40\"}"}│
│     ]                                                           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 12. SEND BACK TO OPENAI (SECOND REQUEST!)                     │
│     POST https://api.openai.com/v1/chat/completions           │
│     Same request as before, but now with tool result added    │
│     stream: false (non-streaming for final answer)            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 13. OPENAI GENERATES FINAL RESPONSE                            │
│     AI Reasoning (internal):                                   │
│     ┌────────────────────────────────────────────┐           │
│     │ Input:                                      │           │
│     │ - User asked: "What is 25 * 40?"          │           │
│     │ - I called calculator tool                 │           │
│     │ - Tool returned: {"result": 1000}         │           │
│     │                                             │           │
│     │ Decision: Give natural language answer     │           │
│     │ Output: "25 times 40 equals 1000."        │           │
│     └────────────────────────────────────────────┘           │
│                                                                 │
│     Response:                                                  │
│     {                                                           │
│       "choices": [{                                            │
│         "message": {                                           │
│           "role": "assistant",                                │
│           "content": "25 times 40 equals 1000."              │
│         },                                                      │
│         "finish_reason": "stop"                               │
│       }]                                                        │
│     }                                                           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 14. SAVE ASSISTANT RESPONSE TO DATABASE                        │
│     messageRepository.save(assistantMessage)                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 15. SEND TO USER VIA SSE                                       │
│     event: done                                                │
│     data: {"id":123,"role":"ASSISTANT",                       │
│            "content":"25 times 40 equals 1000."}              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 16. USER SEES FINAL ANSWER                                     │
│     "25 times 40 equals 1000."                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🧠 How AI Decides Which Tool to Use

### The Secret: Tool Descriptions & Parameters

When we send tools to OpenAI, we include **descriptions** in natural language:

```java
// CalculatorTool.java
@Override
public String getDescription() {
    return "Performs mathematical calculations. Supports +, -, *, /, %, parentheses, and decimal numbers. " +
           "Example: '25 * 40' returns 1000.";
}

@Override
public ToolDefinition getDefinition() {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("type", "object");
    
    Map<String, Object> properties = new HashMap<>();
    Map<String, Object> expressionProperty = new HashMap<>();
    expressionProperty.put("type", "string");
    expressionProperty.put("description", "The mathematical expression to evaluate (e.g., '25 * 40', '(10 + 5) * 2')");
    properties.put("expression", expressionProperty);
    
    parameters.put("properties", properties);
    parameters.put("required", new String[]{"expression"});
    
    return ToolDefinition.create(getName(), getDescription(), parameters);
}
```

### What OpenAI Sees:

```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "calculator",
        "description": "Performs mathematical calculations. Supports +, -, *, /, %, parentheses, and decimal numbers. Example: '25 * 40' returns 1000.",
        "parameters": {
          "type": "object",
          "properties": {
            "expression": {
              "type": "string",
              "description": "The mathematical expression to evaluate (e.g., '25 * 40', '(10 + 5) * 2')"
            }
          },
          "required": ["expression"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "get_current_time",
        "description": "Returns the current date and time for a given timezone or location. Use standard IANA timezone identifiers (e.g., 'Asia/Kolkata', 'America/New_York', 'Europe/London'). Defaults to UTC if no timezone is specified.",
        "parameters": {
          "type": "object",
          "properties": {
            "timezone": {
              "type": "string",
              "description": "IANA timezone identifier (e.g., 'Asia/Kolkata', 'America/New_York'). Defaults to UTC if not specified.",
              "default": "UTC"
            }
          }
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "get_conversation_stats",
        "description": "Retrieves statistics about conversations including total count and message count. This tool provides a summary of the user's conversation history.",
        "parameters": {
          "type": "object",
          "properties": {}
        }
      }
    }
  ]
}
```

### AI's Decision Process:

**Example 1: "What is 25 * 40?"**
```
AI Reasoning:
1. User asked a math question
2. I have a "calculator" tool
3. Description says: "Performs mathematical calculations"
4. This matches! I should use it.
5. Parameters need "expression" (type: string)
6. I'll call: calculator({"expression": "25 * 40"})
```

**Example 2: "What time is it in Tokyo?"**
```
AI Reasoning:
1. User asked about current time
2. I have a "get_current_time" tool
3. Description says: "Returns current date and time for a timezone"
4. Examples show "Asia/Tokyo" format
5. Parameters need "timezone" (type: string)
6. I'll call: get_current_time({"timezone": "Asia/Tokyo"})
```

**Example 3: "What is React?"**
```
AI Reasoning:
1. User asked about React (JavaScript library)
2. Look at available tools:
   - calculator: For math (not relevant)
   - get_current_time: For time (not relevant)
   - get_conversation_stats: For database stats (not relevant)
3. No tools match this question
4. I know about React from my training
5. Decision: Answer directly, don't use tools
```

**Example 4: "Calculate 50 + 75 and tell me the time in London"**
```
AI Reasoning:
1. User asked TWO things:
   - Math calculation
   - Current time
2. I need TWO tools:
   - calculator for "50 + 75"
   - get_current_time for London time
3. I can call MULTIPLE tools at once!
4. I'll call:
   - calculator({"expression": "50 + 75"})
   - get_current_time({"timezone": "Europe/London"})
```

---

## 📝 Code Walkthrough - Step by Step

### Step 1: User Sends Message

**Frontend (React):**
```typescript
// frontend/src/contexts/ChatContext.tsx

const sendMessage = useCallback(async (content: string) => {
  // ...
  const cancelStream = messagesApi.sendStream(
    selectedConversationId,
    { content },
    (chunk) => { /* handle text chunks */ },
    (finalMessage) => { /* handle completion */ },
    (error) => { /* handle error */ }
  );
}, []);
```

**API Call:**
```typescript
// frontend/src/services/api.ts

fetch(`${API_BASE_URL}/conversations/${conversationId}/messages/stream`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ content: "What is 25 * 40?" }),
  signal: abortController.signal,
})
```

---

### Step 2: Spring Boot Receives Request

**Controller:**
```java
// src/main/java/com/abhay/controller/ConversationController.java

@PostMapping("/{conversationId}/messages/stream")
public SseEmitter sendMessageStream(
    @PathVariable Long conversationId,
    @RequestBody SendMessageRequest request
) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
    
    // Start streaming in a separate thread
    conversationService.sendMessageStream(conversationId, request.getContent(), emitter);
    
    return emitter;
}
```

---

### Step 3: ConversationService Prepares Request

```java
// src/main/java/com/abhay/service/ConversationService.java

@Transactional
public void sendMessageStream(Long conversationId, String content, SseEmitter emitter) {
    try {
        // 1. Save user message
        Message userMessage = new Message(Message.Role.USER, content);
        userMessage.setConversation(conversation);
        messageRepository.save(userMessage);
        
        // 2. Load conversation history
        List<Message> history = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        
        // 3. Build LLM messages
        List<com.abhay.model.llm.Message> llmMessages = buildLLMMessages(history);
        
        // 4. Get available tools
        List<ToolDefinition> toolDefinitions = toolRegistry.getAllDefinitions();
        //    ToolRegistry auto-discovered all @Component tools at startup
        //    Returns: [calculator, get_current_time, get_conversation_stats]
        
        // 5. Prepare to accumulate response
        StringBuilder completeResponse = new StringBuilder();
        AtomicReference<List<ToolCall>> toolCallsRef = new AtomicReference<>();
        
        // 6. Call OpenAI with streaming
        openAIClient.sendMessageStream(
            llmMessages,
            toolDefinitions,  // ← SEND TOOLS TO OPENAI
            chunk -> { /* handle text chunks */ },
            toolCalls -> {
                // ← OPENAI REQUESTED TOOLS!
                toolCallsRef.set(toolCalls);
            },
            () -> {
                // ← STREAMING COMPLETE
                List<ToolCall> toolCalls = toolCallsRef.get();
                
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    // TOOL PATH: Execute tools
                    CompletableFuture.runAsync(() -> {
                        handleToolExecution(llmMessages, toolCalls, toolDefinitions, emitter, conversation);
                    });
                } else {
                    // NORMAL PATH: Save text response
                    saveAndCompleteResponse(completeResponse.toString(), emitter, conversation);
                }
            },
            error -> { /* handle error */ }
        );
    } catch (Exception e) {
        // error handling
    }
}
```

---

### Step 4: OpenAIClient Sends Request with Tools

```java
// src/main/java/com/abhay/client/OpenAIClient.java

public void sendMessageStream(
    List<Message> messages,
    List<ToolDefinition> tools,  // ← TOOLS
    Consumer<String> onChunk,
    Consumer<List<ToolCall>> onToolCalls,  // ← NEW CALLBACK
    Runnable onComplete,
    Consumer<Throwable> onError
) {
    // Create request
    LLMRequest request = new LLMRequest(model, messages);
    request.setStream(true);
    request.setTools(tools);  // ← ADD TOOLS TO REQUEST
    
    // Tool call accumulator
    ToolCallAccumulator accumulator = new ToolCallAccumulator();
    
    // Line accumulator (for split JSON across buffers)
    StringBuilder lineAccumulator = new StringBuilder();
    
    // Make HTTP POST request
    webClient.post()
        .uri(apiUrl)
        .header("Authorization", "Bearer " + apiKey)
        .bodyValue(request)
        .retrieve()
        .bodyToFlux(DataBuffer.class)
        .flatMap(dataBuffer -> {
            // Convert buffer to string
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            
            // Accumulate incomplete lines
            lineAccumulator.append(text);
            String accumulated = lineAccumulator.toString();
            
            // Split by newlines
            String[] lines = accumulated.split("\n", -1);
            
            // Process complete lines
            List<String> completeLines = new ArrayList<>();
            for (int i = 0; i < lines.length - 1; i++) {
                completeLines.add(lines[i]);
            }
            
            // Keep last line if incomplete
            if (text.endsWith("\n")) {
                completeLines.add(lines[lines.length - 1]);
                lineAccumulator.setLength(0);
            } else {
                lineAccumulator.setLength(0);
                lineAccumulator.append(lines[lines.length - 1]);
            }
            
            return Flux.fromIterable(completeLines);
        })
        .subscribe(
            line -> {
                // Parse text chunk
                String chunk = parseStreamChunk(line);
                if (chunk != null && !chunk.isEmpty()) {
                    onChunk.accept(chunk);
                }
                
                // Parse tool call deltas
                List<StreamChunk.ToolCallDelta> toolDeltas = parseToolCallDeltas(line);
                if (toolDeltas != null && !toolDeltas.isEmpty()) {
                    for (StreamChunk.ToolCallDelta delta : toolDeltas) {
                        accumulator.addDelta(delta);  // ← ACCUMULATE FRAGMENTS
                    }
                }
                
                // Check finish_reason
                String finishReason = parseFinishReason(line);
                if ("tool_calls".equals(finishReason)) {
                    // ← AI WANTS TO USE TOOLS!
                    List<ToolCall> completedToolCalls = accumulator.getCompletedCalls();
                    if (onToolCalls != null && !completedToolCalls.isEmpty()) {
                        onToolCalls.accept(completedToolCalls);
                    }
                }
            },
            error -> onError.accept(error),
            () -> onComplete.run()
        );
}
```

**ToolCallAccumulator (Inner Class):**
```java
private static class ToolCallAccumulator {
    private final Map<Integer, ToolCall> calls = new HashMap<>();
    private final Map<Integer, StringBuilder> argumentBuilders = new HashMap<>();
    
    public void addDelta(StreamChunk.ToolCallDelta delta) {
        int index = delta.getIndex() != null ? delta.getIndex() : 0;
        
        // First chunk for this index: create ToolCall
        if (!calls.containsKey(index)) {
            ToolCall toolCall = new ToolCall();
            toolCall.setId(delta.getId());
            toolCall.setType(delta.getType() != null ? delta.getType() : "function");
            
            ToolCall.FunctionCall functionCall = new ToolCall.FunctionCall();
            if (delta.getFunction() != null) {
                functionCall.setName(delta.getFunction().getName());
                functionCall.setArguments("");
            }
            toolCall.setFunction(functionCall);
            
            calls.put(index, toolCall);
            argumentBuilders.put(index, new StringBuilder());
        }
        
        // Accumulate arguments
        if (delta.getFunction() != null && delta.getFunction().getArguments() != null) {
            StringBuilder argBuilder = argumentBuilders.get(index);
            argBuilder.append(delta.getFunction().getArguments());
            
            // Update function call's arguments
            ToolCall toolCall = calls.get(index);
            toolCall.getFunction().setArguments(argBuilder.toString());
        }
    }
    
    public List<ToolCall> getCompletedCalls() {
        return new ArrayList<>(calls.values());
    }
}
```

---

### Step 5: Handle Tool Execution

```java
// src/main/java/com/abhay/service/ConversationService.java

private void handleToolExecution(
    List<com.abhay.model.llm.Message> llmMessages,
    List<ToolCall> toolCalls,
    List<ToolDefinition> toolDefinitions,
    SseEmitter emitter,
    Conversation conversation
) {
    try {
        // 1. Notify frontend
        emitter.send(SseEmitter.event()
            .name("tool_execution_start")
            .data(Map.of("count", toolCalls.size())));
        
        // 2. Add assistant message with tool_calls to history
        com.abhay.model.llm.Message assistantMessage = new com.abhay.model.llm.Message();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(null);
        assistantMessage.setToolCalls(toolCalls);
        llmMessages.add(assistantMessage);
        
        // 3. Execute each tool
        for (ToolCall call : toolCalls) {
            String toolName = call.getFunction().getName();
            String arguments = call.getFunction().getArguments();
            
            // Notify frontend
            emitter.send(SseEmitter.event()
                .name("tool_execution")
                .data(Map.of("tool", toolName, "id", call.getId())));
            
            // Execute tool
            String result = toolExecutor.executeTool(toolName, arguments);
            
            // Add tool result message to history
            com.abhay.model.llm.Message toolMessage = new com.abhay.model.llm.Message();
            toolMessage.setRole("tool");
            toolMessage.setToolCallId(call.getId());
            toolMessage.setContent(result);
            llmMessages.add(toolMessage);
        }
        
        // 4. Notify frontend
        emitter.send(SseEmitter.event()
            .name("tool_execution_complete")
            .data("All tools executed successfully"));
        
        // 5. Get final response from OpenAI
        LLMResponse finalResponse = openAIClient.sendMessageWithToolResults(
            llmMessages,
            toolDefinitions
        );
        
        // 6. Extract final answer
        String finalContent = finalResponse.getChoices().get(0).getMessage().getContent();
        
        // 7. Save and complete
        saveAndCompleteResponse(finalContent, emitter, conversation);
        
    } catch (Exception e) {
        // error handling
    }
}
```

---

### Step 6: ToolExecutor Executes Tool

```java
// src/main/java/com/abhay/tool/ToolExecutor.java

@Component
public class ToolExecutor {
    
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    public ToolExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    
    public String executeTool(String toolName, String arguments) {
        try {
            // 1. Check if tool exists
            if (!toolRegistry.hasTool(toolName)) {
                return buildErrorResponse("Unknown tool: " + toolName);
            }
            
            // 2. Get tool
            Tool tool = toolRegistry.getTool(toolName);
            
            // 3. Execute tool (safe execution with error handling)
            String result = tool.execute(arguments);
            
            return result;
            
        } catch (ToolExecutionException e) {
            return buildErrorResponse(e.getMessage());
        } catch (Exception e) {
            return buildErrorResponse("Tool execution failed: " + e.getMessage());
        }
    }
    
    private String buildErrorResponse(String errorMessage) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", errorMessage);
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"error\": \"Failed to format error message\"}";
        }
    }
}
```

---

### Step 7: Calculator Tool Executes

```java
// src/main/java/com/abhay/tool/impl/CalculatorTool.java

@Component
public class CalculatorTool implements Tool {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String getName() {
        return "calculator";
    }
    
    @Override
    public String execute(String arguments) throws ToolExecutionException {
        try {
            // 1. Parse JSON arguments
            // Input: "{\"expression\":\"25 * 40\"}"
            JsonNode argsNode = objectMapper.readTree(arguments);
            String expression = argsNode.get("expression").asText();
            // expression = "25 * 40"
            
            // 2. Validate
            if (expression == null || expression.trim().isEmpty()) {
                throw new ToolExecutionException("Expression cannot be empty");
            }
            
            // 3. Evaluate safely
            double result = evaluateExpression(expression.trim());
            // result = 1000.0
            
            // 4. Return result as JSON
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("expression", expression);
            
            return objectMapper.writeValueAsString(response);
            // Returns: "{\"result\":1000.0,\"expression\":\"25 * 40\"}"
            
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to evaluate expression: " + e.getMessage(), e);
        }
    }
    
    private double evaluateExpression(String expression) throws ToolExecutionException {
        // Remove whitespace
        expression = expression.replaceAll("\\s+", "");
        // "25*40"
        
        // Validate characters
        if (!expression.matches("[0-9+\\-*/%().]+")) {
            throw new ToolExecutionException("Invalid characters");
        }
        
        // Parse and evaluate
        Parser parser = new Parser(expression);
        double result = parser.parseExpression();
        
        return result;  // 1000.0
    }
    
    // Recursive descent parser implementation...
    // (See full implementation in CalculatorTool.java)
}
```

---

### Step 8: Send Tool Result Back to OpenAI

```java
// src/main/java/com/abhay/client/OpenAIClient.java

public LLMResponse sendMessageWithToolResults(
    List<Message> messages,
    List<ToolDefinition> tools
) {
    // Create NON-STREAMING request
    LLMRequest request = new LLMRequest(model, messages);
    request.setStream(false);  // ← Non-streaming for final answer
    request.setTools(tools);
    
    // Make HTTP POST request
    LLMResponse response = webClient.post()
        .uri(apiUrl)
        .header("Authorization", "Bearer " + apiKey)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(LLMResponse.class)
        .block();
    
    return response;
}
```

**Request sent to OpenAI:**
```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "system",
      "content": "You are Nexa AI, a helpful assistant."
    },
    {
      "role": "user",
      "content": "What is 25 * 40?"
    },
    {
      "role": "assistant",
      "tool_calls": [
        {
          "id": "call_abc123",
          "type": "function",
          "function": {
            "name": "calculator",
            "arguments": "{\"expression\":\"25 * 40\"}"
          }
        }
      ]
    },
    {
      "role": "tool",
      "tool_call_id": "call_abc123",
      "content": "{\"result\":1000.0,\"expression\":\"25 * 40\"}"
    }
  ],
  "tools": [...],
  "stream": false
}
```

**OpenAI's response:**
```json
{
  "id": "chatcmpl-xyz789",
  "object": "chat.completion",
  "created": 1787904000,
  "model": "gpt-4o-mini",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "25 times 40 equals 1000."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 150,
    "completion_tokens": 10,
    "total_tokens": 160
  }
}
```

---

### Step 9: Save Final Response

```java
// src/main/java/com/abhay/service/ConversationService.java

private void saveAndCompleteResponse(
    String content,
    SseEmitter emitter,
    Conversation conversation
) throws IOException {
    
    // Save assistant message to database
    Message assistantMessage = new Message(Message.Role.ASSISTANT, content);
    assistantMessage.setConversation(conversation);
    Message saved = messageRepository.save(assistantMessage);
    
    // Send completion event to frontend
    MessageResponse response = mapToMessageResponse(saved);
    emitter.send(SseEmitter.event()
        .name("done")
        .data(response));
    
    // Close SSE connection
    emitter.complete();
}
```

---

## 📦 JSON Payloads at Each Step

### 1. Frontend → Backend
```json
POST /api/conversations/31/messages/stream
{
  "content": "What is 25 * 40?"
}
```

### 2. Backend → OpenAI (First Request)
```json
POST https://api.openai.com/v1/chat/completions
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "system",
      "content": "You are Nexa AI, a helpful assistant."
    },
    {
      "role": "user",
      "content": "What is 25 * 40?"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "calculator",
        "description": "Performs mathematical calculations...",
        "parameters": {
          "type": "object",
          "properties": {
            "expression": {
              "type": "string",
              "description": "The mathematical expression to evaluate"
            }
          },
          "required": ["expression"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "get_current_time",
        "description": "Returns the current date and time...",
        "parameters": {
          "type": "object",
          "properties": {
            "timezone": {
              "type": "string",
              "description": "IANA timezone identifier"
            }
          }
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "get_conversation_stats",
        "description": "Retrieves statistics about conversations...",
        "parameters": {
          "type": "object",
          "properties": {}
        }
      }
    }
  ],
  "stream": true
}
```

### 3. OpenAI → Backend (Streaming Tool Call)

**First chunk (tool call start):**
```json
data: {
  "id": "chatcmpl-abc123",
  "object": "chat.completion.chunk",
  "created": 1787903960,
  "model": "gpt-4o-mini",
  "choices": [{
    "index": 0,
    "delta": {
      "tool_calls": [{
        "index": 0,
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "calculator",
          "arguments": ""
        }
      }]
    },
    "finish_reason": null
  }]
}
```

**Subsequent chunks (building arguments):**
```json
data: {
  "choices": [{
    "delta": {
      "tool_calls": [{
        "index": 0,
        "function": {
          "arguments": "{\""
        }
      }]
    }
  }]
}

data: {
  "choices": [{
    "delta": {
      "tool_calls": [{
        "index": 0,
        "function": {
          "arguments": "expression"
        }
      }]
    }
  }]
}

data: {
  "choices": [{
    "delta": {
      "tool_calls": [{
        "index": 0,
        "function": {
          "arguments": "\":\""
        }
      }]
    }
  }]
}

data: {
  "choices": [{
    "delta": {
      "tool_calls": [{
        "index": 0,
        "function": {
          "arguments": "25 * 40"
        }
      }]
    }
  }]
}

data: {
  "choices": [{
    "delta": {
      "tool_calls": [{
        "index": 0,
        "function": {
          "arguments": "\"}"
        }
      }]
    }
  }]
}
```

**Final chunk (finish_reason):**
```json
data: {
  "choices": [{
    "index": 0,
    "delta": {},
    "finish_reason": "tool_calls"
  }]
}
```

### 4. Tool Execution (Internal)

**Input to CalculatorTool:**
```json
{
  "expression": "25 * 40"
}
```

**Output from CalculatorTool:**
```json
{
  "result": 1000.0,
  "expression": "25 * 40"
}
```

### 5. Backend → OpenAI (Second Request with Tool Result)
```json
POST https://api.openai.com/v1/chat/completions
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "system",
      "content": "You are Nexa AI, a helpful assistant."
    },
    {
      "role": "user",
      "content": "What is 25 * 40?"
    },
    {
      "role": "assistant",
      "content": null,
      "tool_calls": [
        {
          "id": "call_abc123",
          "type": "function",
          "function": {
            "name": "calculator",
            "arguments": "{\"expression\":\"25 * 40\"}"
          }
        }
      ]
    },
    {
      "role": "tool",
      "tool_call_id": "call_abc123",
      "content": "{\"result\":1000.0,\"expression\":\"25 * 40\"}"
    }
  ],
  "tools": [...],
  "stream": false
}
```

### 6. OpenAI → Backend (Final Response)
```json
{
  "id": "chatcmpl-xyz789",
  "object": "chat.completion",
  "created": 1787904000,
  "model": "gpt-4o-mini",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "25 times 40 equals 1000."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 150,
    "completion_tokens": 10,
    "total_tokens": 160
  }
}
```

### 7. Backend → Frontend (SSE Events)
```
event: tool_execution_start
data: {"count":1}

event: tool_execution
data: {"id":"call_abc123","tool":"calculator"}

event: tool_execution_complete
data: All tools executed successfully

event: done
data: {"id":123,"role":"ASSISTANT","content":"25 times 40 equals 1000.","createdAt":"2026-08-29T10:30:00"}
```

---

## 🌊 Streaming with Tool Calls

### Key Challenge: Tool Calls Arrive as Fragments

Tool call JSON is built incrementally:

```
Chunk 1: {"index":0,"id":"call_abc","type":"function","function":{"name":"calculator","arguments":""}}
Chunk 2: {"index":0,"function":{"arguments":"{\""}}
Chunk 3: {"index":0,"function":{"arguments":"expression"}}
Chunk 4: {"index":0,"function":{"arguments":"\":\""}}
Chunk 5: {"index":0,"function":{"arguments":"25 * 40"}}
Chunk 6: {"index":0,"function":{"arguments":"\"}"}}
```

### Solution: ToolCallAccumulator

```java
private static class ToolCallAccumulator {
    private final Map<Integer, ToolCall> calls = new HashMap<>();
    private final Map<Integer, StringBuilder> argumentBuilders = new HashMap<>();
    
    public void addDelta(StreamChunk.ToolCallDelta delta) {
        int index = delta.getIndex();
        
        // First chunk: Initialize
        if (!calls.containsKey(index)) {
            ToolCall toolCall = new ToolCall();
            toolCall.setId(delta.getId());  // "call_abc"
            toolCall.setType("function");
            
            ToolCall.FunctionCall functionCall = new ToolCall.FunctionCall();
            functionCall.setName(delta.getFunction().getName());  // "calculator"
            functionCall.setArguments("");
            toolCall.setFunction(functionCall);
            
            calls.put(index, toolCall);
            argumentBuilders.put(index, new StringBuilder());
        }
        
        // Subsequent chunks: Append arguments
        if (delta.getFunction() != null && delta.getFunction().getArguments() != null) {
            StringBuilder argBuilder = argumentBuilders.get(index);
            argBuilder.append(delta.getFunction().getArguments());
            
            // Update tool call
            calls.get(index).getFunction().setArguments(argBuilder.toString());
        }
    }
    
    public List<ToolCall> getCompletedCalls() {
        // After all chunks processed:
        // calls.get(0) = {
        //   "id": "call_abc",
        //   "type": "function",
        //   "function": {
        //     "name": "calculator",
        //     "arguments": "{\"expression\":\"25 * 40\"}"
        //   }
        // }
        return new ArrayList<>(calls.values());
    }
}
```

---

## 🔄 Multi-Turn Conversation Flow

### The Complete Conversation Cycle:

**Turn 1: User → AI (with tools)**
```json
[
  {"role": "system", "content": "You are Nexa AI"},
  {"role": "user", "content": "What is 25 * 40?"}
]
→ AI Response: tool_calls
```

**Turn 2: AI → Tools → AI (tool results)**
```json
[
  {"role": "system", "content": "You are Nexa AI"},
  {"role": "user", "content": "What is 25 * 40?"},
  {"role": "assistant", "tool_calls": [...]},
  {"role": "tool", "tool_call_id": "...", "content": "{result: 1000}"}
]
→ AI Response: "25 times 40 equals 1000."
```

**Turn 3: User follows up**
```json
[
  {"role": "system", "content": "You are Nexa AI"},
  {"role": "user", "content": "What is 25 * 40?"},
  {"role": "assistant", "content": "25 times 40 equals 1000."},
  {"role": "user", "content": "What about 50 * 3?"}
]
→ AI Response: tool_calls (calculator again)
```

**Important:** The full conversation history (including tool executions) is **NOT** persisted to database. Only the final user and assistant messages are saved. Tool calls happen "in memory" during request processing.

---

## 🎓 Key Takeaways

### 1. AI Decides Based on Descriptions
- You provide tool descriptions in natural language
- AI reads them like a human would
- AI matches user intent with tool descriptions
- Good descriptions = better tool selection

### 2. Two OpenAI Requests Per Tool Call
- **Request 1:** User message → AI returns tool_calls
- **Request 2:** Tool results → AI returns final answer

### 3. Streaming Requires Accumulation
- Tool calls arrive as fragments
- Must accumulate by index
- Only complete when finish_reason="tool_calls"

### 4. Multi-Turn = Single Request
- From user's perspective: one request
- From OpenAI's perspective: multiple turns in same conversation
- Each turn adds messages to history

### 5. Tool Execution is Synchronous
- Tools execute one by one (could be parallelized)
- Each tool result added to conversation history
- All results sent back together to OpenAI

---

## 💡 Why This Design?

### OpenAI's Approach:
1. **Safety:** AI decides tool usage (not hardcoded logic)
2. **Flexibility:** Easy to add new tools without code changes
3. **Natural:** Descriptions are human-readable
4. **Powerful:** AI can combine multiple tools
5. **Extensible:** Foundation for autonomous agents

### Our Implementation:
1. **Spring Boot:** Familiar framework for Java developers
2. **Auto-Discovery:** @Component tools registered automatically
3. **Streaming:** Real-time feedback to users
4. **Security:** Whitelist approach, no arbitrary code execution
5. **Separation:** Tools are independent, easy to test

---

This is the complete picture of how tool calling works in NexaChat! 🎉
