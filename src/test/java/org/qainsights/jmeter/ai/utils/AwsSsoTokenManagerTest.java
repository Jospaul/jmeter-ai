package org.qainsights.jmeter.ai.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for AwsSsoTokenManager
 */
public class AwsSsoTokenManagerTest {

    @BeforeEach
    void setUp() {
        // Reset initialization state before each test
        AwsStartupInitializer.reset();
    }

    @Test
    void testSingletonInstance() {
        AwsSsoTokenManager instance1 = AwsSsoTokenManager.getInstance();
        AwsSsoTokenManager instance2 = AwsSsoTokenManager.getInstance();
        
        assertSame(instance1, instance2, "Should return the same singleton instance");
    }

    @Test
    void testCreateAutoRefreshCredentialsProvider() {
        AwsSsoTokenManager tokenManager = AwsSsoTokenManager.getInstance();
        
        assertThrows(IllegalArgumentException.class, () -> {
            tokenManager.createAutoRefreshCredentialsProvider(null);
        }, "Should throw exception for null profile name");
        
        assertThrows(IllegalArgumentException.class, () -> {
            tokenManager.createAutoRefreshCredentialsProvider("");
        }, "Should throw exception for empty profile name");
        
        assertThrows(IllegalArgumentException.class, () -> {
            tokenManager.createAutoRefreshCredentialsProvider("   ");
        }, "Should throw exception for whitespace-only profile name");
    }

    @Test
    void testIsSsoProfile() {
        AwsSsoTokenManager tokenManager = AwsSsoTokenManager.getInstance();
        
        // This will return false for non-existent profiles, which is expected
        boolean result = tokenManager.isSsoProfile("non-existent-profile");
        assertFalse(result, "Non-existent profile should not be detected as SSO profile");
    }
}