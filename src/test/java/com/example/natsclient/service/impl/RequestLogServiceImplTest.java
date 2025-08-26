package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RequestLogServiceImplTest {

    @Mock
    private NatsRequestLogRepository repository;

    @Mock
    private NatsProperties natsProperties;

    @Mock
    private NatsProperties.Request requestProperties;

    @InjectMocks
    private RequestLogServiceImpl requestLogService;

    private final String testRequestId = "req-123";
    private final String testSubject = "test.subject";
    private final String testPayload = "{\"data\":\"test\"}";
    private final String testResponsePayload = "{\"status\":\"success\"}";
    private final String testErrorMessage = "Test error message";
    private final long testTimeout = 30000L;

    @BeforeEach
    void setUp() {
        lenient().when(natsProperties.getRequest()).thenReturn(requestProperties);
        lenient().when(requestProperties.getTimeout()).thenReturn(testTimeout);
    }

    @Test
    void createRequestLog_ShouldCreateValidRequestLog() {
        // Act
        NatsRequestLog result = requestLogService.createRequestLog(
                testRequestId, testSubject, testPayload);

        // Assert
        assertNotNull(result);
        assertEquals(testRequestId, result.getRequestId());
        assertEquals(testSubject, result.getSubject());
        assertEquals(testPayload, result.getRequestPayload());
        assertEquals(testTimeout, result.getTimeoutDuration());
        assertEquals("SYSTEM", result.getCreatedBy());
        assertNotNull(result.getRequestTimestamp());
    }


    @Test
    void updateWithSuccess_ShouldCallRepositoryWithCorrectParameters() {
        // Act
        requestLogService.updateWithSuccess(testRequestId, testResponsePayload);

        // Assert
        verify(repository).updateResponseByRequestId(
                eq(testRequestId),
                eq(NatsRequestLog.RequestStatus.SUCCESS),
                eq(testResponsePayload),
                any(LocalDateTime.class),
                eq("SYSTEM")
        );
    }

    @Test
    void updateWithTimeout_ShouldCallRepositoryWithCorrectParameters() {
        // Act
        requestLogService.updateWithTimeout(testRequestId, testErrorMessage);

        // Assert
        verify(repository).updateErrorByRequestId(
                eq(testRequestId),
                eq(NatsRequestLog.RequestStatus.TIMEOUT),
                eq(testErrorMessage),
                eq("SYSTEM")
        );
    }

    @Test
    void updateWithError_ShouldCallRepositoryWithCorrectParameters() {
        // Act
        requestLogService.updateWithError(testRequestId, testErrorMessage);

        // Assert
        verify(repository).updateErrorByRequestId(
                eq(testRequestId),
                eq(NatsRequestLog.RequestStatus.ERROR),
                eq(testErrorMessage),
                eq("SYSTEM")
        );
    }

    @Test
    void saveRequestLog_ShouldCallRepositorySave() {
        // Arrange
        NatsRequestLog requestLog = new NatsRequestLog();

        // Act
        requestLogService.saveRequestLog(requestLog);

        // Assert
        verify(repository).save(requestLog);
    }

    @Test
    void updateWithSuccess_WithNullResponse_ShouldWork() {
        // Act
        requestLogService.updateWithSuccess(testRequestId, null);

        // Assert
        verify(repository).updateResponseByRequestId(
                eq(testRequestId),
                eq(NatsRequestLog.RequestStatus.SUCCESS),
                isNull(),
                any(LocalDateTime.class),
                eq("SYSTEM")
        );
    }

    @Test
    void updateWithTimeout_WithEmptyMessage_ShouldWork() {
        // Act
        requestLogService.updateWithTimeout(testRequestId, "");

        // Assert
        verify(repository).updateErrorByRequestId(
                eq(testRequestId),
                eq(NatsRequestLog.RequestStatus.TIMEOUT),
                eq(""),
                eq("SYSTEM")
        );
    }

    @Test
    void updateWithError_WithNullMessage_ShouldWork() {
        // Act
        requestLogService.updateWithError(testRequestId, null);

        // Assert
        verify(repository).updateErrorByRequestId(
                eq(testRequestId),
                eq(NatsRequestLog.RequestStatus.ERROR),
                isNull(),
                eq("SYSTEM")
        );
    }

    @Test
    void createRequestLog_ShouldSetCurrentTimestamp() {
        // Arrange
        LocalDateTime beforeCall = LocalDateTime.now().minusSeconds(1);
        
        // Act
        NatsRequestLog result = requestLogService.createRequestLog(
                testRequestId, testSubject, testPayload);
        
        // Assert
        LocalDateTime afterCall = LocalDateTime.now().plusSeconds(1);
        assertNotNull(result.getRequestTimestamp());
        assertTrue(result.getRequestTimestamp().isAfter(beforeCall));
        assertTrue(result.getRequestTimestamp().isBefore(afterCall));
    }

    @Test
    void createRequestLog_WithLongPayload_ShouldHandleCorrectly() {
        // Arrange
        String longPayload = "a".repeat(10000); // 10KB payload
        
        // Act
        NatsRequestLog result = requestLogService.createRequestLog(
                testRequestId, testSubject, longPayload);

        // Assert
        assertEquals(longPayload, result.getRequestPayload());
        assertEquals(longPayload.length(), result.getRequestPayload().length());
    }

    @Test
    void createRequestLog_WithSpecialCharacters_ShouldHandleCorrectly() {
        // Arrange
        String specialPayload = "{\"message\":\"Hello 世界! 🌍 Special chars: @#$%^&*()\"}";
        
        // Act
        NatsRequestLog result = requestLogService.createRequestLog(
                testRequestId, testSubject, specialPayload);

        // Assert
        assertEquals(specialPayload, result.getRequestPayload());
    }
}