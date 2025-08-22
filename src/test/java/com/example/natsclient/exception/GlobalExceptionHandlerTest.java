package com.example.natsclient.exception;

import com.example.natsclient.controller.NatsController;
import com.example.natsclient.service.NatsOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NatsController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NatsOrchestrationService orchestrationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void handleValidationException_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Invalid request parameters"))
                .andExpect(jsonPath("$.fieldErrors.subject").value("Subject is required"))
                .andExpect(jsonPath("$.fieldErrors.payload").value("Payload is required"));
    }

    @Test
    void handleValidationException_EmptySubject_ShouldReturnBadRequest() throws Exception {
        String requestBody = objectMapper.writeValueAsString(new Object() {
            public String subject = "";
            public Object payload = "test";
        });

        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.subject").value("Subject is required"));
    }

    @Test
    void handleValidationException_NullPayload_ShouldReturnBadRequest() throws Exception {
        String requestBody = objectMapper.writeValueAsString(new Object() {
            public String subject = "test.subject";
            public Object payload = null;
        });

        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.payload").value("Payload is required"));
    }

    @Test
    void handleJsonParseException_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").contains("JSON parse error"));
    }

    @Test
    void handleHttpMessageNotReadableException_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/nats/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void handleIllegalArgumentException_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/nats/requests/INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }
}