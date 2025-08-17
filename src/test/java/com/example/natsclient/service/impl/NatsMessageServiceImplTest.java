package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.validator.RequestValidator;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsMessageServiceImplTest {

    @Mock
    private Connection natsConnection;

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
        when(natsProperties.getRequest()).thenReturn(requestProperties);
        when(requestProperties.getTimeout()).thenReturn(30000L);
    }

    @Test
    void sendRequest_Success_ShouldReturnSuccessfulResponse() throws Exception {
        // Arrange
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(payloadProcessor.fromBytes(responseBytes)).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responseBytes);

        // Act
        CompletableFuture<String> result = natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId);

        // Assert
        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        
        verify(requestValidator).validateRequest(testSubject, testPayload);
        verify(requestValidator).validateCorrelationId(testCorrelationId);
        verify(requestLogService).saveRequestLog(mockRequestLog);
        verify(requestLogService).updateWithSuccess(anyString(), eq(responsePayload));
        verify(natsConnection).request(eq(testSubject), eq(payloadBytes), any(Duration.class));
    }

    @Test
    void sendRequest_TimeoutResponse_ShouldThrowNatsTimeoutException() throws Exception {
        // Arrange
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(null); // NATS returns null on timeout

        // Act & Assert
        assertThrows(NatsTimeoutException.class, () -> {
            natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId).get();
        });

        verify(requestLogService).updateWithTimeout(anyString(), eq("No response received within timeout period"));
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
        verify(natsConnection, never()).request(anyString(), any(byte[].class), any(Duration.class));
    }

    @Test
    void sendRequest_SerializationFailure_ShouldThrowNatsRequestException() throws Exception {
        // Arrange
        when(payloadProcessor.serialize(testPayload))
                .thenThrow(new RuntimeException("Serialization failed"));

        // Act & Assert
        assertThrows(NatsRequestException.class, () -> {
            try {
                natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        verify(requestLogService).updateWithError(anyString(), contains("Serialization failed"));
    }

    @Test
    void sendRequest_NatsConnectionFailure_ShouldThrowNatsRequestException() throws Exception {
        // Arrange
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        // Act & Assert
        assertThrows(NatsRequestException.class, () -> {
            try {
                natsMessageService.sendRequest(testSubject, testPayload, testCorrelationId).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        verify(requestLogService).updateWithError(anyString(), contains("Connection failed"));
    }

    @Test
    void publishMessage_Success_ShouldCompleteSuccessfully() throws Exception {
        // Arrange
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), isNull()))
                .thenReturn(mockRequestLog);

        // Act
        CompletableFuture<Void> result = natsMessageService.publishMessage(testSubject, testPayload);

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> result.get());
        
        verify(requestValidator).validateRequest(testSubject, testPayload);
        verify(natsConnection).publish(eq(testSubject), eq(payloadBytes));
        verify(mockRequestLog).setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        verify(requestLogService).saveRequestLog(mockRequestLog);
    }

    @Test
    void publishMessage_ValidationFailure_ShouldThrowException() {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid payload")).when(requestValidator)
                .validateRequest(testSubject, testPayload);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            natsMessageService.publishMessage(testSubject, testPayload);
        });

        verify(natsConnection, never()).publish(anyString(), any(byte[].class));
    }

    @Test
    void publishMessage_PublishFailure_ShouldThrowNatsRequestException() throws Exception {
        // Arrange
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        doThrow(new RuntimeException("Publish failed")).when(natsConnection)
                .publish(eq(testSubject), eq(payloadBytes));

        // Act & Assert
        assertThrows(NatsRequestException.class, () -> {
            try {
                natsMessageService.publishMessage(testSubject, testPayload).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        verify(requestLogService).updateWithError(anyString(), contains("Publish failed"));
    }

    @Test
    void sendRequest_WithNullCorrelationId_ShouldWork() throws Exception {
        // Arrange
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(payloadProcessor.fromBytes(responseBytes)).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), isNull()))
                .thenReturn(mockRequestLog);
        when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responseBytes);

        // Act
        CompletableFuture<String> result = natsMessageService.sendRequest(testSubject, testPayload, null);

        // Assert
        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        
        verify(requestValidator).validateCorrelationId(null);
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