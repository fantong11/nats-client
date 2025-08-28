package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsClientException;
import com.example.natsclient.model.PublishResult;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsOrchestrationServiceTest {

    @Mock
    private NatsClientService natsClientService;

    @Mock
    private NatsRequestLogRepository requestLogRepository;

    @Mock
    private NatsListenerService natsListenerService;

    @Mock
    private RequestResponseCorrelationService correlationService;

    @Mock
    private PayloadProcessor payloadProcessor;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NatsOrchestrationService orchestrationService;

    private NatsOrchestrationService.NatsPublishRequest testPublishRequest;
    private final String testSubject = "test.subject";
    private final Object testPayload = new TestPayload("test data");

    @BeforeEach
    void setUp() {
        testPublishRequest = new NatsOrchestrationService.NatsPublishRequest();
        testPublishRequest.setSubject(testSubject);
        testPublishRequest.setPayload(testPayload);
    }

    @Test
    void publishMessageWithTracking_Success_ShouldCompleteSuccessfully() throws Exception {
        // Arrange
        PublishResult.Success successResult = new PublishResult.Success("test-request-id", 123L, testSubject);
        CompletableFuture<PublishResult> publishFuture = CompletableFuture.completedFuture(successResult);
        when(natsClientService.publishMessage(anyString(), eq(testSubject), eq(testPayload)))
                .thenReturn(publishFuture);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(testPublishRequest);

        // Assert
        assertNotNull(result);
        String requestId = assertDoesNotThrow(() -> result.get());
        assertNotNull(requestId);
        assertTrue(requestId.startsWith("REQ-"));

        verify(natsClientService).publishMessage(anyString(), eq(testSubject), eq(testPayload));
    }

    @Test
    void publishMessageWithTracking_Exception_ShouldPropagateException() {
        // Arrange
        PublishResult.Failure failureResult = new PublishResult.Failure("test-request-id", testSubject, "Test error", "TestException");
        CompletableFuture<PublishResult> failedFuture = CompletableFuture.completedFuture(failureResult);
        
        when(natsClientService.publishMessage(anyString(), eq(testSubject), eq(testPayload)))
                .thenReturn(failedFuture);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(testPublishRequest);

        // Assert
        assertNotNull(result);
        ExecutionException exception = assertThrows(ExecutionException.class, () -> result.get());
        assertEquals("Publish failed: Test error", exception.getCause().getMessage());
    }

    @Test
    void getRequestStatus_ExistingRequest_ShouldReturnStatus() {
        // Arrange
        String testRequestId = "req-123";
        NatsRequestLog mockLog = createMockRequestLog(testRequestId);
        when(requestLogRepository.findByRequestId(testRequestId)).thenReturn(Optional.of(mockLog));

        // Act
        NatsOrchestrationService.NatsRequestStatus result = orchestrationService.getRequestStatus(testRequestId);

        // Assert
        assertNotNull(result);
        assertEquals(testRequestId, result.getRequestId());
        assertEquals(mockLog.getSubject(), result.getSubject());
        assertEquals(mockLog.getStatus(), result.getStatus());
    }

    @Test
    void getRequestStatus_NonExistingRequest_ShouldThrowException() {
        // Arrange
        String testRequestId = "non-existing";
        when(requestLogRepository.findByRequestId(testRequestId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NatsClientException.class, () -> {
            orchestrationService.getRequestStatus(testRequestId);
        });
    }

    @Test
    void getRequestsByStatus_ExistingRequests_ShouldReturnStatusList() {
        // Arrange
        NatsRequestLog.RequestStatus status = NatsRequestLog.RequestStatus.SUCCESS;
        List<NatsRequestLog> mockLogs = Arrays.asList(
                createMockRequestLog("req-1"),
                createMockRequestLog("req-2")
        );
        when(requestLogRepository.findByStatus(status)).thenReturn(mockLogs);

        // Act
        List<NatsOrchestrationService.NatsRequestStatus> result = 
                orchestrationService.getRequestsByStatus(status);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("req-1", result.get(0).getRequestId());
        assertEquals("req-2", result.get(1).getRequestId());
    }

    @Test
    void getStatistics_ShouldReturnCorrectStatistics() {
        // Arrange
        lenient().when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.SUCCESS)).thenReturn(10L);
        lenient().when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.FAILED)).thenReturn(2L);
        lenient().when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.ERROR)).thenReturn(1L);
        lenient().when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.TIMEOUT)).thenReturn(1L);
        lenient().when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.PENDING)).thenReturn(0L);

        // Act
        NatsOrchestrationService.NatsStatistics result = orchestrationService.getStatistics();

        // Assert
        assertNotNull(result);
        assertEquals(14L, result.getTotalRequests());
        assertEquals(10L, result.getSuccessfulRequests());
        assertEquals(2L, result.getFailedRequests());
        assertEquals(1L, result.getErrorRequests());
        assertEquals(1L, result.getTimeoutRequests());
        assertEquals(0L, result.getPendingRequests());
        assertEquals(71.43, result.getSuccessRate(), 0.01); // 10/14 * 100
    }

    private NatsRequestLog createMockRequestLog(String requestId) {
        NatsRequestLog log = new NatsRequestLog();
        log.setRequestId(requestId);
        log.setSubject(testSubject);
        log.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        log.setCreatedDate(LocalDateTime.now());
        log.setResponseTimestamp(LocalDateTime.now());
        log.setRetryCount(0);
        return log;
    }

    private static class TestPayload {
        private final String data;

        public TestPayload(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }
}