package com.abhay.model.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a single chunk from OpenAI's streaming response.
 * When streaming is enabled, OpenAI sends multiple SSE events, each containing a small chunk of the response.
 * Example streaming response: data:
 * {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
 * data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"choices":[{"index":0,"delta":{"content":"
 * there"},"finish_reason":null}]} data:
 * {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
 * data: [DONE]
 * Key differences from non-streaming response: - object: "chat.completion.chunk" (not "chat.completion") - delta: Contains incremental
 * content (not full message) - finish_reason: null until the last chunk
 *
 * @JsonIgnoreProperties(ignoreUnknown = true): Tells Jackson to ignore fields we don't define. OpenAI sends extra fields like
 *         "service_tier", "system_fingerprint", "obfuscation" that we don't need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamChunk {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;

    // Constructors
    public StreamChunk() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    /**
     * Inner class: Choice Contains the delta (incremental content) for this chunk
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {

        private Integer index;

        // In streaming responses, we get "delta" instead of "message"
        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;

        // Constructors
        public Choice() {
        }

        // Getters and Setters
        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public Delta getDelta() {
            return delta;
        }

        public void setDelta(Delta delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    /**
     * Inner class: Delta Contains the incremental content for this chunk
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {

        private String role;      // Only present in first chunk
        private String content;   // The actual text chunk

        // Tool calls (for streaming tool call fragments)
        @JsonProperty("tool_calls")
        private List<ToolCallDelta> toolCalls;

        // Constructors
        public Delta() {
        }

        // Getters and Setters
        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<ToolCallDelta> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCallDelta> toolCalls) {
            this.toolCalls = toolCalls;
        }
    }

    /**
     * Inner class: ToolCallDelta
     * Represents a fragment of a tool call during streaming.
     *
     * OpenAI sends tool calls in pieces:
     * - First chunk: index, id, type, name, arguments=""
     * - Later chunks: index, arguments="fragment"
     *
     * We need to accumulate these fragments to build the complete tool call.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallDelta {

        private Integer index;   // Which tool call (0, 1, 2, ...)
        private String id;       // Unique ID (only in first chunk)
        private String type;     // "function" (only in first chunk)
        private FunctionCallDelta function;

        // Constructors
        public ToolCallDelta() {
        }

        // Getters and Setters
        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public FunctionCallDelta getFunction() {
            return function;
        }

        public void setFunction(FunctionCallDelta function) {
            this.function = function;
        }
    }

    /**
     * Inner class: FunctionCallDelta
     * Contains function name and argument fragments.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCallDelta {

        private String name;      // Function name (only in first chunk)
        private String arguments; // Argument fragment

        // Constructors
        public FunctionCallDelta() {
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }
}
