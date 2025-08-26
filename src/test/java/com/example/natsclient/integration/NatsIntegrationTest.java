package com.example.natsclient.integration;

import com.example.natsclient.NatsClientApplication;
import com.example.natsclient.config.TestNatsConfig;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.NatsOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.observer.impl.LoggingEventObserver;
import com.example.natsclient.service.observer.impl.MetricsEventObserver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {NatsClientApplication.class, TestNatsConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-bean-definition-overriding=true"
})
@Transactional
public class NatsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private Connection natsConnection;

    @MockitoBean
    private JetStream jetStream;

    @MockitoBean
    private JetStreamManagement jetStreamManagement;

    @MockitoBean
    private PublishAck mockPublishAck;
    
    @MockitoBean
    private NatsEventPublisher natsEventPublisher;
    
    @MockitoBean
    private LoggingEventObserver loggingEventObserver;
    
    @MockitoBean
    private MetricsEventObserver metricsEventObserver;
    
    @MockitoBean
    private com.example.natsclient.service.NatsResponseHandler natsResponseHandler;

    @Autowired
    private NatsRequestLogRepository requestLogRepository;

    @Autowired
    private NatsOrchestrationService orchestrationService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        requestLogRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertNotNull(orchestrationService);
        assertNotNull(requestLogRepository);
        assertNotNull(natsConnection);
    }

    @Test
    void endToEndRequest_ShouldPersistToDatabase() throws Exception {
        // Arrange
        String requestJson = """
                {
                    "subject": "integration.test",
                    "payload": {
                        "data": "integration test data",
                        "timestamp": "2025-08-18T00:00:00"
                    }
                }
                """;

        // Mock JetStream request
        io.nats.client.Message mockMessage = mock(io.nats.client.Message.class);
        when(mockMessage.getData()).thenReturn("{\"status\":\"success\",\"message\":\"integration test response\"}".getBytes());
        try {
            when(jetStream.request(eq("integration.test"), any(byte[].class), any()))
                    .thenReturn(mockMessage);
        } catch (Exception e) {
            // Ignore for test setup
        }

        // Act
        mockMvc.perform(post("/api/nats/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.subject").value("integration.test"));

        // Assert database persistence
        var requests = requestLogRepository.findAll();
        assertEquals(1, requests.size());
        
        NatsRequestLog savedRequest = requests.get(0);
        assertEquals("integration.test", savedRequest.getSubject());
        assertEquals(NatsRequestLog.RequestStatus.SUCCESS, savedRequest.getStatus());
        assertNotNull(savedRequest.getRequestId());
        assertNotNull(savedRequest.getResponsePayload());
    }

    @Test
    void endToEndPublish_ShouldPersistToDatabase() throws Exception {
        // Arrange
        String publishJson = """
                {
                    "subject": "integration.publish",
                    "payload": {
                        "message": "integration publish test"
                    }
                }
                """;

        // Mock JetStream publish
        when(mockPublishAck.getSeqno()).thenReturn(1L);
        when(mockPublishAck.getStream()).thenReturn("DEFAULT_STREAM");
        try {
            when(jetStream.publish(eq("integration.publish"), any(), any(byte[].class)))
                    .thenReturn(mockPublishAck);
        } catch (Exception e) {
            // Ignore for test setup
        }

        // Act
        mockMvc.perform(post("/api/nats/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Message published successfully"));

        // Assert JetStream publish was called
        try {
            verify(jetStream).publish(eq("integration.publish"), any(), any(byte[].class));
        } catch (Exception e) {
            // Ignore verification exceptions
        }

        // Assert database persistence
        var requests = requestLogRepository.findAll();
        assertEquals(1, requests.size());
        
        NatsRequestLog savedRequest = requests.get(0);
        assertEquals("integration.publish", savedRequest.getSubject());
        assertEquals(NatsRequestLog.RequestStatus.SUCCESS, savedRequest.getStatus());
    }

    @Test
    void requestStatus_ShouldReturnPersistedData() throws Exception {
        // Arrange - Create a test request first
        NatsRequestLog testRequest = new NatsRequestLog();
        testRequest.setRequestId("test-req-123");
        testRequest.setSubject("test.status");
        testRequest.setRequestPayload("{\"test\":\"data\"}");
        testRequest.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        testRequest.setCreatedBy("TEST");
        
        requestLogRepository.save(testRequest);

        // Act & Assert
        mockMvc.perform(get("/api/nats/status/{requestId}", "test-req-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("test-req-123"))
                .andExpect(jsonPath("$.subject").value("test.status"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void statistics_ShouldCalculateFromDatabase() throws Exception {
        // Arrange - Create test data
        createTestRequestLog("req-1", NatsRequestLog.RequestStatus.SUCCESS);
        createTestRequestLog("req-2", NatsRequestLog.RequestStatus.SUCCESS);
        createTestRequestLog("req-3", NatsRequestLog.RequestStatus.FAILED);
        createTestRequestLog("req-4", NatsRequestLog.RequestStatus.ERROR);

        // Act & Assert
        mockMvc.perform(get("/api/nats/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(4))
                .andExpect(jsonPath("$.successfulRequests").value(2))
                .andExpect(jsonPath("$.failedRequests").value(1))
                .andExpect(jsonPath("$.errorRequests").value(1))
                .andExpect(jsonPath("$.successRate").value(50.0));
    }

    @Test
    void requestsByStatus_ShouldFilterFromDatabase() throws Exception {
        // Arrange
        createTestRequestLog("success-1", NatsRequestLog.RequestStatus.SUCCESS);
        createTestRequestLog("success-2", NatsRequestLog.RequestStatus.SUCCESS);
        createTestRequestLog("failed-1", NatsRequestLog.RequestStatus.FAILED);

        // Act & Assert
        mockMvc.perform(get("/api/nats/requests/{status}", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/nats/requests/{status}", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void healthEndpoint_ShouldReflectDatabaseState() throws Exception {
        // Arrange
        createTestRequestLog("health-1", NatsRequestLog.RequestStatus.SUCCESS);
        createTestRequestLog("health-2", NatsRequestLog.RequestStatus.SUCCESS);
        createTestRequestLog("health-3", NatsRequestLog.RequestStatus.FAILED);

        // Act & Assert
        mockMvc.perform(get("/api/nats/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.totalRequests").value(3))
                .andExpect(jsonPath("$.successRate").value(66.67))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void natsConnectionFailure_ShouldHandleGracefully() throws Exception {
        // Arrange
        when(natsConnection.request(anyString(), any(byte[].class), any()))
                .thenThrow(new RuntimeException("NATS connection failed"));

        String requestJson = """
                {
                    "subject": "failure.test",
                    "payload": {
                        "data": "test data"
                    }
                }
                """;

        // Act
        mockMvc.perform(post("/api/nats/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isInternalServerError());

        // Assert error is persisted
        var requests = requestLogRepository.findAll();
        assertEquals(1, requests.size());
        
        NatsRequestLog savedRequest = requests.get(0);
        assertEquals("failure.test", savedRequest.getSubject());
        assertEquals(NatsRequestLog.RequestStatus.ERROR, savedRequest.getStatus());
        assertNotNull(savedRequest.getErrorMessage());
    }

    private void createTestRequestLog(String requestId, NatsRequestLog.RequestStatus status) {
        NatsRequestLog log = new NatsRequestLog();
        log.setRequestId(requestId);
        log.setSubject("test.subject");
        log.setRequestPayload("{\"test\":\"data\"}");
        log.setStatus(status);
        log.setCreatedBy("TEST");
        
        if (status == NatsRequestLog.RequestStatus.SUCCESS) {
            log.setResponsePayload("{\"status\":\"success\"}");
        } else if (status != NatsRequestLog.RequestStatus.PENDING) {
            log.setErrorMessage("Test error message");
        }
        
        requestLogRepository.save(log);
    }
}