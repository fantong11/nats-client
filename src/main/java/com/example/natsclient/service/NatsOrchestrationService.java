package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsClientException;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
@Slf4j
public class NatsOrchestrationService {


    @Autowired
    private NatsClientService natsClientService;

    @Autowired
    private NatsRequestLogRepository requestLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public CompletableFuture<NatsRequestResponse> sendRequestWithTracking(NatsRequest request) {
        String requestId = generateRequestId();
        
        log.info("Processing NATS request - Subject: {}, RequestID: {}", 
                   request.getSubject(), requestId);

        try {
            validateRequest(request);
            
            CompletableFuture<String> natsResponse = natsClientService.sendRequest(
                request.getSubject(), 
                request.getPayload()
            );

            return natsResponse.thenApply(response -> {
                NatsRequestResponse result = new NatsRequestResponse();
                result.setRequestId(requestId);
                result.setSubject(request.getSubject());
                result.setSuccess(response != null);
                
                // 嘗試將 JSON 字串解析為物件
                if (response != null) {
                    try {
                        Object jsonObject = objectMapper.readValue(response, Object.class);
                        result.setResponsePayload(jsonObject);
                    } catch (Exception e) {
                        // 如果不是有效的 JSON，就直接返回字串
                        result.setResponsePayload(response);
                        log.debug("Response is not valid JSON, returning as string: {}", e.getMessage());
                    }
                } else {
                    result.setErrorMessage("No response received");
                }
                
                result.setTimestamp(LocalDateTime.now());
                return result;
            }).exceptionally(throwable -> {
                log.error("Error processing NATS request", throwable);
                
                NatsRequestResponse errorResult = new NatsRequestResponse();
                errorResult.setRequestId(requestId);
                errorResult.setSubject(request.getSubject());
                errorResult.setSuccess(false);
                errorResult.setErrorMessage(throwable.getMessage());
                errorResult.setTimestamp(LocalDateTime.now());
                
                return errorResult;
            });

        } catch (Exception e) {
            log.error("Failed to send NATS request", e);
            
            CompletableFuture<NatsRequestResponse> errorFuture = new CompletableFuture<>();
            NatsRequestResponse errorResult = new NatsRequestResponse();
            errorResult.setRequestId(requestId);
            errorResult.setSubject(request.getSubject());
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            errorResult.setTimestamp(LocalDateTime.now());
            
            errorFuture.complete(errorResult);
            return errorFuture;
        }
    }

    public CompletableFuture<String> publishMessageWithTracking(NatsPublishRequest request) {
        log.info("Publishing NATS message - Subject: {}", request.getSubject());

        try {
            validatePublishRequest(request);
            
            return natsClientService.publishMessage(request.getSubject(), request.getPayload())
                .thenApply(result -> {
                    // 生成並返回請求ID
                    String requestId = UUID.randomUUID().toString();
                    log.info("Message published successfully with request ID: {}", requestId);
                    return requestId;
                });

        } catch (Exception e) {
            log.error("Failed to publish NATS message", e);
            
            CompletableFuture<String> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(new NatsClientException(
                "Failed to publish message: " + e.getMessage(),
                e,
                null,
                request.getSubject(),
                NatsClientException.ErrorType.UNKNOWN_ERROR
            ));
            
            return errorFuture;
        }
    }

    public NatsRequestStatus getRequestStatus(String requestId) {
        Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByRequestId(requestId);
        
        if (requestLogOpt.isEmpty()) {
            throw new NatsClientException(
                "Request not found",
                requestId,
                null,
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }

        NatsRequestLog requestLog = requestLogOpt.get();
        
        NatsRequestStatus status = new NatsRequestStatus();
        status.setRequestId(requestLog.getRequestId());
        status.setSubject(requestLog.getSubject());
        status.setStatus(requestLog.getStatus());
        status.setRequestTimestamp(requestLog.getRequestTimestamp());
        status.setResponseTimestamp(requestLog.getResponseTimestamp());
        status.setRetryCount(requestLog.getRetryCount());
        status.setErrorMessage(requestLog.getErrorMessage());
        
        return status;
    }


    public List<NatsRequestStatus> getRequestsByStatus(NatsRequestLog.RequestStatus status) {
        List<NatsRequestLog> requests = requestLogRepository.findByStatus(status);
        
        return requests.stream()
                .map(log -> {
                    NatsRequestStatus requestStatus = new NatsRequestStatus();
                    requestStatus.setRequestId(log.getRequestId());
                    requestStatus.setSubject(log.getSubject());
                    requestStatus.setStatus(log.getStatus());
                    requestStatus.setRequestTimestamp(log.getRequestTimestamp());
                    requestStatus.setResponseTimestamp(log.getResponseTimestamp());
                    requestStatus.setRetryCount(log.getRetryCount());
                    requestStatus.setErrorMessage(log.getErrorMessage());
                    return requestStatus;
                })
                .toList();
    }

    public NatsStatistics getStatistics() {
        NatsStatistics stats = new NatsStatistics();
        
        stats.setPendingRequests(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.PENDING));
        stats.setSuccessfulRequests(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.SUCCESS));
        stats.setFailedRequests(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.FAILED));
        stats.setTimeoutRequests(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.TIMEOUT));
        stats.setErrorRequests(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.ERROR));
        
        stats.setTotalRequests(
            stats.getPendingRequests() + 
            stats.getSuccessfulRequests() + 
            stats.getFailedRequests() + 
            stats.getTimeoutRequests() + 
            stats.getErrorRequests()
        );
        
        if (stats.getTotalRequests() > 0) {
            stats.setSuccessRate(
                (double) stats.getSuccessfulRequests() / stats.getTotalRequests() * 100
            );
        }
        
        return stats;
    }

    private void validateRequest(NatsRequest request) {
        if (request == null) {
            throw new NatsClientException(
                "Request cannot be null",
                null,
                null,
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }
        
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            throw new NatsClientException(
                "Subject cannot be null or empty",
                null,
                request.getSubject(),
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }
        
        if (request.getPayload() == null) {
            throw new NatsClientException(
                "Payload cannot be null",
                null,
                request.getSubject(),
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }
    }

    private void validatePublishRequest(NatsPublishRequest request) {
        if (request == null) {
            throw new NatsClientException(
                "Publish request cannot be null",
                null,
                null,
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }
        
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            throw new NatsClientException(
                "Subject cannot be null or empty",
                null,
                request.getSubject(),
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }
        
        if (request.getPayload() == null) {
            throw new NatsClientException(
                "Payload cannot be null",
                null,
                request.getSubject(),
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }
    }

    private String generateRequestId() {
        return "REQ-" + UUID.randomUUID().toString();
    }

    @Data
    public static class NatsRequest {
        private String subject;
        private Object payload;
    }

    @Data
    public static class NatsPublishRequest {
        private String subject;
        private Object payload;
    }

    @Data
    public static class NatsRequestResponse {
        private String requestId;
        private String subject;
        private boolean success;
        private Object responsePayload;
        private String errorMessage;
        private LocalDateTime timestamp;
    }

    @Data
    public static class NatsRequestStatus {
        private String requestId;
        private String subject;
        private NatsRequestLog.RequestStatus status;
        private LocalDateTime requestTimestamp;
        private LocalDateTime responseTimestamp;
        private Integer retryCount;
        private String errorMessage;
    }

    @Data
    public static class NatsStatistics {
        private long totalRequests;
        private long pendingRequests;
        private long successfulRequests;
        private long failedRequests;
        private long timeoutRequests;
        private long errorRequests;
        private double successRate;
    }
}