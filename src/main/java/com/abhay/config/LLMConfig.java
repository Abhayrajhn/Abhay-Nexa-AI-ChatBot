package com.abhay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration class for beans used throughout the application.
 * WebClient is Spring's modern HTTP client (replaces RestTemplate). We'll use it to make HTTP requests to the OpenAI API.
 */
@Configuration
public class LLMConfig {

    /**
     * Creates a WebClient bean for making HTTP requests. WebClient is non-blocking and supports reactive programming.
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
