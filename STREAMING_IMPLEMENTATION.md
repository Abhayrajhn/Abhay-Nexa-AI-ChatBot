# Streaming LLM Responses - Implementation Guide

## Overview

This document explains how streaming was implemented in the Nexa chatbot to display AI responses progressively (like ChatGPT) instead of waiting for the complete response.

**Date Implemented:** August 27-28, 2026  
**Author:** Learning Implementation for NexaChat

---

## Table of Contents

1. [What is Streaming?](#what-is-streaming)
2. [Why Streaming?](#why-streaming)
3. [Architecture Overview](#architecture-overview)
4. [Backend Implementation](#backend-implementation)
5. [Frontend Implementation](#frontend-implementation)
6. [Key Challenges & Solutions](#key-challenges--solutions)
7. [Testing Checklist](#testing-checklist)
8. [Future Enhancements](#future-enhancements)

---

## What is Streaming?

### Non-Streaming (Before)
```
User: "Tell me about cricket"
[Wait 5 seconds...]
Assistant: [Complete response appears all at once]
```

### Streaming (After)
```
User: "Tell me about cricket"
Assistant: Cricket is a...        [appears immediately]
Assistant: Cricket is a bat...    [words appear progressively]
Assistant: Cricket is a bat-and-ball game... [continues streaming]
```

**Key Concept:** Instead of waiting for the complete response, we display text as it arrives from OpenAI, chunk by chunk.

---

## Why Streaming?

### Benefits:
1. **Better UX:** Users see the response immediately instead of staring at a loading spinner
2. **Perceived Performance:** Feels much faster even though total time is similar
3. **Modern Experience:** Matches the behavior users expect from ChatGPT
4. **Engagement:** Users can start reading while the AI is still generating

### Technical Benefit:
- Prepares the architecture for future features (function calling, tool use, RAG)

---

## Architecture Overview

### Request Flow

```
Frontend (React)
    ↓ POST /api/conversations/{id}/messages/stream
Backend (Spring Boot)
    ↓ Save user message to DB
    ↓ Build conversation history
    ↓ Call OpenAI API with stream=true
OpenAI API
    ↓ Returns Server-Sent Events (SSE)
    ↓ Chunk 1: " Cricket"
    ↓ Chunk 2: " is"
    ↓ Chunk 3: " a"
Backend
    ↓ Forwards each chunk to frontend via SSE
    ↓ Accumulates complete response
    ↓ Saves complete message to DB when done
Frontend
    ↓ Displays each chunk immediately
    ↓ Shows final message when complete
```

### Key Technologies

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **OpenAI API** | HTTP Streaming | Sends response in small chunks |
| **Backend** | Spring WebFlux (WebClient) | Handles reactive streaming |
| **Backend** | SseEmitter | Sends Server-Sent Events to frontend |
| **Frontend** | Fetch API + ReadableStream | Receives and parses SSE |
| **Frontend** | React State | Updates UI as chunks arrive |

---

## Backend Implementation

### 1. New Model: StreamChunk.java

**Location:** `/src/main/java/com/abhay/model/llm/StreamChunk.java`

**Purpose:** Models OpenAI's streaming response format

```java
@JsonIgnoreProperties(ignoreUnknown = true)  // Ignore extra fields from OpenAI
public class StreamChunk {
    private String id;
    private String object;
    private List<Choice> choices;
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Delta delta;  // Contains incremental content
        @JsonProperty("finish_reason")
        private String finishReason;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        private String content;  // The actual text chunk
    }
}
```

**Key Learning:** OpenAI's streaming response uses `delta` instead of `message`, and `delta.content` contains each small piece of text.

**Critical Fix:** The `@JsonIgnoreProperties(ignoreUnknown = true)` annotation was essential! OpenAI sends extra fields like:
- `service_tier`
- `system_fingerprint`
- `logprobs`
- `obfuscation`

Without this annotation, JSON parsing would fail for every chunk.

---

### 2. Modified: LLMRequest.java

**Added:**
```java
private Boolean stream;  // Enables streaming mode

public void setStream(Boolean stream) {
    this.stream = stream;
}
```

**Purpose:** Tell OpenAI to stream the response instead of sending it all at once.

---

### 3. Modified: OpenAIClient.java

**Added Method:** `sendMessageStream()`

**Key Implementation Details:**

```java
public void sendMessageStream(List<Message> messages,
                               Consumer<String> onChunk,
                               Runnable onComplete,
                               Consumer<Throwable> onError) {
    
    // 1. Create request with stream=true
    LLMRequest request = new LLMRequest(model, messages);
    request.setStream(true);
    
    // 2. Use WebClient to make streaming request
    webClient.post()
        .uri(apiUrl)
        .header("Authorization", "Bearer " + apiKey)
        .bodyValue(request)
        .retrieve()
        // 3. Get response as stream of DataBuffers
        .bodyToFlux(DataBuffer.class)
        .flatMap(dataBuffer -> {
            // 4. Convert bytes to string
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            String text = new String(bytes, StandardCharsets.UTF_8);
            
            // 5. Split by newlines (SSE format)
            String[] lines = text.split("\n");
            return Flux.fromArray(lines);
        })
        .subscribe(
            // 6. Parse each line and extract content
            line -> {
                String chunk = parseStreamChunk(line);
                if (chunk != null && !chunk.isEmpty()) {
                    onChunk.accept(chunk);  // Call callback with chunk
                }
            },
            error -> onError.accept(error),
            () -> onComplete.run()
        );
}
```

**parseStreamChunk() Method:**
```java
private String parseStreamChunk(String line) {
    // SSE format: "data: {json}"
    if (line.startsWith("data: ")) {
        line = line.substring(6);
    }
    
    if (line.equals("[DONE]")) {
        return null;  // End of stream marker
    }
    
    // Parse JSON and extract content
    StreamChunk chunk = objectMapper.readValue(line, StreamChunk.class);
    return chunk.getChoices().get(0).getDelta().getContent();
}
```

**Why DataBuffer instead of String?**
- `bodyToFlux(String.class)` doesn't handle SSE format properly
- `bodyToFlux(DataBuffer.class)` gives us raw bytes which we can parse line by line

---

### 4. Modified: ConversationService.java

**Added Method:** `sendMessageStream()`

**Key Features:**

```java
public void sendMessageStream(Long conversationId, String content, SseEmitter emitter) {
    // 1. Save user message to DB
    Message userMessage = new Message(Message.Role.USER, content);
    messageRepository.save(userMessage);
    
    // 2. Build conversation history
    List<Message> history = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
    List<LLMMessage> llmMessages = buildLLMMessages(history);
    
    // 3. Accumulate complete response
    StringBuilder completeResponse = new StringBuilder();
    
    // 4. Call OpenAI with callbacks
    openAIClient.sendMessageStream(
        llmMessages,
        // onChunk: Send each chunk to frontend AND accumulate
        chunk -> {
            completeResponse.append(chunk);
            emitter.send(SseEmitter.event()
                .name("chunk")
                .data(chunk));
        },
        // onComplete: Save complete message to DB
        () -> {
            // Save assistant's complete response
            Message assistantMessage = new Message(Message.Role.ASSISTANT, completeResponse.toString());
            Message saved = messageRepository.save(assistantMessage);
            
            // Send final message to frontend
            emitter.send(SseEmitter.event()
                .name("done")
                .data(mapToMessageResponse(saved)));
            
            emitter.complete();
        },
        // onError: Handle errors
        error -> {
            emitter.send(SseEmitter.event()
                .name("error")
                .data(error.getMessage()));
            emitter.completeWithError(error);
        }
    );
}
```

**Critical Design Decision:** We accumulate chunks in memory and save the COMPLETE message to the database once, not individual chunks. This keeps the database clean and simple.

---

### 5. Modified: ConversationController.java

**Added Endpoint:**

```java
@PostMapping("/{conversationId}/messages/stream")
public SseEmitter sendMessageStream(@PathVariable Long conversationId, 
                                    @RequestBody SendMessageRequest request) {
    logger.info("POST /api/conversations/{}/messages/stream", conversationId);
    
    // 1. Create SSE emitter with 5-minute timeout
    SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
    
    // 2. Handle client disconnect
    emitter.onTimeout(() -> logger.warn("SSE timeout"));
    emitter.onError(e -> logger.error("SSE error: {}", e.getMessage()));
    
    // 3. Run streaming in background thread (non-blocking)
    CompletableFuture.runAsync(() -> {
        conversationService.sendMessageStream(conversationId, request.getContent(), emitter);
    });
    
    // 4. Return emitter immediately (connection stays open)
    return emitter;
}
```

**Why CompletableFuture?**
- Streaming takes time (5-30 seconds)
- We don't want to block the HTTP thread
- Return emitter immediately, process in background

**SSE Format Sent to Frontend:**
```
event: chunk
data:  Cricket

event: chunk
data:  is

event: chunk
data:  a

event: done
data: {"id":123,"role":"ASSISTANT","content":"Cricket is a..."}
```

---

## Frontend Implementation

### 1. Modified: api.ts

**Added Function:** `sendStream()`

**Purpose:** Handle SSE connection and parse events

```typescript
sendStream: (
  conversationId: string,
  data: SendMessageRequest,
  onChunk: (chunk: string) => void,
  onDone: (message: Message) => void,
  onError: (error: string) => void
): (() => void) => {
  const abortController = new AbortController();
  
  fetch(`${API_BASE_URL}/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    signal: abortController.signal,
  })
    .then(async (response) => {
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let currentEvent = '';
      let currentData = '';

      // Read stream chunk by chunk
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        
        // Add to buffer
        buffer += decoder.decode(value, { stream: true });
        
        // Process complete lines
        let newlineIndex;
        while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
          const line = buffer.substring(0, newlineIndex);
          buffer = buffer.substring(newlineIndex + 1);
          
          // Parse SSE line
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            currentData = line.substring(5);  // Keep space after colon
          } else if (line.trim() === '') {
            // Empty line = end of event
            if (currentEvent === 'chunk') {
              onChunk(currentData);
            } else if (currentEvent === 'done') {
              onDone(JSON.parse(currentData));
            }
            currentEvent = '';
            currentData = '';
          }
        }
      }
    });
  
  // Return cancel function
  return () => abortController.abort();
}
```

**Key Implementation Details:**

1. **Line-by-line parsing:** Uses `indexOf('\n')` to process one line at a time, avoiding race conditions
2. **State persistence:** `currentEvent` and `currentData` live outside the loop
3. **Space preservation:** `line.substring(5)` keeps the space after `data:` which is part of the content
4. **Cancellable:** Returns abort function for cleanup

---

### 2. Modified: ChatContext.tsx

**Added State:**
```typescript
const [isStreaming, setIsStreaming] = useState(false);
const [streamingMessage, setStreamingMessage] = useState('');
```

**Modified sendMessage():**

```typescript
const sendMessage = useCallback(async (content: string) => {
  setIsStreaming(true);
  setStreamingMessage('');
  
  // 1. Optimistically show user message
  const tempUserMessage = {
    id: 'temp-' + Date.now(),
    role: 'USER',
    content: content,
    createdAt: new Date().toISOString(),
  };
  setMessages(prev => [...prev, tempUserMessage]);
  
  // 2. Accumulate streaming response
  let accumulatedContent = '';
  
  messagesApi.sendStream(
    conversationId,
    { content },
    // onChunk: Update streaming message
    (chunk) => {
      accumulatedContent += chunk;
      setStreamingMessage(accumulatedContent);
    },
    // onDone: Replace with final message from DB
    async (finalMessage) => {
      setIsStreaming(false);
      setStreamingMessage('');
      
      // Reload all messages from backend
      const allMessages = await messagesApi.getByConversationId(conversationId);
      setMessages(allMessages);
      
      // Generate title if first message
      if (isFirstMessage) {
        const title = content.trim().split(/\s+/).slice(0, 5).join(' ');
        await conversationsApi.update(conversationId, { title });
      }
      
      await loadConversations();
    },
    // onError: Show error
    (errorMessage) => {
      setError(errorMessage);
      setIsStreaming(false);
    }
  );
}, [conversationId]);
```

**Key Design Decisions:**

1. **Optimistic Updates:** Show user message immediately (don't wait for backend)
2. **Accumulation:** Build up the streaming message client-side as chunks arrive
3. **Final Reload:** When done, reload from database to get real IDs and timestamps
4. **Title Generation:** Create conversation title from first 5 words of user's message

---

### 3. Modified: MessageList.tsx

**Added Streaming Message Display:**

```typescript
{isStreaming && streamingMessage && (
  <div className="flex items-start gap-3 p-4">
    <div className="flex-shrink-0 w-8 h-8 rounded-full bg-purple-500 flex items-center justify-center">
      <Bot className="w-5 h-5 text-white" />
    </div>
    <div className="flex-1 space-y-1">
      <div className="text-xs text-gray-500">AI Assistant • Streaming...</div>
      <div className="prose prose-sm max-w-none">
        {streamingMessage}
        <span className="inline-block w-2 h-4 ml-1 bg-purple-500 animate-pulse"></span>
      </div>
    </div>
  </div>
)}
```

**Features:**
- Shows "Streaming..." indicator
- Displays accumulated text
- Blinking cursor animation during streaming
- Auto-scrolls to bottom as content appears

---

### 4. Modified: MessageInput.tsx

**Disabled During Streaming:**

```typescript
<button
  type="submit"
  disabled={!inputValue.trim() || isStreaming}
  className={`p-2 rounded-lg transition-colors ${
    !inputValue.trim() || isStreaming
      ? 'text-gray-400 cursor-not-allowed'
      : 'text-purple-600 hover:bg-purple-50'
  }`}
>
  {isStreaming ? (
    <div className="flex items-center gap-2">
      <div className="w-2 h-2 bg-purple-500 rounded-full animate-pulse"></div>
      <div className="w-2 h-2 bg-purple-500 rounded-full animate-pulse delay-100"></div>
      <div className="w-2 h-2 bg-purple-500 rounded-full animate-pulse delay-200"></div>
    </div>
  ) : (
    <Send className="w-5 h-5" />
  )}
</button>
```

**Features:**
- Disabled input while streaming
- Visual indicator (pulsing dots)
- Changed placeholder text

---

## Key Challenges & Solutions

### Challenge 1: JSON Parsing Failure ❌ → ✅

**Problem:**
```
Unrecognized field "service_tier" (class StreamChunk)
Unrecognized field "logprobs" (class StreamChunk$Choice)
```

**Root Cause:** OpenAI sends extra fields that our model classes didn't define.

**Solution:** Added `@JsonIgnoreProperties(ignoreUnknown = true)` to:
- `StreamChunk` class
- `StreamChunk.Choice` inner class
- `StreamChunk.Delta` inner class

**Learning:** Always use `@JsonIgnoreProperties` when consuming external APIs - they may add fields without notice.

---

### Challenge 2: Missing Spaces in Text ❌ → ✅

**Problem:**
```
Frontend displayed: "Cricketisabat-and-ballgame"
Backend sent: "Cricket is a bat-and-ball game"
```

**Root Cause:** Frontend was using `.trim()` on SSE data lines, which removed leading spaces from chunks like `" is"` and `" a"`.

**Wrong Code:**
```typescript
currentData = line.substring(5).trim();  // ❌ Removes spaces!
```

**Correct Code:**
```typescript
currentData = line.substring(5);  // ✅ Keeps spaces
```

**Learning:** In SSE format, `data: <content>` means there's ONE space after the colon as part of the format, but the rest is content. We must preserve spaces in the content.

---

### Challenge 3: Race Condition in SSE Parsing ❌ → ✅

**Problem:** Streaming worked for short responses but broke for long responses.

**Root Cause:** Using `buffer.split('\n')` processed all lines at once, causing state to be lost between reads.

**Wrong Approach:**
```typescript
const lines = buffer.split('\n');  // ❌ Loses state between chunks
buffer = lines.pop() || '';
let currentEvent = '';  // ❌ Recreated each time
let currentData = '';

for (const line of lines) {
  // Process lines...
}
```

**Correct Approach:**
```typescript
let currentEvent = '';  // ✅ Persists across reads
let currentData = '';

while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
  const line = buffer.substring(0, newlineIndex);
  buffer = buffer.substring(newlineIndex + 1);
  
  // Process one line at a time
  if (line.startsWith('event:')) {
    currentEvent = line.substring(6).trim();
  } else if (line.startsWith('data:')) {
    currentData = line.substring(5);
  } else if (line.trim() === '') {
    // Empty line = process event
    if (currentEvent && currentData) {
      // Handle event...
      currentEvent = '';
      currentData = '';
    }
  }
}
```

**Learning:** When parsing streaming data, state must persist across buffer reads. Process line-by-line, not batch.

---

### Challenge 4: Empty Responses Saved to Database ❌ → ✅

**Problem:** Database contained messages with empty content.

**Root Cause:** No chunks were being extracted from OpenAI's response due to JSON parsing failures.

**Solution:** Fixed by adding `@JsonIgnoreProperties` annotations (see Challenge 1).

**Learning:** Always check logs end-to-end. In this case, backend logs showed "Total response length: 0", which pointed to the parsing issue.

---

## Testing Checklist

### Backend Testing

- [x] OpenAI API returns streaming chunks
- [x] Chunks are parsed correctly (no JSON errors)
- [x] Spaces are preserved in chunks
- [x] Complete message is saved to database once
- [x] User message is saved before streaming starts
- [x] SSE connection stays open during streaming
- [x] SSE connection closes after streaming completes
- [x] Error handling works (invalid conversation ID, OpenAI API errors)

### Frontend Testing

- [x] User message appears immediately (optimistic update)
- [x] Streaming message appears with cursor animation
- [x] Text appears progressively without missing spaces
- [x] Input is disabled during streaming
- [x] Final message replaces streaming message
- [x] Conversation title is generated for first message
- [x] Short responses work correctly
- [x] Long responses work correctly
- [x] Multiple consecutive messages work
- [x] Browser page refresh shows correct message history

### Edge Cases

- [x] User sends message while streaming (input disabled)
- [x] Network error during streaming (error displayed)
- [x] OpenAI API error (error displayed)
- [x] Empty response from OpenAI (handled gracefully)
- [x] Very long response (thousands of words)

---

## Code Changes Summary

### Files Created
1. `/src/main/java/com/abhay/model/llm/StreamChunk.java` - Models OpenAI streaming format

### Files Modified

#### Backend (Java/Spring Boot)
1. `/src/main/java/com/abhay/model/llm/LLMRequest.java` - Added `stream` field
2. `/src/main/java/com/abhay/client/OpenAIClient.java` - Added `sendMessageStream()` method
3. `/src/main/java/com/abhay/service/ConversationService.java` - Added `sendMessageStream()` method
4. `/src/main/java/com/abhay/api/impl/ConversationController.java` - Added `/messages/stream` endpoint

#### Frontend (TypeScript/React)
1. `/frontend/src/services/api.ts` - Added `sendStream()` function
2. `/frontend/src/contexts/ChatContext.tsx` - Added streaming state and logic
3. `/frontend/src/components/chat/MessageList.tsx` - Added streaming message display
4. `/frontend/src/components/chat/MessageInput.tsx` - Added streaming state handling
5. `/frontend/src/types/index.ts` - No changes (existing types work)

---

## Future Enhancements

### Planned Features

1. **Function Calling / Tool Use**
   - Current streaming architecture supports this
   - OpenAI sends special "function_call" deltas
   - Can display "Calling calculator..." while executing

2. **RAG (Retrieval-Augmented Generation)**
   - Show "Searching knowledge base..." before streaming
   - Stream response after retrieval
   - Display sources alongside streaming text

3. **Multi-turn Reasoning**
   - Show intermediate reasoning steps as they stream
   - "Step 1: Understanding the question..."
   - "Step 2: Analyzing data..."

4. **Streaming with Images**
   - OpenAI can stream descriptions of generated images
   - Show progress: "Generating image... 25%"

5. **Token Count Display**
   - Show tokens used in real-time during streaming
   - Help users understand costs

### Technical Improvements

1. **Retry Logic**
   - Auto-retry if SSE connection drops mid-stream
   - Resume from last received chunk

2. **Compression**
   - Enable gzip compression for SSE
   - Reduce bandwidth for long responses

3. **Caching**
   - Cache common responses (e.g., "Hello", "What can you do?")
   - Return cached response with simulated streaming

4. **Rate Limiting**
   - Prevent users from spamming requests
   - Queue messages if streaming in progress

---

## Debugging Tips

### Backend Issues

**Problem:** "No chunks received from OpenAI"

**Check:**
```bash
# Look for these logs:
tail -f backend.log | grep "Extracted content"

# Should see:
INFO ... : Extracted content: ' Cricket'
INFO ... : Extracted content: ' is'
```

**Problem:** "JSON parsing error"

**Check:**
```bash
# Look for:
tail -f backend.log | grep "Unrecognized field"

# Fix: Add @JsonIgnoreProperties to the class
```

---

### Frontend Issues

**Problem:** "Chunks received but no spaces"

**Check browser console:**
```javascript
// Should see:
Chunk received: " Cricket"  // ✅ Has leading space
Chunk received: " is"       // ✅ Has leading space

// NOT:
Chunk received: "Cricket"   // ❌ Missing space
Chunk received: "is"        // ❌ Missing space
```

**Fix:** Remove `.trim()` from data extraction

---

**Problem:** "First message works, second breaks"

**Check:** State persistence in SSE parser. `currentEvent` and `currentData` must be outside the read loop.

---

## Performance Metrics

### Before Streaming
- Time to first token: ~3-5 seconds
- Total response time: 5-30 seconds
- User experience: Stare at spinner

### After Streaming
- Time to first token: ~300-500ms
- Total response time: Same (5-30 seconds)
- User experience: Start reading immediately

**Perception:** Feels 5-10x faster even though actual time is similar!

---

## Conclusion

Streaming implementation transforms the user experience from "waiting and hoping" to "reading and engaging". The technical complexity is manageable once you understand:

1. **SSE Format:** How Server-Sent Events work
2. **Reactive Streams:** Spring WebFlux and WebClient
3. **State Management:** React hooks and optimistic updates
4. **Edge Cases:** Space preservation, race conditions, error handling

This implementation prepares the architecture for advanced features like function calling, RAG, and agentic workflows - all of which rely on streaming to provide real-time feedback to users.

---

**Questions or Issues?**

Check the code comments in the modified files - each has detailed explanations of:
- Why it exists
- How it works
- What patterns it uses
- Edge cases to watch for

Happy streaming! 🚀
