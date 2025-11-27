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
 * When a response is received, it finds and updates the corresponding request
 * log entry.
 */
@Service
@Transactional
public class RequestResponseCorrelationService {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseCorrelationService.class);

    @Autowired
    private NatsRequestLogRepository requestLogRepository;

    @Autowired
    private WebhookService webhookService;

    /**
     * Process a response message and update the corresponding request log.
     *
     * @param responseMessage The received response message
     * @param expectedSubject The subject that sent the original request
     * @param customStatus    Custom status to set (e.g., PARTIAL_SUCCESS, FAILED)
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

            logger.info(
                    "Successfully correlated response for request ID '{}' with status '{}' - Subject: '{}', Response from: '{}'",
                    extractedId, customStatus, requestLog.getSubject(), responseMessage.subject());

            // Send webhook notification if URL is present
            if (requestLog.getWebhookUrl() != null && !requestLog.getWebhookUrl().isEmpty()) {
                try {
                    webhookService.sendWebhook(requestLog.getWebhookUrl(), requestLog);
                    logger.info("Webhook notification triggered for request ID: {}", requestLog.getRequestId());
                } catch (Exception e) {
                    logger.error("Failed to trigger webhook for request ID: {}", requestLog.getRequestId(), e);
                }
            }

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
     * @param requestId    The request ID to mark as failed
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

    /**
     * Mark a request as TIMEOUT.
     * 
     * @param requestId The request ID to mark as timeout
     * @return true if update was successful, false otherwise
     */
    public boolean markRequestAsTimeout(String requestId) {
        try {
            Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByRequestId(requestId);

            if (requestLogOpt.isEmpty()) {
                logger.warn("No request found for ID '{}' to mark as timeout", requestId);
                return false;
            }

            NatsRequestLog requestLog = requestLogOpt.get();
            requestLog.setStatus(NatsRequestLog.RequestStatus.TIMEOUT);
            requestLog.setResponsePayload("TIMEOUT: Request timed out");
            requestLog.setResponseTimestamp(LocalDateTime.now());
            requestLog.setUpdatedBy("TIMEOUT_MANAGER");

            requestLogRepository.save(requestLog);

            logger.info("Marked request ID '{}' as TIMEOUT", requestId);
            return true;

        } catch (Exception e) {
            logger.error("Error marking request '{}' as timeout", requestId, e);
            return false;
        }
    }

    // Added processResponse method to match the one expected by tests and other
    // services
    public boolean processResponse(ListenerResult.MessageReceived responseMessage, String expectedSubject) {
        return processResponseWithStatus(responseMessage, expectedSubject, NatsRequestLog.RequestStatus.SUCCESS);
    }
}