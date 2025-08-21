package com.example.natsclient.config;

import com.example.natsclient.model.NatsCredentials;
import com.example.natsclient.service.impl.K8sCredentialServiceImpl;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamOptions;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StorageType;
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
                .connectionName(natsProperties.getConnectionName())
                .connectionTimeout(Duration.ofMillis(natsProperties.getConnection().getTimeout()))
                .reconnectWait(Duration.ofMillis(natsProperties.getConnection().getReconnect().getWait()))
                .maxReconnects(natsProperties.getConnection().getReconnect().getMaxAttempts())
                .pingInterval(Duration.ofMillis(natsProperties.getConnection().getPingInterval()))
                .requestCleanupInterval(Duration.ofMillis(natsProperties.getConnection().getRequestCleanupInterval()))
                .maxControlLine(natsProperties.getConnection().getMaxControlLine());
        
        // 條件性設定 noEcho
        if (natsProperties.getConnection().isNoEcho()) {
            optionsBuilder.noEcho();
        }
        
        // 優先使用Vault/K8s credentials，然後才使用properties配置
        if (vaultCredentials.hasUserPassword()) {
            optionsBuilder.userInfo(vaultCredentials.getUsername(), vaultCredentials.getPassword());
            log.info("Using username/password authentication from Vault/K8s");
        } else if (vaultCredentials.hasToken()) {
            optionsBuilder.authHandler(Nats.staticCredentials(null, vaultCredentials.getToken().toCharArray()));
            log.info("Using token authentication from Vault/K8s");
        } else if (vaultCredentials.hasCredentialsFile()) {
            optionsBuilder.authHandler(Nats.credentials(vaultCredentials.getCredentialsFile()));
            log.info("Using credentials file authentication from Vault/K8s");
        } else if (StringUtils.hasText(natsProperties.getUsername()) && StringUtils.hasText(natsProperties.getPassword())) {
            optionsBuilder.userInfo(natsProperties.getUsername(), natsProperties.getPassword());
            log.info("Using username/password authentication from properties");
        } else if (StringUtils.hasText(natsProperties.getToken())) {
            optionsBuilder.authHandler(Nats.staticCredentials(null, natsProperties.getToken().toCharArray()));
            log.info("Using token authentication from properties");
        } else if (StringUtils.hasText(natsProperties.getCredentials())) {
            optionsBuilder.authHandler(Nats.credentials(natsProperties.getCredentials()));
            log.info("Using credentials file authentication from properties");
        }
        
        Options options = optionsBuilder
                .connectionListener((conn, type) -> {
                    if (natsProperties.getLogging().isEnableConnectionEvents()) {
                        log.info("NATS connection event: {}", type);
                    }
                })
                .errorListener(new io.nats.client.ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        if (natsProperties.getLogging().isEnableErrorLogging()) {
                            log.error("NATS connection error: {}", error);
                        }
                    }

                    @Override
                    public void exceptionOccurred(Connection conn, Exception exp) {
                        if (natsProperties.getLogging().isEnableErrorLogging()) {
                            log.error("NATS connection exception", exp);
                        }
                    }

                    @Override
                    public void slowConsumerDetected(Connection conn, io.nats.client.Consumer consumer) {
                        if (natsProperties.getLogging().isEnableSlowConsumerWarning()) {
                            log.warn("NATS slow consumer detected: {}", consumer.toString());
                        }
                    }
                })
                .build();

        Connection connection = Nats.connect(options);
        log.info("Connected to NATS server: {} with connection name: '{}' [Enhanced with unlimited reconnection]", 
                natsUrl, natsProperties.getConnectionName());
        return connection;
    }

    @Bean
    public JetStreamManagement jetStreamManagement(Connection connection) throws IOException, InterruptedException {
        JetStreamManagement jsm = connection.jetStreamManagement();
        
        // Create default stream if JetStream is enabled
        if (natsProperties.getJetStream().isEnabled()) {
            createDefaultStreamIfNotExists(jsm);
        }
        
        log.info("JetStreamManagement initialized");
        return jsm;
    }

    @Bean
    public JetStream jetStream(Connection connection) throws IOException, InterruptedException {
        if (!natsProperties.getJetStream().isEnabled()) {
            throw new IllegalStateException("JetStream is not enabled in configuration");
        }

        JetStreamOptions.Builder jsOptionsBuilder = JetStreamOptions.builder();
        
        if (StringUtils.hasText(natsProperties.getJetStream().getDomain())) {
            jsOptionsBuilder.domain(natsProperties.getJetStream().getDomain());
        }
        
        if (StringUtils.hasText(natsProperties.getJetStream().getPrefix())) {
            jsOptionsBuilder.prefix(natsProperties.getJetStream().getPrefix());
        }
        
        jsOptionsBuilder.requestTimeout(Duration.ofMillis(natsProperties.getJetStream().getDefaultTimeout()));

        JetStream js = connection.jetStream(jsOptionsBuilder.build());
        log.info("JetStream context created with domain: {} and prefix: {}", 
                natsProperties.getJetStream().getDomain(), 
                natsProperties.getJetStream().getPrefix());
        return js;
    }

    private void createDefaultStreamIfNotExists(JetStreamManagement jsm) {
        try {
            String streamName = natsProperties.getJetStream().getStream().getDefaultName();
            
            // Check if stream already exists
            try {
                jsm.getStreamInfo(streamName);
                log.info("Stream '{}' already exists", streamName);
                return;
            } catch (Exception e) {
                // Stream doesn't exist, create it
                log.info("Creating default stream: {}", streamName);
            }

            StorageType storageType = "FILE".equalsIgnoreCase(natsProperties.getJetStream().getStream().getStorage()) 
                    ? StorageType.File : StorageType.Memory;

            StreamConfiguration streamConfig = StreamConfiguration.builder()
                    .name(streamName)
                    .subjects(natsProperties.getJetStream().getStream().getSubjects())
                    .storageType(storageType)
                    .maxAge(Duration.ofMillis(natsProperties.getJetStream().getStream().getMaxAge()))
                    .maxMessages(natsProperties.getJetStream().getStream().getMaxMsgs())
                    .replicas(natsProperties.getJetStream().getStream().getReplicas())
                    .build();

            jsm.addStream(streamConfig);
            log.info("Successfully created stream '{}' with subjects: {}", streamName, 
                    String.join(", ", natsProperties.getJetStream().getStream().getSubjects()));
            
        } catch (Exception e) {
            log.error("Failed to create default stream", e);
            throw new RuntimeException("Failed to initialize JetStream default stream", e);
        }
    }
}