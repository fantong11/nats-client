package com.example.natsclient.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NatsCredentials {
    
    private String username;
    private String password;
    private String token;
    private String credentialsFile;
    private String url;
    
    
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