package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;

public interface RequestLogService {
    
    NatsRequestLog createRequestLog(String requestId, String subject, String payload, 
                                   String responseSubject, String responseIdField);
    
    void updateWithSuccess(String requestId, String responsePayload);
    
    void updateWithTimeout(String requestId, String errorMessage);
    
    void updateWithError(String requestId, String errorMessage);
    
    void saveRequestLog(NatsRequestLog requestLog);
}