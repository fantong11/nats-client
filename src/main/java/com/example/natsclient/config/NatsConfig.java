package com.example.natsclient.config;

import com.example.natsclient.model.NatsCredentials;
import com.example.natsclient.service.impl.K8sCredentialServiceImpl;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class NatsConfig {

    private static final Logger logger = LoggerFactory.getLogger(NatsConfig.class);

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
                .maxReconnects(natsProperties.getConnection().getReconnect().getMaxAttempts());
        
        // 優先使用Vault/K8s credentials，然後才使用properties配置
        if (vaultCredentials.hasUserPassword()) {
            optionsBuilder.userInfo(vaultCredentials.getUsername(), vaultCredentials.getPassword());
            logger.info("Using username/password authentication from Vault/K8s");
        } else if (vaultCredentials.hasToken()) {
            optionsBuilder.token(vaultCredentials.getToken());
            logger.info("Using token authentication from Vault/K8s");
        } else if (vaultCredentials.hasCredentialsFile()) {
            optionsBuilder.authHandler(Nats.credentials(vaultCredentials.getCredentialsFile()));
            logger.info("Using credentials file authentication from Vault/K8s");
        } else if (StringUtils.hasText(natsProperties.getUsername()) && StringUtils.hasText(natsProperties.getPassword())) {
            optionsBuilder.userInfo(natsProperties.getUsername(), natsProperties.getPassword());
            logger.info("Using username/password authentication from properties");
        } else if (StringUtils.hasText(natsProperties.getToken())) {
            optionsBuilder.token(natsProperties.getToken());
            logger.info("Using token authentication from properties");
        } else if (StringUtils.hasText(natsProperties.getCredentials())) {
            optionsBuilder.authHandler(Nats.credentials(natsProperties.getCredentials()));
            logger.info("Using credentials file authentication from properties");
        }
        
        Options options = optionsBuilder
                .connectionListener((conn, type) -> {
                    logger.info("NATS connection event: {}", type);
                })
                .errorListener(new io.nats.client.ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        logger.error("NATS connection error: {}", error);
                    }

                    @Override
                    public void exceptionOccurred(Connection conn, Exception exp) {
                        logger.error("NATS connection exception", exp);
                    }

                    @Override
                    public void slowConsumerDetected(Connection conn, io.nats.client.Consumer consumer) {
                        logger.warn("NATS slow consumer detected: {}", consumer.toString());
                    }
                })
                .build();

        Connection connection = Nats.connect(options);
        logger.info("Connected to NATS server: {}", natsUrl);
        return connection;
    }
}