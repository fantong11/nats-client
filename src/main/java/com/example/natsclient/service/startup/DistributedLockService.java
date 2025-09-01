package com.example.natsclient.service.startup;

import com.example.natsclient.entity.ListenerRecoveryLock;
import com.example.natsclient.repository.ListenerRecoveryLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing distributed locks to coordinate operations across multiple pods.
 * Provides thread-safe lock acquisition and release mechanisms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {
    
    private static final String LOCK_STATUS_ACTIVE = "ACTIVE";
    private static final String LOCK_STATUS_COMPLETED = "COMPLETED";
    private static final String LOCK_STATUS_EXPIRED = "EXPIRED";
    
    private final ListenerRecoveryLockRepository lockRepository;
    private final String podId = determinePodId();
    
    /**
     * Attempt to acquire a distributed lock.
     * 
     * @param lockKey Unique identifier for the lock
     * @param timeoutMinutes Lock timeout in minutes
     * @return LockResult indicating success/failure and details
     */
    @Transactional
    public LockResult acquireLock(String lockKey, int timeoutMinutes) {
        try {
            // Cleanup expired locks first
            cleanupExpiredLocks();
            
            // Check for existing active lock
            Optional<ListenerRecoveryLock> existingLock = findActiveLock(lockKey);
            if (existingLock.isPresent()) {
                log.debug("Lock '{}' already held by pod: {}", lockKey, existingLock.get().getPodId());
                return LockResult.alreadyHeld(existingLock.get().getPodId());
            }
            
            // Create and save new lock
            ListenerRecoveryLock newLock = createLock(lockKey, timeoutMinutes);
            lockRepository.save(newLock);
            
            log.info("Successfully acquired lock '{}' on pod: {}", lockKey, podId);
            return LockResult.acquired(podId, newLock.getExpiresAt());
            
        } catch (DataIntegrityViolationException e) {
            log.debug("Failed to acquire lock '{}' - race condition with another pod", lockKey);
            return LockResult.raceCondition();
        } catch (Exception e) {
            log.error("Error acquiring lock '{}'", lockKey, e);
            return LockResult.error(e.getMessage());
        }
    }
    
    /**
     * Release a distributed lock by marking it as completed.
     * 
     * @param lockKey The lock key to release
     * @return true if successfully released, false otherwise
     */
    @Transactional
    public boolean releaseLock(String lockKey) {
        try {
            int updatedRows = lockRepository.markCompleted(lockKey, podId);
            if (updatedRows > 0) {
                log.info("Successfully released lock '{}' on pod: {}", lockKey, podId);
                return true;
            } else {
                log.warn("Failed to release lock '{}' - not found or not owned by pod: {}", lockKey, podId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error releasing lock '{}'", lockKey, e);
            return false;
        }
    }
    
    private void cleanupExpiredLocks() {
        try {
            int expiredCount = lockRepository.markExpiredLocks(LocalDateTime.now());
            if (expiredCount > 0) {
                log.info("Cleaned up {} expired locks", expiredCount);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up expired locks", e);
        }
    }
    
    private Optional<ListenerRecoveryLock> findActiveLock(String lockKey) {
        return lockRepository.findActiveLock(lockKey, LocalDateTime.now());
    }
    
    private ListenerRecoveryLock createLock(String lockKey, int timeoutMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return ListenerRecoveryLock.builder()
                .lockKey(lockKey)
                .podId(podId)
                .acquiredAt(now)
                .expiresAt(now.plusMinutes(timeoutMinutes))
                .status(LOCK_STATUS_ACTIVE)
                .build();
    }
    
    private static String determinePodId() {
        // In Kubernetes, HOSTNAME is set to the pod name
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.trim().isEmpty()) {
            return hostname;
        }
        
        // Fallback for local development
        return "local-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Result of a lock acquisition attempt.
     */
    public static class LockResult {
        private final boolean success;
        private final String reason;
        private final String holderPodId;
        private final LocalDateTime expiresAt;
        
        private LockResult(boolean success, String reason, String holderPodId, LocalDateTime expiresAt) {
            this.success = success;
            this.reason = reason;
            this.holderPodId = holderPodId;
            this.expiresAt = expiresAt;
        }
        
        public static LockResult acquired(String podId, LocalDateTime expiresAt) {
            return new LockResult(true, "Lock acquired successfully", podId, expiresAt);
        }
        
        public static LockResult alreadyHeld(String holderPodId) {
            return new LockResult(false, "Lock already held by another pod", holderPodId, null);
        }
        
        public static LockResult raceCondition() {
            return new LockResult(false, "Race condition - another pod acquired the lock first", null, null);
        }
        
        public static LockResult error(String errorMessage) {
            return new LockResult(false, "Error: " + errorMessage, null, null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getReason() { return reason; }
        public String getHolderPodId() { return holderPodId; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
}