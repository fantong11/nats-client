package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsClientException;
import com.example.natsclient.model.PublishResult;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.contract.RequestTrackingContext;
import com.example.natsclient.service.contract.RequestTrackingStrategy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class NatsOrchestrationService {

    private final NatsMessageService natsMessageService;
    private final NatsRequestLogRepository requestLogRepository;
    private final RequestTrackingStrategy trackingStrategy;


    public CompletableFuture<String> publishMessageWithTracking(NatsPublishRequest request) {
        log.info("Publishing NATS message - Subject: {}", request.getSubject());

        try {
            validatePublishRequest(request);
            
            String requestId = generateRequestId();
            RequestTrackingContext context = trackingStrategy.processRequest(request, requestId);
            
            return natsMessageService.publishMessage(requestId, request.getSubject(), context.getPublishPayload())
                .thenApply(publishResult -> handlePublishResult(publishResult, context));

        } catch (Exception e) {
            return handlePublishError(e, request);
        }
    }
    
    private String handlePublishResult(PublishResult publishResult, RequestTrackingContext context) {
        if (publishResult instanceof PublishResult.Success success) {
            log.info("Message published successfully - RequestID: {}, Sequence: {}", 
                    context.getRequestId(), success.sequence());
            trackingStrategy.handlePublishSuccess(context);
            return context.getRequestId();
        } 
        
        if (publishResult instanceof PublishResult.Failure failure) {
            log.error("Message publish failed - RequestID: {}, Error: {}", 
                    failure.requestId(), failure.errorMessage());
            throw new RuntimeException("Publish failed: " + failure.errorMessage());
        }
        
        throw new IllegalStateException("Unknown publish result type: " + publishResult.getClass());
    }
    
    private CompletableFuture<String> handlePublishError(Exception e, NatsPublishRequest request) {
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
    public static class NatsPublishRequest {
        private String subject;
        private Object payload;
        private String responseSubject;
        private String responseIdField;
    }
    
    @Data
    public static class ListenerStartRequest {
        private String subject;
        private String idField;
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