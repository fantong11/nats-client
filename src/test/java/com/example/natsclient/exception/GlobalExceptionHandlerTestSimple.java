package com.example.natsclient.exception;

import com.example.natsclient.controller.NatsController;
import com.example.natsclient.service.NatsOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NatsController.class)
class GlobalExceptionHandlerTestSimple {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NatsOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Mock a successful response to avoid NullPointerException in the controller
        NatsOrchestrationService.NatsRequestResponse mockResponse = new NatsOrchestrationService.NatsRequestResponse();
        when(orchestrationService.sendRequestWithTracking(any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
    }

    @Test
    void handleValidationException_EmptyRequest_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.subject").exists())
                .andExpect(jsonPath("$.fieldErrors.payload").exists());
    }

    @Test
    void handleValidationException_EmptySubject_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"\",\"payload\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.subject").exists());
    }

    @Test
    void handleValidRequest_ShouldPass() throws Exception {
        // This test verifies that validation passes for valid requests
        // Now that we have proper mocking, it should succeed
        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"test.subject\",\"payload\":\"test data\"}"))
                .andExpect(status().isOk()); // Should succeed with proper mocking
    }
}