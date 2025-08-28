package com.example.natsclient.service.contract;

import com.example.natsclient.service.NatsOrchestrationService;
import lombok.Builder;
import lombok.Data;

/**
 * Context object that carries tracking information through the request processing pipeline.
 * Follows Context Pattern and encapsulates tracking state.
 */
@Data
@Builder
public class RequestTrackingContext {
    private final String requestId;
    private final NatsOrchestrationService.NatsPublishRequest originalRequest;
    private final Object publishPayload;
    private final String extractedId;
    private final boolean requiresResponseTracking;
    private final String responseSubject;
    private final String responseIdField;
}