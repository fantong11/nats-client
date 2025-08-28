package com.example.natsclient;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.model.PublishResult;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.NatsOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the requestId returned by /publish endpoint 
 * matches the requestId inserted in the database table.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class RequestIdConsistencyTest {

    @Autowired
    private NatsOrchestrationService orchestrationService;

    @Autowired
    private NatsRequestLogRepository requestLogRepository;

    @Test
    void publishMessage_ShouldReturnSameRequestIdAsInDatabase() throws Exception {
        // Arrange
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test User");
        payload.put("email", "test@example.com");
        
        NatsOrchestrationService.NatsPublishRequest request = 
            new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("test.subject");
        request.setPayload(payload);

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(request);
        String returnedRequestId = result.get();

        // Assert
        assertNotNull(returnedRequestId, "Returned requestId should not be null");
        assertTrue(returnedRequestId.startsWith("REQ-"), "RequestId should start with REQ-");

        // Verify database contains the same requestId
        Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByRequestId(returnedRequestId);
        assertTrue(requestLogOpt.isPresent(), 
            "Database should contain request log with the returned requestId: " + returnedRequestId);

        NatsRequestLog requestLog = requestLogOpt.get();
        assertEquals(returnedRequestId, requestLog.getRequestId(), 
            "Database requestId should match returned requestId");
        assertEquals("test.subject", requestLog.getSubject());
        assertNotNull(requestLog.getRequestPayload());
        
        System.out.println("✅ Test passed: RequestId consistency verified");
        System.out.println("   Returned RequestId: " + returnedRequestId);
        System.out.println("   Database RequestId: " + requestLog.getRequestId());
        System.out.println("   Status: " + requestLog.getStatus());
    }

    @Test  
    void publishMessageWithResponseTracking_ShouldReturnSameRequestIdAsInDatabase() throws Exception {
        // Arrange
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", "12345");
        payload.put("action", "create_account");
        
        NatsOrchestrationService.NatsPublishRequest request = 
            new NatsOrchestrationService.NatsPublishRequest();
        request.setSubject("requests.user.create");
        request.setPayload(payload);
        request.setResponseSubject("responses.user.create");
        request.setResponseIdField("correlationId");

        // Act
        CompletableFuture<String> result = orchestrationService.publishMessageWithTracking(request);
        String returnedRequestId = result.get();

        // Assert
        assertNotNull(returnedRequestId, "Returned requestId should not be null");
        assertTrue(returnedRequestId.startsWith("REQ-"), "RequestId should start with REQ-");

        // Verify database contains the same requestId
        Optional<NatsRequestLog> requestLogOpt = requestLogRepository.findByRequestId(returnedRequestId);
        assertTrue(requestLogOpt.isPresent(), 
            "Database should contain request log with the returned requestId: " + returnedRequestId);

        NatsRequestLog requestLog = requestLogOpt.get();
        assertEquals(returnedRequestId, requestLog.getRequestId(), 
            "Database requestId should match returned requestId");
        assertEquals("requests.user.create", requestLog.getSubject());
        assertEquals(NatsRequestLog.RequestStatus.PENDING, requestLog.getStatus(),
            "Status should be PENDING for response tracking");
        
        // Verify correlationId was injected into payload
        String requestPayload = requestLog.getRequestPayload();
        assertTrue(requestPayload.contains("correlationId"), 
            "Request payload should contain correlationId");
        assertTrue(requestPayload.contains(returnedRequestId),
            "Request payload should contain the returned requestId as correlationId");
        
        System.out.println("✅ Test passed: RequestId consistency with response tracking verified");
        System.out.println("   Returned RequestId: " + returnedRequestId);
        System.out.println("   Database RequestId: " + requestLog.getRequestId());
        System.out.println("   Status: " + requestLog.getStatus());
        System.out.println("   Payload: " + requestPayload);
    }
}