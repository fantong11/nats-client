package com.example.natsclient.service.impl;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.NatsMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RetryServiceImpl {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryServiceImpl.class);
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_CUTOFF_MINUTES = 5;
    
    private final NatsRequestLogRepository requestLogRepository;
    private final NatsMessageService natsMessageService;
    
    @Autowired
    public RetryServiceImpl(NatsRequestLogRepository requestLogRepository, 
                           NatsMessageService natsMessageService) {
        this.requestLogRepository = requestLogRepository;
        this.natsMessageService = natsMessageService;
    }
    
    public void retryFailedRequests() {
        logger.info("Starting retry process for failed requests");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(RETRY_CUTOFF_MINUTES);
        List<NatsRequestLog> failedRequests = requestLogRepository.findByStatusAndCreatedDateBefore(
            NatsRequestLog.RequestStatus.FAILED, cutoffTime
        );
        
        for (NatsRequestLog failedRequest : failedRequests) {
            if (shouldRetry(failedRequest)) {
                retryRequest(failedRequest);
            }
        }
    }
    
    private boolean shouldRetry(NatsRequestLog failedRequest) {
        return failedRequest.getRetryCount() < MAX_RETRY_COUNT;
    }
    
    private void retryRequest(NatsRequestLog failedRequest) {
        try {
            logger.info("Retrying request ID: {}", failedRequest.getRequestId());
            
            natsMessageService.sendRequest(
                failedRequest.getSubject(), 
                failedRequest.getRequestPayload(), 
                failedRequest.getCorrelationId()
            );
            
        } catch (Exception e) {
            logger.error("Retry failed for request ID: {}", failedRequest.getRequestId(), e);
        }
    }
}