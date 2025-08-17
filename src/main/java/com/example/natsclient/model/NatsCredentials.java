package com.example.natsclient.model;

public class NatsCredentials {
    
    private String username;
    private String password;
    private String token;
    private String credentialsFile;
    private String url;
    
    public NatsCredentials() {}
    
    public NatsCredentials(String username, String password, String url) {
        this.username = username;
        this.password = password;
        this.url = url;
    }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public String getCredentialsFile() { return credentialsFile; }
    public void setCredentialsFile(String credentialsFile) { this.credentialsFile = credentialsFile; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public boolean hasUserPassword() {
        return username != null && !username.isEmpty() && 
               password != null && !password.isEmpty();
    }
    
    public boolean hasToken() {
        return token != null && !token.isEmpty();
    }
    
    public boolean hasCredentialsFile() {
        return credentialsFile != null && !credentialsFile.isEmpty();
    }
}