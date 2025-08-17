package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsClientException;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class NatsOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(NatsOrchestrationService.class);

    @Autowired
    private NatsClientService natsClientService;

    @Autowired
    private NatsRequestLogRepository requestLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public CompletableFuture<NatsRequestResponse> sendRequestWithTracking(NatsRequest request) {
        String correlationId = generateCorrelationId();
        
        logger.info("Processing NATS request - Subject: {}, CorrelationID: {}", 
                   request.getSubject(), correlationId);

        try {
            validateRequest(request);
            
            CompletableFuture<String> natsResponse = natsClientService.sendRequest(
                request.getSubject(), 
                request.getPayload(), 
                correlationId
            );

            return natsResponse.thenApply(response -> {
                NatsRequestResponse result = new NatsRequestResponse();
                result.setCorrelationId(correlationId);
                result.setSubject(request.getSubject());
                result.setSuccess(response != null);
                result.setResponsePayload(response);
                result.setTimestamp(LocalDateTime.now());
                
                if (response == null) {
                    result.setErrorMessage("No response received");
                }
                
                return result;
            }).exceptionally(throwable -> {
                logger.error("Error processing NATS request", throwable);
                
                NatsRequestResponse errorResult = new NatsRequestResponse();
                errorResult.setCorrelationId(correlationId);
                errorResult.setSubject(request.getSubject());
                errorResult.setSuccess(false);
                errorResult.setErrorMessage(throwable.getMessage());
                errorResult.setTimestamp(LocalDateTime.now());
                
                return errorResult;
            });

        } catch (Exception e) {
            logger.error("Failed to send NATS request", e);
            
            CompletableFuture<NatsRequestResponse> errorFuture = new CompletableFuture<>();
            NatsRequestResponse errorResult = new NatsRequestResponse();
            errorResult.setCorrelationId(correlationId);
            errorResult.setSubject(request.getSubject());
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            errorResult.setTimestamp(LocalDateTime.now());
            
            errorFuture.complete(errorResult);
            return errorFuture;
        }
    }

    public CompletableFuture<Void> publishMessageWithTracking(NatsPublishRequest request) {
        logger.info("Publishing NATS message - Subject: {}", request.getSubject());

        try {
            validatePublishRequest(request);
            
            return natsClientService.publishMessage(request.getSubject(), request.getPayload());

        } catch (Exception e) {
            logger.error("Failed to publish NATS message", e);
            
            CompletableFuture<Void> errorFuture = new CompletableFuture<>();
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
        status.setCorrelationId(requestLog.getCorrelationId());
        status.setSubject(requestLog.getSubject());
        status.setStatus(requestLog.getStatus());
        status.setRequestTimestamp(requestLog.getRequestTimestamp());
        status.setResponseTimestamp(requestLog.getResponseTimestamp());
        status.setRetryCount(requestLog.getRetryCount());
        status.setErrorMessage(requestLog.getErrorMessage());
        
        return status;
    }

    public NatsRequestStatus getRequestStatusByCorrelationId(String correlationId) {
        Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByCorrelationId(correlationId);
        
        if (requestLogOpt.isEmpty()) {
            throw new NatsClientException(
                "Request not found for correlation ID",
                null,
                null,
                NatsClientException.ErrorType.VALIDATION_ERROR
            );
        }

        return getRequestStatus(requestLogOpt.get().getRequestId());
    }

    public List<NatsRequestStatus> getRequestsByStatus(NatsRequestLog.RequestStatus status) {
        List<NatsRequestLog> requests = requestLogRepository.findByStatus(status);
        
        return requests.stream()
                .map(log -> {
                    NatsRequestStatus requestStatus = new NatsRequestStatus();
                    requestStatus.setRequestId(log.getRequestId());
                    requestStatus.setCorrelationId(log.getCorrelationId());
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

    private String generateCorrelationId() {
        return "CORR-" + UUID.randomUUID().toString();
    }

    public static class NatsRequest {
        private String subject;
        private Object payload;

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public Object getPayload() { return payload; }
        public void setPayload(Object payload) { this.payload = payload; }
    }

    public static class NatsPublishRequest {
        private String subject;
        private Object payload;

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public Object getPayload() { return payload; }
        public void setPayload(Object payload) { this.payload = payload; }
    }

    public static class NatsRequestResponse {
        private String correlationId;
        private String subject;
        private boolean success;
        private String responsePayload;
        private String errorMessage;
        private LocalDateTime timestamp;

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getResponsePayload() { return responsePayload; }
        public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class NatsRequestStatus {
        private String requestId;
        private String correlationId;
        private String subject;
        private NatsRequestLog.RequestStatus status;
        private LocalDateTime requestTimestamp;
        private LocalDateTime responseTimestamp;
        private Integer retryCount;
        private String errorMessage;

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public NatsRequestLog.RequestStatus getStatus() { return status; }
        public void setStatus(NatsRequestLog.RequestStatus status) { this.status = status; }
        public LocalDateTime getRequestTimestamp() { return requestTimestamp; }
        public void setRequestTimestamp(LocalDateTime requestTimestamp) { this.requestTimestamp = requestTimestamp; }
        public LocalDateTime getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(LocalDateTime responseTimestamp) { this.responseTimestamp = responseTimestamp; }
        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class NatsStatistics {
        private long totalRequests;
        private long pendingRequests;
        private long successfulRequests;
        private long failedRequests;
        private long timeoutRequests;
        private long errorRequests;
        private double successRate;

        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
        public long getPendingRequests() { return pendingRequests; }
        public void setPendingRequests(long pendingRequests) { this.pendingRequests = pendingRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public void setSuccessfulRequests(long successfulRequests) { this.successfulRequests = successfulRequests; }
        public long getFailedRequests() { return failedRequests; }
        public void setFailedRequests(long failedRequests) { this.failedRequests = failedRequests; }
        public long getTimeoutRequests() { return timeoutRequests; }
        public void setTimeoutRequests(long timeoutRequests) { this.timeoutRequests = timeoutRequests; }
        public long getErrorRequests() { return errorRequests; }
        public void setErrorRequests(long errorRequests) { this.errorRequests = errorRequests; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }
}