package org.qainsights.jmeter.ai.utils;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to map Bedrock model display names to their corresponding
 * inference profile ARNs or model IDs for API calls.
 */
public class BedrockModelMapper {
    private static final Logger log = LoggerFactory.getLogger(BedrockModelMapper.class);
    
    // Map of display names to actual model IDs/ARNs
    private static final Map<String, String> MODEL_MAPPING = new LinkedHashMap<>();
    private static boolean initialized = false;
    
    /**
     * Initialize the model mapping by fetching from AWS Bedrock.
     * 
     * @param bedrockClient The Bedrock client to use for fetching models
     */
    public static synchronized void initialize(BedrockClient bedrockClient) {
        if (initialized) {
            return;
        }
        
        try {
            log.info("Fetching available models and inference profiles from AWS Bedrock");
            
            // First, get foundation models (all providers)
            ListFoundationModelsRequest foundationRequest = ListFoundationModelsRequest.builder()
                    .build();
            ListFoundationModelsResponse foundationModels = bedrockClient.listFoundationModels(foundationRequest);
            
            // Add foundation models to mapping (display model ID, use model ID for API)
            for (FoundationModelSummary model : foundationModels.modelSummaries()) {
                String modelId = model.modelId();
                MODEL_MAPPING.put(modelId, modelId);
                log.debug("Added foundation model: {} -> {}", modelId, modelId);
            }
            
            // Then, get inference profiles which are preferred for newer models
            try {
                ListInferenceProfilesRequest profileRequest = ListInferenceProfilesRequest.builder().build();
                ListInferenceProfilesResponse inferenceProfiles = bedrockClient.listInferenceProfiles(profileRequest);
                
                for (InferenceProfileSummary profile : inferenceProfiles.inferenceProfileSummaries()) {
                    String profileId = profile.inferenceProfileId();
                    // Include all inference profiles
                    String baseModelId = extractBaseModelId(profileId);
                    if (baseModelId != null) {
                        // Display the base model ID, but use the inference profile for API calls
                        MODEL_MAPPING.put(baseModelId, profileId);
                        log.debug("Added inference profile: {} -> {}", baseModelId, profileId);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not fetch inference profiles (may not be available in this region): {}", e.getMessage());
            }
            
            initialized = true;
            log.info("Successfully initialized {} Bedrock models", MODEL_MAPPING.size());
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("security token")) {
                log.error("AWS credentials are invalid or expired. Please check your AWS configuration: {}", errorMessage);
            } else {
                log.error("Failed to initialize Bedrock models, using fallback models: {}", errorMessage);
            }
            initializeFallbackModels();
            initialized = true;
        }
    }
    
    /**
     * Initialize fallback models if AWS API calls fail.
     */
    private static void initializeFallbackModels() {
        MODEL_MAPPING.clear();
        // Use inference profiles as the API values, display base model IDs
        // Anthropic models
        MODEL_MAPPING.put("anthropic.claude-3-5-sonnet-20241022-v2:0", "us.anthropic.claude-3-5-sonnet-20241022-v2:0");
        MODEL_MAPPING.put("anthropic.claude-3-5-sonnet-20240620-v1:0", "us.anthropic.claude-3-5-sonnet-20240620-v1:0");
        MODEL_MAPPING.put("anthropic.claude-3-opus-20240229-v1:0", "us.anthropic.claude-3-opus-20240229-v1:0");
        MODEL_MAPPING.put("anthropic.claude-3-sonnet-20240229-v1:0", "us.anthropic.claude-3-sonnet-20240229-v1:0");
        MODEL_MAPPING.put("anthropic.claude-3-haiku-20240307-v1:0", "us.anthropic.claude-3-haiku-20240307-v1:0");
        // Amazon models
        MODEL_MAPPING.put("amazon.nova-micro-v1:0", "us.amazon.nova-micro-v1:0");
        MODEL_MAPPING.put("amazon.nova-lite-v1:0", "us.amazon.nova-lite-v1:0");
        MODEL_MAPPING.put("amazon.nova-pro-v1:0", "us.amazon.nova-pro-v1:0");
        // Meta models
        MODEL_MAPPING.put("meta.llama3-2-1b-instruct-v1:0", "meta.llama3-2-1b-instruct-v1:0");
        MODEL_MAPPING.put("meta.llama3-2-3b-instruct-v1:0", "meta.llama3-2-3b-instruct-v1:0");
        MODEL_MAPPING.put("meta.llama3-2-11b-instruct-v1:0", "meta.llama3-2-11b-instruct-v1:0");
        MODEL_MAPPING.put("meta.llama3-2-90b-instruct-v1:0", "meta.llama3-2-90b-instruct-v1:0");
        log.info("Initialized {} fallback models", MODEL_MAPPING.size());
    }
    
    /**
     * Extract the base model ID from an inference profile ID.
     * 
     * @param profileId The inference profile ID
     * @return The base model ID for display, or null if not extractable
     */
    private static String extractBaseModelId(String profileId) {
        // Convert inference profile ID to base model ID
        // e.g., "us.anthropic.claude-3-5-sonnet-20241022-v2:0" -> "anthropic.claude-3-5-sonnet-20241022-v2:0"
        // e.g., "us.amazon.nova-micro-v1:0" -> "amazon.nova-micro-v1:0"
        if (profileId.startsWith("us.")) {
            return profileId.substring(3); // Remove "us." prefix
        } else if (profileId.startsWith("eu.")) {
            return profileId.substring(3); // Remove "eu." prefix
        }
        return profileId; // Return as-is if pattern doesn't match
    }
    
    /**
     * Get the actual model ID/ARN for API calls from the display name.
     * 
     * @param displayName The display name shown in the UI
     * @return The actual model ID or inference profile ARN
     */
    public static String getModelId(String displayName) {
        return MODEL_MAPPING.getOrDefault(displayName, displayName);
    }
    
    /**
     * Get the display name from the model ID/ARN.
     * 
     * @param modelId The actual model ID or inference profile ARN
     * @return The display name, or the original modelId if not found
     */
    public static String getDisplayName(String modelId) {
        for (Map.Entry<String, String> entry : MODEL_MAPPING.entrySet()) {
            if (entry.getValue().equals(modelId)) {
                return entry.getKey();
            }
        }
        return modelId;
    }
    
    /**
     * Get all available display names for the model selector.
     * 
     * @return Array of display names
     */
    public static String[] getDisplayNames() {
        if (!initialized) {
            log.warn("BedrockModelMapper not initialized, returning empty array");
            return new String[0];
        }
        return MODEL_MAPPING.keySet().toArray(new String[0]);
    }
    
    /**
     * Get all available display names as a list.
     * 
     * @return List of display names
     */
    public static List<String> getDisplayNamesList() {
        return new ArrayList<>(MODEL_MAPPING.keySet());
    }
    
    /**
     * Check if a display name is valid.
     * 
     * @param displayName The display name to check
     * @return true if the display name exists in the mapping
     */
    public static boolean isValidDisplayName(String displayName) {
        return MODEL_MAPPING.containsKey(displayName);
    }
    
    /**
     * Get the default model display name based on configuration.
     * 
     * @param configuredModelId The model ID from configuration
     * @return The default model display name (which is the model ID itself)
     */
    public static String getDefaultDisplayName(String configuredModelId) {
        if (configuredModelId != null && MODEL_MAPPING.containsKey(configuredModelId)) {
            return configuredModelId;
        }
        // Check if the configured model is an inference profile (API value)
        if (configuredModelId != null && MODEL_MAPPING.containsValue(configuredModelId)) {
            // Find the display name (key) for this API value
            for (Map.Entry<String, String> entry : MODEL_MAPPING.entrySet()) {
                if (entry.getValue().equals(configuredModelId)) {
                    return entry.getKey();
                }
            }
        }
        // Fallback to first available model or default
        if (!MODEL_MAPPING.isEmpty()) {
            return MODEL_MAPPING.keySet().iterator().next();
        }
        return "anthropic.claude-3-sonnet-20240229-v1:0";
    }
    
    /**
     * Get the default model ID for API calls.
     * 
     * @param configuredModelId The model ID from configuration
     * @return The default model ID/ARN for API calls
     */
    public static String getDefaultModelId(String configuredModelId) {
        String displayName = getDefaultDisplayName(configuredModelId);
        return getModelId(displayName);
    }
}