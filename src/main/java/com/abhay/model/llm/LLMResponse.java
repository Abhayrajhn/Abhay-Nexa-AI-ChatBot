package com.abhay.model.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents the response from OpenAI's Chat Completions API.
 *
 * OpenAI returns a complex JSON structure. We only map the fields we need.
 *
 * Example response structure:
 * {
 *   "id": "chatcmpl-123",
 *   "object": "chat.completion",
 *   "created": 1677652288,
 *   "choices": [{
 *     "index": 0,
 *     "message": {
 *       "role": "assistant",
 *       "content": "Hello! How can I help you?"
 *     },
 *     "finish_reason": "stop"
 *   }],
 *   "usage": {
 *     "prompt_tokens": 10,
 *     "completion_tokens": 20,
 *     "total_tokens": 30
 *   }
 * }
 */
public class LLMResponse {
    private String id;
    private String object;
    private Long created;
    private List<Choice> choices;
    private Usage usage;

    // Constructors
    public LLMResponse() {
    }

    public LLMResponse(String id, String object, Long created, List<Choice> choices, Usage usage) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.choices = choices;
        this.usage = usage;
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

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    // Inner class: Choice
    public static class Choice {
        private Integer index;
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;

        // Constructors
        public Choice() {
        }

        public Choice(Integer index, Message message, String finishReason) {
            this.index = index;
            this.message = message;
            this.finishReason = finishReason;
        }

        // Getters and Setters
        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    // Inner class: Usage
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        // Constructors
        public Usage() {
        }

        public Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        // Getters and Setters
        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
