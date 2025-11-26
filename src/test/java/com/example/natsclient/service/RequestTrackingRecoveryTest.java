package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.repository.NatsRequestLogRepository;
import com.example.natsclient.service.contract.ResponseListenerManager;
import com.example.natsclient.service.recovery.PendingRequestRecoveryService;
import com.example.natsclient.service.timeout.RequestTimeoutManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RequestTrackingRecoveryTest {

        @Mock
        private NatsRequestLogRepository requestLogRepository;

        @Mock
        private RequestResponseCorrelationService correlationService;

        @Mock
        private ResponseListenerManager responseListenerManager;

        private RequestTimeoutManager requestTimeoutManager;
        private PendingRequestRecoveryService recoveryService;

        @BeforeEach
        public void setup() {
                requestTimeoutManager = new RequestTimeoutManager(requestLogRepository, correlationService);
                recoveryService = new PendingRequestRecoveryService(requestLogRepository, responseListenerManager);
        }

        @Test
        public void testRequestTimeout() {
                // 1. Prepare data
                NatsRequestLog requestLog = NatsRequestLog.builder()
                                .requestId("REQ-TIMEOUT-TEST")
                                .subject("test.subject")
                                .requestPayload("{}")
                                .status(NatsRequestLog.RequestStatus.PENDING)
                                .requestTimestamp(LocalDateTime.now().minusMinutes(1))
                                .build();

                // 2. Mock behavior
                when(requestLogRepository.findTimedOutRequests(any(LocalDateTime.class)))
                                .thenReturn(List.of(requestLog));

                // 3. Trigger timeout check
                requestTimeoutManager.checkTimeouts();

                // 4. Verify
                verify(requestLogRepository, times(1)).findTimedOutRequests(any(LocalDateTime.class));
                verify(correlationService, times(1)).markRequestAsTimeout("REQ-TIMEOUT-TEST");
        }

        @Test
        public void testPendingRequestRecovery() {
                // 1. Prepare data
                NatsRequestLog requestLog = NatsRequestLog.builder()
                                .requestId("REQ-RECOVERY-TEST")
                                .subject("test.subject")
                                .requestPayload("{}")
                                .status(NatsRequestLog.RequestStatus.PENDING)
                                .requestTimestamp(LocalDateTime.now())
                                .responseSubject("response.subject")
                                .responseIdField("id")
                                .build();

                // 2. Mock behavior
                when(requestLogRepository.findByStatus(NatsRequestLog.RequestStatus.PENDING))
                                .thenReturn(List.of(requestLog));

                // 3. Trigger recovery
                recoveryService.recoverPendingRequests();

                // 4. Verify
                verify(requestLogRepository, times(1)).findByStatus(NatsRequestLog.RequestStatus.PENDING);
                verify(responseListenerManager, times(1)).ensureListenerActive("response.subject", "id");
        }
}
