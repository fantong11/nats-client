package com.example.natsclient.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;

@Configuration
public class VaultConfig extends AbstractVaultConfiguration {
    
    @Autowired
    private VaultProperties vaultProperties;
    
    @Override
    public VaultEndpoint vaultEndpoint() {
        VaultEndpoint endpoint = VaultEndpoint.create(vaultProperties.getHost(), vaultProperties.getPort());
        endpoint.setScheme(vaultProperties.getScheme());
        return endpoint;
    }
    
    @Override
    public TokenAuthentication clientAuthentication() {
        return new TokenAuthentication(vaultProperties.getToken());
    }
}