package com.example.natsclient.service.impl;

import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.repository.JdbcNatsRequestLogRepository;
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
    
    private final JdbcNatsRequestLogRepository requestLogRepository;
    private final NatsMessageService natsMessageService;
    
    @Autowired
    public RetryServiceImpl(JdbcNatsRequestLogRepository requestLogRepository, 
                           NatsMessageService natsMessageService) {
        this.requestLogRepository = requestLogRepository;
        this.natsMessageService = natsMessageService;
    }
    
    public void retryFailedRequests() {
        logger.info("Starting retry process for failed requests");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(RETRY_CUTOFF_MINUTES);
        List<NatsRequestLogDto> failedRequests = requestLogRepository.findByStatusAndCreatedDateBefore(
            NatsRequestLogDto.RequestStatus.FAILED, cutoffTime
        );
        
        for (NatsRequestLogDto failedRequest : failedRequests) {
            if (shouldRetry(failedRequest)) {
                retryRequest(failedRequest);
            }
        }
    }
    
    private boolean shouldRetry(NatsRequestLogDto failedRequest) {
        return failedRequest.getRetryCount() < MAX_RETRY_COUNT;
    }
    
    private void retryRequest(NatsRequestLogDto failedRequest) {
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