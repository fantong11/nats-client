package com.example.natsclient.config;

import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.JetStreamManagement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自動設置JetStream Streams
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class JetStreamSetup {
    
    private final JetStreamManagement jetStreamManagement;
    private final NatsProperties natsProperties;
    
    @Bean
    public ApplicationRunner setupJetStreamStreams() {
        return args -> {
            log.info("Setting up JetStream streams...");
            
            try {
                // 創建主要的requests stream
                createStreamIfNotExists("REQUESTS_STREAM", "requests.*");
                
                // 創建responses stream  
                createStreamIfNotExists("RESPONSES_STREAM", "responses.*");
                
                // 創建測試stream
                createStreamIfNotExists("TEST_STREAM", "test.*");
                
                log.info("JetStream streams setup completed successfully");
                
            } catch (Exception e) {
                log.error("Failed to setup JetStream streams", e);
                throw new RuntimeException("JetStream setup failed", e);
            }
        };
    }
    
    private void createStreamIfNotExists(String streamName, String subject) throws Exception {
        try {
            // 檢查stream是否已存在
            StreamInfo streamInfo = jetStreamManagement.getStreamInfo(streamName);
            log.info("Stream '{}' already exists with {} messages", streamName, streamInfo.getStreamState().getMsgCount());
            
        } catch (Exception e) {
            // Stream不存在，創建新的
            log.info("Creating new stream '{}' for subject '{}'", streamName, subject);
            
            StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subject)
                .storageType(StorageType.Memory) // 使用內存存儲
                .maxAge(java.time.Duration.ofHours(24)) // 保留24小時
                .maxMessages(10000) // 最多保留10000條消息
                .build();
            
            StreamInfo createdStream = jetStreamManagement.addStream(streamConfig);
            log.info("Successfully created stream '{}' with storage type: MEMORY", 
                    createdStream.getConfiguration().getName());
        }
    }
}