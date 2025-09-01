package com.example.natsclient.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to implement distributed locking for listener recovery in multi-pod environments.
 * Ensures only one pod performs listener recovery at startup.
 */
@Entity
@Table(name = "LISTENER_RECOVERY_LOCK")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListenerRecoveryLock {
    
    @Id
    @Column(name = "LOCK_KEY")
    private String lockKey; // Fixed value: "RECOVERY_LOCK"
    
    @Column(name = "POD_ID")
    private String podId;
    
    @Column(name = "ACQUIRED_AT")
    private LocalDateTime acquiredAt;
    
    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;
    
    @Column(name = "STATUS")
    private String status; // "ACTIVE", "COMPLETED", "EXPIRED"
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}