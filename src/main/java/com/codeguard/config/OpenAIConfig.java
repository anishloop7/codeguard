package com.codeguard.config;

import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model:gpt-4-turbo}")
    private String model;

    @Value("${openai.max-tokens:2048}")
    private Integer maxTokens;

    @Value("${openai.temperature:0.2}")
    private Double temperature;

    @Bean
    public OpenAiApi openAiApi() {
        return new OpenAiApi(apiKey);
    }

    @Bean
    public OpenAiChatClient openAiChatClient(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withMaxTokens(maxTokens)
                .withTemperature(temperature.floatValue())
                .build();
        return new OpenAiChatClient(openAiApi, options);
    }
}
