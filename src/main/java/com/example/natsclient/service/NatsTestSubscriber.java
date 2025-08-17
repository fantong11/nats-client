package com.example.natsclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class NatsTestSubscriber {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsTestSubscriber.class);
    
    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private Dispatcher dispatcher;
    
    @Autowired
    public NatsTestSubscriber(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void startSubscribers() {
        try {
            dispatcher = natsConnection.createDispatcher(this::handleMessage);
            
            // 訂閱測試相關的主題
            dispatcher.subscribe("test.echo");
            dispatcher.subscribe("test.timeout");
            dispatcher.subscribe("test.error");
            dispatcher.subscribe("demo.*");
            dispatcher.subscribe("api.*");
            
            logger.info("NATS test subscribers started");
            
        } catch (Exception e) {
            logger.error("Failed to start NATS subscribers", e);
        }
    }
    
    private void handleMessage(Message message) {
        String subject = message.getSubject();
        String replyTo = message.getReplyTo();
        String payload = new String(message.getData(), StandardCharsets.UTF_8);
        
        logger.info("Received message - Subject: {}, ReplyTo: {}, Payload: {}", subject, replyTo, payload);
        
        try {
            String response = processMessage(subject, payload);
            
            if (replyTo != null) {
                // 發送回覆
                natsConnection.publish(replyTo, response.getBytes(StandardCharsets.UTF_8));
                logger.info("Sent reply to: {}, Response: {}", replyTo, response);
            }
            
        } catch (Exception e) {
            logger.error("Error processing message for subject: {}", subject, e);
            
            if (replyTo != null) {
                try {
                    String errorResponse = createErrorResponse(e.getMessage());
                    natsConnection.publish(replyTo, errorResponse.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    logger.error("Failed to send error response", ex);
                }
            }
        }
    }
    
    private String processMessage(String subject, String payload) throws Exception {
        switch (subject) {
            case "test.echo":
                return processEcho(payload);
                
            case "test.timeout":
                return processTimeout(payload);
                
            case "test.error":
                return processError(payload);
                
            default:
                if (subject.startsWith("demo.") || subject.startsWith("api.")) {
                    return processGeneric(subject, payload);
                }
                return createSuccessResponse("Message received", payload);
        }
    }
    
    private String processEcho(String payload) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Echo response");
        response.put("original_payload", payload);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("processed_by", "nats-test-subscriber");
        
        return objectMapper.writeValueAsString(response);
    }
    
    private String processTimeout(String payload) throws Exception {
        // 模擬處理時間，但不會真的timeout (讓NATS client timeout)
        logger.info("Processing timeout test message: {}", payload);
        
        // 可以選擇性地延遲回覆來測試timeout
        Thread.sleep(2000); // 2秒延遲
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Timeout test completed");
        response.put("delay", "2000ms");
        response.put("timestamp", LocalDateTime.now().toString());
        
        return objectMapper.writeValueAsString(response);
    }
    
    private String processError(String payload) throws Exception {
        logger.info("Processing error test message: {}", payload);
        
        // 模擬處理錯誤
        throw new RuntimeException("Simulated processing error for testing");
    }
    
    private String processGeneric(String subject, String payload) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Generic message processed");
        response.put("subject", subject);
        response.put("original_payload", payload);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("server_info", "NATS Test Subscriber v1.0");
        
        return objectMapper.writeValueAsString(response);
    }
    
    private String createSuccessResponse(String message, String originalPayload) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", message);
        response.put("original_payload", originalPayload);
        response.put("timestamp", LocalDateTime.now().toString());
        
        return objectMapper.writeValueAsString(response);
    }
    
    private String createErrorResponse(String errorMessage) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Processing failed");
        response.put("error", errorMessage);
        response.put("timestamp", LocalDateTime.now().toString());
        
        return objectMapper.writeValueAsString(response);
    }
}