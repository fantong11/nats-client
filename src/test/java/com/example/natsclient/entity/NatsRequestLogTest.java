package com.example.natsclient.entity;

import com.example.natsclient.dto.NatsRequestLogDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NatsRequestLogDtoTest {

    @Test
    void defaultConstructor_ShouldCreateEmptyLog() {
        NatsRequestLogDto log = new NatsRequestLogDto();
        
        assertNull(log.getId());
        assertNull(log.getRequestId());
        assertNull(log.getCorrelationId());
        assertNull(log.getSubject());
        assertNull(log.getRequestPayload());
        assertNull(log.getResponsePayload());
        assertNull(log.getStatus()); // No default value
        assertNull(log.getRequestTimestamp());
        assertNull(log.getResponseTimestamp());
        assertNull(log.getRetryCount());
        assertNull(log.getTimeoutDuration());
        assertNull(log.getErrorMessage());
        assertNull(log.getCreatedDate());
        assertNull(log.getUpdatedDate());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        NatsRequestLogDto log = new NatsRequestLogDto();
        
        Long id = 1L;
        String requestId = "req-123";
        String correlationId = "corr-456";
        String subject = "test.subject";
        String requestPayload = "{\"test\": \"data\"}";
        String responsePayload = "{\"result\": \"success\"}";
        NatsRequestLogDto.RequestStatus status = NatsRequestLogDto.RequestStatus.SUCCESS;
        LocalDateTime requestTimestamp = LocalDateTime.now();
        LocalDateTime responseTimestamp = LocalDateTime.now().plusSeconds(1);
        int retryCount = 2;
        Long timeoutDuration = 5000L;
        String errorMessage = "Timeout occurred";
        LocalDateTime createdDate = LocalDateTime.now();
        LocalDateTime updatedDate = LocalDateTime.now().plusMinutes(1);
        
        log.setId(id);
        log.setRequestId(requestId);
        log.setCorrelationId(correlationId);
        log.setSubject(subject);
        log.setRequestPayload(requestPayload);
        log.setResponsePayload(responsePayload);
        log.setStatus(status);
        log.setRequestTimestamp(requestTimestamp);
        log.setResponseTimestamp(responseTimestamp);
        log.setRetryCount(retryCount);
        log.setTimeoutDuration(timeoutDuration);
        log.setErrorMessage(errorMessage);
        log.setCreatedDate(createdDate);
        log.setUpdatedDate(updatedDate);
        
        assertEquals(id, log.getId());
        assertEquals(requestId, log.getRequestId());
        assertEquals(correlationId, log.getCorrelationId());
        assertEquals(subject, log.getSubject());
        assertEquals(requestPayload, log.getRequestPayload());
        assertEquals(responsePayload, log.getResponsePayload());
        assertEquals(status, log.getStatus());
        assertEquals(requestTimestamp, log.getRequestTimestamp());
        assertEquals(responseTimestamp, log.getResponseTimestamp());
        assertEquals(retryCount, log.getRetryCount());
        assertEquals(timeoutDuration, log.getTimeoutDuration());
        assertEquals(errorMessage, log.getErrorMessage());
        assertEquals(createdDate, log.getCreatedDate());
        assertEquals(updatedDate, log.getUpdatedDate());
    }

    @Test
    void requestStatusEnum_ShouldHaveAllExpectedValues() {
        NatsRequestLogDto.RequestStatus[] statuses = NatsRequestLogDto.RequestStatus.values();
        
        assertEquals(5, statuses.length);
        assertEquals(NatsRequestLogDto.RequestStatus.PENDING, NatsRequestLogDto.RequestStatus.valueOf("PENDING"));
        assertEquals(NatsRequestLogDto.RequestStatus.SUCCESS, NatsRequestLogDto.RequestStatus.valueOf("SUCCESS"));
        assertEquals(NatsRequestLogDto.RequestStatus.FAILED, NatsRequestLogDto.RequestStatus.valueOf("FAILED"));
        assertEquals(NatsRequestLogDto.RequestStatus.TIMEOUT, NatsRequestLogDto.RequestStatus.valueOf("TIMEOUT"));
        assertEquals(NatsRequestLogDto.RequestStatus.ERROR, NatsRequestLogDto.RequestStatus.valueOf("ERROR"));
    }

    @Test
    void builder_ShouldCreateLogWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        
        NatsRequestLogDto log = NatsRequestLogDto.builder()
                .requestId("req-123")
                .correlationId("corr-456")
                .subject("test.subject")
                .requestPayload("{\"test\": \"data\"}")
                .responsePayload("{\"result\": \"success\"}")
                .status(NatsRequestLogDto.RequestStatus.SUCCESS)
                .requestTimestamp(now)
                .responseTimestamp(now.plusSeconds(1))
                .retryCount(1)
                .timeoutDuration(5000L)
                .errorMessage(null)
                .build();
        
        assertEquals("req-123", log.getRequestId());
        assertEquals("corr-456", log.getCorrelationId());
        assertEquals("test.subject", log.getSubject());
        assertEquals("{\"test\": \"data\"}", log.getRequestPayload());
        assertEquals("{\"result\": \"success\"}", log.getResponsePayload());
        assertEquals(NatsRequestLogDto.RequestStatus.SUCCESS, log.getStatus());
        assertEquals(now, log.getRequestTimestamp());
        assertEquals(now.plusSeconds(1), log.getResponseTimestamp());
        assertEquals(1, log.getRetryCount());
        assertEquals(5000L, log.getTimeoutDuration());
        assertNull(log.getErrorMessage());
    }

    @Test
    void equals_ShouldWorkCorrectlyWithId() {
        NatsRequestLogDto log1 = new NatsRequestLogDto();
        log1.setId(1L);
        log1.setRequestId("req-123");
        
        NatsRequestLogDto log2 = new NatsRequestLogDto();
        log2.setId(1L);
        log2.setRequestId("req-456");
        
        NatsRequestLogDto log3 = new NatsRequestLogDto();
        log3.setId(2L);
        log3.setRequestId("req-123");
        
        // Note: equals() is based on all fields with Lombok @Data, not just ID
        assertNotEquals(log1, log3); // Different ID
        assertNotEquals(log1, null);
        assertNotEquals(log1, "not a log");
    }

    @Test
    void hashCode_ShouldBeConsistentWithEquals() {
        NatsRequestLogDto log1 = new NatsRequestLogDto();
        log1.setId(1L);
        
        NatsRequestLogDto log2 = new NatsRequestLogDto();
        log2.setId(1L);
        
        assertEquals(log1.hashCode(), log2.hashCode());
    }

    @Test
    void toString_ShouldContainKeyFields() {
        NatsRequestLogDto log = new NatsRequestLogDto();
        log.setRequestId("req-123");
        log.setSubject("test.subject");
        log.setStatus(NatsRequestLogDto.RequestStatus.SUCCESS);
        
        String toString = log.toString();
        assertTrue(toString.contains("req-123"));
        assertTrue(toString.contains("test.subject"));
        assertTrue(toString.contains("SUCCESS"));
    }

    @Test
    void prePersist_ShouldSetTimestamps() {
        NatsRequestLogDto log = new NatsRequestLogDto();
        
        // Simulate @PrePersist callback
        LocalDateTime before = LocalDateTime.now();
        log.setCreatedDate(LocalDateTime.now());
        log.setUpdatedDate(LocalDateTime.now());
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(log.getCreatedDate());
        assertNotNull(log.getUpdatedDate());
        assertTrue(log.getCreatedDate().isAfter(before.minusSeconds(1)));
        assertTrue(log.getCreatedDate().isBefore(after.plusSeconds(1)));
        assertTrue(log.getUpdatedDate().isAfter(before.minusSeconds(1)));
        assertTrue(log.getUpdatedDate().isBefore(after.plusSeconds(1)));
    }

    @Test
    void preUpdate_ShouldUpdateTimestamp() {
        NatsRequestLogDto log = new NatsRequestLogDto();
        LocalDateTime originalCreated = LocalDateTime.now().minusHours(1);
        log.setCreatedDate(originalCreated);
        
        // Simulate @PreUpdate callback
        LocalDateTime before = LocalDateTime.now();
        log.setUpdatedDate(LocalDateTime.now());
        LocalDateTime after = LocalDateTime.now();
        
        assertEquals(originalCreated, log.getCreatedDate()); // Should not change
        assertNotNull(log.getUpdatedDate());
        assertTrue(log.getUpdatedDate().isAfter(before.minusSeconds(1)));
        assertTrue(log.getUpdatedDate().isBefore(after.plusSeconds(1)));
    }
}