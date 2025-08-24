package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.NatsOperations;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.ResponseHandler;
import com.example.natsclient.service.validator.RequestValidator;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsMessageServiceImplTest {

    @Mock
    private NatsOperations natsOperations;

    @Mock
    private ResponseHandler<String> responseHandler;

    @Mock
    private RequestLogService requestLogService;

    @Mock
    private PayloadProcessor payloadProcessor;

    @Mock
    private RequestValidator requestValidator;

    @Mock
    private NatsProperties natsProperties;

    @Mock
    private NatsProperties.Request requestProperties;

    @Mock
    private Message mockMessage;

    @Mock
    private PublishAck mockPublishAck;

    @InjectMocks
    private NatsMessageServiceImpl natsMessageService;

    private final String testSubject = "test.subject";
    private final Object testPayload = new TestPayload("test data");
    private final String testCorrelationId = "corr-123";
    private final String serializedPayload = "{\"data\":\"test data\"}";
    private final byte[] payloadBytes = serializedPayload.getBytes();
    private final String responsePayload = "{\"status\":\"success\"}";
    private final byte[] responseBytes = responsePayload.getBytes();

    @BeforeEach
    void setUp() {
        // Configure common mock behavior - only when actually used
        lenient().when(natsProperties.getRequest()).thenReturn(requestProperties);
        lenient().when(requestProperties.getTimeout()).thenReturn(30000L);
        lenient().when(mockPublishAck.getSeqno()).thenReturn(1L);
        lenient().when(mockPublishAck.getStream()).thenReturn("DEFAULT_STREAM");
    }

    @Test
    void sendRequest_Success_ShouldReturnSuccessfulResponse() throws Exception {
        // Arrange
        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        CompletableFuture<String> expectedResponse = CompletableFuture.completedFuture(responsePayload);
        
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsOperations.sendRequest(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(mockMessage);
        when(responseHandler.handleSuccess(anyString(), eq(mockMessage)))
                .thenReturn(expectedResponse);

        // Act
        CompletableFuture<String> result = natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId);

        // Assert
        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        
        verify(requestValidator).validateRequest(testSubject, testPayload);
        verify(requestValidator).validateCorrelationId(testCorrelationId);
        verify(requestLogService).saveRequestLog(mockRequestLog);
        verify(natsOperations).sendRequest(eq(testSubject), eq(payloadBytes), any(Duration.class));
        verify(responseHandler).handleSuccess(anyString(), eq(mockMessage));
    }

    @Test
    void sendRequest_TimeoutResponse_ShouldThrowNatsTimeoutException() throws Exception {
        // Arrange
        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        CompletableFuture<String> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new NatsTimeoutException("Request timeout", "test-id"));
        
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsOperations.sendRequest(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(null); // NATS returns null on timeout
        when(responseHandler.handleTimeout(anyString()))
                .thenReturn(timeoutFuture);

        // Act & Assert
        CompletableFuture<String> result = natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId);
        
        ExecutionException executionException = assertThrows(ExecutionException.class, () -> {
            result.get(); // This should throw the ExecutionException
        });
        
        assertTrue(executionException.getCause() instanceof NatsTimeoutException);

        verify(responseHandler).handleTimeout(anyString());
    }

    @Test
    void sendRequest_ValidationFailure_ShouldThrowException() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid subject")).when(requestValidator)
                .validateRequest(testSubject, testPayload);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId);
        });

        verify(requestLogService, never()).createRequestLog(anyString(), anyString(), anyString(), anyString());
        verify(natsOperations, never()).sendRequest(anyString(), any(byte[].class), any(Duration.class));
    }

    @Test
    void sendRequest_SerializationFailure_ShouldThrowNatsRequestException() throws Exception {
        // Arrange
        RuntimeException serializationException = new RuntimeException("Serialization failed");
        CompletableFuture<String> errorFuture = new CompletableFuture<>();
        errorFuture.completeExceptionally(new NatsRequestException("Serialization failed", "test-id", serializationException));
        
        when(payloadProcessor.serialize(testPayload))
                .thenThrow(serializationException);
        when(responseHandler.handleError(anyString(), eq(serializationException)))
                .thenReturn(errorFuture);

        // Act & Assert
        CompletableFuture<String> result = natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId);
        
        ExecutionException executionException = assertThrows(ExecutionException.class, () -> {
            result.get(); // This should throw the ExecutionException
        });
        
        assertTrue(executionException.getCause() instanceof NatsRequestException);

        verify(responseHandler).handleError(anyString(), eq(serializationException));
    }

    @Test
    void sendRequest_NatsConnectionFailure_ShouldThrowNatsRequestException() throws Exception {
        // Arrange
        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        RuntimeException connectionException = new RuntimeException("Connection failed");
        CompletableFuture<String> errorFuture = new CompletableFuture<>();
        errorFuture.completeExceptionally(new NatsRequestException("Connection failed", "test-id", connectionException));
        
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsOperations.sendRequest(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenThrow(connectionException);
        when(responseHandler.handleError(anyString(), eq(connectionException)))
                .thenReturn(errorFuture);

        // Act & Assert
        CompletableFuture<String> result = natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId);
        
        ExecutionException executionException = assertThrows(ExecutionException.class, () -> {
            result.get(); // This should throw the ExecutionException
        });
        
        assertTrue(executionException.getCause() instanceof NatsRequestException);

        verify(responseHandler).handleError(anyString(), eq(connectionException));
    }

    @Test
    void publishMessage_Success_ShouldCompleteSuccessfully() throws Exception {
        // Arrange
        NatsRequestLogDto mockRequestLog = mock(NatsRequestLogDto.class);
        CompletableFuture<PublishAck> publishFuture = CompletableFuture.completedFuture(mockPublishAck);
        
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), isNull()))
                .thenReturn(mockRequestLog);
        when(natsOperations.publishMessage(eq(testSubject), eq(payloadBytes)))
                .thenReturn(publishFuture);

        // Act
        CompletableFuture<Void> result = natsMessageService.publishMessage(testSubject, testPayload);

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> result.get());
        
        verify(requestValidator).validateRequest(testSubject, testPayload);
        verify(natsOperations).publishMessage(eq(testSubject), eq(payloadBytes));
        verify(mockRequestLog).setStatus(NatsRequestLogDto.RequestStatus.SUCCESS);
        verify(mockRequestLog).setResponsePayload(contains("JetStream Publish ACK"));
        verify(requestLogService).saveRequestLog(mockRequestLog);
    }

    @Test
    void publishMessage_ValidationFailure_ShouldThrowException() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid payload")).when(requestValidator)
                .validateRequest(testSubject, testPayload);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            natsMessageService.publishMessage(testSubject, testPayload);
        });

        verify(natsOperations, never()).publishMessage(anyString(), any(byte[].class));
    }

    @Test
    void publishMessage_PublishFailure_ShouldThrowNatsRequestException() throws Exception {
        // Arrange
        when(payloadProcessor.serialize(testPayload))
                .thenThrow(new RuntimeException("JetStream publish failed"));

        // Act & Assert
        assertThrows(NatsRequestException.class, () -> {
            natsMessageService.publishMessage(testSubject, testPayload);
        });

        verify(requestLogService).updateWithError(anyString(), contains("JetStream publish failed"));
    }

    @Test
    void sendRequest_WithNullCorrelationId_ShouldWork() throws Exception {
        // Arrange
        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        CompletableFuture<String> expectedResponse = CompletableFuture.completedFuture(responsePayload);
        
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), isNull()))
                .thenReturn(mockRequestLog);
        when(natsOperations.sendRequest(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(mockMessage);
        when(responseHandler.handleSuccess(anyString(), eq(mockMessage)))
                .thenReturn(expectedResponse);

        // Act
        CompletableFuture<String> result = natsMessageService.sendRequest(testSubject, testPayload, null);

        // Assert
        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        
        verify(requestValidator).validateCorrelationId(null);
        verify(responseHandler).handleSuccess(anyString(), eq(mockMessage));
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