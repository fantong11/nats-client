package com.example.natsclient.service.contract;

import com.example.natsclient.service.NatsOrchestrationService;

/**
 * Strategy interface for different request tracking approaches.
 * Follows Strategy Pattern and Open/Closed Principle.
 */
public interface RequestTrackingStrategy {
    
    /**
     * Process the request payload for tracking.
     * 
     * @param request The publish request
     * @param requestId The generated request ID
     * @return The processed tracking context
     */
    RequestTrackingContext processRequest(NatsOrchestrationService.NatsPublishRequest request, String requestId);
    
    /**
     * Get the final payload to be published.
     * 
     * @param context The tracking context
     * @return The payload to publish
     */
    Object getPublishPayload(RequestTrackingContext context);
    
    /**
     * Handle post-publish success operations.
     * 
     * @param context The tracking context
     */
    void handlePublishSuccess(RequestTrackingContext context);
}