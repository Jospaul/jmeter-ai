package org.qainsights.jmeter.ai.usage;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.qainsights.jmeter.ai.utils.AiConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class to track and provide AWS Bedrock token usage information.
 */
public class BedrockUsage {
    private static final Logger log = LoggerFactory.getLogger(BedrockUsage.class);

    // Singleton instance
    private static final BedrockUsage INSTANCE = new BedrockUsage();

    // Bedrock client for API calls
    private BedrockRuntimeClient client;

    // Store usage history
    private final List<UsageRecord> usageHistory = new ArrayList<>();

    // Private constructor for singleton
    private BedrockUsage() {
        initializeClient();
    }

    /**
     * Initialize the Bedrock client
     */
    private void initializeClient() {
        try {
            String region = AiConfig.getProperty("aws.bedrock.region", "us-east-1");
            log.info("Bedrock client initialized for usage tracking in region: {}", region);
        } catch (Exception e) {
            log.error("Failed to initialize Bedrock client for usage tracking", e);
        }
    }

    /**
     * Get the singleton instance of BedrockUsage.
     *
     * @return The singleton instance
     */
    public static BedrockUsage getInstance() {
        return INSTANCE;
    }

    /**
     * Record usage from a Bedrock response.
     *
     * @param model            The model used for the completion
     * @param promptTokens     The number of prompt tokens (input)
     * @param completionTokens The number of completion tokens (output)
     */
    public void recordUsage(String model, long promptTokens, long completionTokens) {
        try {
            long totalTokens = promptTokens + completionTokens;

            // Record usage
            UsageRecord record = new UsageRecord(
                    new Date(),
                    model,
                    promptTokens,
                    completionTokens,
                    totalTokens);

            usageHistory.add(record);
            log.info("Recorded usage: {}", record);
        } catch (Exception e) {
            log.error("Error recording usage", e);
        }
    }

    /**
     * Set the Bedrock client for usage tracking
     * 
     * @param client The Bedrock client to use
     */
    public void setClient(BedrockRuntimeClient client) {
        this.client = client;
        log.info("Bedrock client set for usage tracking");
    }

    /**
     * Get usage summary as a formatted string.
     *
     * @return The usage summary
     */
    public String getUsageSummary() {
        if (usageHistory.isEmpty()) {
            return "No AWS Bedrock usage data available. Try using the Claude service first.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("# AWS Bedrock Usage Summary\n\n");

        // Summary totals
        long totalPromptTokens = 0;
        long totalCompletionTokens = 0;
        long totalTokens = 0;

        // Calculate totals
        for (UsageRecord record : usageHistory) {
            totalPromptTokens += record.promptTokens;
            totalCompletionTokens += record.completionTokens;
            totalTokens += record.totalTokens;
        }

        // Add summary information
        summary.append("## Overall Summary\n");
        summary.append("- **Total Conversations**: ").append(usageHistory.size()).append("\n");
        summary.append("- **Total Input Tokens**: ").append(totalPromptTokens).append("\n");
        summary.append("- **Total Output Tokens**: ").append(totalCompletionTokens).append("\n");
        summary.append("- **Total Tokens**: ").append(totalTokens).append("\n\n");

        // Add pricing note
        summary.append("## Pricing Information\n");
        summary.append("For up-to-date pricing information, please visit AWS Bedrock's official pricing page:\n");
        summary.append("https://aws.amazon.com/bedrock/pricing/\n\n");
        summary.append("AWS Bedrock pricing varies by model and may change over time.\n\n");

        // Add detail for the last 10 conversations
        summary.append("## Recent Conversations\n");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Get the most recent 10 records or fewer if less than 10 exist
        int startIndex = Math.max(0, usageHistory.size() - 10);
        for (int i = startIndex; i < usageHistory.size(); i++) {
            UsageRecord record = usageHistory.get(i);
            summary.append("### Conversation ").append(i + 1 - startIndex).append("\n");
            summary.append("- **Date**: ").append(dateFormat.format(record.timestamp)).append("\n");
            summary.append("- **Model**: ").append(record.model).append("\n");
            summary.append("- **Input Tokens**: ").append(record.promptTokens).append("\n");
            summary.append("- **Output Tokens**: ").append(record.completionTokens).append("\n");
            summary.append("- **Total Tokens**: ").append(record.totalTokens).append("\n\n");
        }

        return summary.toString();
    }

    /**
     * Class to store a single usage record.
     */
    private static class UsageRecord {
        private final Date timestamp;
        private final String model;
        private final long promptTokens;
        private final long completionTokens;
        private final long totalTokens;

        public UsageRecord(Date timestamp, String model, long promptTokens, long completionTokens,
                long totalTokens) {
            this.timestamp = timestamp;
            this.model = model;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        @Override
        public String toString() {
            return "UsageRecord{" +
                    "timestamp=" + timestamp +
                    ", model='" + model + '\'' +
                    ", promptTokens=" + promptTokens +
                    ", completionTokens=" + completionTokens +
                    ", totalTokens=" + totalTokens +
                    '}';
        }
    }
}