package com.example.natsclient.service.strategy;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.NatsOrchestrationService;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.contract.RequestTrackingContext;
import com.example.natsclient.service.contract.RequestTrackingStrategy;
import com.example.natsclient.service.contract.ResponseListenerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Strategy for tracking requests using payload ID matching.
 * Follows Single Responsibility Principle by focusing only on payload ID tracking.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayloadIdTrackingStrategy implements RequestTrackingStrategy {
    
    private final PayloadProcessor payloadProcessor;
    private final NatsRequestLogRepository requestLogRepository;
    private final ResponseListenerManager responseListenerManager;
    
    @Override
    public RequestTrackingContext processRequest(NatsOrchestrationService.NatsPublishRequest request, String requestId) {
        boolean hasResponseTracking = hasResponseTrackingConfig(request);
        String payloadId = null;
        
        if (hasResponseTracking) {
            payloadId = extractPayloadId(request.getPayload(), request.getResponseIdField());
            
            // Subscribe BEFORE publishing to avoid race condition
            responseListenerManager.ensureListenerActive(
                request.getResponseSubject(), 
                request.getResponseIdField()
            );
        }
        
        return RequestTrackingContext.builder()
                .requestId(requestId)
                .originalRequest(request)
                .publishPayload(request.getPayload()) // Keep payload unchanged
                .extractedId(payloadId)
                .requiresResponseTracking(hasResponseTracking)
                .responseSubject(request.getResponseSubject())
                .responseIdField(request.getResponseIdField())
                .build();
    }
    
    @Override
    public Object getPublishPayload(RequestTrackingContext context) {
        // Payload remains unchanged in this strategy
        return context.getPublishPayload();
    }
    
    @Override
    public void handlePublishSuccess(RequestTrackingContext context) {
        updateRequestLogStatus(context);
        
        // Listener is already active (subscribed before publishing)
        // No additional action needed here
    }
    
    private boolean hasResponseTrackingConfig(NatsOrchestrationService.NatsPublishRequest request) {
        return request.getResponseSubject() != null && request.getResponseIdField() != null;
    }
    
    private String extractPayloadId(Object payload, String idFieldName) {
        try {
            String payloadId = payloadProcessor.extractIdFromPayload(payload, idFieldName);
            if (payloadId != null) {
                log.debug("Found ID '{}' in payload field '{}' for response correlation", 
                        payloadId, idFieldName);
            } else {
                log.warn("Could not find ID in payload field '{}' - response correlation may fail", 
                        idFieldName);
            }
            return payloadId;
        } catch (Exception e) {
            log.error("Error extracting ID from payload field '{}' for response correlation", 
                    idFieldName, e);
            return null;
        }
    }
    
    private void updateRequestLogStatus(RequestTrackingContext context) {
        try {
            Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByRequestId(context.getRequestId());
            if (requestLogOpt.isPresent()) {
                NatsRequestLog requestLog = requestLogOpt.get();
                requestLog.setStatus(NatsRequestLog.RequestStatus.PENDING);
                requestLogRepository.save(requestLog);
                log.info("Updated request log status to PENDING for response tracking - RequestID: {}", 
                        context.getRequestId());
            }
        } catch (Exception e) {
            log.error("Failed to update request log status - RequestID: {}", context.getRequestId(), e);
        }
    }
}