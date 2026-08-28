package com.abhay.client;

import com.abhay.model.llm.LLMRequest;
import com.abhay.model.llm.LLMResponse;
import com.abhay.model.llm.Message;
import com.abhay.model.llm.StreamChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client for communicating with OpenAI's API. This is the CORE of how your app talks to an LLM.
 * Key Learning Points: 1. We make an HTTP POST request to OpenAI's endpoint 2. We send JSON with: model name + array of messages 3. We
 * authenticate using an API key in the Authorization header 4. OpenAI returns JSON with the AI's response 5. We extract the assistant's
 * message from the response
 * The API is stateless - it doesn't remember previous conversations. YOU must send the full conversation history each time.
 * STREAMING: When stream=true, OpenAI sends Server-Sent Events (SSE) instead of a single JSON response. Each event contains a small chunk
 * of text, allowing us to display the response progressively.
 */
@Component
public class OpenAIClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    public OpenAIClient(WebClient webClient) {
        this.webClient = webClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sends messages to OpenAI and returns the assistant's response.
     * NON-STREAMING VERSION (original method - kept for compatibility)
     *
     * @param messages
     *         Full conversation history including system message
     * @return The AI's response text
     */
    public String sendMessage(List<Message> messages) {
        logger.info("Sending request to OpenAI with {} messages", messages.size());

        // Create the request body that OpenAI expects
        LLMRequest request = new LLMRequest(model, messages);

        try {
            // Make the HTTP POST request
            LLMResponse response = webClient.post().uri(apiUrl).header("Authorization", "Bearer " + apiKey)  // Authentication
                    .header("Content-Type", "application/json").bodyValue(request)  // Send our request as JSON
                    .retrieve().bodyToMono(LLMResponse.class)  // Parse response JSON to LLMResponse
                    .block();  // Wait for response (blocking call for simplicity)

            // Extract the assistant's message from the response
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String assistantMessage = response.getChoices().get(0).getMessage().getContent();

                logger.info("Received response from OpenAI. Tokens used: {}",
                        response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");

                return assistantMessage;
            } else {
                logger.error("Received empty response from OpenAI");
                throw new RuntimeException("Empty response from OpenAI");
            }

        } catch (Exception e) {
            logger.error("Error calling OpenAI API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get response from OpenAI: " + e.getMessage(), e);
        }
    }

    /**
     * Sends messages to OpenAI and streams the response chunk by chunk.
     * STREAMING VERSION (new method)
     * How streaming works: 1. We set stream=true in the request 2. OpenAI sends multiple Server-Sent Events (SSE) 3. Each event contains a
     * small chunk of text (a "delta") 4. We parse each chunk and call onChunk() with the text 5. When streaming completes, we call
     * onComplete() 6. If an error occurs, we call onError()
     *
     * @param messages
     *         Full conversation history including system message
     * @param onChunk
     *         Callback function called for each chunk of text
     * @param onComplete
     *         Callback function called when streaming completes
     * @param onError
     *         Callback function called if an error occurs
     */
    public void sendMessageStream(List<Message> messages, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        logger.info("Sending streaming request to OpenAI with {} messages", messages.size());

        // Create the request body with streaming enabled
        LLMRequest request = new LLMRequest(model, messages);
        request.setStream(true);  // This tells OpenAI to stream the response

        logger.info("Request body: model={}, stream={}, messages count={}", model, request.getStream(), messages.size());

        try {
            // Make the HTTP POST request and handle streaming response
            webClient.post().uri(apiUrl).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .bodyValue(request).retrieve().onStatus(status -> !status.is2xxSuccessful(), response -> {
                        logger.error("HTTP error from OpenAI: {}", response.statusCode());
                        return response.bodyToMono(String.class).map(body -> {
                            logger.error("Error body: {}", body);
                            return new RuntimeException("OpenAI API error: " + body);
                        });
                    }).bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                    .doOnSubscribe(sub -> logger.info("Subscribed to OpenAI stream"))
                    .doOnNext(buffer -> logger.info("Received DataBuffer of size: {}", buffer.readableByteCount()))
                    .doOnComplete(() -> logger.info("Stream flux completed"))
                    .doOnError(error -> logger.error("Stream flux error: {}", error.getMessage())).flatMap(dataBuffer -> {
                        // Convert DataBuffer to String
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String text = new String(bytes, StandardCharsets.UTF_8);

                        logger.info("Raw text from buffer (first 500 chars): {}", text.substring(0, Math.min(500, text.length())));

                        // Split by newlines to get individual SSE lines
                        String[] lines = text.split("\n");
                        logger.info("Split into {} lines", lines.length);
                        return reactor.core.publisher.Flux.fromArray(lines);
                    }).subscribe(
                            // onNext: Called for each line of data from the SSE stream
                            line -> {
                                try {
                                    logger.info("Processing line: {}", line.length() > 100 ? line.substring(0, 100) + "..." : line);
                                    // Parse the SSE line and extract content
                                    String chunk = parseStreamChunk(line);
                                    if (chunk != null && !chunk.isEmpty()) {
                                        logger.info("Extracted chunk: '{}'", chunk);
                                        onChunk.accept(chunk);
                                    }
                                } catch (Exception e) {
                                    logger.error("Error parsing chunk: {}", e.getMessage(), e);
                                }
                            },
                            // onError: Called if an error occurs
                            error -> {
                                logger.error("Error during streaming: {}", error.getMessage(), error);
                                onError.accept(error);
                            },
                            // onComplete: Called when streaming finishes
                            () -> {
                                logger.info("Streaming completed successfully");
                                onComplete.run();
                            });

        } catch (Exception e) {
            logger.error("Error initiating streaming: {}", e.getMessage(), e);
            onError.accept(e);
        }
    }

    /**
     * Parse a single line from the OpenAI streaming response.
     * OpenAI sends data in Server-Sent Events (SSE) format: - Each line starts with "data: " - The special line "data: [DONE]" signals
     * completion - Other lines contain JSON with the chunk data
     * Example lines: data:
     * {"id":"chatcmpl-123","object":"chat.completion.chunk","choices":[{"delta":{"content":"Hello"},"finish_reason":null}]} data:
     * {"id":"chatcmpl-123","object":"chat.completion.chunk","choices":[{"delta":{"content":" there"},"finish_reason":null}]} data: [DONE]
     *
     * @param line
     *         A single line from the SSE stream
     * @return The content chunk, or null if the line doesn't contain content
     */
    private String parseStreamChunk(String line) {
        logger.debug("parseStreamChunk called with line: '{}'", line);
        try {
            // Remove "data: " prefix if present
            if (line.startsWith("data: ")) {
                line = line.substring(6);
                logger.debug("After removing 'data: ' prefix: '{}'", line);
            }

            // Skip empty lines and the [DONE] marker
            if (line.trim().isEmpty()) {
                logger.debug("Skipping empty line");
                return null;
            }
            if (line.equals("[DONE]")) {
                logger.info("Received [DONE] marker");
                return null;
            }

            // Parse the JSON
            logger.debug("Attempting to parse JSON: '{}'", line);
            StreamChunk chunk = objectMapper.readValue(line, StreamChunk.class);
            logger.debug("Successfully parsed StreamChunk: {}", chunk);

            // Extract the content from the first choice's delta
            if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                StreamChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                logger.debug("Delta: {}", delta);
                if (delta != null && delta.getContent() != null) {
                    logger.info("Extracted content: '{}'", delta.getContent());
                    return delta.getContent();
                } else {
                    logger.debug("Delta is null or has no content");
                }
            } else {
                logger.debug("No choices in chunk");
            }

            return null;

        } catch (Exception e) {
            // Not all lines will be valid JSON (e.g., empty lines)
            logger.debug("Failed to parse line as JSON (this is normal for empty lines): {}", e.getMessage());
            return null;
        }
    }
}
