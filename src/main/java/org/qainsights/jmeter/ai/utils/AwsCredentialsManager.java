package org.qainsights.jmeter.ai.utils;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

/**
 * Centralized AWS credentials management for the application
 */
public class AwsCredentialsManager {
    private static final Logger log = LoggerFactory.getLogger(AwsCredentialsManager.class);
    
    /**
     * Creates the appropriate credentials provider based on configuration
     */
    public static AwsCredentialsProvider createCredentialsProvider() {
        // First try properties configuration
        String accessKey = AiConfig.getProperty("aws.access.key.id", "");
        String secretKey = AiConfig.getProperty("aws.secret.access.key", "");
        String sessionToken = AiConfig.getProperty("aws.session.token", "");
        
        if (accessKey != null && !accessKey.trim().isEmpty() && 
            secretKey != null && !secretKey.trim().isEmpty()) {
            
            if (sessionToken != null && !sessionToken.trim().isEmpty()) {
                log.info("Using AWS credentials from properties: AccessKey={}, SecretKey={}, SessionToken={}", 
                    maskCredential(accessKey), maskCredential(secretKey), maskCredential(sessionToken));
                // Use session credentials
                return StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(accessKey, secretKey, sessionToken));
            } else {
                log.info("Using AWS credentials from properties: AccessKey={}, SecretKey={}", 
                    maskCredential(accessKey), maskCredential(secretKey));
                // Use basic credentials
                return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
            }
        }
        
        // Try environment variables
        String envAccessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String envSecretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String envSessionToken = System.getenv("AWS_SESSION_TOKEN");
        
        if (envAccessKey != null && envSecretKey != null) {
            if (envSessionToken != null) {
                log.info("Using AWS credentials from environment: AccessKey={}, SecretKey={}, SessionToken={}", 
                    maskCredential(envAccessKey), maskCredential(envSecretKey), maskCredential(envSessionToken));
                return StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(envAccessKey, envSecretKey, envSessionToken));
            } else {
                log.info("Using AWS credentials from environment: AccessKey={}, SecretKey={}", 
                    maskCredential(envAccessKey), maskCredential(envSecretKey));
                return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(envAccessKey, envSecretKey));
            }
        }
        
        // Try profile-based credentials
        String profileName = AiConfig.getProperty("aws.profile.name", "");
        if (profileName != null && !profileName.trim().isEmpty()) {
            log.info("Using AWS profile: {}", profileName);
            try {
                // Check if this is an SSO profile
                try {
                    ProfileFile profileFile = ProfileFile.defaultProfileFile();
                    Optional<Profile> profile = profileFile.profile(profileName);
                    if (profile.isPresent()) {
                        Profile p = profile.get();
                        if (p.property("sso_start_url").isPresent() || 
                            p.property("sso_session").isPresent()) {
                            log.info("Detected SSO profile: {}", profileName);
                        }
                    }
                } catch (Exception profileException) {
                    log.debug("Could not read profile file: {}", profileException.getMessage());
                }
                return ProfileCredentialsProvider.builder()
                    .profileName(profileName)
                    .build();
            } catch (Exception e) {
                log.warn("Failed to create profile credentials provider for profile '{}': {}", profileName, e.getMessage());
                log.info("Falling back to default credentials provider");
            }
        }
        
        // Fall back to default credentials provider chain
        log.info("Using AWS default credentials provider chain");
        return DefaultCredentialsProvider.builder().build();
    }
    
    /**
     * Masks sensitive credential information for logging
     */
    private static String maskCredential(String credential) {
        if (credential == null || credential.length() <= 8) {
            return "****";
        }
        return credential.substring(0, 4) + "****" + credential.substring(credential.length() - 4);
    }
    
    /**
     * Validates if AWS credentials are configured
     */
    public static boolean isConfigValid() {
        String accessKey = AiConfig.getProperty("aws.access.key.id", "");
        String secretKey = AiConfig.getProperty("aws.secret.access.key", "");
        String profileName = AiConfig.getProperty("aws.profile.name", "");
        
        // Check if explicit credentials are provided
        boolean hasExplicitCredentials = (accessKey != null && !accessKey.trim().isEmpty()) &&
                                       (secretKey != null && !secretKey.trim().isEmpty());
        
        // Check if profile is specified
        boolean hasProfile = (profileName != null && !profileName.trim().isEmpty());
        
        // If profile is specified, validate it exists
        if (hasProfile) {
            try {
                ProfileFile profileFile = ProfileFile.defaultProfileFile();
                Optional<Profile> profile = profileFile.profile(profileName);
                if (profile.isPresent()) {
                    log.info("Found AWS profile: {}", profileName);
                    return true;
                } else {
                    log.warn("AWS profile '{}' not found in credentials file", profileName);
                    return false;
                }
            } catch (Exception e) {
                log.warn("Error validating AWS profile '{}': {}", profileName, e.getMessage());
                return false;
            }
        }
        
        // If no explicit credentials or profile, assume IAM roles or environment variables are used
        return hasExplicitCredentials ||
               System.getenv("AWS_ACCESS_KEY_ID") != null || 
               System.getProperty("aws.accessKeyId") != null;
    }
}