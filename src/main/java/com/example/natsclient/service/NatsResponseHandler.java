package com.example.natsclient.service;

import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.repository.JdbcNatsRequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class NatsResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(NatsResponseHandler.class);

    @Autowired
    private Connection natsConnection;

    @Autowired
    private JdbcNatsRequestLogRepository requestLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Dispatcher dispatcher;
    private final Map<String, String> activeSubscriptions = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        dispatcher = natsConnection.createDispatcher();
        
        subscribeToResponseSubject("response.success.*");
        subscribeToResponseSubject("response.error.*");
        subscribeToResponseSubject("response.delayed.*");
        
        logger.info("NATS response handler initialized with subscriptions");
    }

    @PreDestroy
    public void cleanup() {
        if (dispatcher != null) {
            try {
                dispatcher.unsubscribe("response.success.*");
                dispatcher.unsubscribe("response.error.*");
                dispatcher.unsubscribe("response.delayed.*");
                logger.info("NATS response handler cleanup completed");
            } catch (Exception e) {
                logger.error("Error during NATS response handler cleanup", e);
            }
        }
    }

    private void subscribeToResponseSubject(String subject) {
        dispatcher.subscribe(subject, new ResponseMessageHandler());
        activeSubscriptions.put(subject, subject);
        logger.info("Subscribed to NATS subject: {}", subject);
    }

    public void subscribeToCustomResponseSubject(String subject) {
        if (!activeSubscriptions.containsKey(subject)) {
            dispatcher.subscribe(subject, new ResponseMessageHandler());
            activeSubscriptions.put(subject, subject);
            logger.info("Subscribed to custom NATS response subject: {}", subject);
        }
    }

    private class ResponseMessageHandler implements MessageHandler {
        @Override
        @Transactional
        public void onMessage(Message message) {
            try {
                String responsePayload = new String(message.getData(), StandardCharsets.UTF_8);
                String subject = message.getSubject();
                
                logger.debug("Received response on subject: {}, payload length: {}", 
                           subject, responsePayload.length());
                
                processResponse(subject, responsePayload, message);
                
            } catch (Exception e) {
                logger.error("Error processing NATS response message", e);
            }
        }
    }

    @Transactional
    private void processResponse(String subject, String responsePayload, Message message) {
        try {
            Map<String, Object> responseData = objectMapper.readValue(responsePayload, new TypeReference<Map<String, Object>>() {});
            
            String requestId = extractRequestId(responseData, message);
            String correlationId = extractCorrelationId(responseData, message);
            
            Optional<NatsRequestLogDto> requestLogOpt = findRequestLog(requestId, correlationId);
            
            if (requestLogOpt.isPresent()) {
                NatsRequestLogDto requestLog = requestLogOpt.get();
                
                if (subject.contains("success")) {
                    handleSuccessResponse(requestLog, responsePayload);
                } else if (subject.contains("error")) {
                    handleErrorResponse(requestLog, responsePayload, responseData);
                } else if (subject.contains("delayed")) {
                    handleDelayedResponse(requestLog, responsePayload);
                } else {
                    handleGenericResponse(requestLog, responsePayload);
                }
                
                logger.info("Processed response for request ID: {}, correlation ID: {}, status: {}", 
                           requestLog.getRequestId(), requestLog.getCorrelationId(), requestLog.getStatus());
                
            } else {
                logger.warn("No matching request found for response - RequestID: {}, CorrelationID: {}", 
                           requestId, correlationId);
                
                logUnmatchedResponse(subject, responsePayload, requestId, correlationId);
            }
            
        } catch (Exception e) {
            logger.error("Error processing response payload", e);
        }
    }

    private String extractRequestId(Map<String, Object> responseData, Message message) {
        String requestId = (String) responseData.get("requestId");
        if (requestId == null) {
            requestId = (String) responseData.get("request_id");
        }
        if (requestId == null && message.getReplyTo() != null) {
            requestId = message.getReplyTo();
        }
        return requestId;
    }

    private String extractCorrelationId(Map<String, Object> responseData, Message message) {
        String correlationId = (String) responseData.get("correlationId");
        if (correlationId == null) {
            correlationId = (String) responseData.get("correlation_id");
        }
        if (correlationId == null && message.getHeaders() != null) {
            correlationId = message.getHeaders().getFirst("Correlation-ID");
        }
        return correlationId;
    }

    private Optional<NatsRequestLogDto> findRequestLog(String requestId, String correlationId) {
        if (requestId != null) {
            return requestLogRepository.findByRequestId(requestId);
        } else if (correlationId != null) {
            return requestLogRepository.findByCorrelationId(correlationId);
        }
        return Optional.empty();
    }

    private void handleSuccessResponse(NatsRequestLogDto requestLog, String responsePayload) {
        requestLogRepository.updateResponseByRequestId(
            requestLog.getRequestId(),
            NatsRequestLogDto.RequestStatus.SUCCESS,
            responsePayload,
            LocalDateTime.now(),
            "ASYNC_HANDLER"
        );
    }

    private void handleErrorResponse(NatsRequestLogDto requestLog, String responsePayload, Map<String, Object> responseData) {
        String errorMessage = (String) responseData.getOrDefault("error", "Unknown error occurred");
        
        requestLog.setStatus(NatsRequestLogDto.RequestStatus.FAILED);
        requestLog.setResponsePayload(responsePayload);
        requestLog.setResponseTimestamp(LocalDateTime.now());
        requestLog.setErrorMessage(errorMessage);
        requestLog.setUpdatedBy("ASYNC_HANDLER");
        
        requestLogRepository.save(requestLog);
    }

    private void handleDelayedResponse(NatsRequestLogDto requestLog, String responsePayload) {
        requestLog.setResponsePayload(responsePayload);
        requestLog.setResponseTimestamp(LocalDateTime.now());
        requestLog.setUpdatedBy("ASYNC_HANDLER");
        
        requestLogRepository.save(requestLog);
        
        logger.info("Received delayed response for request ID: {}", requestLog.getRequestId());
    }

    private void handleGenericResponse(NatsRequestLogDto requestLog, String responsePayload) {
        requestLogRepository.updateResponseByRequestId(
            requestLog.getRequestId(),
            NatsRequestLogDto.RequestStatus.SUCCESS,
            responsePayload,
            LocalDateTime.now(),
            "ASYNC_HANDLER"
        );
    }

    private void logUnmatchedResponse(String subject, String responsePayload, String requestId, String correlationId) {
        NatsRequestLogDto unmatchedLog = NatsRequestLogDto.builder()
                .requestId("UNMATCHED_" + System.currentTimeMillis())
                .subject(subject)
                .responsePayload(responsePayload)
                .correlationId(correlationId)
                .status(NatsRequestLogDto.RequestStatus.ERROR)
                .errorMessage("No matching request found for response")
                .createdBy("ASYNC_HANDLER")
                .updatedBy("ASYNC_HANDLER")
                .requestTimestamp(LocalDateTime.now())
                .responseTimestamp(LocalDateTime.now())
                .retryCount(0)
                .build();
        
        requestLogRepository.save(unmatchedLog);
    }
}