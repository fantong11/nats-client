package com.example.natsclient.config;

import com.example.natsclient.model.NatsCredentials;
import com.example.natsclient.service.impl.K8sCredentialServiceImpl;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;

@Configuration
@Slf4j
public class NatsConfig {


    @Autowired
    private NatsProperties natsProperties;
    
    @Autowired
    private K8sCredentialServiceImpl credentialService;

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        // 從Vault/K8s獲取credentials
        NatsCredentials vaultCredentials = credentialService.loadNatsCredentials();
        
        // 決定使用哪個URL：Vault credentials > properties配置
        String natsUrl = StringUtils.hasText(vaultCredentials.getUrl()) ? 
                        vaultCredentials.getUrl() : natsProperties.getUrl();
        
        Options.Builder optionsBuilder = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofMillis(natsProperties.getConnection().getTimeout()))
                .reconnectWait(Duration.ofMillis(natsProperties.getConnection().getReconnect().getWait()))
                .maxReconnects(-1);  // 改進：無限重連，防止永久失敗
        
        // 優先使用Vault/K8s credentials，然後才使用properties配置
        if (vaultCredentials.hasUserPassword()) {
            optionsBuilder.userInfo(vaultCredentials.getUsername(), vaultCredentials.getPassword());
            log.info("Using username/password authentication from Vault/K8s");
        } else if (vaultCredentials.hasToken()) {
            optionsBuilder.token(vaultCredentials.getToken());
            log.info("Using token authentication from Vault/K8s");
        } else if (vaultCredentials.hasCredentialsFile()) {
            optionsBuilder.authHandler(Nats.credentials(vaultCredentials.getCredentialsFile()));
            log.info("Using credentials file authentication from Vault/K8s");
        } else if (StringUtils.hasText(natsProperties.getUsername()) && StringUtils.hasText(natsProperties.getPassword())) {
            optionsBuilder.userInfo(natsProperties.getUsername(), natsProperties.getPassword());
            log.info("Using username/password authentication from properties");
        } else if (StringUtils.hasText(natsProperties.getToken())) {
            optionsBuilder.token(natsProperties.getToken());
            log.info("Using token authentication from properties");
        } else if (StringUtils.hasText(natsProperties.getCredentials())) {
            optionsBuilder.authHandler(Nats.credentials(natsProperties.getCredentials()));
            log.info("Using credentials file authentication from properties");
        }
        
        Options options = optionsBuilder
                .connectionListener((conn, type) -> {
                    log.info("NATS connection event: {}", type);
                })
                .errorListener(new io.nats.client.ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        log.error("NATS connection error: {}", error);
                    }

                    @Override
                    public void exceptionOccurred(Connection conn, Exception exp) {
                        log.error("NATS connection exception", exp);
                    }

                    @Override
                    public void slowConsumerDetected(Connection conn, io.nats.client.Consumer consumer) {
                        log.warn("NATS slow consumer detected: {}", consumer.toString());
                    }
                })
                .build();

        Connection connection = Nats.connect(options);
        log.info("Connected to NATS server: {} [Enhanced with unlimited reconnection]", natsUrl);
        return connection;
    }
}