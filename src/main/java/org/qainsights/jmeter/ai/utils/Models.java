package org.qainsights.jmeter.ai.utils;

import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.models.ModelInfo;
import com.anthropic.models.models.ModelListPage;
import com.anthropic.models.models.ModelListParams;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Models {
    private static final Logger log = LoggerFactory.getLogger(Models.class);
    
    /**
     * Validates if OpenAI API configuration is valid
     * @return true if OpenAI API key is configured and not empty
     */
    private static boolean isOpenAiConfigValid() {
        String apiKey = AiConfig.getProperty("openai.api.key", "");
        return apiKey != null && !apiKey.trim().isEmpty() && !"YOUR_OPENAI_API_KEY".equals(apiKey.trim());
    }
    
    /**
     * Get a combined list of model IDs from both Bedrock, Anthropic and OpenAI
     * @param bedrockClient Bedrock client
     * @param anthropicClient Anthropic client
     * @param openAiClient OpenAI client
     * @return List of model IDs
     */
    public static List<String> getModelIds(BedrockClient bedrockClient, AnthropicClient anthropicClient, OpenAIClient openAiClient) {
        List<String> modelIds = new ArrayList<>();
        
        try {
            // Get Bedrock models
            List<String> bedrockModels = getBedrockModelIds(bedrockClient);
            if (bedrockModels != null) {
                modelIds.addAll(bedrockModels);
            }
            
            // Get Anthropic models
            List<String> anthropicModels = getAnthropicModelIds(anthropicClient);
            if (anthropicModels != null) {
                modelIds.addAll(anthropicModels);
            }
            // Get OpenAI models
            List<String> openAiModels = getOpenAiModelIds(openAiClient);
            if (openAiModels != null) {
                modelIds.addAll(openAiModels);
            }
            
            log.info("Combined {} models from Bedrock and OpenAI", modelIds.size());
            return modelIds;
        } catch (Exception e) {
            log.error("Error combining models: {}", e.getMessage(), e);
            return modelIds; // Return whatever we have, even if empty
        }
    }
    
    /**
     * Get Bedrock models as ListFoundationModelsResponse
     * @param client Bedrock client
     * @return ListFoundationModelsResponse containing Bedrock models
     */
    public static ListFoundationModelsResponse getBedrockModels(BedrockClient client) {
        if (client == null) {
            log.warn("Bedrock client is null, cannot fetch models");
            return null;
        }
        
        try {
            log.info("Fetching available models from AWS Bedrock");
            
            ListFoundationModelsRequest request = ListFoundationModelsRequest.builder()
                    .build();
            ListFoundationModelsResponse models = client.listFoundationModels(request);
            
            log.info("Successfully retrieved {} models from AWS Bedrock", models.modelSummaries().size());
            for (FoundationModelSummary model : models.modelSummaries()) {
                log.debug("Available Bedrock model: {}", model.modelId());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from AWS Bedrock: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get Bedrock model display names as a List of Strings
     * @param client Bedrock client
     * @return List of model display names
     */
    public static List<String> getBedrockModelIds(BedrockClient client) {
        if (client == null) {
            log.warn("Bedrock client is null, cannot fetch model IDs");
            return new ArrayList<>();
        }
        
        try {
            // Initialize the model mapper if not already done
            BedrockModelMapper.initialize(client);
            
            // Get display names from the initialized mapper
            List<String> displayNames = BedrockModelMapper.getDisplayNamesList();
            log.info("Retrieved {} Bedrock model display names", displayNames.size());
            return displayNames;
        } catch (Exception e) {
            log.error("Error retrieving Bedrock model IDs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get Anthropic models as ModelListPage
     * @param client Anthropic client
     * @return ModelListPage containing Anthropic models
     */
    public static ModelListPage getAnthropicModels(AnthropicClient client) {
        try {
            log.info("Fetching available models from Anthropic API");
            client = AnthropicOkHttpClient.builder()
                    .apiKey(AiConfig.getProperty("anthropic.api.key", "YOUR_API_KEY"))
                    .build();

            ModelListParams modelListParams = ModelListParams.builder().build();
            ModelListPage models = client.models().list(modelListParams);
            
            log.info("Successfully retrieved {} models from Anthropic API", models.data().size());
            for (ModelInfo model : models.data()) {
                log.debug("Available Anthropic model: {}", model.id());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from Anthropic API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get Anthropic model IDs as a List of Strings
     * @param client Anthropic client
     * @return List of model IDs
     */
    public static List<String> getAnthropicModelIds(AnthropicClient client) {
        ModelListPage models = getAnthropicModels(client);
        if (models != null && models.data() != null) {
            return models.data().stream()
                    .map(ModelInfo::id)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
    
    /**
     * Get OpenAI models as ModelListPage
     * @param client OpenAI client
     * @return OpenAI ModelListPage
     */
    public static com.openai.models.ModelListPage getOpenAiModels(OpenAIClient client) {
        if (!isOpenAiConfigValid()) {
            log.info("OpenAI configuration is not valid, skipping model fetch");
            return null;
        }
        
        try {
            log.info("Fetching available models from OpenAI API");
            client = OpenAIOkHttpClient.builder()
                    .apiKey(AiConfig.getProperty("openai.api.key", "YOUR_API_KEY"))
                    .build();            

            com.openai.models.ModelListPage models = client.models().list();
            
            log.info("Successfully retrieved {} models from OpenAI API", models.data().size());
            for (Model model : models.data()) {
                log.debug("Available OpenAI model: {}", model.id());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from OpenAI API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get OpenAI model IDs as a List of Strings
     * @param client OpenAI client
     * @return List of model IDs
     */
    public static List<String> getOpenAiModelIds(OpenAIClient client) {
        if (!isOpenAiConfigValid()) {
            log.info("OpenAI configuration is not valid, skipping model fetch");
            return new ArrayList<>();
        }
        
        com.openai.models.ModelListPage models = getOpenAiModels(client);
        if (models != null && models.data() != null) {
            // Return the list of GPT models only, excluding audio and TTS models
            return models.data().stream()
                    .filter(model -> model.id().startsWith("gpt")) // Include only GPT models
                    .filter(model -> !model.id().contains("audio")) // Exclude audio models
                    .filter(model -> !model.id().contains("tts")) // Exclude text-to-speech models
                    .filter(model -> !model.id().contains("whisper")) // Exclude whisper models
                    .filter(model -> !model.id().contains("davinci")) // Exclude Davinci models
                    .filter(model -> !model.id().contains("search")) // Exclude search models
                    .filter(model -> !model.id().contains("transcribe")) // Exclude transcribe models
                    .filter(model -> !model.id().contains("realtime")) // Exclude realtime models
                    .filter(model -> !model.id().contains("instruct")) // Exclude instruct models
                    .map(com.openai.models.Model::id)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
    

}
