package com.example.natsclient.service.timeout;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.RequestResponseCorrelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service that periodically checks for timed-out requests and updates their
 * status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestTimeoutManager {

    private final NatsRequestLogRepository requestLogRepository;
    private final RequestResponseCorrelationService correlationService;

    // Default timeout duration in seconds if not specified in request
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    @Scheduled(fixedRate = 5000) // Run every 5 seconds
    @Transactional
    public void checkTimeouts() {
        log.trace("Checking for timed-out requests...");

        // Calculate threshold time (requests older than this are considered timed out)
        // Using a conservative default timeout for now.
        // Ideally, each request could have its own timeout duration stored.
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(DEFAULT_TIMEOUT_SECONDS);

        List<NatsRequestLog> timedOutRequests = requestLogRepository.findTimedOutRequests(threshold);

        if (!timedOutRequests.isEmpty()) {
            log.info("Found {} timed-out requests", timedOutRequests.size());

            for (NatsRequestLog request : timedOutRequests) {
                handleTimeout(request);
            }
        }
    }

    private void handleTimeout(NatsRequestLog request) {
        try {
            boolean updated = correlationService.markRequestAsTimeout(request.getRequestId());
            if (updated) {
                log.info("Request '{}' marked as TIMEOUT", request.getRequestId());
            } else {
                log.warn("Failed to mark request '{}' as TIMEOUT", request.getRequestId());
            }
        } catch (Exception e) {
            log.error("Error handling timeout for request '{}'", request.getRequestId(), e);
        }
    }
}
