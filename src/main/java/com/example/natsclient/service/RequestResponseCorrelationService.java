package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.PayloadProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for correlating NATS request-response pairs.
 * When a response is received, it finds and updates the corresponding request log entry.
 */
@Service
@Transactional
public class RequestResponseCorrelationService {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestResponseCorrelationService.class);
    
    @Autowired
    private NatsRequestLogRepository requestLogRepository;
    
    @Autowired
    private PayloadProcessor payloadProcessor;
    
    /**
     * Process a received response message and correlate it with the original request using correlationId.
     * 
     * @param responseMessage The received response message
     * @param expectedSubject The subject that sent the original request (optional)
     * @return true if correlation was successful, false otherwise
     */
    public boolean processResponse(ListenerResult.MessageReceived responseMessage, String responseIdField) {
        // Extract ID from the response JSON payload using the specified field
        String responseId = payloadProcessor.extractIdFromJson(responseMessage.jsonPayload(), responseIdField);
        
        if (responseId == null || responseId.trim().isEmpty()) {
            logger.warn("No ID found in response message field '{}' on subject '{}'", 
                       responseIdField, responseMessage.subject());
            return false;
        }
        
        try {
            // Find requests with PENDING status that contain the same ID in their payload
            java.util.List<NatsRequestLog> candidateRequests = requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING);
            
            NatsRequestLog matchingRequest = null;
            for (NatsRequestLog candidateRequest : candidateRequests) {
                // Extract ID from request payload using the same field name
                String requestId = payloadProcessor.extractIdFromJson(candidateRequest.getRequestPayload(), responseIdField);
                
                if (responseId.equals(requestId)) {
                    matchingRequest = candidateRequest;
                    logger.debug("Found matching request - RequestId: '{}', PayloadId: '{}'", 
                               candidateRequest.getRequestId(), requestId);
                    break;
                }
            }
            
            if (matchingRequest == null) {
                logger.warn("No matching PENDING request found for response ID '{}' (field: '{}') from subject '{}'", 
                           responseId, responseIdField, responseMessage.subject());
                return false;
            }
            
            // Update the request log with response information
            matchingRequest.setResponsePayload(responseMessage.jsonPayload());
            matchingRequest.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
            matchingRequest.setResponseTimestamp(LocalDateTime.now());
            matchingRequest.setUpdatedBy("RESPONSE_CORRELATION");
            
            requestLogRepository.save(matchingRequest);
            
            logger.info("Successfully correlated response for payload ID '{}' - RequestId: '{}', Subject: '{}', Response from: '{}'",
                       responseId, matchingRequest.getRequestId(), matchingRequest.getSubject(), responseMessage.subject());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error correlating response for ID '{}' (field: '{}') from subject '{}'", 
                        responseId, responseIdField, responseMessage.subject(), e);
            return false;
        }
    }
    
    /**
     * Process a response and also update with custom status.
     * 
     * @param responseMessage The received response message
     * @param expectedSubject The subject that sent the original request
     * @param customStatus Custom status to set (e.g., PARTIAL_SUCCESS, FAILED)
     * @return true if correlation was successful, false otherwise
     */
    public boolean processResponseWithStatus(ListenerResult.MessageReceived responseMessage, 
                                           String expectedSubject, 
                                           NatsRequestLog.RequestStatus customStatus) {
        String extractedId = responseMessage.extractedId();
        
        if (extractedId == null || extractedId.trim().isEmpty()) {
            logger.warn("No ID extracted from response message on subject '{}'", responseMessage.subject());
            return false;
        }
        
        try {
            Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByRequestId(extractedId);
            
            if (requestLogOpt.isEmpty()) {
                logger.warn("No matching request found for response ID '{}' from subject '{}'", 
                           extractedId, responseMessage.subject());
                return false;
            }
            
            NatsRequestLog requestLog = requestLogOpt.get();
            
            // Update with response and custom status
            requestLog.setResponsePayload(responseMessage.jsonPayload());
            requestLog.setStatus(customStatus);
            requestLog.setResponseTimestamp(LocalDateTime.now());
            requestLog.setUpdatedBy("RESPONSE_CORRELATION");
            
            requestLogRepository.save(requestLog);
            
            logger.info("Successfully correlated response for request ID '{}' with status '{}' - Subject: '{}', Response from: '{}'",
                       extractedId, customStatus, requestLog.getSubject(), responseMessage.subject());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error correlating response for ID '{}' from subject '{}'", 
                        extractedId, responseMessage.subject(), e);
            return false;
        }
    }
    
    /**
     * Mark a request as failed due to timeout or other reasons.
     * 
     * @param requestId The request ID to mark as failed
     * @param errorMessage Error message to record
     * @return true if update was successful, false otherwise
     */
    public boolean markRequestAsFailed(String requestId, String errorMessage) {
        try {
            Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByRequestId(requestId);
            
            if (requestLogOpt.isEmpty()) {
                logger.warn("No request found for ID '{}' to mark as failed", requestId);
                return false;
            }
            
            NatsRequestLog requestLog = requestLogOpt.get();
            requestLog.setStatus(NatsRequestLog.RequestStatus.FAILED);
            requestLog.setResponsePayload("ERROR: " + errorMessage);
            requestLog.setResponseTimestamp(LocalDateTime.now());
            requestLog.setUpdatedBy("TIMEOUT_HANDLER");
            
            requestLogRepository.save(requestLog);
            
            logger.info("Marked request ID '{}' as failed: {}", requestId, errorMessage);
            return true;
            
        } catch (Exception e) {
            logger.error("Error marking request '{}' as failed", requestId, e);
            return false;
        }
    }
}