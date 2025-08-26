package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.RequestLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class RequestLogServiceImpl implements RequestLogService {
    
    private static final String SYSTEM_USER = "SYSTEM";
    
    private final NatsRequestLogRepository repository;
    private final NatsProperties natsProperties;
    
    @Autowired
    public RequestLogServiceImpl(NatsRequestLogRepository repository, NatsProperties natsProperties) {
        this.repository = repository;
        this.natsProperties = natsProperties;
    }
    
    @Override
    public NatsRequestLog createRequestLog(String requestId, String subject, String payload) {
        NatsRequestLog requestLog = NatsRequestLog.builder()
                .requestId(requestId)
                .subject(subject)
                .requestPayload(payload)
                .status(NatsRequestLog.RequestStatus.PENDING)
                .requestTimestamp(LocalDateTime.now())
                .retryCount(0)
                .timeoutDuration(natsProperties.getRequest().getTimeout())
                .createdBy(SYSTEM_USER)
                .createdDate(LocalDateTime.now())
                .updatedBy(SYSTEM_USER)
                .updatedDate(LocalDateTime.now())
                .build();
        return requestLog;
    }
    
    @Override
    public void updateWithSuccess(String requestId, String responsePayload) {
        repository.updateResponseByRequestId(
            requestId,
            NatsRequestLog.RequestStatus.SUCCESS,
            responsePayload,
            LocalDateTime.now(),
            SYSTEM_USER
        );
    }
    
    @Override
    public void updateWithTimeout(String requestId, String errorMessage) {
        repository.updateErrorByRequestId(
            requestId,
            NatsRequestLog.RequestStatus.TIMEOUT,
            errorMessage,
            SYSTEM_USER
        );
    }
    
    @Override
    public void updateWithError(String requestId, String errorMessage) {
        repository.updateErrorByRequestId(
            requestId,
            NatsRequestLog.RequestStatus.ERROR,
            errorMessage,
            SYSTEM_USER
        );
    }
    
    @Override
    public void saveRequestLog(NatsRequestLog requestLog) {
        repository.save(requestLog);
    }
}