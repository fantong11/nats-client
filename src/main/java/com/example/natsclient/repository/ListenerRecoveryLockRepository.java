package com.example.natsclient.repository;

import com.example.natsclient.entity.ListenerRecoveryLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for managing distributed locks for listener recovery.
 */
@Repository
public interface ListenerRecoveryLockRepository extends JpaRepository<ListenerRecoveryLock, String> {
    
    /**
     * Find an active, non-expired lock.
     */
    @Query("SELECT l FROM ListenerRecoveryLock l WHERE l.lockKey = :lockKey AND l.status = 'ACTIVE' AND l.expiresAt > :now")
    Optional<ListenerRecoveryLock> findActiveLock(@Param("lockKey") String lockKey, @Param("now") LocalDateTime now);
    
    /**
     * Cleanup expired locks.
     */
    @Modifying
    @Query("UPDATE ListenerRecoveryLock l SET l.status = 'EXPIRED' WHERE l.expiresAt < :now AND l.status = 'ACTIVE'")
    int markExpiredLocks(@Param("now") LocalDateTime now);
    
    /**
     * Update lock status to completed.
     */
    @Modifying
    @Query("UPDATE ListenerRecoveryLock l SET l.status = 'COMPLETED' WHERE l.lockKey = :lockKey AND l.podId = :podId")
    int markCompleted(@Param("lockKey") String lockKey, @Param("podId") String podId);
}