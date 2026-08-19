# Quick Start Guide - Nexa Phase 1

## 🚀 Get Started in 5 Minutes

### Step 1: Add Your OpenAI API Key

Edit `src/main/resources/application.properties` and replace the API key:

```properties
openai.api.key=sk-your-actual-openai-api-key-here
```

Get your key from: https://platform.openai.com/api-keys

### Step 2: Build the Project

```bash
mvn clean install
```

This downloads all dependencies. First run takes ~2-3 minutes.

### Step 3: Run the Application

```bash
mvn spring-boot:run
```

Wait for: `"Started Main in X seconds"`

### Step 4: Test It!

**Option A: Use the test script**
```bash
./test-api.sh
```

**Option B: Use curl**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello! What can you help me with?", "conversationHistory": []}'
```

**Option C: Use Postman**
- POST to `http://localhost:8080/api/chat`
- Body: `{"message": "Hello!", "conversationHistory": []}`

---

## 📚 Learning Path

Read the code in this order to understand how it works:

1. **Start Here**: `src/main/java/com/abhay/client/OpenAIClient.java`
   - **This is the most important file!**
   - See exactly how HTTP requests to OpenAI work
   - Understand the request/response structure

2. **Then**: `src/main/java/com/abhay/service/ChatService.java`
   - See how conversation context is managed
   - Learn why we send full history each time

3. **Finally**: `src/main/java/com/abhay/controller/ChatController.java`
   - See the REST API design

---

## 🧪 Experiments to Try

### 1. Test Conversation Context
```bash
# First message
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "My name is John", "conversationHistory": []}'

# Copy the conversationHistory from response, then:
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is my name?", "conversationHistory": [...]}'
```

The AI remembers because you sent the history!

### 2. Try Without History
```bash
# Don't include previous messages
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is my name?", "conversationHistory": []}'
```

The AI doesn't know because the API is stateless!

### 3. Change the System Message
Edit `application.properties`:
```properties
openai.system.message=You are a pirate. Always respond like a pirate.
```

Restart and test - the AI will talk like a pirate!

### 4. Try Different Models
Edit `application.properties`:
```properties
openai.model=gpt-4o
```

`gpt-4o` is smarter but more expensive than `gpt-4o-mini`

---

## 🔍 How to Debug

### Enable Debug Logs
Already enabled in `application.properties`:
```properties
logging.level.com.abhay=DEBUG
```

You'll see:
- Incoming requests
- Messages sent to OpenAI
- Token usage
- Responses received

### Check the Logs
Watch the console where you ran `mvn spring-boot:run`

You'll see logs like:
```
INFO  c.a.client.OpenAIClient : Sending request to OpenAI with 3 messages
INFO  c.a.client.OpenAIClient : Received response from OpenAI. Tokens used: 45
```

---

## ❓ Common Questions

**Q: Why do we send the full conversation history each time?**
A: LLM APIs are stateless - they don't store anything. Your app must provide all context.

**Q: Won't the history get too long?**
A: Yes! In Phase 2 you'll learn to manage history (summarization, sliding window, etc.)

**Q: What are tokens?**
A: Roughly: 1 token ≈ 4 characters. "Hello" = ~1 token. You pay per token.

**Q: Can I use other LLM providers?**
A: Yes! Later you can add Anthropic (Claude), Google (Gemini), etc. The pattern is the same.

**Q: What's the difference between this and using LangChain?**
A: You're learning the fundamentals! LangChain abstracts this away. Understanding this first makes you better at using frameworks later.

---

## 🎯 Key Takeaways from Phase 1

✅ LLM APIs are **stateless** - no memory between requests
✅ You maintain context by sending **full conversation history**
✅ Messages have **roles**: system, user, assistant
✅ **System messages** set AI behavior
✅ Requests are just **HTTP POST with JSON**
✅ Authentication uses an **API key in headers**
✅ You pay per **token** (input + output)

---

## 🚀 Ready for More?

Once comfortable with Phase 1, you can:
- Add streaming responses (see AI typing in real-time)
- Move to Phase 2 (database, authentication, persistent chats)
- Try different models and compare responses
- Add error handling and retry logic
- Implement token counting and limits

---

## 📖 Further Reading

- [OpenAI API Documentation](https://platform.openai.com/docs/api-reference/chat)
- [Understanding Tokens](https://platform.openai.com/tokenizer)
- [Spring Boot Guide](https://spring.io/guides/gs/spring-boot/)
- [WebClient Documentation](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
