package com.example.natsclient.service.startup;

import com.example.natsclient.config.ListenerRecoveryProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.contract.ResponseListenerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service to recover NATS response listeners after application restart.
 * This ensures that PENDING requests can still receive their responses
 * even after a deployment or restart.
 * 
 * Uses distributed locking to ensure only one pod performs recovery in K8s environments.
 * Can be disabled via configuration: nats.listener.recovery.enabled=false
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Order(1) // Run early in the startup process
@ConditionalOnProperty(value = "nats.listener.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class ListenerRecoveryService implements ApplicationRunner {
    
    private static final String RECOVERY_LOCK_KEY = "LISTENER_RECOVERY";
    
    private final NatsRequestLogRepository requestLogRepository;
    private final ResponseListenerManager responseListenerManager;
    private final DistributedLockService distributedLockService;
    private final ListenerRecoveryProperties properties;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.isEnabled()) {
            log.info("Listener recovery is disabled via configuration");
            return;
        }
        
        log.info("Starting NATS listener recovery process...");
        
        DistributedLockService.LockResult lockResult = distributedLockService.acquireLock(
            RECOVERY_LOCK_KEY, properties.getLockTimeoutMinutes());
        
        if (!lockResult.isSuccess()) {
            handleLockAcquisitionFailure(lockResult);
            return;
        }
        
        try {
            RecoveryResult result = performRecoveryWithRetry();
            logRecoveryResult(result);
        } finally {
            distributedLockService.releaseLock(RECOVERY_LOCK_KEY);
        }
    }
    
    private void handleLockAcquisitionFailure(DistributedLockService.LockResult lockResult) {
        String reason = lockResult.getReason();
        String holderPod = lockResult.getHolderPodId();
        
        if (holderPod != null) {
            log.info("Listener recovery already in progress on pod: {}. Skipping recovery.", holderPod);
        } else {
            log.warn("Failed to acquire recovery lock: {}", reason);
            if (properties.isFailFast()) {
                throw new IllegalStateException("Failed to acquire recovery lock: " + reason);
            }
        }
    }
    
    private RecoveryResult performRecoveryWithRetry() {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= properties.getMaxRetryAttempts(); attempt++) {
            try {
                return performRecovery();
            } catch (Exception e) {
                lastException = e;
                log.warn("Recovery attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < properties.getMaxRetryAttempts()) {
                    try {
                        Thread.sleep(properties.getRetryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return RecoveryResult.failed(lastException);
    }
    
    private RecoveryResult performRecovery() {
        log.info("Performing listener recovery...");
        
        // Find all PENDING requests that need response tracking
        List<NatsRequestLog> pendingRequests = requestLogRepository.findByStatus(
            NatsRequestLog.RequestStatus.PENDING);
        
        // Filter requests that have response tracking configuration
        List<NatsRequestLog> trackingRequests = pendingRequests.stream()
            .filter(this::hasResponseTrackingConfig)
            .collect(Collectors.toList());
        
        if (trackingRequests.isEmpty()) {
            log.info("No PENDING requests with response tracking found. Recovery complete.");
            return RecoveryResult.success(0, 0);
        }
        
        log.info("Found {} PENDING requests that need response tracking", trackingRequests.size());
        
        // Group by response subject and ID field combination to avoid duplicate listeners
        Map<String, ListenerConfig> uniqueListeners = groupUniqueListeners(trackingRequests);
        
        // Restore listeners for each unique combination
        return recoverListeners(uniqueListeners, trackingRequests.size());
    }
    
    private boolean hasResponseTrackingConfig(NatsRequestLog request) {
        return request.getResponseSubject() != null && 
               request.getResponseIdField() != null;
    }
    
    private Map<String, ListenerConfig> groupUniqueListeners(List<NatsRequestLog> trackingRequests) {
        return trackingRequests.stream()
            .collect(Collectors.toMap(
                request -> request.getResponseSubject() + ":" + request.getResponseIdField(),
                request -> new ListenerConfig(request.getResponseSubject(), request.getResponseIdField()),
                (existing, replacement) -> existing // Keep first occurrence
            ));
    }
    
    private RecoveryResult recoverListeners(Map<String, ListenerConfig> uniqueListeners, int totalRequests) {
        int successCount = 0;
        int failureCount = 0;
        
        for (Map.Entry<String, ListenerConfig> entry : uniqueListeners.entrySet()) {
            ListenerConfig config = entry.getValue();
            
            try {
                responseListenerManager.ensureListenerActive(config.subject, config.idField);
                successCount++;
                log.info("Recovered listener for {} with ID field {}", config.subject, config.idField);
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to recover listener for {} with ID field {}: {}", 
                         config.subject, config.idField, e.getMessage());
            }
        }
        
        return RecoveryResult.success(successCount, totalRequests);
    }
    
    private void logRecoveryResult(RecoveryResult result) {
        if (result.isSuccess()) {
            log.info("Listener recovery completed successfully. Recovered {} listeners for {} PENDING requests", 
                    result.getRecoveredListeners(), result.getTotalPendingRequests());
        } else {
            log.error("Listener recovery failed: {}", result.getErrorMessage());
        }
    }
    
    // Helper classes
    private static class ListenerConfig {
        final String subject;
        final String idField;
        
        ListenerConfig(String subject, String idField) {
            this.subject = subject;
            this.idField = idField;
        }
    }
    
    private static class RecoveryResult {
        private final boolean success;
        private final int recoveredListeners;
        private final int totalPendingRequests;
        private final String errorMessage;
        
        private RecoveryResult(boolean success, int recoveredListeners, int totalPendingRequests, String errorMessage) {
            this.success = success;
            this.recoveredListeners = recoveredListeners;
            this.totalPendingRequests = totalPendingRequests;
            this.errorMessage = errorMessage;
        }
        
        static RecoveryResult success(int recoveredListeners, int totalPendingRequests) {
            return new RecoveryResult(true, recoveredListeners, totalPendingRequests, null);
        }
        
        static RecoveryResult failed(Exception e) {
            return new RecoveryResult(false, 0, 0, e != null ? e.getMessage() : "Unknown error");
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public int getRecoveredListeners() { return recoveredListeners; }
        public int getTotalPendingRequests() { return totalPendingRequests; }
        public String getErrorMessage() { return errorMessage; }
    }
}