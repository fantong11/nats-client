package com.example.natsclient.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamOptions;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NatsProperties.class)
@Slf4j
public class NatsConfig {

    private final NatsProperties natsProperties;

    public NatsConfig(NatsProperties natsProperties) {
        this.natsProperties = natsProperties;
    }

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        Options.Builder optionsBuilder = new Options.Builder()
                .server(natsProperties.getUrl())
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
        
        // 認證邏輯 - 從 application.yml 配置取得
        if (StringUtils.hasText(natsProperties.getUsername()) && StringUtils.hasText(natsProperties.getPassword())) {
            optionsBuilder.userInfo(natsProperties.getUsername(), natsProperties.getPassword());
            log.info("Using username/password authentication");
        } else if (StringUtils.hasText(natsProperties.getToken())) {
            optionsBuilder.authHandler(Nats.staticCredentials(null, natsProperties.getToken().toCharArray()));
            log.info("Using token authentication");
        } else if (StringUtils.hasText(natsProperties.getCredentials())) {
            optionsBuilder.authHandler(Nats.credentials(natsProperties.getCredentials()));
            log.info("Using credentials file authentication");
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
        log.info("Connected to NATS server: {} with connection name: '{}'", 
                natsProperties.getUrl(), natsProperties.getConnectionName());
        return connection;
    }

    @Bean
    public JetStreamManagement jetStreamManagement(Connection connection) throws IOException, InterruptedException {
        JetStreamManagement jsm = connection.jetStreamManagement();
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
                natsProperties.getJetStream().getDomain(), natsProperties.getJetStream().getPrefix());
        return js;
    }

}