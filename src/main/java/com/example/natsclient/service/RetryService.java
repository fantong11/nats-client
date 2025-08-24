package com.example.natsclient.service;

import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.exception.NatsClientException;
import com.example.natsclient.repository.JdbcNatsRequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MINUTES = 5;

    @Autowired
    private JdbcNatsRequestLogRepository requestLogRepository;

    @Autowired
    private NatsClientService natsClientService;

    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    @Transactional
    public void retryFailedRequests() {
        logger.info("Starting scheduled retry process for failed requests");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
            
            List<NatsRequestLogDto> failedRequests = requestLogRepository.findByStatusAndCreatedDateBefore(
                NatsRequestLogDto.RequestStatus.FAILED, cutoffTime
            );

            List<NatsRequestLogDto> timeoutRequests = requestLogRepository.findByStatusAndCreatedDateBefore(
                NatsRequestLogDto.RequestStatus.TIMEOUT, cutoffTime
            );

            List<NatsRequestLogDto> errorRequests = requestLogRepository.findByStatusAndCreatedDateBefore(
                NatsRequestLogDto.RequestStatus.ERROR, cutoffTime
            );

            int totalRetries = 0;
            totalRetries += processRetryList(failedRequests, "FAILED");
            totalRetries += processRetryList(timeoutRequests, "TIMEOUT");
            totalRetries += processRetryList(errorRequests, "ERROR");

            logger.info("Completed retry process. Total retry attempts: {}", totalRetries);

        } catch (Exception e) {
            logger.error("Error during retry process", e);
        }
    }

    private int processRetryList(List<NatsRequestLogDto> requests, String type) {
        int retryCount = 0;
        
        for (NatsRequestLogDto request : requests) {
            if (shouldRetry(request)) {
                try {
                    retryRequest(request);
                    retryCount++;
                } catch (Exception e) {
                    logger.error("Failed to retry request ID: {}", request.getRequestId(), e);
                    markRetryFailed(request, e.getMessage());
                }
            } else {
                logger.debug("Skipping retry for request ID: {} (max attempts reached)", 
                           request.getRequestId());
            }
        }
        
        if (retryCount > 0) {
            logger.info("Processed {} retry attempts for {} requests", retryCount, type);
        }
        
        return retryCount;
    }

    private boolean shouldRetry(NatsRequestLogDto request) {
        return request.getRetryCount() < MAX_RETRY_ATTEMPTS && 
               !isNonRetryableError(request.getErrorMessage());
    }

    private boolean isNonRetryableError(String errorMessage) {
        if (errorMessage == null) return false;
        
        String lowerCaseError = errorMessage.toLowerCase();
        return lowerCaseError.contains("bad request") ||
               lowerCaseError.contains("validation") ||
               lowerCaseError.contains("unauthorized") ||
               lowerCaseError.contains("forbidden") ||
               lowerCaseError.contains("not found");
    }

    @Transactional
    public void retryRequest(NatsRequestLogDto request) {
        logger.info("Retrying request ID: {}, attempt: {}", 
                   request.getRequestId(), request.getRetryCount() + 1);

        try {
            request.setRetryCount(request.getRetryCount() + 1);
            request.setStatus(NatsRequestLogDto.RequestStatus.PENDING);
            request.setUpdatedBy("RETRY_SERVICE");
            requestLogRepository.save(request);

            CompletableFuture<String> future = natsClientService.sendRequest(
                request.getSubject(), 
                request.getRequestPayload(), 
                request.getCorrelationId()
            );

            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    handleRetryFailure(request, throwable);
                } else {
                    handleRetrySuccess(request, response);
                }
            });

        } catch (Exception e) {
            throw new NatsClientException(
                "Failed to retry request: " + e.getMessage(),
                e,
                request.getRequestId(),
                request.getSubject(),
                NatsClientException.ErrorType.UNKNOWN_ERROR
            );
        }
    }

    @Transactional
    private void handleRetrySuccess(NatsRequestLogDto request, String response) {
        logger.info("Retry successful for request ID: {}", request.getRequestId());
        
        request.setStatus(NatsRequestLogDto.RequestStatus.SUCCESS);
        request.setResponsePayload(response);
        request.setResponseTimestamp(LocalDateTime.now());
        request.setErrorMessage(null);
        request.setUpdatedBy("RETRY_SERVICE");
        
        requestLogRepository.save(request);
    }

    @Transactional
    private void handleRetryFailure(NatsRequestLogDto request, Throwable throwable) {
        logger.error("Retry failed for request ID: {}, attempt: {}", 
                    request.getRequestId(), request.getRetryCount(), throwable);

        if (request.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            request.setStatus(NatsRequestLogDto.RequestStatus.FAILED);
            request.setErrorMessage("Max retry attempts exceeded: " + throwable.getMessage());
        } else {
            request.setStatus(NatsRequestLogDto.RequestStatus.ERROR);
            request.setErrorMessage("Retry failed: " + throwable.getMessage());
        }
        
        request.setUpdatedBy("RETRY_SERVICE");
        requestLogRepository.save(request);
    }

    @Transactional
    private void markRetryFailed(NatsRequestLogDto request, String errorMessage) {
        request.setStatus(NatsRequestLogDto.RequestStatus.FAILED);
        request.setErrorMessage("Retry process failed: " + errorMessage);
        request.setUpdatedBy("RETRY_SERVICE");
        requestLogRepository.save(request);
    }

    @Scheduled(fixedDelay = 3600000) // Run every hour
    @Transactional
    public void cleanupOldRequests() {
        logger.info("Starting cleanup of old request logs");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
            
            List<NatsRequestLogDto> completedRequests = requestLogRepository.findByStatusAndCreatedDateBefore(
                NatsRequestLogDto.RequestStatus.SUCCESS, cutoffTime
            );

            List<NatsRequestLogDto> finalFailedRequests = requestLogRepository.findByStatusAndCreatedDateBefore(
                NatsRequestLogDto.RequestStatus.FAILED, cutoffTime
            );

            int deletedCount = 0;
            
            for (NatsRequestLogDto request : completedRequests) {
                requestLogRepository.deleteById(request.getId());
                deletedCount++;
            }
            
            for (NatsRequestLogDto request : finalFailedRequests) {
                if (request.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                    requestLogRepository.deleteById(request.getId());
                    deletedCount++;
                }
            }

            logger.info("Cleanup completed. Deleted {} old request logs", deletedCount);

        } catch (Exception e) {
            logger.error("Error during cleanup process", e);
        }
    }
}