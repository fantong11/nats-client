package com.example.natsclient.service;

import com.example.natsclient.dto.NatsRequestLogDto;

public interface RequestLogService {
    
    NatsRequestLogDto createRequestLog(String requestId, String subject, String payload, String correlationId);
    
    void updateWithSuccess(String requestId, String responsePayload);
    
    void updateWithTimeout(String requestId, String errorMessage);
    
    void updateWithError(String requestId, String errorMessage);
    
    void saveRequestLog(NatsRequestLogDto requestLog);
}