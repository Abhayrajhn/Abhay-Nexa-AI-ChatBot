package com.abhay.client;

import com.abhay.model.llm.LLMRequest;
import com.abhay.model.llm.LLMResponse;
import com.abhay.model.llm.Message;
import com.abhay.model.llm.StreamChunk;
import com.abhay.model.llm.ToolCall;
import com.abhay.model.llm.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Client for communicating with OpenAI's API. This is the CORE of how your app talks to an LLM. Key Learning Points: 1. We make an HTTP
 * POST request to OpenAI's endpoint 2. We send JSON with: model name + array of messages 3. We authenticate using an API key in the
 * Authorization header 4. OpenAI returns JSON with the AI's response 5. We extract the assistant's message from the response The API is
 * stateless - it doesn't remember previous conversations. YOU must send the full conversation history each time. STREAMING: When
 * stream=true, OpenAI sends Server-Sent Events (SSE) instead of a single JSON response. Each event contains a small chunk of text, allowing
 * us to display the response progressively.
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
     * Sends messages to OpenAI and returns the assistant's response. NON-STREAMING VERSION (original method - kept for compatibility)
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
     * Sends messages to OpenAI and streams the response chunk by chunk. STREAMING VERSION WITH TOOL SUPPORT How streaming works: 1. We set
     * stream=true in the request 2. OpenAI sends multiple Server-Sent Events (SSE) 3. Each event contains a "delta" (text chunk or tool
     * call fragment) 4. We accumulate tool call fragments and text chunks 5. When finish_reason="tool_calls", we call onToolCalls() 6. When
     * streaming completes, we call onComplete() 7. If an error occurs, we call onError()
     *
     * @param messages
     *         Full conversation history including system message
     * @param tools
     *         List of available tool definitions (can be null)
     * @param onChunk
     *         Callback function called for each chunk of text
     * @param onToolCalls
     *         Callback function called when LLM requests tools
     * @param onComplete
     *         Callback function called when streaming completes
     * @param onError
     *         Callback function called if an error occurs
     */
    public void sendMessageStream(List<Message> messages, List<ToolDefinition> tools, Consumer<String> onChunk,
            Consumer<List<ToolCall>> onToolCalls, Runnable onComplete, Consumer<Throwable> onError) {
        logger.info("Sending streaming request to OpenAI with {} messages and {} tools", messages.size(), tools != null ? tools.size() : 0);

        // Create the request body with streaming enabled
        LLMRequest request = new LLMRequest(model, messages);
        request.setStream(true);  // This tells OpenAI to stream the response
        request.setTools(tools);  // Add tool definitions (can be null)

        logger.info("Request body: model={}, stream={}, messages count={}, tools count={}", model, request.getStream(), messages.size(),
                tools != null ? tools.size() : 0);

        try {
            // Tool call accumulator for streaming fragments
            ToolCallAccumulator accumulator = new ToolCallAccumulator();

            // Line accumulator for incomplete SSE lines split across buffers
            StringBuilder lineAccumulator = new StringBuilder();

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

                        // Accumulate text with previous incomplete line
                        lineAccumulator.append(text);
                        String accumulated = lineAccumulator.toString();

                        // Split by newlines to get individual SSE lines
                        String[] lines = accumulated.split("\n", -1);
                        logger.info("Split into {} lines", lines.length);

                        // If the last line doesn't end with newline, it's incomplete - keep it for next buffer
                        List<String> completeLines = new ArrayList<>();
                        if (lines.length > 0) {
                            for (int i = 0; i < lines.length - 1; i++) {
                                completeLines.add(lines[i]);
                            }

                            // Check if text ends with newline
                            if (text.endsWith("\n")) {
                                completeLines.add(lines[lines.length - 1]);
                                lineAccumulator.setLength(0);  // Clear accumulator
                            } else {
                                // Last line is incomplete, keep it
                                lineAccumulator.setLength(0);
                                lineAccumulator.append(lines[lines.length - 1]);
                            }
                        }

                        return reactor.core.publisher.Flux.fromIterable(completeLines);
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

                                    // Check for tool call deltas
                                    List<StreamChunk.ToolCallDelta> toolDeltas = parseToolCallDeltas(line);
                                    if (toolDeltas != null && !toolDeltas.isEmpty()) {
                                        logger.info("Extracted {} tool call deltas", toolDeltas.size());
                                        for (StreamChunk.ToolCallDelta delta : toolDeltas) {
                                            accumulator.addDelta(delta);
                                        }
                                    }

                                    // Check for finish_reason
                                    String finishReason = parseFinishReason(line);
                                    if ("tool_calls".equals(finishReason)) {
                                        logger.info("Finish reason: tool_calls detected");
                                        List<ToolCall> completedToolCalls = accumulator.getCompletedCalls();
                                        logger.info("Completed tool calls: {}", completedToolCalls.size());
                                        if (onToolCalls != null && !completedToolCalls.isEmpty()) {
                                            onToolCalls.accept(completedToolCalls);
                                        }
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
     * Parse a single line from the OpenAI streaming response. OpenAI sends data in Server-Sent Events (SSE) format: - Each line starts with
     * "data: " - The special line "data: [DONE]" signals completion - Other lines contain JSON with the chunk data Example lines: data:
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

    /**
     * Parse tool call deltas from a streaming chunk. Tool calls arrive in fragments during streaming: - First chunk: index, id, type, name,
     * arguments="" - Later chunks: index, arguments="fragment"
     *
     * @param line
     *         A single line from the SSE stream
     * @return List of tool call deltas, or null if none present
     */
    private List<StreamChunk.ToolCallDelta> parseToolCallDeltas(String line) {
        try {
            // Remove "data: " prefix if present
            if (line.startsWith("data: ")) {
                line = line.substring(6);
            }

            // Skip empty lines and [DONE]
            if (line.trim().isEmpty() || line.equals("[DONE]")) {
                return null;
            }

            // Parse the JSON
            StreamChunk chunk = objectMapper.readValue(line, StreamChunk.class);

            // Extract tool call deltas from the first choice's delta
            if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                StreamChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                if (delta != null && delta.getToolCalls() != null) {
                    return delta.getToolCalls();
                }
            }

            return null;

        } catch (Exception e) {
            logger.debug("No tool call deltas in line: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse finish_reason from a streaming chunk.
     *
     * @param line
     *         A single line from the SSE stream
     * @return The finish_reason string, or null if not present
     */
    private String parseFinishReason(String line) {
        try {
            // Remove "data: " prefix if present
            if (line.startsWith("data: ")) {
                line = line.substring(6);
            }

            // Skip empty lines and [DONE]
            if (line.trim().isEmpty() || line.equals("[DONE]")) {
                return null;
            }

            // Parse the JSON
            StreamChunk chunk = objectMapper.readValue(line, StreamChunk.class);

            // Extract finish_reason from the first choice
            if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                return chunk.getChoices().get(0).getFinishReason();
            }

            return null;

        } catch (Exception e) {
            logger.debug("No finish_reason in line: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Accumulates tool call fragments from streaming deltas. OpenAI sends tool calls in pieces: - First delta: { index: 0, id: "call_abc",
     * type: "function", function: { name: "calculator", arguments: "" } } - Next deltas: { index: 0, function: { arguments: "{\"expr" } } -
     * Next deltas: { index: 0, function: { arguments: "ession\":" } } - Next deltas: { index: 0, function: { arguments: "\"25*40\"}" } } We
     * accumulate by index to build the complete tool call.
     */
    private static class ToolCallAccumulator {

        private final Map<Integer, ToolCall> calls = new HashMap<>();
        private final Map<Integer, StringBuilder> argumentBuilders = new HashMap<>();

        public void addDelta(StreamChunk.ToolCallDelta delta) {
            int index = delta.getIndex() != null ? delta.getIndex() : 0;

            // First chunk for this index: create the ToolCall
            if (!calls.containsKey(index)) {
                ToolCall toolCall = new ToolCall();
                toolCall.setId(delta.getId());
                toolCall.setType(delta.getType() != null ? delta.getType() : "function");

                ToolCall.FunctionCall functionCall = new ToolCall.FunctionCall();
                if (delta.getFunction() != null) {
                    functionCall.setName(delta.getFunction().getName());
                    functionCall.setArguments("");  // Start with empty arguments
                }
                toolCall.setFunction(functionCall);

                calls.put(index, toolCall);
                argumentBuilders.put(index, new StringBuilder());
            }

            // Accumulate arguments
            if (delta.getFunction() != null && delta.getFunction().getArguments() != null) {
                StringBuilder argBuilder = argumentBuilders.get(index);
                argBuilder.append(delta.getFunction().getArguments());

                // Update the function call's arguments
                ToolCall toolCall = calls.get(index);
                toolCall.getFunction().setArguments(argBuilder.toString());
            }
        }

        public List<ToolCall> getCompletedCalls() {
            return new ArrayList<>(calls.values());
        }
    }

    /**
     * Sends messages with tool results and returns the final response. NON-STREAMING VERSION for getting final answer after tool execution.
     * Used in the tool execution flow: 1. User message → OpenAI (streaming, with tools) → tool_calls 2. Execute tools → tool results 3.
     * Send tool results → OpenAI (non-streaming) → final answer
     *
     * @param messages
     *         Full conversation history including tool messages
     * @param tools
     *         List of available tool definitions
     * @return The complete LLM response
     */
    public LLMResponse sendMessageWithToolResults(List<Message> messages, List<ToolDefinition> tools) {
        logger.info("Sending non-streaming request to OpenAI with {} messages and {} tools", messages.size(),
                tools != null ? tools.size() : 0);

        // Create the request body (non-streaming)
        LLMRequest request = new LLMRequest(model, messages);
        request.setStream(false);  // Non-streaming for tool results
        request.setTools(tools);

        try {
            // Make the HTTP POST request
            LLMResponse response = webClient.post().uri(apiUrl).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").bodyValue(request).retrieve().bodyToMono(LLMResponse.class).block();

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                logger.info("Received response from OpenAI. Tokens used: {}",
                        response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");
                return response;
            } else {
                logger.error("Received empty response from OpenAI");
                throw new RuntimeException("Empty response from OpenAI");
            }

        } catch (Exception e) {
            logger.error("Error calling OpenAI API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get response from OpenAI: " + e.getMessage(), e);
        }
    }
}
