package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.repository.NatsRequestLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestResponseCorrelationServiceTest {

        @Mock
        private NatsRequestLogRepository requestLogRepository;

        @Mock
        private WebhookService webhookService;

        @InjectMocks
        private RequestResponseCorrelationService correlationService;

        @Test
        void processResponse_MatchingRequestFound_ShouldUpdateLogAndTriggerWebhook() {
                // Arrange
                String responseIdField = "correlationId";
                String correlationId = "12345";
                String webhookUrl = "https://webhook.site/test";

                ListenerResult.MessageReceived responseMessage = new ListenerResult.MessageReceived(
                                "response.subject",
                                "msg-id-1",
                                correlationId,
                                "{\"correlationId\":\"12345\", \"status\":\"OK\"}",
                                1L);

                NatsRequestLog pendingRequest = new NatsRequestLog();
                pendingRequest.setRequestId("req-001");
                pendingRequest.setStatus(NatsRequestLog.RequestStatus.PENDING);
                pendingRequest.setRequestPayload("{\"correlationId\":\"12345\"}");
                pendingRequest.setWebhookUrl(webhookUrl);

                // We don't need PayloadProcessor mock anymore as extractedId is passed in
                // MessageReceived
                when(requestLogRepository.findByRequestId(correlationId))
                                .thenReturn(Optional.of(pendingRequest));

                // Act
                boolean result = correlationService.processResponse(responseMessage, responseIdField);

                // Assert
                assertTrue(result);
                assertEquals(NatsRequestLog.RequestStatus.SUCCESS, pendingRequest.getStatus());
                verify(requestLogRepository).save(pendingRequest);
                verify(webhookService).sendWebhook(eq(webhookUrl), eq(pendingRequest));
        }

        @Test
        void processResponse_NoMatchingRequest_ShouldReturnFalse() {
                // Arrange
                String responseIdField = "correlationId";
                String correlationId = "12345";

                ListenerResult.MessageReceived responseMessage = new ListenerResult.MessageReceived(
                                "response.subject",
                                "msg-id-1",
                                correlationId,
                                "{\"correlationId\":\"12345\", \"status\":\"OK\"}",
                                1L);

                when(requestLogRepository.findByRequestId(correlationId))
                                .thenReturn(Optional.empty());

                // Act
                boolean result = correlationService.processResponse(responseMessage, responseIdField);

                // Assert
                assertFalse(result);
                verify(requestLogRepository, never()).save(any());
                verify(webhookService, never()).sendWebhook(anyString(), any());
        }
}
