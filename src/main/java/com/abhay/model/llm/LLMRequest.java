package com.abhay.model.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents the request body sent to OpenAI's Chat Completions API.
 *
 * Key fields:
 * - model: The OpenAI model to use (e.g., "gpt-4", "gpt-3.5-turbo")
 * - messages: Array of conversation messages (system, user, assistant)
 * - temperature: Controls randomness (0.0 = deterministic, 2.0 = very random)
 * - max_tokens: Maximum length of the response
 */
public class LLMRequest {
    private String model;
    private List<Message> messages;
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    // Constructors
    public LLMRequest() {
    }

    public LLMRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
        this.temperature = 0.7; // Default reasonable temperature
        this.maxTokens = 1000;   // Default max tokens
    }

    public LLMRequest(String model, List<Message> messages, Double temperature, Integer maxTokens) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    // Getters and Setters
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
