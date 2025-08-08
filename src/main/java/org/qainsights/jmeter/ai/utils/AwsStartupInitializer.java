package org.qainsights.jmeter.ai.utils;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles AWS initialization and proactive SSO token refresh on application startup
 */
public class AwsStartupInitializer {
    private static final Logger log = LoggerFactory.getLogger(AwsStartupInitializer.class);
    private static volatile boolean initialized = false;

    /**
     * Initializes AWS credentials and proactively refreshes SSO tokens if needed
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        synchronized (AwsStartupInitializer.class) {
            if (initialized) {
                return;
            }

            // Only proceed if profile is configured as SSO
            boolean isSsoProfile = Boolean.parseBoolean(AiConfig.getProperty("aws.profile.is.sso", "false"));
            boolean startupCheckEnabled = Boolean.parseBoolean(AiConfig.getProperty("aws.sso.startup.check", "true"));
            
            if (!isSsoProfile || !startupCheckEnabled) {
                log.info("SSO startup check skipped (SSO profile: {}, startup check enabled: {})", isSsoProfile, startupCheckEnabled);
                initialized = true;
                return;
            }

            log.info("Initializing AWS credentials and checking SSO token status...");
            
            try {
                String profileName = AiConfig.getProperty("aws.profile.name", "");
                
                if (profileName != null && !profileName.trim().isEmpty()) {
                    log.info("Checking SSO token validity for profile: {}", profileName);
                    checkAndRefreshSsoToken(profileName);
                } else {
                    log.info("No AWS profile configured, skipping SSO token check");
                }
                
                initialized = true;
                log.info("AWS initialization completed");
                
            } catch (Exception e) {
                log.error("Error during AWS initialization: {}", e.getMessage());
                // Don't mark as initialized if there was an error
            }
        }
    }

    /**
     * Checks if SSO token is valid and refreshes if needed
     */
    private static void checkAndRefreshSsoToken(String profileName) {
        try {
            // Try to create credentials provider and resolve credentials
            AwsCredentialsProvider credentialsProvider = AwsCredentialsManager.createCredentialsProvider();
            credentialsProvider.resolveCredentials();
            
            log.info("SSO token for profile {} is valid", profileName);
            
        } catch (SdkClientException e) {
            String errorMessage = e.getMessage().toLowerCase();
            
            if (isTokenExpiredError(errorMessage)) {
                log.warn("SSO token for profile {} appears to be expired. Initiating refresh...", profileName);
                
                AwsSsoTokenManager tokenManager = AwsSsoTokenManager.getInstance();
                CompletableFuture<Boolean> refreshFuture = tokenManager.refreshSsoToken(profileName);
                
                try {
                    Boolean refreshSuccess = refreshFuture.get(300, TimeUnit.SECONDS);
                    if (refreshSuccess) {
                        log.info("SSO token refresh completed successfully for profile: {}", profileName);
                    } else {
                        log.error("SSO token refresh failed for profile: {}. You may need to run 'aws sso login --profile {}' manually.", 
                                profileName, profileName);
                    }
                } catch (Exception refreshException) {
                    log.error("Error during SSO token refresh: {}", refreshException.getMessage());
                    log.info("Please run 'aws sso login --profile {}' manually to refresh your SSO session", profileName);
                }
            } else {
                log.error("AWS credentials error (not token-related): {}", e.getMessage());
            }
        }
    }

    /**
     * Checks if the error message indicates a token expiration issue
     */
    private static boolean isTokenExpiredError(String errorMessage) {
        return errorMessage.contains("token") && 
               (errorMessage.contains("expired") || 
                errorMessage.contains("invalid") ||
                errorMessage.contains("not found") ||
                errorMessage.contains("unauthorized"));
    }

    /**
     * Resets the initialization flag (useful for testing)
     */
    public static void reset() {
        initialized = false;
    }
}