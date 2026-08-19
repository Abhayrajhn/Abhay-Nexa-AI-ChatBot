# Nexa - AI Chat Application (Phase 1)

A learning project to build a ChatGPT-like application from scratch using Spring Boot and OpenAI.

## What You'll Learn in Phase 1

- How LLM APIs work (OpenAI Chat Completions API)
- What system/user/assistant messages mean
- How conversation context is maintained (stateless API + history management)
- How to design REST APIs for AI applications
- How Spring Boot communicates with external LLM services

## Architecture

```
[Frontend] 
    ↓ POST /api/chat
[ChatController] 
    ↓
[ChatService] - Business logic, manages conversation context
    ↓
[OpenAIClient] - HTTP client to OpenAI API
    ↓
[OpenAI API] - Returns AI response
```

## Project Structure

```
src/main/java/com/abhay/
├── Main.java                    - Spring Boot entry point
├── controller/
│   └── ChatController.java      - REST API endpoint
├── service/
│   └── ChatService.java         - Business logic, conversation management
├── client/
│   └── OpenAIClient.java        - OpenAI API client (CORE learning component)
├── model/
│   ├── dto/
│   │   ├── ChatRequest.java     - Request from frontend
│   │   └── ChatResponse.java    - Response to frontend
│   └── llm/
│       ├── Message.java          - Single message (role + content)
│       ├── LLMRequest.java       - OpenAI API request structure
│       └── LLMResponse.java      - OpenAI API response structure
└── config/
    └── LLMConfig.java           - WebClient configuration
```

## Setup Instructions

### 1. Get OpenAI API Key

1. Go to https://platform.openai.com/
2. Sign up or log in
3. Navigate to API keys
4. Create a new API key
5. Copy the key (starts with `sk-`)

### 2. Configure the Application

Edit `src/main/resources/application.properties`:

```properties
openai.api.key=sk-your-actual-api-key-here
```

**IMPORTANT**: Replace `your-api-key-here` with your actual OpenAI API key.

### 3. Build the Project

```bash
mvn clean install
```

This will download all dependencies (Spring Boot, WebFlux, Lombok, etc.)

### 4. Run the Application

```bash
mvn spring-boot:run
```

The server will start on `http://localhost:8080`

## Testing the API

### Using curl

**Test 1: Simple message without history**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is the capital of France?",
    "conversationHistory": []
  }'
```

**Test 2: Message with conversation history**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is its population?",
    "conversationHistory": [
      {"role": "user", "content": "What is the capital of France?"},
      {"role": "assistant", "content": "The capital of France is Paris."}
    ]
  }'
```

**Test 3: Health check**

```bash
curl http://localhost:8080/api/health
```

### Using Postman

1. Create a new POST request to `http://localhost:8080/api/chat`
2. Set header: `Content-Type: application/json`
3. Body (raw JSON):
```json
{
  "message": "Hello! What can you help me with?",
  "conversationHistory": []
}
```

## How It Works - Step by Step

1. **User sends a message** via POST to `/api/chat`
2. **ChatController** receives the request, validates it
3. **ChatService** builds the message array:
   - Adds system message ("You are a helpful assistant")
   - Adds conversation history (if any)
   - Adds the new user message
4. **OpenAIClient** makes HTTP POST to OpenAI:
   - Sends: `{"model": "gpt-4o-mini", "messages": [...]}`
   - Authenticates with API key in Authorization header
5. **OpenAI API** processes the request and returns JSON response
6. **OpenAIClient** extracts the assistant's message
7. **ChatService** builds updated conversation history
8. **ChatController** returns response to frontend

## Key Concepts Explained

### Message Roles

- **system**: Instructions for AI behavior (e.g., "You are a helpful assistant")
- **user**: Messages from the human
- **assistant**: Messages from the AI

### Conversation Context

- LLM APIs are **stateless** - they don't remember previous conversations
- Your app must send the **entire conversation history** with each request
- The conversation history grows: `[msg1, response1, msg2, response2, ...]`
- Later you'll need to manage history size (APIs have token limits)

### Request Flow

```
Frontend → Backend → OpenAI
   ↓         ↓          ↓
 "Hi"    + system     processes
         + history    all messages
         + "Hi"       
                       ↓
Frontend ← Backend ← "Hello! How can I help?"
```

## Troubleshooting

### Error: "Unauthorized" (401)
- Check your API key in `application.properties`
- Ensure the key starts with `sk-`
- Verify the key is valid at OpenAI platform

### Error: "Rate limit exceeded" (429)
- You've used too many tokens
- Wait a few minutes or upgrade your OpenAI plan

### Error: "Model not found"
- Check the model name in `application.properties`
- Use: `gpt-4o-mini`, `gpt-4o`, `gpt-3.5-turbo`

### Application won't start
- Check if port 8080 is already in use
- Run `mvn clean install` first
- Check Java version: `java -version` (should be 21)

## What's Next (Future Phases)

- **Phase 2**: Add PostgreSQL, authentication, persistent conversations
- **Phase 3**: File uploads, document processing, embeddings, RAG
- **Phase 4**: Turn into an agent with tool calling
- **Phase 5**: Integrate MCP (Model Context Protocol)
- **Phase 6**: Multi-agent orchestration, observability

## Understanding the Code

Start reading in this order:
1. `Main.java` - Entry point
2. `model/dto/` - See what frontend sends/receives
3. `model/llm/` - See OpenAI's API structure
4. `client/OpenAIClient.java` - **MOST IMPORTANT** - See how LLM APIs work
5. `service/ChatService.java` - See how conversation context is managed
6. `controller/ChatController.java` - See the REST API

## Costs

- `gpt-4o-mini`: ~$0.15 per 1M input tokens, ~$0.60 per 1M output tokens
- A typical chat message uses ~100-500 tokens
- You can chat for hours with just a few dollars
- Monitor usage at: https://platform.openai.com/usage
