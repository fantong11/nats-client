package com.example.natsclient.controller;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.service.NatsOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(NatsController.class)
class NatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NatsOrchestrationService orchestrationService;

    @Autowired
    private ObjectMapper objectMapper;

    private NatsOrchestrationService.NatsRequestResponse successResponse;
    private NatsOrchestrationService.NatsRequestResponse failureResponse;
    private NatsOrchestrationService.NatsRequestStatus requestStatus;
    private NatsOrchestrationService.NatsStatistics statistics;

    @BeforeEach
    void setUp() {
        // Setup success response
        successResponse = new NatsOrchestrationService.NatsRequestResponse();
        successResponse.setRequestId("req-123");
        successResponse.setSubject("test.subject");
        successResponse.setSuccess(true);
        successResponse.setResponsePayload("{\"status\":\"success\"}");
        successResponse.setTimestamp(LocalDateTime.now());

        // Setup failure response
        failureResponse = new NatsOrchestrationService.NatsRequestResponse();
        failureResponse.setRequestId("req-456");
        failureResponse.setSubject("test.error");
        failureResponse.setSuccess(false);
        failureResponse.setErrorMessage("Test error");
        failureResponse.setTimestamp(LocalDateTime.now());

        // Setup request status
        requestStatus = new NatsOrchestrationService.NatsRequestStatus();
        requestStatus.setRequestId("req-123");
        requestStatus.setSubject("test.subject");
        requestStatus.setStatus(NatsRequestLog.RequestStatus.SUCCESS);

        // Setup statistics
        statistics = new NatsOrchestrationService.NatsStatistics();
        statistics.setTotalRequests(100);
        statistics.setSuccessfulRequests(95);
        statistics.setFailedRequests(5);
        statistics.setSuccessRate(95.0);
    }

    @Test
    void sendRequest_ValidRequest_ShouldReturnSuccessResponse() throws Exception {
        // Arrange
        when(orchestrationService.sendRequestWithTracking(any(NatsOrchestrationService.NatsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        String requestJson = """
                {
                    "subject": "test.subject",
                    "payload": {
                        "data": "test data"
                    }
                }
                """;

        // Act & Assert
        var mvcResult = mockMvc.perform(post("/api/nats/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.subject").value("test.subject"))
                .andExpect(jsonPath("$.responsePayload").value("{\"status\":\"success\"}"));

        verify(orchestrationService).sendRequestWithTracking(any(NatsOrchestrationService.NatsRequest.class));
    }

    @Test
    void sendRequest_FailureResponse_ShouldReturn500() throws Exception {
        // Arrange
        when(orchestrationService.sendRequestWithTracking(any(NatsOrchestrationService.NatsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(failureResponse));

        String requestJson = """
                {
                    "subject": "test.error",
                    "payload": {
                        "data": "test data"
                    }
                }
                """;

        // Act & Assert
        var mvcResult = mockMvc.perform(post("/api/nats/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage").value("Test error"));
    }

    @Test
    void sendRequest_InvalidSubject_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String requestJson = """
                {
                    "subject": "",
                    "payload": {
                        "data": "test data"
                    }
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/nats/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(orchestrationService, never()).sendRequestWithTracking(any());
    }

    @Test
    void sendRequest_MissingPayload_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String requestJson = """
                {
                    "subject": "test.subject"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/nats/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());

        verify(orchestrationService, never()).sendRequestWithTracking(any());
    }

    @Test
    void publishMessage_ValidRequest_ShouldReturnSuccess() throws Exception {
        // Arrange
        when(orchestrationService.publishMessageWithTracking(any(NatsOrchestrationService.NatsPublishRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("test-request-id-123"));

        String requestJson = """
                {
                    "subject": "test.publish",
                    "payload": {
                        "message": "Hello World"
                    }
                }
                """;

        // Act & Assert
        var mvcResult = mockMvc.perform(post("/api/nats/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("test-request-id-123"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.subject").value("test.publish"));

        verify(orchestrationService).publishMessageWithTracking(any(NatsOrchestrationService.NatsPublishRequest.class));
    }

    @Test
    void publishMessage_Exception_ShouldReturn500() throws Exception {
        // Arrange
        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Publish failed"));
        
        when(orchestrationService.publishMessageWithTracking(any(NatsOrchestrationService.NatsPublishRequest.class)))
                .thenReturn(failedFuture);

        String requestJson = """
                {
                    "subject": "test.publish",
                    "payload": {
                        "message": "Hello World"
                    }
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/nats/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(request().asyncStarted())
                .andDo(result -> mockMvc.perform(asyncDispatch(result)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("Failed to publish message")));
    }

    @Test
    void getRequestStatus_ExistingRequest_ShouldReturnStatus() throws Exception {
        // Arrange
        String requestId = "req-123";
        when(orchestrationService.getRequestStatus(requestId)).thenReturn(requestStatus);

        // Act & Assert
        mockMvc.perform(get("/api/nats/status/{requestId}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.subject").value("test.subject"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(orchestrationService).getRequestStatus(requestId);
    }


    @Test
    void getRequestsByStatus_ValidStatus_ShouldReturnStatusList() throws Exception {
        // Arrange
        List<NatsOrchestrationService.NatsRequestStatus> statusList = Arrays.asList(requestStatus);
        when(orchestrationService.getRequestsByStatus(NatsRequestLog.RequestStatus.SUCCESS))
                .thenReturn(statusList);

        // Act & Assert
        mockMvc.perform(get("/api/nats/requests/{status}", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].requestId").value("req-123"));

        verify(orchestrationService).getRequestsByStatus(NatsRequestLog.RequestStatus.SUCCESS);
    }

    @Test
    void getRequestsByStatus_InvalidStatus_ShouldReturnBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/nats/requests/{status}", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());

        verify(orchestrationService, never()).getRequestsByStatus(any());
    }

    @Test
    void getStatistics_ShouldReturnStatistics() throws Exception {
        // Arrange
        when(orchestrationService.getStatistics()).thenReturn(statistics);

        // Act & Assert
        mockMvc.perform(get("/api/nats/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.successfulRequests").value(95))
                .andExpect(jsonPath("$.failedRequests").value(5))
                .andExpect(jsonPath("$.successRate").value(95.0));

        verify(orchestrationService).getStatistics();
    }

    @Test
    void testEcho_ValidRequest_ShouldReturnEchoResponse() throws Exception {
        // Arrange
        when(orchestrationService.sendRequestWithTracking(any(NatsOrchestrationService.NatsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        String echoJson = """
                {
                    "message": "Hello Echo",
                    "metadata": "test metadata"
                }
                """;

        // Act & Assert
        var mvcResult = mockMvc.perform(post("/api/nats/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(echoJson))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(orchestrationService).sendRequestWithTracking(argThat(request -> 
                "test.echo".equals(request.getSubject())));
    }

    @Test
    void testEcho_InvalidMessage_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String echoJson = """
                {
                    "message": "",
                    "metadata": "test metadata"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/nats/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(echoJson))
                .andExpect(status().isBadRequest());

        verify(orchestrationService, never()).sendRequestWithTracking(any());
    }

    @Test
    void testTimeout_ShouldTriggerTimeoutTest() throws Exception {
        // Arrange
        when(orchestrationService.sendRequestWithTracking(any(NatsOrchestrationService.NatsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        // Act & Assert
        mockMvc.perform(post("/api/nats/test/timeout"))
                .andExpect(status().isOk());

        verify(orchestrationService).sendRequestWithTracking(argThat(request -> 
                "test.timeout".equals(request.getSubject())));
    }

    @Test
    void testError_ShouldTriggerErrorTest() throws Exception {
        // Arrange
        when(orchestrationService.sendRequestWithTracking(any(NatsOrchestrationService.NatsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(failureResponse));

        // Act & Assert
        mockMvc.perform(post("/api/nats/test/error"))
                .andExpect(status().isOk());

        verify(orchestrationService).sendRequestWithTracking(argThat(request -> 
                "test.error".equals(request.getSubject())));
    }

    @Test
    void health_ShouldReturnHealthStatus() throws Exception {
        // Arrange
        when(orchestrationService.getStatistics()).thenReturn(statistics);

        // Act & Assert
        mockMvc.perform(get("/api/nats/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.successRate").value(95.0))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(orchestrationService).getStatistics();
    }
}