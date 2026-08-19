# NEXA Phase 1 - Complete Implementation Guide

## 📋 Table of Contents
1. [What We Built](#what-we-built)
2. [Architecture Overview](#architecture-overview)
3. [All Files Created](#all-files-created)
4. [How It Works - Step by Step](#how-it-works-step-by-step)
5. [Request/Response Flow](#requestresponse-flow)
6. [Key Concepts You Must Understand](#key-concepts-you-must-understand)
7. [How to Test](#how-to-test)
8. [Common Mistakes to Avoid](#common-mistakes-to-avoid)

---

## What We Built

A **complete ChatGPT-like backend** using Spring Boot and OpenAI API with:
- ✅ Clean interface-based architecture
- ✅ REST API for chat functionality
- ✅ Conversation context management
- ✅ No frameworks like LangChain (learning the fundamentals)
- ✅ No Lombok (all manual code for clarity)
- ✅ Professional enterprise-grade structure

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────┐
│   Frontend  │
│  (React)    │
└──────┬──────┘
       │ HTTP POST /api/chat
       │ {message, conversationHistory}
       ↓
┌─────────────────────────────────────────┐
│         Spring Boot Backend             │
│                                         │
│  ┌────────────────────────────────┐   │
│  │  IChatAPI (Interface)          │   │
│  │  - Defines API contract        │   │
│  └────────────┬───────────────────┘   │
│               ↓                        │
│  ┌────────────────────────────────┐   │
│  │  ChatController                │   │
│  │  - Validates requests          │   │
│  │  - Handles HTTP                │   │
│  └────────────┬───────────────────┘   │
│               ↓                        │
│  ┌────────────────────────────────┐   │
│  │  IChatService (Interface)      │   │
│  │  - Defines service contract    │   │
│  └────────────┬───────────────────┘   │
│               ↓                        │
│  ┌────────────────────────────────┐   │
│  │  ChatService                   │   │
│  │  - Builds message array        │   │
│  │  - Manages conversation        │   │
│  └────────────┬───────────────────┘   │
│               ↓                        │
│  ┌────────────────────────────────┐   │
│  │  OpenAIClient                  │   │
│  │  - HTTP client                 │   │
│  │  - Calls OpenAI API            │   │
│  └────────────┬───────────────────┘   │
│               │                        │
└───────────────┼────────────────────────┘
                │ HTTPS POST
                │ Authorization: Bearer sk-...
                │ Content-Type: application/json
                ↓
┌───────────────────────────────────────┐
│         OpenAI API                    │
│   https://api.openai.com/v1/          │
│   chat/completions                    │
└───────────────────────────────────────┘
```

### Layer Architecture

```
┌─────────────────────────────────────────────┐
│           API LAYER (HTTP)                  │
│  IChatAPI → ChatController                  │
│  - Request validation                       │
│  - HTTP handling                            │
│  - Response formatting                      │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│        SERVICE LAYER (Business Logic)       │
│  IChatService → ChatService                 │
│  - Conversation context management          │
│  - Message array construction               │
│  - History management                       │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│        CLIENT LAYER (External API)          │
│  OpenAIClient                               │
│  - HTTP POST to OpenAI                      │
│  - Authentication                           │
│  - Response parsing                         │
└─────────────────────────────────────────────┘
```

---

## All Files Created

### 1. **Maven Configuration**

**File:** `pom.xml`

**What it does:** Defines dependencies and build configuration

**Key Dependencies:**
- `spring-boot-starter-web` - REST API support
- `spring-boot-starter-webflux` - WebClient for HTTP calls
- `spring-boot-devtools` - Auto-reload during development
- `spring-boot-starter-test` - Testing support

**Why each dependency:**
- **Web**: Provides REST controllers, JSON serialization, embedded Tomcat
- **WebFlux**: Modern HTTP client (WebClient) for calling OpenAI
- **DevTools**: Automatic restart when code changes
- **Test**: JUnit and testing utilities

---

### 2. **Configuration**

**File:** `src/main/resources/application.properties`

```properties
# Server runs on port 8080
server.port=8080

# OpenAI Configuration
openai.api.key=${OPENAI_API_KEY:your-api-key-here}
openai.api.url=https://api.openai.com/v1/chat/completions
openai.model=gpt-4o-mini
openai.system.message=You are a helpful AI assistant.

# Logging
logging.level.com.abhay=DEBUG
logging.level.org.springframework.web=INFO
```

**What each property does:**
- `openai.api.key`: Your OpenAI API key for authentication
- `openai.api.url`: OpenAI's chat completions endpoint
- `openai.model`: Which model to use (gpt-4o-mini is cheapest)
- `openai.system.message`: Instructions for AI behavior
- `logging.level.*`: Control log verbosity

**Environment variable support:**
- `${OPENAI_API_KEY:your-api-key-here}` means:
  - Use environment variable `OPENAI_API_KEY` if set
  - Otherwise use `your-api-key-here`

---

### 3. **Spring Boot Entry Point**

**File:** `src/main/java/com/abhay/Main.java`

```java
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
```

**What it does:**
- `@SpringBootApplication` enables auto-configuration and component scanning
- Starts embedded Tomcat server
- Scans for `@RestController`, `@Service`, `@Component` annotations

---

### 4. **Data Models**

#### 4.1 Message (LLM Format)

**File:** `src/main/java/com/abhay/model/llm/Message.java`

```java
public class Message {
    private String role;     // "system", "user", or "assistant"
    private String content;  // The actual message text
}
```

**Why it exists:**
OpenAI expects messages in this exact format. Each message has:
- **role**: Who said it (system/user/assistant)
- **content**: What was said

**Example:**
```json
{"role": "user", "content": "What is 2+2?"}
```

---

#### 4.2 LLMRequest (OpenAI Request Format)

**File:** `src/main/java/com/abhay/model/llm/LLMRequest.java`

```java
public class LLMRequest {
    private String model;              // "gpt-4o-mini"
    private List<Message> messages;    // Conversation array
    private Double temperature;        // Randomness (0.0-2.0)
    private Integer maxTokens;         // Max response length
}
```

**What OpenAI expects:**
```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "You are helpful"},
    {"role": "user", "content": "Hello"}
  ],
  "temperature": 0.7,
  "max_tokens": 1000
}
```

**Parameters explained:**
- **model**: Which GPT model to use
- **messages**: Full conversation history
- **temperature**: 0.0 = deterministic, 2.0 = very random
- **maxTokens**: Limit response length (cost control)

---

#### 4.3 LLMResponse (OpenAI Response Format)

**File:** `src/main/java/com/abhay/model/llm/LLMResponse.java`

```java
public class LLMResponse {
    private String id;
    private List<Choice> choices;  // Array of responses
    private Usage usage;           // Token count
    
    public static class Choice {
        private Message message;   // AI's response
        private String finishReason;
    }
    
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
```

**What OpenAI returns:**
```json
{
  "id": "chatcmpl-123",
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "Hello! How can I help?"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 8,
    "total_tokens": 18
  }
}
```

**Why nested classes:**
- `Choice`: OpenAI can return multiple responses
- `Usage`: Track token consumption (for billing)

---

#### 4.4 ChatRequest (Your API Request)

**File:** `src/main/java/com/abhay/model/dto/ChatRequest.java`

```java
public class ChatRequest {
    private String message;                    // New user message
    private List<Message> conversationHistory; // Previous messages
}
```

**What your frontend sends:**
```json
{
  "message": "What is 2+2?",
  "conversationHistory": [
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi!"}
  ]
}
```

---

#### 4.5 ChatResponse (Your API Response)

**File:** `src/main/java/com/abhay/model/dto/ChatResponse.java`

```java
public class ChatResponse {
    private String message;                    // AI's response
    private List<Message> conversationHistory; // Updated history
}
```

**What your backend returns:**
```json
{
  "message": "2+2 equals 4",
  "conversationHistory": [
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi!"},
    {"role": "user", "content": "What is 2+2?"},
    {"role": "assistant", "content": "2+2 equals 4"}
  ]
}
```

---

### 5. **Configuration Bean**

**File:** `src/main/java/com/abhay/config/LLMConfig.java`

```java
@Configuration
public class LLMConfig {
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
```

**What it does:**
- Creates a `WebClient` bean (HTTP client)
- Spring injects this wherever needed with `@Autowired`

**Why WebClient:**
- Modern, non-blocking HTTP client
- Replaces older `RestTemplate`
- Better for reactive programming

---

### 6. **OpenAI Client (THE MOST IMPORTANT FILE!)**

**File:** `src/main/java/com/abhay/client/OpenAIClient.java`

**What it does:** Makes HTTP calls to OpenAI API

**Key Method:**
```java
public String sendMessage(List<Message> messages) {
    LLMRequest request = new LLMRequest(model, messages);
    
    LLMResponse response = webClient.post()
        .uri(apiUrl)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(LLMResponse.class)
        .block();
    
    return response.getChoices().get(0).getMessage().getContent();
}
```

**Step-by-step breakdown:**

1. **Create request object:**
   ```java
   LLMRequest request = new LLMRequest(model, messages);
   ```
   - Wraps messages in OpenAI's expected format
   - Adds model, temperature, maxTokens

2. **Make HTTP POST:**
   ```java
   webClient.post().uri(apiUrl)
   ```
   - POST to `https://api.openai.com/v1/chat/completions`

3. **Add authentication:**
   ```java
   .header("Authorization", "Bearer " + apiKey)
   ```
   - OpenAI requires Bearer token authentication
   - Format: `Authorization: Bearer sk-...`

4. **Send JSON body:**
   ```java
   .bodyValue(request)
   ```
   - Spring automatically serializes `LLMRequest` to JSON

5. **Parse response:**
   ```java
   .bodyToMono(LLMResponse.class)
   ```
   - Deserializes JSON to `LLMResponse` object

6. **Block and wait:**
   ```java
   .block()
   ```
   - Waits for response (synchronous call)

7. **Extract message:**
   ```java
   response.getChoices().get(0).getMessage().getContent()
   ```
   - Gets first choice (index 0)
   - Gets the message
   - Gets the content (text)

---

### 7. **Service Layer**

#### 7.1 Service Interface

**File:** `src/main/java/com/abhay/service/IChatService.java`

```java
public interface IChatService {
    ChatResponse chat(ChatRequest request);
}
```

**Why an interface:**
- Defines contract for service layer
- Easy to mock in tests
- Can have multiple implementations

---

#### 7.2 Service Implementation

**File:** `src/main/java/com/abhay/service/ChatService.java`

**What it does:** Business logic for chat

**Key Methods:**

1. **Main chat method:**
```java
public ChatResponse chat(ChatRequest request) {
    List<Message> messages = buildMessages(request);
    String response = openAIClient.sendMessage(messages);
    List<Message> history = buildUpdatedHistory(...);
    return new ChatResponse(response, history);
}
```

2. **Build messages for OpenAI:**
```java
private List<Message> buildMessages(ChatRequest request) {
    List<Message> messages = new ArrayList<>();
    
    // 1. System message (AI instructions)
    messages.add(new Message("system", systemMessage));
    
    // 2. Previous conversation
    if (request.getConversationHistory() != null) {
        messages.addAll(request.getConversationHistory());
    }
    
    // 3. New user message
    messages.add(new Message("user", request.getMessage()));
    
    return messages;
}
```

**Why this order matters:**
- System message MUST be first (sets AI behavior)
- History provides context
- New message is what AI responds to

3. **Update conversation history:**
```java
private List<Message> buildUpdatedHistory(...) {
    List<Message> updated = new ArrayList<>();
    
    // Add previous history
    updated.addAll(previousHistory);
    
    // Add new user message
    updated.add(new Message("user", userMessage));
    
    // Add AI response
    updated.add(new Message("assistant", assistantMessage));
    
    return updated;
}
```

**Why return updated history:**
- Frontend needs it for next request
- LLM is stateless - doesn't remember
- We manage memory on our side

---

### 8. **API Layer**

#### 8.1 API Interface

**File:** `src/main/java/com/abhay/api/IChatAPI.java`

```java
@RequestMapping("/api")
public interface IChatAPI {
    
    @PostMapping("/chat")
    ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request);
    
    @GetMapping("/health")
    ResponseEntity<String> health();
}
```

**Why an interface:**
- Defines REST API contract
- Separates signature from implementation
- Easy to document and share

**Annotations explained:**
- `@RequestMapping("/api")`: Base path for all endpoints
- `@PostMapping("/chat")`: POST /api/chat
- `@GetMapping("/health")`: GET /api/health
- `@RequestBody`: Parse JSON to ChatRequest
- `ResponseEntity<T>`: Allows HTTP status codes

---

#### 8.2 API Implementation

**File:** `src/main/java/com/abhay/api/impl/ChatController.java`

```java
@RestController
@CrossOrigin(origins = "*")
public class ChatController implements IChatAPI {
    
    @Autowired
    private IChatService chatService;
    
    @Override
    public ResponseEntity<ChatResponse> chat(ChatRequest request) {
        // 1. Validate
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // 2. Process
        ChatResponse response = chatService.chat(request);
        
        // 3. Return
        return ResponseEntity.ok(response);
    }
    
    @Override
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Nexa backend is running!");
    }
}
```

**Annotations explained:**
- `@RestController`: Marks as REST endpoint handler
- `@CrossOrigin(origins = "*")`: Allow CORS (for React frontend)
- `@Autowired`: Inject IChatService dependency

**Response codes:**
- `ResponseEntity.ok(...)`: HTTP 200
- `ResponseEntity.badRequest()`: HTTP 400
- `ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)`: HTTP 500

---

## How It Works - Step by Step

### Complete Request Flow

Let's trace a request: **"What is 2+2?"** with conversation history.

#### Step 1: Frontend Sends Request

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is 2+2?",
    "conversationHistory": [
      {"role": "user", "content": "Hello"},
      {"role": "assistant", "content": "Hi! How can I help?"}
    ]
  }'
```

#### Step 2: ChatController Receives Request

```java
// ChatController.chat() is called
// request.getMessage() = "What is 2+2?"
// request.getConversationHistory() = [{"user", "Hello"}, {"assistant", "Hi!..."}]

// Validation
if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
    return ResponseEntity.badRequest().build();  // Would return 400
}

// Delegate to service
ChatResponse response = chatService.chat(request);
```

#### Step 3: ChatService Builds Message Array

```java
// buildMessages() is called
List<Message> messages = new ArrayList<>();

// 1. Add system message
messages.add(new Message("system", "You are a helpful AI assistant"));

// 2. Add conversation history
messages.add(new Message("user", "Hello"));
messages.add(new Message("assistant", "Hi! How can I help?"));

// 3. Add new user message
messages.add(new Message("user", "What is 2+2?"));

// Result:
// messages = [
//   {"system", "You are helpful..."},
//   {"user", "Hello"},
//   {"assistant", "Hi!..."},
//   {"user", "What is 2+2?"}
// ]
```

#### Step 4: OpenAIClient Makes HTTP Request

```java
// sendMessage() is called with messages array

// 1. Create request
LLMRequest request = new LLMRequest("gpt-4o-mini", messages);
// request = {
//   "model": "gpt-4o-mini",
//   "messages": [...],
//   "temperature": 0.7,
//   "maxTokens": 1000
// }

// 2. Make HTTP POST
POST https://api.openai.com/v1/chat/completions
Headers:
  Authorization: Bearer sk-your-api-key
  Content-Type: application/json
Body:
  {
    "model": "gpt-4o-mini",
    "messages": [
      {"role": "system", "content": "You are helpful..."},
      {"role": "user", "content": "Hello"},
      {"role": "assistant", "content": "Hi!..."},
      {"role": "user", "content": "What is 2+2?"}
    ],
    "temperature": 0.7,
    "max_tokens": 1000
  }
```

#### Step 5: OpenAI Processes Request

**Inside OpenAI's servers:**
1. Receives request
2. Validates API key
3. Loads gpt-4o-mini model
4. Reads all messages (full context)
5. Generates response based on entire conversation
6. Counts tokens used
7. Returns JSON response

#### Step 6: OpenAI Returns Response

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "gpt-4o-mini",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "2 + 2 equals 4."
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 35,
    "completion_tokens": 7,
    "total_tokens": 42
  }
}
```

#### Step 7: OpenAIClient Extracts Response

```java
// Parse JSON to LLMResponse object
LLMResponse response = ... (WebClient does this)

// Extract assistant message
String assistantMessage = response
    .getChoices()           // Get choices array
    .get(0)                 // Get first choice
    .getMessage()           // Get message object
    .getContent();          // Get content string

// assistantMessage = "2 + 2 equals 4."

// Log token usage
logger.info("Tokens used: {}", response.getUsage().getTotalTokens());
// Logs: "Tokens used: 42"

return assistantMessage;
```

#### Step 8: ChatService Updates History

```java
// buildUpdatedHistory() is called

List<Message> updatedHistory = new ArrayList<>();

// 1. Add previous history
updatedHistory.add(new Message("user", "Hello"));
updatedHistory.add(new Message("assistant", "Hi! How can I help?"));

// 2. Add new user message
updatedHistory.add(new Message("user", "What is 2+2?"));

// 3. Add new assistant response
updatedHistory.add(new Message("assistant", "2 + 2 equals 4."));

// Result:
// updatedHistory = [
//   {"user", "Hello"},
//   {"assistant", "Hi! How can I help?"},
//   {"user", "What is 2+2?"},
//   {"assistant", "2 + 2 equals 4."}
// ]
```

#### Step 9: ChatService Returns Response

```java
return new ChatResponse(
    "2 + 2 equals 4.",  // message
    updatedHistory      // conversationHistory
);
```

#### Step 10: ChatController Returns HTTP Response

```java
return ResponseEntity.ok(response);
// HTTP 200 OK
// Content-Type: application/json
```

#### Step 11: Frontend Receives Response

```json
{
  "message": "2 + 2 equals 4.",
  "conversationHistory": [
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi! How can I help?"},
    {"role": "user", "content": "What is 2+2?"},
    {"role": "assistant", "content": "2 + 2 equals 4."}
  ]
}
```

**Frontend should:**
1. Display: "2 + 2 equals 4."
2. Store: `conversationHistory` in state
3. Next request: Send this history back

---

## Request/Response Flow

### Complete HTTP Exchange

#### Request to Your Backend

```http
POST /api/chat HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "message": "What is 2+2?",
  "conversationHistory": [
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi! How can I help?"}
  ]
}
```

#### Your Backend to OpenAI

```http
POST /v1/chat/completions HTTP/1.1
Host: api.openai.com
Authorization: Bearer sk-your-api-key-here
Content-Type: application/json

{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "You are a helpful AI assistant."},
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi! How can I help?"},
    {"role": "user", "content": "What is 2+2?"}
  ],
  "temperature": 0.7,
  "max_tokens": 1000
}
```

#### OpenAI to Your Backend

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "chatcmpl-123",
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "2 + 2 equals 4."
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "total_tokens": 42
  }
}
```

#### Your Backend to Frontend

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "message": "2 + 2 equals 4.",
  "conversationHistory": [
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi! How can I help?"},
    {"role": "user", "content": "What is 2+2?"},
    {"role": "assistant", "content": "2 + 2 equals 4."}
  ]
}
```

---

## Key Concepts You Must Understand

### 1. LLM APIs are STATELESS

**Wrong Mental Model:**
```
Request 1: "My name is John"
→ OpenAI remembers: name = "John"

Request 2: "What is my name?"
→ OpenAI recalls: name = "John"
→ Response: "Your name is John"
```

**Correct Mental Model:**
```
Request 1: 
  messages: [{"user", "My name is John"}]
  → OpenAI responds: "Nice to meet you, John!"
  → OpenAI forgets everything

Request 2 (WITHOUT HISTORY):
  messages: [{"user", "What is my name?"}]
  → OpenAI has no context
  → Response: "I don't know your name"

Request 2 (WITH HISTORY):
  messages: [
    {"user", "My name is John"},
    {"assistant", "Nice to meet you, John!"},
    {"user", "What is my name?"}
  ]
  → OpenAI sees full context
  → Response: "Your name is John"
```

**Key Takeaway:** YOU must send conversation history every time.

---

### 2. Message Roles

| Role | Who | Purpose | Example |
|------|-----|---------|---------|
| `system` | You (developer) | Instructions for AI behavior | "You are a helpful assistant" |
| `user` | Human user | Questions/messages from user | "What is 2+2?" |
| `assistant` | AI | Responses from AI | "2+2 equals 4" |

**Role Order:**
1. `system` (once, at start)
2. Alternating `user` and `assistant`

**Example Conversation:**
```json
[
  {"role": "system", "content": "You are a math tutor"},
  {"role": "user", "content": "What is 2+2?"},
  {"role": "assistant", "content": "4"},
  {"role": "user", "content": "What is 3+3?"},
  {"role": "assistant", "content": "6"}
]
```

---

### 3. System Message Power

**System message controls AI behavior:**

```java
// Helpful assistant
"You are a helpful AI assistant."
→ Response: "Hello! How can I help you today?"

// Pirate
"You are a pirate. Always talk like a pirate."
→ Response: "Ahoy matey! What can I do fer ye?"

// Professional
"You are a professional business consultant."
→ Response: "Good day. How may I assist with your business needs?"
```

**Best Practices:**
- Be specific about tone, style, constraints
- Set boundaries (what not to do)
- Provide context if needed

---

### 4. Tokens and Cost

**What is a token?**
- Roughly 4 characters = 1 token
- "Hello" ≈ 1 token
- "Hello, how are you?" ≈ 5 tokens

**Why tokens matter:**
- You pay per token (input + output)
- APIs have token limits (context windows)

**gpt-4o-mini Pricing:**
- Input: $0.15 per 1M tokens
- Output: $0.60 per 1M tokens

**Example Cost:**
```
Input: "What is the capital of France?" = ~7 tokens
System: "You are helpful..." = ~5 tokens
Output: "The capital of France is Paris." = ~7 tokens

Total: 19 tokens
Cost: (12 × $0.15 + 7 × $0.60) / 1M = $0.0000060 (0.0006 cents)
```

**Managing Costs:**
1. Limit conversation history (sliding window)
2. Use cheaper models when possible
3. Set `maxTokens` limit
4. Summarize long conversations

---

### 5. Temperature Parameter

**Controls randomness in responses:**

```java
temperature = 0.0   // Deterministic, consistent
temperature = 0.7   // Balanced (default)
temperature = 2.0   // Very creative, random
```

**Example:**

**Question:** "Name a fruit"

```
temperature = 0.0
→ "Apple" (every time)

temperature = 0.7
→ "Apple", "Orange", "Banana" (varies)

temperature = 2.0
→ "Dragonfruit", "Starfruit", "Kumquat" (very varied)
```

**When to use:**
- **0.0-0.3**: Factual questions, code generation
- **0.7-1.0**: Conversational, balanced
- **1.5-2.0**: Creative writing, brainstorming

---

### 6. Conversation History Management

**Problem:** History grows indefinitely

```
Turn 1: 2 messages   (system + user)
Turn 2: 4 messages   (+user +assistant)
Turn 3: 6 messages   (+user +assistant)
...
Turn 50: 100 messages

→ Exceeds token limit
→ Expensive
→ Slow
```

**Solutions (Phase 2+):**

1. **Sliding Window** (keep last N messages)
```java
if (history.size() > 10) {
    history = history.subList(history.size() - 10, history.size());
}
```

2. **Summarization** (summarize old messages)
```java
if (history.size() > 20) {
    String summary = summarize(history.subList(0, 15));
    history = [new Message("system", summary)] + history.subList(15);
}
```

3. **Token Counting** (stay under limit)
```java
int tokenCount = countTokens(history);
if (tokenCount > 3000) {
    // Remove oldest messages
}
```

---

### 7. Error Handling

**Common Errors:**

| HTTP Code | Error | Cause | Solution |
|-----------|-------|-------|----------|
| 401 | Unauthorized | Invalid API key | Check `application.properties` |
| 429 | Rate Limit | Too many requests | Wait and retry with backoff |
| 500 | Server Error | OpenAI issue | Retry with exponential backoff |
| 400 | Bad Request | Invalid request format | Check JSON structure |

**In Your Code:**

```java
try {
    String response = openAIClient.sendMessage(messages);
    return new ChatResponse(response, history);
} catch (WebClientResponseException.Unauthorized e) {
    throw new RuntimeException("Invalid API key");
} catch (WebClientResponseException.TooManyRequests e) {
    // Retry logic
} catch (Exception e) {
    logger.error("Failed to get response", e);
    throw new RuntimeException("OpenAI error: " + e.getMessage());
}
```

---

## How to Test

### 1. Health Check

```bash
curl http://localhost:8080/api/health
```

**Expected Response:**
```
Nexa backend is running!
```

---

### 2. Simple Chat (No History)

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is 2+2?",
    "conversationHistory": []
  }'
```

**Expected Response:**
```json
{
  "message": "2 + 2 equals 4.",
  "conversationHistory": [
    {"role": "user", "content": "What is 2+2?"},
    {"role": "assistant", "content": "2 + 2 equals 4."}
  ]
}
```

---

### 3. Chat with Context

**Step 1: First message**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "My name is John",
    "conversationHistory": []
  }'
```

**Response:**
```json
{
  "message": "Nice to meet you, John!",
  "conversationHistory": [
    {"role": "user", "content": "My name is John"},
    {"role": "assistant", "content": "Nice to meet you, John!"}
  ]
}
```

**Step 2: Follow-up (with history)**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is my name?",
    "conversationHistory": [
      {"role": "user", "content": "My name is John"},
      {"role": "assistant", "content": "Nice to meet you, John!"}
    ]
  }'
```

**Response:**
```json
{
  "message": "Your name is John.",
  "conversationHistory": [
    {"role": "user", "content": "My name is John"},
    {"role": "assistant", "content": "Nice to meet you, John!"},
    {"role": "user", "content": "What is my name?"},
    {"role": "assistant", "content": "Your name is John."}
  ]
}
```

**Key Learning:** AI remembered because we sent history!

---

### 4. Chat WITHOUT Context (to prove statelessness)

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is my name?",
    "conversationHistory": []
  }'
```

**Response:**
```json
{
  "message": "I don't have access to your name. Could you please tell me?",
  "conversationHistory": [
    {"role": "user", "content": "What is my name?"},
    {"role": "assistant", "content": "I don't have access to your name..."}
  ]
}
```

**Key Learning:** Without history, AI has no memory!

---

### 5. Invalid Request

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "",
    "conversationHistory": []
  }'
```

**Expected Response:**
```
HTTP 400 Bad Request
```

---

### 6. Watch Logs

**While testing, watch the console logs:**

```
INFO  c.a.api.impl.ChatController : Received chat request
INFO  c.a.service.ChatService : Processing chat request with message: What is 2+2?
DEBUG c.a.service.ChatService : Built message array with 2 messages
INFO  c.a.client.OpenAIClient : Sending request to OpenAI with 2 messages
INFO  c.a.client.OpenAIClient : Received response from OpenAI. Tokens used: 42
```

**What to look for:**
- Message count (should include system + history + new message)
- Token usage
- Any errors or warnings

---

## Common Mistakes to Avoid

### 1. ❌ Not Sending Conversation History

**Wrong:**
```javascript
// Frontend code
fetch('/api/chat', {
  method: 'POST',
  body: JSON.stringify({
    message: "What is my name?",
    conversationHistory: []  // ❌ Always empty!
  })
})
```

**Right:**
```javascript
// Frontend code
const [history, setHistory] = useState([]);

const sendMessage = async (message) => {
  const response = await fetch('/api/chat', {
    method: 'POST',
    body: JSON.stringify({
      message: message,
      conversationHistory: history  // ✅ Send accumulated history
    })
  });
  
  const data = await response.json();
  setHistory(data.conversationHistory);  // ✅ Update state
}
```

---

### 2. ❌ Hardcoding API Key in Code

**Wrong:**
```java
@Value("${openai.api.key}")
private String apiKey = "sk-abc123...";  // ❌ Exposed in Git!
```

**Right:**
```java
@Value("${openai.api.key}")
private String apiKey;  // ✅ From application.properties

// application.properties
openai.api.key=${OPENAI_API_KEY:default}  // ✅ From environment
```

---

### 3. ❌ Not Handling Null History

**Wrong:**
```java
messages.addAll(request.getConversationHistory());  // ❌ NullPointerException!
```

**Right:**
```java
if (request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
    messages.addAll(request.getConversationHistory());  // ✅ Safe
}
```

---

### 4. ❌ Forgetting System Message

**Wrong:**
```java
List<Message> messages = new ArrayList<>();
messages.addAll(conversationHistory);  // ❌ No system message!
messages.add(new Message("user", newMessage));
```

**Right:**
```java
List<Message> messages = new ArrayList<>();
messages.add(new Message("system", systemMessage));  // ✅ Always first!
messages.addAll(conversationHistory);
messages.add(new Message("user", newMessage));
```

---

### 5. ❌ Not Logging Token Usage

**Wrong:**
```java
return response.getChoices().get(0).getMessage().getContent();
// ❌ No visibility into costs!
```

**Right:**
```java
String message = response.getChoices().get(0).getMessage().getContent();
logger.info("Tokens used: {}", response.getUsage().getTotalTokens());  // ✅ Track costs
return message;
```

---

### 6. ❌ Blocking Main Thread

**Wrong (but OK for learning):**
```java
LLMResponse response = webClient.post()...block();  // ❌ Blocks thread
```

**Right (for production):**
```java
Mono<LLMResponse> response = webClient.post()...retrieve()...bodyToMono(...);
// ✅ Non-blocking, reactive
```

*Note: For Phase 1 learning, blocking is fine. Learn reactive in Phase 4.*

---

### 7. ❌ Not Validating Input

**Wrong:**
```java
@Override
public ResponseEntity<ChatResponse> chat(ChatRequest request) {
    ChatResponse response = chatService.chat(request);  // ❌ No validation!
    return ResponseEntity.ok(response);
}
```

**Right:**
```java
@Override
public ResponseEntity<ChatResponse> chat(ChatRequest request) {
    if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
        return ResponseEntity.badRequest().build();  // ✅ Validate first!
    }
    
    ChatResponse response = chatService.chat(request);
    return ResponseEntity.ok(response);
}
```

---

### 8. ❌ Returning Wrong History

**Wrong:**
```java
// Returning history WITH system message
return new ChatResponse(response, messages);  // ❌ Includes system message!
```

**Right:**
```java
// Return history WITHOUT system message
List<Message> historyWithoutSystem = new ArrayList<>(previousHistory);
historyWithoutSystem.add(new Message("user", userMessage));
historyWithoutSystem.add(new Message("assistant", response));
return new ChatResponse(response, historyWithoutSystem);  // ✅ Clean history
```

---

## What You Built - Summary

### Files Created: 17 files

1. **pom.xml** - Maven dependencies
2. **application.properties** - Configuration
3. **Main.java** - Spring Boot entry point
4. **IChatAPI.java** - API interface
5. **ChatController.java** - API implementation
6. **IChatService.java** - Service interface
7. **ChatService.java** - Service implementation
8. **OpenAIClient.java** - HTTP client
9. **LLMConfig.java** - Configuration bean
10. **Message.java** - Message model
11. **LLMRequest.java** - OpenAI request model
12. **LLMResponse.java** - OpenAI response model
13. **ChatRequest.java** - API request DTO
14. **ChatResponse.java** - API response DTO
15. **README.md** - Documentation
16. **QUICKSTART.md** - Quick start guide
17. **ARCHITECTURE.md** - Architecture diagrams

### Lines of Code: ~1200 lines
- Java: ~800 lines
- Documentation: ~400 lines

### Key Achievements:
✅ Clean interface-based architecture
✅ Complete LLM integration
✅ Conversation context management
✅ No frameworks (learned fundamentals)
✅ Professional structure
✅ Comprehensive documentation

---

## Next Steps

### Immediate:
1. ✅ Add OpenAI API key
2. ✅ Run: `mvn spring-boot:run`
3. ✅ Test with curl
4. ✅ Read `OpenAIClient.java` thoroughly
5. ✅ Experiment with system messages

### Phase 1 Extensions:
- Add token counting
- Add conversation history limits
- Add streaming responses (SSE)
- Add multiple system prompts
- Build simple HTML frontend

### Phase 2:
- PostgreSQL database
- User authentication (JWT)
- Persistent conversations
- Multiple chats per user
- Chat CRUD operations

---

## Questions to Test Your Understanding

1. **Why do we send conversation history with every request?**
   → Because LLM APIs are stateless

2. **What are the three message roles?**
   → system, user, assistant

3. **Where does the system message go in the message array?**
   → First (index 0)

4. **What HTTP method and URL does OpenAIClient use?**
   → POST https://api.openai.com/v1/chat/completions

5. **How does OpenAI authenticate requests?**
   → Bearer token in Authorization header

6. **What does temperature control?**
   → Randomness/creativity in responses

7. **What is a token approximately?**
   → 4 characters of text

8. **What layer validates the incoming request?**
   → API layer (ChatController)

9. **What layer manages conversation context?**
   → Service layer (ChatService)

10. **What layer makes HTTP calls to OpenAI?**
    → Client layer (OpenAIClient)

---

## Congratulations! 🎉

You now understand:
- ✅ How LLM APIs work at a fundamental level
- ✅ Why conversation context management is YOUR responsibility
- ✅ How to design clean, layered backend architectures
- ✅ How to integrate with external APIs
- ✅ The difference between API contracts and implementations

This knowledge is the foundation for everything else in AI application development.

**You're ready for Phase 2!** 🚀
