package com.example.natsclient;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.model.PublishResult;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.NatsClientService;
import com.example.natsclient.service.NatsListenerService;
import com.example.natsclient.service.NatsOrchestrationService;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestResponseCorrelationService;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test to verify that the requestId returned by publishMessageWithTracking 
 * matches the requestId used in the underlying service calls.
 */
@ExtendWith(MockitoExtension.class)
public class RequestIdConsistencyUnitTest {

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
        // Reset all mocks before each test
        reset(natsClientService, requestLogRepository, payloadProcessor, objectMapper, trackingStrategy);
        
        // Mock strategy behavior - return the original payload unchanged
        when(trackingStrategy.processRequest(any(), anyString())).thenAnswer(invocation -> {
            NatsOrchestrationService.NatsPublishRequest request = invocation.getArgument(0);
            String requestId = invocation.getArgument(1);
            return RequestTrackingContext.builder()
                    .requestId(requestId)
                    .originalRequest(request)
                    .publishPayload(request.getPayload())
                    .requiresResponseTracking(false)
                    .build();
        });
        
        when(trackingStrategy.getPublishPayload(any())).thenAnswer(invocation -> {
            RequestTrackingContext context = invocation.getArgument(0);
            return context.getPublishPayload();
        });
    }

    @Test
    void publishMessageWithTracking_ShouldPassSameRequestIdToNatsClientService() throws Exception {
        // Arrange
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test User");
        payload.put("email", "test@example.com");

        NatsOrchestrationService.NatsPublishRequest request = new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("test.subject");
        request.setPayload(payload);

        // Mock successful publish result
        PublishResult.Success successResult = new PublishResult.Success("generated-req-id", 123L, "test.subject", Instant.now());
        CompletableFuture<PublishResult> publishFuture = CompletableFuture.completedFuture(successResult);
        when(natsClientService.publishMessage(anyString(), eq("test.subject"), eq(payload)))
                .thenReturn(publishFuture);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(request);
        String returnedRequestId = result.get();

        // Assert
        assertNotNull(returnedRequestId, "Returned requestId should not be null");
        assertTrue(returnedRequestId.startsWith("REQ-"), "RequestId should start with REQ-");

        // Capture the requestId passed to natsClientService.publishMessage
        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsClientService, times(1)).publishMessage(requestIdCaptor.capture(), eq("test.subject"), eq(payload));
        
        String passedRequestId = requestIdCaptor.getValue();
        assertEquals(returnedRequestId, passedRequestId, 
            "RequestId passed to NatsClientService should match returned requestId");

        System.out.println("✅ Test passed: RequestId consistency verified");
        System.out.println("   Returned RequestId: " + returnedRequestId);
        System.out.println("   Passed RequestId: " + passedRequestId);
    }

    @Test
    void publishMessageWithTracking_WithResponseSubject_ShouldPassSameRequestIdAndUpdateDatabase() throws Exception {
        // Arrange
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", "12345");
        payload.put("action", "create_account");

        Map<String, Object> processedPayload = new HashMap<>(payload);
        processedPayload.put("correlationId", "REQ-test-correlation-id");

        NatsOrchestrationService.NatsPublishRequest request = new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("requests.user.create");
        request.setPayload(payload);
        request.setResponseSubject("responses.user.create");
        request.setResponseIdField("correlationId");

        // Mock payload processing
        when(payloadProcessor.injectCorrelationId(eq(payload), anyString()))
                .thenReturn(processedPayload);

        // Mock successful publish result
        PublishResult.Success successResult = new PublishResult.Success("generated-req-id", 123L, "requests.user.create", Instant.now());
        CompletableFuture<PublishResult> publishFuture = CompletableFuture.completedFuture(successResult);
        when(natsClientService.publishMessage(anyString(), eq("requests.user.create"), eq(processedPayload)))
                .thenReturn(publishFuture);

        // Mock database operations
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        mockRequestLog.setRequestId("captured-request-id");
        mockRequestLog.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        when(requestLogRepository.findByRequestId(anyString())).thenReturn(Optional.of(mockRequestLog));
        when(requestLogRepository.save(any(NatsRequestLog.class))).thenReturn(mockRequestLog);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(request);
        String returnedRequestId = result.get();

        // Assert
        assertNotNull(returnedRequestId, "Returned requestId should not be null");
        assertTrue(returnedRequestId.startsWith("REQ-"), "RequestId should start with REQ-");

        // Verify payload processing was called with the correct requestId
        ArgumentCaptor<String> processingRequestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(payloadProcessor, times(1))
                .injectCorrelationId(eq(payload), processingRequestIdCaptor.capture());
        assertEquals(returnedRequestId, processingRequestIdCaptor.getValue(),
            "RequestId used for payload processing should match returned requestId");

        // Verify natsClientService was called with the correct requestId
        ArgumentCaptor<String> clientRequestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsClientService, times(1))
                .publishMessage(clientRequestIdCaptor.capture(), eq("requests.user.create"), eq(processedPayload));
        assertEquals(returnedRequestId, clientRequestIdCaptor.getValue(),
            "RequestId passed to NatsClientService should match returned requestId");

        // Verify database update was called with the correct requestId
        ArgumentCaptor<String> dbRequestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestLogRepository, times(1)).findByRequestId(dbRequestIdCaptor.capture());
        assertEquals(returnedRequestId, dbRequestIdCaptor.getValue(),
            "RequestId used for database lookup should match returned requestId");

        System.out.println("✅ Test passed: RequestId consistency with response tracking verified");
        System.out.println("   Returned RequestId: " + returnedRequestId);
        System.out.println("   Processing RequestId: " + processingRequestIdCaptor.getValue());
        System.out.println("   Client RequestId: " + clientRequestIdCaptor.getValue());
        System.out.println("   Database RequestId: " + dbRequestIdCaptor.getValue());
    }

    @Test 
    void publishMessageWithTracking_ShouldGenerateUniqueRequestIds() throws Exception {
        // Arrange
        Map<String, Object> payload = new HashMap<>();
        payload.put("test", "data");

        NatsOrchestrationService.NatsPublishRequest request = new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("test.subject");
        request.setPayload(payload);

        PublishResult.Success successResult = new PublishResult.Success("generated-req-id", 123L, "test.subject", Instant.now());
        CompletableFuture<PublishResult> publishFuture = CompletableFuture.completedFuture(successResult);
        when(natsClientService.publishMessage(anyString(), eq("test.subject"), eq(payload)))
                .thenReturn(publishFuture);

        // Act - Call twice to verify different requestIds are generated
        CompletableFuture<String> result1 = orchestrationService.publishMessageWithTracking(request);
        CompletableFuture<String> result2 = orchestrationService.publishMessageWithTracking(request);
        
        String requestId1 = result1.get();
        String requestId2 = result2.get();

        // Assert
        assertNotNull(requestId1);
        assertNotNull(requestId2);
        assertNotEquals(requestId1, requestId2, "Each call should generate a unique requestId");
        assertTrue(requestId1.startsWith("REQ-"));
        assertTrue(requestId2.startsWith("REQ-"));

        System.out.println("✅ Test passed: Unique requestId generation verified");
        System.out.println("   First RequestId: " + requestId1);
        System.out.println("   Second RequestId: " + requestId2);
    }
}