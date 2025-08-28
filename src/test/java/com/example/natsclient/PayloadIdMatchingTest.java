package com.example.natsclient;

import com.example.natsclient.model.PublishResult;
import com.example.natsclient.service.NatsClientService;
import com.example.natsclient.service.NatsListenerService;
import com.example.natsclient.service.NatsOrchestrationService;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestResponseCorrelationService;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.contract.RequestTrackingContext;
import com.example.natsclient.service.contract.RequestTrackingStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test to verify the new payload ID matching logic.
 */
@ExtendWith(MockitoExtension.class)
public class PayloadIdMatchingTest {

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

    @Mock
    private RequestTrackingStrategy trackingStrategy;

    @InjectMocks
    private NatsOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        reset(natsClientService, payloadProcessor, trackingStrategy);
        
        // Mock strategy behavior - return the original payload unchanged
        when(trackingStrategy.processRequest(any(), anyString())).thenAnswer(invocation -> {
            NatsOrchestrationService.NatsPublishRequest request = invocation.getArgument(0);
            String requestId = invocation.getArgument(1);
            return RequestTrackingContext.builder()
                    .requestId(requestId)
                    .originalRequest(request)
                    .publishPayload(request.getPayload())
                    .extractedId("12345")
                    .requiresResponseTracking(request.getResponseSubject() != null)
                    .responseSubject(request.getResponseSubject())
                    .responseIdField(request.getResponseIdField())
                    .build();
        });
        
        when(trackingStrategy.getPublishPayload(any())).thenAnswer(invocation -> {
            RequestTrackingContext context = invocation.getArgument(0);
            return context.getPublishPayload();
        });
    }

    @Test
    void publishMessage_WithPayloadIdExtraction_ShouldExtractIdAndKeepPayloadUnchanged() throws Exception {
        // Arrange
        Map<String, Object> originalPayload = new HashMap<>();
        originalPayload.put("userId", "12345");
        originalPayload.put("name", "John Doe");
        originalPayload.put("email", "john@example.com");

        NatsOrchestrationService.NatsPublishRequest request = new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("requests.user.create");
        request.setPayload(originalPayload);
        request.setResponseSubject("responses.user.create");
        request.setResponseIdField("userId");

        // Mock ID extraction
        when(payloadProcessor.extractIdFromPayload(originalPayload, "userId"))
                .thenReturn("12345");

        // Mock successful publish result
        PublishResult.Success successResult = new PublishResult.Success("generated-req-id", 123L, "requests.user.create", Instant.now());
        CompletableFuture<PublishResult> publishFuture = CompletableFuture.completedFuture(successResult);
        when(natsClientService.publishMessage(anyString(), eq("requests.user.create"), eq(originalPayload)))
                .thenReturn(publishFuture);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(request);
        String returnedRequestId = result.get();

        // Assert - Basic request ID
        assertNotNull(returnedRequestId);
        assertTrue(returnedRequestId.startsWith("REQ-"));

        // Assert - Payload ID extraction was called
        verify(payloadProcessor, times(1))
                .extractIdFromPayload(originalPayload, "userId");

        // Assert - Original payload was passed unchanged to natsClientService
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(natsClientService, times(1))
                .publishMessage(anyString(), eq("requests.user.create"), payloadCaptor.capture());
        
        Object passedPayload = payloadCaptor.getValue();
        assertEquals(originalPayload, passedPayload, "Original payload should be passed unchanged");

        // Assert - No payload processing (correlationId injection) was called
        verify(payloadProcessor, never())
                .injectCorrelationId(any(), any());

        System.out.println("✅ Test passed: Payload ID extraction with unchanged payload");
        System.out.println("   Extracted ID: 12345");
        System.out.println("   Payload remained unchanged: " + originalPayload);
        System.out.println("   RequestId: " + returnedRequestId);
    }

    @Test
    void publishMessage_WithoutResponseTracking_ShouldNotExtractId() throws Exception {
        // Arrange
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "some data");

        NatsOrchestrationService.NatsPublishRequest request = new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("simple.publish");
        request.setPayload(payload);
        // No responseSubject or responseIdField specified

        PublishResult.Success successResult = new PublishResult.Success("generated-req-id", 123L, "simple.publish", Instant.now());
        CompletableFuture<PublishResult> publishFuture = CompletableFuture.completedFuture(successResult);
        when(natsClientService.publishMessage(anyString(), eq("simple.publish"), eq(payload)))
                .thenReturn(publishFuture);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(request);
        String returnedRequestId = result.get();

        // Assert
        assertNotNull(returnedRequestId);
        assertTrue(returnedRequestId.startsWith("REQ-"));

        // Assert - No ID extraction should occur
        verify(payloadProcessor, never())
                .extractIdFromPayload(any(), any());

        // Assert - No payload processing should occur
        verify(payloadProcessor, never())
                .injectCorrelationId(any(), any());

        System.out.println("✅ Test passed: No ID extraction without response tracking");
        System.out.println("   RequestId: " + returnedRequestId);
    }

    @Test
    void publishMessage_WithNestedIdField_ShouldExtractNestedId() throws Exception {
        // Arrange
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("id", "nested-123");
        userDetails.put("type", "premium");

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", userDetails);
        payload.put("action", "upgrade");

        NatsOrchestrationService.NatsPublishRequest request = new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("requests.user.upgrade");
        request.setPayload(payload);
        request.setResponseSubject("responses.user.upgrade");
        request.setResponseIdField("user.id");  // Nested field

        // Mock nested ID extraction
        when(payloadProcessor.extractIdFromPayload(payload, "user.id"))
                .thenReturn("nested-123");

        PublishResult.Success successResult = new PublishResult.Success("generated-req-id", 123L, "requests.user.upgrade", Instant.now());
        CompletableFuture<PublishResult> publishFuture = CompletableFuture.completedFuture(successResult);
        when(natsClientService.publishMessage(anyString(), eq("requests.user.upgrade"), eq(payload)))
                .thenReturn(publishFuture);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(request);
        String returnedRequestId = result.get();

        // Assert
        assertNotNull(returnedRequestId);
        assertTrue(returnedRequestId.startsWith("REQ-"));

        // Assert - Nested ID extraction was called
        verify(payloadProcessor, times(1))
                .extractIdFromPayload(payload, "user.id");

        // Assert - Original payload structure preserved
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(natsClientService, times(1))
                .publishMessage(anyString(), eq("requests.user.upgrade"), payloadCaptor.capture());
        
        assertEquals(payload, payloadCaptor.getValue(), "Original nested payload should be preserved");

        System.out.println("✅ Test passed: Nested ID extraction");
        System.out.println("   Extracted nested ID: nested-123");
        System.out.println("   Nested field: user.id");
        System.out.println("   RequestId: " + returnedRequestId);
    }
}