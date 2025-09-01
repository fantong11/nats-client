package com.example.natsclient.service.startup;

import com.example.natsclient.entity.ListenerRecoveryLock;
import com.example.natsclient.repository.ListenerRecoveryLockRepository;
import com.example.natsclient.service.startup.DistributedLockService.LockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock
    private ListenerRecoveryLockRepository lockRepository;

    @InjectMocks
    private DistributedLockService distributedLockService;

    private final String testLockKey = "TEST_LOCK";
    private final int timeoutMinutes = 5;
    private final String testPodId = "test-pod-123";

    @BeforeEach
    void setUp() {
        // Set the podId via reflection since it's determined in constructor
        ReflectionTestUtils.setField(distributedLockService, "podId", testPodId);
    }

    @Test
    void acquireLock_WhenNoExistingLock_ShouldSucceed() {
        // Arrange
        when(lockRepository.markExpiredLocks(any(LocalDateTime.class))).thenReturn(0);
        when(lockRepository.findActiveLock(eq(testLockKey), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(lockRepository.save(any(ListenerRecoveryLock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LockResult result = distributedLockService.acquireLock(testLockKey, timeoutMinutes);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Lock acquired successfully", result.getReason());
        assertEquals(testPodId, result.getHolderPodId());
        assertNotNull(result.getExpiresAt());

        verify(lockRepository).markExpiredLocks(any(LocalDateTime.class));
        verify(lockRepository).findActiveLock(eq(testLockKey), any(LocalDateTime.class));
        verify(lockRepository).save(any(ListenerRecoveryLock.class));
    }

    @Test
    void acquireLock_WhenLockAlreadyExists_ShouldReturnAlreadyHeld() {
        // Arrange
        String existingPodId = "existing-pod-456";
        ListenerRecoveryLock existingLock = ListenerRecoveryLock.builder()
                .lockKey(testLockKey)
                .podId(existingPodId)
                .acquiredAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .status("ACTIVE")
                .build();

        when(lockRepository.markExpiredLocks(any(LocalDateTime.class))).thenReturn(1);
        when(lockRepository.findActiveLock(eq(testLockKey), any(LocalDateTime.class)))
                .thenReturn(Optional.of(existingLock));

        // Act
        LockResult result = distributedLockService.acquireLock(testLockKey, timeoutMinutes);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Lock already held by another pod", result.getReason());
        assertEquals(existingPodId, result.getHolderPodId());
        assertNull(result.getExpiresAt());

        verify(lockRepository).markExpiredLocks(any(LocalDateTime.class));
        verify(lockRepository).findActiveLock(eq(testLockKey), any(LocalDateTime.class));
        verify(lockRepository, never()).save(any(ListenerRecoveryLock.class));
    }

    @Test
    void acquireLock_WhenRaceConditionOccurs_ShouldReturnRaceCondition() {
        // Arrange
        when(lockRepository.markExpiredLocks(any(LocalDateTime.class))).thenReturn(0);
        when(lockRepository.findActiveLock(eq(testLockKey), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(lockRepository.save(any(ListenerRecoveryLock.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // Act
        LockResult result = distributedLockService.acquireLock(testLockKey, timeoutMinutes);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Race condition - another pod acquired the lock first", result.getReason());
        assertNull(result.getHolderPodId());
        assertNull(result.getExpiresAt());

        verify(lockRepository).save(any(ListenerRecoveryLock.class));
    }

    @Test
    void acquireLock_WhenDatabaseError_ShouldReturnError() {
        // Arrange
        RuntimeException dbException = new RuntimeException("Database connection failed");
        when(lockRepository.markExpiredLocks(any(LocalDateTime.class))).thenReturn(0);
        when(lockRepository.findActiveLock(eq(testLockKey), any(LocalDateTime.class))).thenThrow(dbException);

        // Act
        LockResult result = distributedLockService.acquireLock(testLockKey, timeoutMinutes);

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("Database connection failed"));
        assertNull(result.getHolderPodId());
        assertNull(result.getExpiresAt());
        
        // Verify the methods were called in correct order
        verify(lockRepository).markExpiredLocks(any(LocalDateTime.class));
        verify(lockRepository).findActiveLock(eq(testLockKey), any(LocalDateTime.class));
    }

    @Test
    void releaseLock_WhenLockExists_ShouldSucceed() {
        // Arrange
        when(lockRepository.markCompleted(testLockKey, testPodId)).thenReturn(1);

        // Act
        boolean result = distributedLockService.releaseLock(testLockKey);

        // Assert
        assertTrue(result);
        verify(lockRepository).markCompleted(testLockKey, testPodId);
    }

    @Test
    void releaseLock_WhenLockNotFound_ShouldReturnFalse() {
        // Arrange
        when(lockRepository.markCompleted(testLockKey, testPodId)).thenReturn(0);

        // Act
        boolean result = distributedLockService.releaseLock(testLockKey);

        // Assert
        assertFalse(result);
        verify(lockRepository).markCompleted(testLockKey, testPodId);
    }

    @Test
    void releaseLock_WhenDatabaseError_ShouldReturnFalse() {
        // Arrange
        when(lockRepository.markCompleted(testLockKey, testPodId))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        boolean result = distributedLockService.releaseLock(testLockKey);

        // Assert
        assertFalse(result);
        verify(lockRepository).markCompleted(testLockKey, testPodId);
    }

    @Test
    void acquireLock_ShouldCleanupExpiredLocks() {
        // Arrange
        when(lockRepository.markExpiredLocks(any(LocalDateTime.class))).thenReturn(3);
        when(lockRepository.findActiveLock(eq(testLockKey), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(lockRepository.save(any(ListenerRecoveryLock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LockResult result = distributedLockService.acquireLock(testLockKey, timeoutMinutes);

        // Assert
        assertTrue(result.isSuccess());
        verify(lockRepository).markExpiredLocks(any(LocalDateTime.class));
    }

    @Test
    void acquireLock_ShouldSetCorrectExpirationTime() {
        // Arrange
        when(lockRepository.markExpiredLocks(any(LocalDateTime.class))).thenReturn(0);
        when(lockRepository.findActiveLock(eq(testLockKey), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        
        // Capture the saved lock
        when(lockRepository.save(any(ListenerRecoveryLock.class)))
                .thenAnswer(invocation -> {
                    ListenerRecoveryLock savedLock = invocation.getArgument(0);
                    
                    // Verify lock properties
                    assertEquals(testLockKey, savedLock.getLockKey());
                    assertEquals(testPodId, savedLock.getPodId());
                    assertEquals("ACTIVE", savedLock.getStatus());
                    assertNotNull(savedLock.getAcquiredAt());
                    assertNotNull(savedLock.getExpiresAt());
                    
                    // Verify expiration time is approximately correct (within 1 minute tolerance)
                    LocalDateTime expectedExpiry = savedLock.getAcquiredAt().plusMinutes(timeoutMinutes);
                    assertTrue(savedLock.getExpiresAt().isAfter(expectedExpiry.minusMinutes(1)));
                    assertTrue(savedLock.getExpiresAt().isBefore(expectedExpiry.plusMinutes(1)));
                    
                    return savedLock;
                });

        // Act
        LockResult result = distributedLockService.acquireLock(testLockKey, timeoutMinutes);

        // Assert
        assertTrue(result.isSuccess());
        verify(lockRepository).save(any(ListenerRecoveryLock.class));
    }

    @Test
    void lockResult_StaticMethods_ShouldCreateCorrectInstances() {
        // Test acquired result
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        LockResult acquired = LockResult.acquired("pod-1", expiresAt);
        assertTrue(acquired.isSuccess());
        assertEquals("Lock acquired successfully", acquired.getReason());
        assertEquals("pod-1", acquired.getHolderPodId());
        assertEquals(expiresAt, acquired.getExpiresAt());

        // Test already held result
        LockResult alreadyHeld = LockResult.alreadyHeld("pod-2");
        assertFalse(alreadyHeld.isSuccess());
        assertEquals("Lock already held by another pod", alreadyHeld.getReason());
        assertEquals("pod-2", alreadyHeld.getHolderPodId());
        assertNull(alreadyHeld.getExpiresAt());

        // Test race condition result
        LockResult raceCondition = LockResult.raceCondition();
        assertFalse(raceCondition.isSuccess());
        assertEquals("Race condition - another pod acquired the lock first", raceCondition.getReason());
        assertNull(raceCondition.getHolderPodId());
        assertNull(raceCondition.getExpiresAt());

        // Test error result
        LockResult error = LockResult.error("Test error");
        assertFalse(error.isSuccess());
        assertEquals("Error: Test error", error.getReason());
        assertNull(error.getHolderPodId());
        assertNull(error.getExpiresAt());
    }
}