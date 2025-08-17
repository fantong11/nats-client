package com.example.natsclient.service.validator;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RequestValidator {
    
    public void validateRequest(String subject, Object payload) {
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
        
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
    }
    
    public void validateCorrelationId(String correlationId) {
        if (correlationId != null && correlationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID cannot be empty if provided");
        }
    }
}