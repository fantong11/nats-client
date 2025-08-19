package com.example.natsclient.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;

@Configuration
@ConditionalOnProperty(value = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultConfig extends AbstractVaultConfiguration {
    
    @Autowired
    private VaultProperties vaultProperties;
    
    @Override
    @NonNull
    public VaultEndpoint vaultEndpoint() {
        if (vaultProperties == null) {
            throw new IllegalStateException("VaultProperties not initialized");
        }
        
        String host = vaultProperties.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("Vault host is not configured");
        }
        
        VaultEndpoint endpoint = VaultEndpoint.create(host, vaultProperties.getPort());
        String scheme = vaultProperties.getScheme();
        if (StringUtils.hasText(scheme)) {
            endpoint.setScheme(scheme);
        }
        return endpoint;
    }
    
    @Override
    @NonNull
    public ClientAuthentication clientAuthentication() {
        if (vaultProperties == null) {
            throw new IllegalStateException("VaultProperties not initialized");
        }
        
        String token = vaultProperties.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Vault token is not configured");
        }
        
        return new TokenAuthentication(token);
    }
}