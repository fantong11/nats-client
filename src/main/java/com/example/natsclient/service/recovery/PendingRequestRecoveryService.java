package com.example.natsclient.service.recovery;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.contract.ResponseListenerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service that recovers pending requests on application startup.
 * It ensures that listeners are active for any requests that are still PENDING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingRequestRecoveryService {

    private final NatsRequestLogRepository requestLogRepository;
    private final ResponseListenerManager responseListenerManager;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingRequests() {
        log.info("Starting recovery of pending requests...");

        List<NatsRequestLog> pendingRequests = requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING);

        if (pendingRequests.isEmpty()) {
            log.info("No pending requests found for recovery.");
            return;
        }

        log.info("Found {} pending requests to recover", pendingRequests.size());

        int recoveredCount = 0;
        for (NatsRequestLog request : pendingRequests) {
            if (recoverRequest(request)) {
                recoveredCount++;
            }
        }

        log.info("Recovery complete. Recovered {}/{} pending requests", recoveredCount, pendingRequests.size());
    }

    private boolean recoverRequest(NatsRequestLog request) {
        try {
            String responseSubject = request.getResponseSubject();
            String responseIdField = request.getResponseIdField();

            if (responseSubject != null && responseIdField != null) {
                log.debug("Restoring listener for pending request '{}' - Subject: '{}', ID Field: '{}'",
                        request.getRequestId(), responseSubject, responseIdField);

                responseListenerManager.ensureListenerActive(responseSubject, responseIdField);
                return true;
            } else {
                log.warn("Cannot recover request '{}': Missing response subject or ID field", request.getRequestId());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to recover request '{}'", request.getRequestId(), e);
            return false;
        }
    }
}
