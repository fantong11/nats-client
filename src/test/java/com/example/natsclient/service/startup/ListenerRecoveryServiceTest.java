package com.example.natsclient.service.startup;

import com.example.natsclient.config.ListenerRecoveryProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.contract.ResponseListenerManager;
import com.example.natsclient.service.startup.DistributedLockService.LockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ListenerRecoveryServiceTest {

    @Mock
    private NatsRequestLogRepository requestLogRepository;

    @Mock
    private ResponseListenerManager responseListenerManager;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private ListenerRecoveryProperties properties;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private ListenerRecoveryService listenerRecoveryService;

    @BeforeEach
    void setUp() {
        // Reset mocks to avoid unnecessary stubbing warnings
        reset(properties);
        
        // Set up basic lenient mocks that may not be used in all tests
        lenient().when(properties.getMaxRetryAttempts()).thenReturn(3);
        lenient().when(properties.getRetryDelayMs()).thenReturn(1000L);
    }

    @Test
    void run_WhenRecoveryDisabled_ShouldSkipRecovery() throws Exception {
        // Arrange
        when(properties.isEnabled()).thenReturn(false);

        // Act
        listenerRecoveryService.run(applicationArguments);

        // Assert
        verifyNoInteractions(distributedLockService);
        verifyNoInteractions(requestLogRepository);
        verifyNoInteractions(responseListenerManager);
    }

    @Test
    void run_WhenLockAcquisitionFails_ShouldSkipRecovery() throws Exception {
        // Arrange
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getLockTimeoutMinutes()).thenReturn(10);
        
        LockResult failedLockResult = LockResult.alreadyHeld("other-pod");
        when(distributedLockService.acquireLock("LISTENER_RECOVERY", 10)).thenReturn(failedLockResult);

        // Act
        listenerRecoveryService.run(applicationArguments);

        // Assert
        verify(distributedLockService).acquireLock("LISTENER_RECOVERY", 10);
        verifyNoInteractions(requestLogRepository);
        verifyNoInteractions(responseListenerManager);
        verifyNoMoreInteractions(distributedLockService);
    }

    @Test
    void run_WhenLockAcquisitionSucceeds_ShouldPerformRecovery() throws Exception {
        // Arrange
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getLockTimeoutMinutes()).thenReturn(10);
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        LockResult successLockResult = LockResult.acquired("current-pod", expiresAt);
        when(distributedLockService.acquireLock("LISTENER_RECOVERY", 10)).thenReturn(successLockResult);
        when(distributedLockService.releaseLock("LISTENER_RECOVERY")).thenReturn(true);

        // Mock empty PENDING requests for simple test
        when(requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING))
                .thenReturn(Collections.emptyList());

        // Act
        listenerRecoveryService.run(applicationArguments);

        // Assert
        verify(distributedLockService).acquireLock("LISTENER_RECOVERY", 10);
        verify(requestLogRepository).findByStatus(NatsRequestLog.RequestStatus.PENDING);
        verify(distributedLockService).releaseLock("LISTENER_RECOVERY");
    }

    @Test
    void run_WhenPendingRequestsExist_ShouldRecoverListeners() throws Exception {
        // Arrange
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getLockTimeoutMinutes()).thenReturn(10);
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        LockResult successLockResult = LockResult.acquired("current-pod", expiresAt);
        when(distributedLockService.acquireLock("LISTENER_RECOVERY", 10)).thenReturn(successLockResult);
        when(distributedLockService.releaseLock("LISTENER_RECOVERY")).thenReturn(true);

        // Create test requests with response tracking
        NatsRequestLog request1 = createTestRequest("req-1", "orders.create", "orders.response", "orderId");
        NatsRequestLog request2 = createTestRequest("req-2", "users.create", "users.response", "userId");
        NatsRequestLog request3 = createTestRequest("req-3", "orders.create", "orders.response", "orderId"); // Same as request1
        
        List<NatsRequestLog> pendingRequests = Arrays.asList(request1, request2, request3);
        when(requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING))
                .thenReturn(pendingRequests);

        // Act
        listenerRecoveryService.run(applicationArguments);

        // Assert
        verify(distributedLockService).acquireLock("LISTENER_RECOVERY", 10);
        verify(requestLogRepository).findByStatus(NatsRequestLog.RequestStatus.PENDING);
        
        // Should create only 2 unique listeners (orders.response:orderId and users.response:userId)
        verify(responseListenerManager).ensureListenerActive("orders.response", "orderId");
        verify(responseListenerManager).ensureListenerActive("users.response", "userId");
        verify(responseListenerManager, times(2)).ensureListenerActive(anyString(), anyString());
        
        verify(distributedLockService).releaseLock("LISTENER_RECOVERY");
    }

    @Test
    void run_WhenPendingRequestsHaveNoResponseTracking_ShouldSkipRecovery() throws Exception {
        // Arrange
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getLockTimeoutMinutes()).thenReturn(10);
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        LockResult successLockResult = LockResult.acquired("current-pod", expiresAt);
        when(distributedLockService.acquireLock("LISTENER_RECOVERY", 10)).thenReturn(successLockResult);
        when(distributedLockService.releaseLock("LISTENER_RECOVERY")).thenReturn(true);

        // Create requests without response tracking
        NatsRequestLog request1 = createTestRequestWithoutResponseTracking("req-1", "orders.create");
        List<NatsRequestLog> pendingRequests = Arrays.asList(request1);
        when(requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING))
                .thenReturn(pendingRequests);

        // Act
        listenerRecoveryService.run(applicationArguments);

        // Assert
        verify(distributedLockService).acquireLock("LISTENER_RECOVERY", 10);
        verify(requestLogRepository).findByStatus(NatsRequestLog.RequestStatus.PENDING);
        verifyNoInteractions(responseListenerManager);
        verify(distributedLockService).releaseLock("LISTENER_RECOVERY");
    }

    @Test
    void run_WhenListenerRecoveryFails_ShouldRetry() throws Exception {
        // Arrange
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getLockTimeoutMinutes()).thenReturn(10);
        when(properties.getMaxRetryAttempts()).thenReturn(2);
        when(properties.getRetryDelayMs()).thenReturn(100L); // Short delay for testing
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        LockResult successLockResult = LockResult.acquired("current-pod", expiresAt);
        when(distributedLockService.acquireLock("LISTENER_RECOVERY", 10)).thenReturn(successLockResult);
        when(distributedLockService.releaseLock("LISTENER_RECOVERY")).thenReturn(true);

        // First call throws exception, second call succeeds
        when(requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING))
                .thenThrow(new RuntimeException("Database error"))
                .thenReturn(Collections.emptyList());

        // Act
        listenerRecoveryService.run(applicationArguments);

        // Assert
        verify(distributedLockService).acquireLock("LISTENER_RECOVERY", 10);
        verify(requestLogRepository, times(2)).findByStatus(NatsRequestLog.RequestStatus.PENDING);
        verify(distributedLockService).releaseLock("LISTENER_RECOVERY");
    }

    @Test
    void run_WhenFailFastEnabled_ShouldThrowExceptionOnLockFailure() {
        // Arrange
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getLockTimeoutMinutes()).thenReturn(10);
        when(properties.isFailFast()).thenReturn(true);
        LockResult failedLockResult = LockResult.error("Database connection failed");
        when(distributedLockService.acquireLock("LISTENER_RECOVERY", 10)).thenReturn(failedLockResult);

        // Act & Assert
        try {
            listenerRecoveryService.run(applicationArguments);
        } catch (IllegalStateException e) {
            // Expected exception
            assert(e.getMessage().contains("Failed to acquire recovery lock"));
        } catch (Exception e) {
            throw new AssertionError("Expected IllegalStateException", e);
        }

        verify(distributedLockService).acquireLock("LISTENER_RECOVERY", 10);
        verifyNoInteractions(requestLogRepository);
        verifyNoInteractions(responseListenerManager);
    }

    @Test
    void run_WhenListenerCreationFails_ShouldContinueWithOtherListeners() throws Exception {
        // Arrange
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getLockTimeoutMinutes()).thenReturn(10);
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        LockResult successLockResult = LockResult.acquired("current-pod", expiresAt);
        when(distributedLockService.acquireLock("LISTENER_RECOVERY", 10)).thenReturn(successLockResult);
        when(distributedLockService.releaseLock("LISTENER_RECOVERY")).thenReturn(true);

        NatsRequestLog request1 = createTestRequest("req-1", "orders.create", "orders.response", "orderId");
        NatsRequestLog request2 = createTestRequest("req-2", "users.create", "users.response", "userId");
        List<NatsRequestLog> pendingRequests = Arrays.asList(request1, request2);
        when(requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING))
                .thenReturn(pendingRequests);

        // First listener creation fails, second succeeds
        doThrow(new RuntimeException("NATS connection failed"))
                .when(responseListenerManager).ensureListenerActive("orders.response", "orderId");
        doNothing()
                .when(responseListenerManager).ensureListenerActive("users.response", "userId");

        // Act
        listenerRecoveryService.run(applicationArguments);

        // Assert
        verify(distributedLockService).acquireLock("LISTENER_RECOVERY", 10);
        verify(requestLogRepository).findByStatus(NatsRequestLog.RequestStatus.PENDING);
        verify(responseListenerManager).ensureListenerActive("orders.response", "orderId");
        verify(responseListenerManager).ensureListenerActive("users.response", "userId");
        verify(distributedLockService).releaseLock("LISTENER_RECOVERY");
    }

    private NatsRequestLog createTestRequest(String requestId, String subject, String responseSubject, String responseIdField) {
        return NatsRequestLog.builder()
                .requestId(requestId)
                .subject(subject)
                .responseSubject(responseSubject)
                .responseIdField(responseIdField)
                .status(NatsRequestLog.RequestStatus.PENDING)
                .requestTimestamp(LocalDateTime.now())
                .build();
    }

    private NatsRequestLog createTestRequestWithoutResponseTracking(String requestId, String subject) {
        return NatsRequestLog.builder()
                .requestId(requestId)
                .subject(subject)
                .responseSubject(null) // No response tracking
                .responseIdField(null)
                .status(NatsRequestLog.RequestStatus.PENDING)
                .requestTimestamp(LocalDateTime.now())
                .build();
    }
}