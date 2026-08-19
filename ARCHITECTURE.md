# Nexa Architecture & Data Flow

## Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        YOUR APPLICATION                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐                                        │
│  │  ChatController  │  ← Handles HTTP requests               │
│  │  @RestController │                                        │
│  └────────┬─────────┘                                        │
│           │                                                   │
│           ↓                                                   │
│  ┌──────────────────┐                                        │
│  │   ChatService    │  ← Business logic                      │
│  │    @Service      │  ← Manages conversation context       │
│  └────────┬─────────┘                                        │
│           │                                                   │
│           ↓                                                   │
│  ┌──────────────────┐                                        │
│  │  OpenAIClient    │  ← HTTP client                         │
│  │   @Component     │  ← Makes API calls                     │
│  └────────┬─────────┘                                        │
│           │                                                   │
└───────────┼─────────────────────────────────────────────────┘
            │
            │ HTTPS POST
            │ Authorization: Bearer sk-...
            ↓
┌───────────────────────────────────────────────────────────┐
│                    OpenAI API                              │
│              https://api.openai.com/v1/                    │
└───────────────────────────────────────────────────────────┘
```

## Request Flow - Detailed

```
1. USER SENDS MESSAGE
   ↓
   POST /api/chat
   {
     "message": "What is 2+2?",
     "conversationHistory": []
   }

2. CONTROLLER RECEIVES
   ↓
   ChatController.chat()
   - Validates request
   - Calls service layer

3. SERVICE BUILDS MESSAGE ARRAY
   ↓
   ChatService.chat()
   [
     {"role": "system", "content": "You are a helpful assistant"},
     {"role": "user", "content": "What is 2+2?"}
   ]

4. CLIENT CALLS OPENAI
   ↓
   OpenAIClient.sendMessage()
   POST https://api.openai.com/v1/chat/completions
   {
     "model": "gpt-4o-mini",
     "messages": [...]
   }

5. OPENAI PROCESSES
   ↓
   - Reads all messages
   - Generates response
   - Returns JSON

6. CLIENT EXTRACTS RESPONSE
   ↓
   OpenAI returns:
   {
     "choices": [{
       "message": {
         "role": "assistant",
         "content": "2+2 equals 4"
       }
     }]
   }
   ↓
   Extract: "2+2 equals 4"

7. SERVICE BUILDS UPDATED HISTORY
   ↓
   [
     {"role": "user", "content": "What is 2+2?"},
     {"role": "assistant", "content": "2+2 equals 4"}
   ]

8. CONTROLLER RETURNS RESPONSE
   ↓
   {
     "message": "2+2 equals 4",
     "conversationHistory": [...]
   }
```

## Conversation Context Example

### First Request
```
Frontend sends:
{
  "message": "Hi, I'm John",
  "conversationHistory": []
}

Backend sends to OpenAI:
[
  {"role": "system", "content": "You are helpful"},
  {"role": "user", "content": "Hi, I'm John"}
]

OpenAI responds:
"Hello John! How can I help you?"

Backend returns:
{
  "message": "Hello John! How can I help you?",
  "conversationHistory": [
    {"role": "user", "content": "Hi, I'm John"},
    {"role": "assistant", "content": "Hello John! How can I help you?"}
  ]
}
```

### Second Request (WITH CONTEXT)
```
Frontend sends (includes history from previous response):
{
  "message": "What's my name?",
  "conversationHistory": [
    {"role": "user", "content": "Hi, I'm John"},
    {"role": "assistant", "content": "Hello John! How can I help you?"}
  ]
}

Backend sends to OpenAI:
[
  {"role": "system", "content": "You are helpful"},
  {"role": "user", "content": "Hi, I'm John"},           ← Previous
  {"role": "assistant", "content": "Hello John!..."},    ← Previous
  {"role": "user", "content": "What's my name?"}         ← New
]

OpenAI responds:
"Your name is John!"  ← AI remembers because we sent history!

Backend returns:
{
  "message": "Your name is John!",
  "conversationHistory": [
    {"role": "user", "content": "Hi, I'm John"},
    {"role": "assistant", "content": "Hello John! How can I help you?"},
    {"role": "user", "content": "What's my name?"},
    {"role": "assistant", "content": "Your name is John!"}
  ]
}
```

## Key Insight: Stateless API

```
❌ WRONG ASSUMPTION:
   OpenAI remembers previous requests
   
✅ REALITY:
   OpenAI is stateless - YOU must provide context
   
📝 HOW IT WORKS:
   Every request includes full conversation history
   The AI sees everything each time
```

## Data Models

### Message
```java
{
  "role": "user",        // "system" | "user" | "assistant"
  "content": "Hello"     // The actual text
}
```

### ChatRequest (from frontend)
```java
{
  "message": "New user message",
  "conversationHistory": [...]
}
```

### ChatResponse (to frontend)
```java
{
  "message": "AI response",
  "conversationHistory": [...]  // Updated with new exchange
}
```

### LLMRequest (to OpenAI)
```java
{
  "model": "gpt-4o-mini",
  "messages": [...],
  "temperature": 0.7,
  "maxTokens": 1000
}
```

### LLMResponse (from OpenAI)
```java
{
  "id": "chatcmpl-123",
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "The response text"
    }
  }],
  "usage": {
    "total_tokens": 42
  }
}
```

## Package Structure Explained

```
com.abhay/
├── Main.java                  ← Spring Boot entry point
│
├── controller/                ← REST API layer
│   └── ChatController.java    ← Handles HTTP requests
│                                 Validates input
│                                 Returns responses
│
├── service/                   ← Business logic layer
│   └── ChatService.java       ← Manages conversation flow
│                                 Builds message arrays
│                                 Updates history
│
├── client/                    ← External API layer
│   └── OpenAIClient.java      ← HTTP client to OpenAI
│                                 Authenticates
│                                 Sends/receives data
│
├── model/
│   ├── dto/                   ← Data Transfer Objects
│   │   ├── ChatRequest.java   ← API request format
│   │   └── ChatResponse.java  ← API response format
│   │
│   └── llm/                   ← LLM-specific models
│       ├── Message.java        ← Single message
│       ├── LLMRequest.java     ← OpenAI request
│       └── LLMResponse.java    ← OpenAI response
│
└── config/                    ← Configuration
    └── LLMConfig.java         ← WebClient bean
```

## Why This Architecture?

### Separation of Concerns
- **Controller**: HTTP handling only
- **Service**: Business logic
- **Client**: External API communication

### Benefits
- Easy to test each layer independently
- Easy to swap OpenAI for another provider
- Easy to add features (caching, rate limiting, etc.)
- Clear responsibilities

### Future Extensions
- Add database → Service layer
- Add authentication → Controller layer
- Add caching → Client layer
- Add new LLM provider → New client class
