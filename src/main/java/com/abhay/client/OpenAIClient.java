package com.abhay.client;

import com.abhay.model.llm.LLMRequest;
import com.abhay.model.llm.LLMResponse;
import com.abhay.model.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Client for communicating with OpenAI's API.
 * This is the CORE of how your app talks to an LLM.
 * Key Learning Points: 1. We make an HTTP POST request to OpenAI's endpoint 2. We send JSON with: model name + array of messages 3. We
 * authenticate using an API key in the Authorization header 4. OpenAI returns JSON with the AI's response 5. We extract the assistant's
 * message from the response
 * The API is stateless - it doesn't remember previous conversations. YOU must send the full conversation history each time.
 */
@Component
public class OpenAIClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);

    private final WebClient webClient;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    public OpenAIClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Sends messages to OpenAI and returns the assistant's response.
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
}
