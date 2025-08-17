package com.example.natsclient.service.impl;

import com.example.natsclient.model.NatsCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class K8sCredentialServiceImpl {
    
    private static final Logger logger = LoggerFactory.getLogger(K8sCredentialServiceImpl.class);
    
    private final ObjectMapper objectMapper;
    
    // Vault Agent會將secrets寫到這些路徑
    @Value("${nats.credentials.vault-path:/vault/secrets/nats}")
    private String vaultSecretsPath;
    
    @Value("${nats.credentials.k8s-secret-path:/etc/secrets/nats}")
    private String k8sSecretPath;
    
    @Autowired
    public K8sCredentialServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public NatsCredentials loadNatsCredentials() {
        // 優先順序：Vault Agent > K8s Secrets > Environment Variables
        
        NatsCredentials credentials = loadFromVaultAgent();
        if (credentials != null) {
            logger.info("Loaded NATS credentials from Vault Agent");
            return credentials;
        }
        
        credentials = loadFromK8sSecrets();
        if (credentials != null) {
            logger.info("Loaded NATS credentials from Kubernetes Secrets");
            return credentials;
        }
        
        credentials = loadFromEnvironment();
        if (credentials != null) {
            logger.info("Loaded NATS credentials from Environment Variables");
            return credentials;
        }
        
        logger.warn("No NATS credentials found, using default configuration");
        return new NatsCredentials();
    }
    
    private NatsCredentials loadFromVaultAgent() {
        Path secretPath = Paths.get(vaultSecretsPath, "nats.json");
        
        if (!Files.exists(secretPath)) {
            logger.debug("Vault Agent secret file not found: {}", secretPath);
            return null;
        }
        
        try {
            String content = Files.readString(secretPath);
            JsonNode jsonNode = objectMapper.readTree(content);
            
            NatsCredentials credentials = new NatsCredentials();
            
            if (jsonNode.has("data")) {
                JsonNode data = jsonNode.get("data");
                credentials.setUrl(data.path("url").asText(null));
                credentials.setUsername(data.path("username").asText(null));
                credentials.setPassword(data.path("password").asText(null));
                credentials.setToken(data.path("token").asText(null));
                credentials.setCredentialsFile(data.path("credentials_file").asText(null));
            }
            
            return credentials;
            
        } catch (IOException e) {
            logger.error("Failed to read Vault Agent secrets from: {}", secretPath, e);
            return null;
        }
    }
    
    private NatsCredentials loadFromK8sSecrets() {
        NatsCredentials credentials = new NatsCredentials();
        boolean hasCredentials = false;
        
        // 讀取K8s Secret掛載的檔案
        hasCredentials |= readSecretFile(k8sSecretPath, "url", credentials::setUrl);
        hasCredentials |= readSecretFile(k8sSecretPath, "username", credentials::setUsername);
        hasCredentials |= readSecretFile(k8sSecretPath, "password", credentials::setPassword);
        hasCredentials |= readSecretFile(k8sSecretPath, "token", credentials::setToken);
        hasCredentials |= readSecretFile(k8sSecretPath, "credentials", credentials::setCredentialsFile);
        
        return hasCredentials ? credentials : null;
    }
    
    private boolean readSecretFile(String basePath, String fileName, java.util.function.Consumer<String> setter) {
        Path filePath = Paths.get(basePath, fileName);
        
        if (!Files.exists(filePath)) {
            return false;
        }
        
        try {
            String content = Files.readString(filePath).trim();
            if (StringUtils.hasText(content)) {
                setter.accept(content);
                return true;
            }
        } catch (IOException e) {
            logger.warn("Failed to read secret file: {}", filePath, e);
        }
        
        return false;
    }
    
    private NatsCredentials loadFromEnvironment() {
        NatsCredentials credentials = new NatsCredentials();
        boolean hasCredentials = false;
        
        String url = System.getenv("NATS_URL");
        if (StringUtils.hasText(url)) {
            credentials.setUrl(url);
            hasCredentials = true;
        }
        
        String username = System.getenv("NATS_USERNAME");
        String password = System.getenv("NATS_PASSWORD");
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            credentials.setUsername(username);
            credentials.setPassword(password);
            hasCredentials = true;
        }
        
        String token = System.getenv("NATS_TOKEN");
        if (StringUtils.hasText(token)) {
            credentials.setToken(token);
            hasCredentials = true;
        }
        
        String credentialsFile = System.getenv("NATS_CREDENTIALS_FILE");
        if (StringUtils.hasText(credentialsFile)) {
            credentials.setCredentialsFile(credentialsFile);
            hasCredentials = true;
        }
        
        return hasCredentials ? credentials : null;
    }
}