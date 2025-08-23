package com.example.natsclient.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NatsCredentialsTest {

    @Test
    void defaultConstructor_ShouldCreateEmptyCredentials() {
        NatsCredentials credentials = new NatsCredentials();
        
        assertNull(credentials.getUrl());
        assertNull(credentials.getUsername());
        assertNull(credentials.getPassword());
        assertNull(credentials.getToken());
        assertNull(credentials.getCredentialsFile());
        
        assertFalse(credentials.hasUserPassword());
        assertFalse(credentials.hasToken());
        assertFalse(credentials.hasCredentialsFile());
    }

    @Test
    void hasUserPassword_WhenUsernameAndPasswordSet_ShouldReturnTrue() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setUsername("testuser");
        credentials.setPassword("testpass");
        
        assertTrue(credentials.hasUserPassword());
    }

    @Test
    void hasUserPassword_WhenOnlyUsernameSet_ShouldReturnFalse() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setUsername("testuser");
        
        assertFalse(credentials.hasUserPassword());
    }

    @Test
    void hasUserPassword_WhenOnlyPasswordSet_ShouldReturnFalse() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setPassword("testpass");
        
        assertFalse(credentials.hasUserPassword());
    }

    @Test
    void hasUserPassword_WhenEmptyCredentials_ShouldReturnFalse() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setUsername("");
        credentials.setPassword("");
        
        assertFalse(credentials.hasUserPassword());
    }

    @Test
    void hasUserPassword_WhenWhitespaceCredentials_ShouldReturnTrue() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setUsername("   ");
        credentials.setPassword("   ");
        
        assertTrue(credentials.hasUserPassword()); // Only checks null and empty, not whitespace
    }

    @Test
    void hasToken_WhenTokenSet_ShouldReturnTrue() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setToken("some-jwt-token");
        
        assertTrue(credentials.hasToken());
    }

    @Test
    void hasToken_WhenTokenEmpty_ShouldReturnFalse() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setToken("");
        
        assertFalse(credentials.hasToken());
    }

    @Test
    void hasToken_WhenTokenWhitespace_ShouldReturnTrue() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setToken("   ");
        
        assertTrue(credentials.hasToken()); // Only checks null and empty, not whitespace
    }

    @Test
    void hasCredentialsFile_WhenFilePathSet_ShouldReturnTrue() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setCredentialsFile("/path/to/credentials.creds");
        
        assertTrue(credentials.hasCredentialsFile());
    }

    @Test
    void hasCredentialsFile_WhenFilePathEmpty_ShouldReturnFalse() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setCredentialsFile("");
        
        assertFalse(credentials.hasCredentialsFile());
    }

    @Test
    void hasCredentialsFile_WhenFilePathWhitespace_ShouldReturnTrue() {
        NatsCredentials credentials = new NatsCredentials();
        credentials.setCredentialsFile("   ");
        
        assertTrue(credentials.hasCredentialsFile()); // Only checks null and empty, not whitespace
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        NatsCredentials credentials = new NatsCredentials();
        
        String url = "nats://localhost:4222";
        String username = "admin";
        String password = "secret";
        String token = "jwt-token";
        String credentialsFile = "/etc/nats/creds";
        
        credentials.setUrl(url);
        credentials.setUsername(username);
        credentials.setPassword(password);
        credentials.setToken(token);
        credentials.setCredentialsFile(credentialsFile);
        
        assertEquals(url, credentials.getUrl());
        assertEquals(username, credentials.getUsername());
        assertEquals(password, credentials.getPassword());
        assertEquals(token, credentials.getToken());
        assertEquals(credentialsFile, credentials.getCredentialsFile());
    }

    @Test
    void multipleAuthenticationMethods_ShouldBeIndependent() {
        NatsCredentials credentials = new NatsCredentials();
        
        credentials.setUsername("user");
        credentials.setPassword("pass");
        credentials.setToken("token");
        credentials.setCredentialsFile("/path/to/creds");
        
        assertTrue(credentials.hasUserPassword());
        assertTrue(credentials.hasToken());
        assertTrue(credentials.hasCredentialsFile());
    }

    @Test
    void nullValues_ShouldBeHandledGracefully() {
        NatsCredentials credentials = new NatsCredentials();
        
        credentials.setUsername(null);
        credentials.setPassword(null);
        credentials.setToken(null);
        credentials.setCredentialsFile(null);
        
        assertFalse(credentials.hasUserPassword());
        assertFalse(credentials.hasToken());
        assertFalse(credentials.hasCredentialsFile());
    }
}