package org.qainsights.jmeter.ai.utils;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages AWS SSO token refresh automatically when tokens expire
 */
public class AwsSsoTokenManager {
    private static final Logger log = LoggerFactory.getLogger(AwsSsoTokenManager.class);
    private static volatile AwsSsoTokenManager instance;
    private volatile boolean refreshInProgress = false;

    private AwsSsoTokenManager() {}

    public static AwsSsoTokenManager getInstance() {
        if (instance == null) {
            synchronized (AwsSsoTokenManager.class) {
                if (instance == null) {
                    instance = new AwsSsoTokenManager();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a credentials provider that automatically handles SSO token refresh
     */
    public AwsCredentialsProvider createAutoRefreshCredentialsProvider(String profileName) {
        if (profileName == null || profileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be null or empty");
        }

        // Check if auto-refresh is enabled
        boolean autoRefreshEnabled = Boolean.parseBoolean(
            AiConfig.getProperty("aws.sso.auto.refresh", "true"));
        
        if (autoRefreshEnabled) {
            return new AutoRefreshProfileCredentialsProvider(profileName);
        } else {
            log.info("SSO auto-refresh is disabled, using standard profile provider");
            return ProfileCredentialsProvider.builder()
                .profileName(profileName)
                .build();
        }
    }

    /**
     * Checks if the given profile is an SSO profile
     */
    public boolean isSsoProfile(String profileName) {
        try {
            ProfileFile profileFile = ProfileFile.defaultProfileFile();
            Optional<Profile> profile = profileFile.profile(profileName);
            if (profile.isPresent()) {
                Profile p = profile.get();
                return p.property("sso_start_url").isPresent() || 
                       p.property("sso_session").isPresent();
            }
        } catch (Exception e) {
            log.debug("Could not determine if profile is SSO: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Triggers AWS SSO login for the specified profile
     */
    public CompletableFuture<Boolean> refreshSsoToken(String profileName) {
        if (refreshInProgress) {
            log.info("SSO token refresh already in progress, waiting...");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                if (refreshInProgress) {
                    return false;
                }
                refreshInProgress = true;
            }

            try {
                log.info("Attempting to refresh SSO token for profile: {}", profileName);
                
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "aws", "sso", "login", "--profile", profileName
                );
                processBuilder.inheritIO(); // This allows user interaction
                
                Process process = processBuilder.start();
                boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5 minute timeout
                
                if (finished && process.exitValue() == 0) {
                    log.info("SSO token refresh completed successfully for profile: {}", profileName);
                    return true;
                } else {
                    log.error("SSO token refresh failed or timed out for profile: {}", profileName);
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error during SSO token refresh: {}", e.getMessage());
                return false;
            } finally {
                refreshInProgress = false;
            }
        });
    }

    /**
     * Custom credentials provider that handles automatic SSO token refresh
     */
    private class AutoRefreshProfileCredentialsProvider implements AwsCredentialsProvider {
        private final String profileName;
        private volatile ProfileCredentialsProvider delegate;
        private volatile long lastRefreshAttempt = 0;
        private static final long REFRESH_COOLDOWN_MS = 60000; // 1 minute cooldown

        public AutoRefreshProfileCredentialsProvider(String profileName) {
            this.profileName = profileName;
            this.delegate = ProfileCredentialsProvider.builder()
                .profileName(profileName)
                .build();
        }

        @Override
        public software.amazon.awssdk.auth.credentials.AwsCredentials resolveCredentials() {
            try {
                return delegate.resolveCredentials();
            } catch (SdkClientException e) {
                String errorMessage = e.getMessage().toLowerCase();
                
                // Check if this is a token expiration error
                if (isTokenExpiredError(errorMessage) && isSsoProfile(profileName)) {
                    long currentTime = System.currentTimeMillis();
                    
                    // Avoid too frequent refresh attempts
                    if (currentTime - lastRefreshAttempt > REFRESH_COOLDOWN_MS) {
                        lastRefreshAttempt = currentTime;
                        log.warn("SSO token appears to be expired for profile: {}. Attempting refresh...", profileName);
                        
                        try {
                            CompletableFuture<Boolean> refreshFuture = refreshSsoToken(profileName);
                            Boolean refreshSuccess = refreshFuture.get(300, TimeUnit.SECONDS);
                            
                            if (refreshSuccess) {
                                // Create new delegate after successful refresh
                                delegate = ProfileCredentialsProvider.builder()
                                    .profileName(profileName)
                                    .build();
                                
                                // Retry credential resolution
                                return delegate.resolveCredentials();
                            }
                        } catch (Exception refreshException) {
                            log.error("Failed to refresh SSO token: {}", refreshException.getMessage());
                        }
                    }
                }
                
                // Re-throw the original exception if refresh failed or not applicable
                throw e;
            }
        }

        private boolean isTokenExpiredError(String errorMessage) {
            return errorMessage.contains("token") && 
                   (errorMessage.contains("expired") || 
                    errorMessage.contains("invalid") ||
                    errorMessage.contains("not found") ||
                    errorMessage.contains("unauthorized"));
        }

       
    }
}