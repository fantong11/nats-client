package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.repository.JdbcNatsRequestLogRepository;
import com.example.natsclient.service.RequestLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RequestLogServiceImpl implements RequestLogService {
    
    private static final String SYSTEM_USER = "SYSTEM";
    
    private final JdbcNatsRequestLogRepository repository;
    private final NatsProperties natsProperties;
    
    @Autowired
    public RequestLogServiceImpl(JdbcNatsRequestLogRepository repository, NatsProperties natsProperties) {
        this.repository = repository;
        this.natsProperties = natsProperties;
    }
    
    @Override
    public NatsRequestLogDto createRequestLog(String requestId, String subject, String payload, String correlationId) {
        NatsRequestLogDto requestLog = NatsRequestLogDto.builder()
                .requestId(requestId)
                .subject(subject)
                .requestPayload(payload)
                .correlationId(correlationId)
                .status(NatsRequestLogDto.RequestStatus.PENDING)
                .requestTimestamp(LocalDateTime.now())
                .retryCount(0)
                .timeoutDuration(natsProperties.getRequest().getTimeout())
                .createdBy(SYSTEM_USER)
                .updatedBy(SYSTEM_USER)
                .build();
        return requestLog;
    }
    
    @Override
    public void updateWithSuccess(String requestId, String responsePayload) {
        repository.updateResponseByRequestId(
            requestId,
            NatsRequestLogDto.RequestStatus.SUCCESS,
            responsePayload,
            LocalDateTime.now(),
            SYSTEM_USER
        );
    }
    
    @Override
    public void updateWithTimeout(String requestId, String errorMessage) {
        repository.updateErrorByRequestId(
            requestId,
            NatsRequestLogDto.RequestStatus.TIMEOUT,
            errorMessage,
            SYSTEM_USER
        );
    }
    
    @Override
    public void updateWithError(String requestId, String errorMessage) {
        repository.updateErrorByRequestId(
            requestId,
            NatsRequestLogDto.RequestStatus.ERROR,
            errorMessage,
            SYSTEM_USER
        );
    }
    
    @Override
    public void saveRequestLog(NatsRequestLogDto requestLog) {
        repository.save(requestLog);
    }
}