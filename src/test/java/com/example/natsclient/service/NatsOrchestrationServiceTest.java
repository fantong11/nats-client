package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
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
    private ObjectMapper objectMapper;

    @InjectMocks
    private NatsOrchestrationService orchestrationService;

    private NatsOrchestrationService.NatsRequest testRequest;
    private NatsOrchestrationService.NatsPublishRequest testPublishRequest;
    private final String testSubject = "test.subject";
    private final Object testPayload = new TestPayload("test data");
    private final String testResponse = "{\"status\":\"success\"}";

    @BeforeEach
    void setUp() {
        testRequest = new NatsOrchestrationService.NatsRequest();
        testRequest.setSubject(testSubject);
        testRequest.setPayload(testPayload);

        testPublishRequest = new NatsOrchestrationService.NatsPublishRequest();
        testPublishRequest.setSubject(testSubject);
        testPublishRequest.setPayload(testPayload);
    }

    @Test
    void sendRequestWithTracking_Success_ShouldReturnSuccessfulResponse() throws Exception {
        // Arrange
        CompletableFuture<String> natsResponseFuture = CompletableFuture.completedFuture(testResponse);
        when(natsClientService.sendRequest(eq(testSubject), eq(testPayload), anyString()))
                .thenReturn(natsResponseFuture);

        // Act
        CompletableFuture<NatsOrchestrationService.NatsRequestResponse> result = 
                orchestrationService.sendRequestWithTracking(testRequest);

        // Assert
        assertNotNull(result);
        NatsOrchestrationService.NatsRequestResponse response = result.get();
        
        assertTrue(response.isSuccess());
        assertEquals(testSubject, response.getSubject());
        assertEquals(testResponse, response.getResponsePayload());
        assertNotNull(response.getCorrelationId());
        assertNotNull(response.getTimestamp());
        assertNull(response.getErrorMessage());

        verify(natsClientService).sendRequest(eq(testSubject), eq(testPayload), anyString());
    }

    @Test
    void sendRequestWithTracking_NullResponse_ShouldReturnFailureResponse() throws Exception {
        // Arrange
        CompletableFuture<String> natsResponseFuture = CompletableFuture.completedFuture(null);
        when(natsClientService.sendRequest(eq(testSubject), eq(testPayload), anyString()))
                .thenReturn(natsResponseFuture);

        // Act
        CompletableFuture<NatsOrchestrationService.NatsRequestResponse> result = 
                orchestrationService.sendRequestWithTracking(testRequest);

        // Assert
        assertNotNull(result);
        NatsOrchestrationService.NatsRequestResponse response = result.get();
        
        assertFalse(response.isSuccess());
        assertEquals(testSubject, response.getSubject());
        assertNull(response.getResponsePayload());
        assertEquals("No response received", response.getErrorMessage());
        assertNotNull(response.getCorrelationId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void sendRequestWithTracking_Exception_ShouldReturnErrorResponse() throws Exception {
        // Arrange
        RuntimeException testException = new RuntimeException("Test exception");
        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(testException);
        
        when(natsClientService.sendRequest(eq(testSubject), eq(testPayload), anyString()))
                .thenReturn(failedFuture);

        // Act
        CompletableFuture<NatsOrchestrationService.NatsRequestResponse> result = 
                orchestrationService.sendRequestWithTracking(testRequest);

        // Assert
        assertNotNull(result);
        NatsOrchestrationService.NatsRequestResponse response = result.get();
        
        assertFalse(response.isSuccess());
        assertEquals(testSubject, response.getSubject());
        assertNull(response.getResponsePayload());
        assertEquals("Test exception", response.getErrorMessage());
        assertNotNull(response.getCorrelationId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void sendRequestWithTracking_ValidationFailure_ShouldReturnErrorResponse() throws Exception {
        // Arrange
        NatsOrchestrationService.NatsRequest invalidRequest = new NatsOrchestrationService.NatsRequest();
        invalidRequest.setSubject(""); // Invalid empty subject
        invalidRequest.setPayload(testPayload);

        // Act
        CompletableFuture<NatsOrchestrationService.NatsRequestResponse> result = 
                orchestrationService.sendRequestWithTracking(invalidRequest);

        // Assert
        assertNotNull(result);
        NatsOrchestrationService.NatsRequestResponse response = result.get();
        
        assertFalse(response.isSuccess());
        assertEquals("", response.getSubject());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getCorrelationId());
        assertNotNull(response.getTimestamp());

        verify(natsClientService, never()).sendRequest(anyString(), any(), anyString());
    }

    @Test
    void publishMessageWithTracking_Success_ShouldCompleteSuccessfully() throws Exception {
        // Arrange
        CompletableFuture<Void> publishFuture = CompletableFuture.completedFuture(null);
        when(natsClientService.publishMessage(eq(testSubject), eq(testPayload)))
                .thenReturn(publishFuture);

        // Act
        CompletableFuture<Void> result = orchestrationService.publishMessageWithTracking(testPublishRequest);

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> result.get());

        verify(natsClientService).publishMessage(eq(testSubject), eq(testPayload));
    }

    @Test
    void publishMessageWithTracking_Exception_ShouldPropagateException() {
        // Arrange
        RuntimeException testException = new RuntimeException("Publish failed");
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(testException);
        
        when(natsClientService.publishMessage(eq(testSubject), eq(testPayload)))
                .thenReturn(failedFuture);

        // Act
        CompletableFuture<Void> result = orchestrationService.publishMessageWithTracking(testPublishRequest);

        // Assert
        assertNotNull(result);
        ExecutionException exception = assertThrows(ExecutionException.class, () -> result.get());
        assertEquals("Publish failed", exception.getCause().getMessage());
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
        assertEquals(mockLog.getCorrelationId(), result.getCorrelationId());
        assertEquals(mockLog.getSubject(), result.getSubject());
        assertEquals(mockLog.getStatus(), result.getStatus());
    }

    @Test
    void getRequestStatus_NonExistingRequest_ShouldReturnNull() {
        // Arrange
        String testRequestId = "non-existing";
        when(requestLogRepository.findByRequestId(testRequestId)).thenReturn(Optional.empty());

        // Act
        NatsOrchestrationService.NatsRequestStatus result = orchestrationService.getRequestStatus(testRequestId);

        // Assert
        assertNull(result);
    }

    @Test
    void getRequestStatusByCorrelationId_ExistingRequest_ShouldReturnStatus() {
        // Arrange
        String testCorrelationId = "corr-456";
        NatsRequestLog mockLog = createMockRequestLog("req-123");
        mockLog.setCorrelationId(testCorrelationId);
        when(requestLogRepository.findByCorrelationId(testCorrelationId)).thenReturn(Optional.of(mockLog));

        // Act
        NatsOrchestrationService.NatsRequestStatus result = 
                orchestrationService.getRequestStatusByCorrelationId(testCorrelationId);

        // Assert
        assertNotNull(result);
        assertEquals(testCorrelationId, result.getCorrelationId());
        assertEquals(mockLog.getRequestId(), result.getRequestId());
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
        when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.SUCCESS)).thenReturn(10L);
        when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.FAILED)).thenReturn(2L);
        when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.ERROR)).thenReturn(1L);
        when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.TIMEOUT)).thenReturn(1L);
        when(requestLogRepository.countByStatus(NatsRequestLog.RequestStatus.PENDING)).thenReturn(0L);
        when(requestLogRepository.count()).thenReturn(14L);

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

    @Test
    void getStatistics_NoRequests_ShouldReturnZeroStats() {
        // Arrange
        when(requestLogRepository.countByStatus(any())).thenReturn(0L);
        when(requestLogRepository.count()).thenReturn(0L);

        // Act
        NatsOrchestrationService.NatsStatistics result = orchestrationService.getStatistics();

        // Assert
        assertNotNull(result);
        assertEquals(0L, result.getTotalRequests());
        assertEquals(0L, result.getSuccessfulRequests());
        assertEquals(0.0, result.getSuccessRate(), 0.01);
    }

    @Test
    void sendRequestWithTracking_GeneratesUniqueCorrelationIds() throws Exception {
        // Arrange
        CompletableFuture<String> natsResponseFuture = CompletableFuture.completedFuture(testResponse);
        when(natsClientService.sendRequest(eq(testSubject), eq(testPayload), anyString()))
                .thenReturn(natsResponseFuture);

        // Act
        CompletableFuture<NatsOrchestrationService.NatsRequestResponse> result1 = 
                orchestrationService.sendRequestWithTracking(testRequest);
        CompletableFuture<NatsOrchestrationService.NatsRequestResponse> result2 = 
                orchestrationService.sendRequestWithTracking(testRequest);

        // Assert
        NatsOrchestrationService.NatsRequestResponse response1 = result1.get();
        NatsOrchestrationService.NatsRequestResponse response2 = result2.get();
        
        assertNotEquals(response1.getCorrelationId(), response2.getCorrelationId());
    }

    private NatsRequestLog createMockRequestLog(String requestId) {
        NatsRequestLog log = new NatsRequestLog();
        log.setRequestId(requestId);
        log.setSubject(testSubject);
        log.setCorrelationId("corr-" + requestId);
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