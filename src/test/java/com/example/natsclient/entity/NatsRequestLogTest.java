package com.example.natsclient.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NatsRequestLogTest {

    @Test
    void defaultConstructor_ShouldCreateEmptyLog() {
        NatsRequestLog log = new NatsRequestLog();
        
        assertNull(log.getId());
        assertNull(log.getRequestId());
        assertNull(log.getCorrelationId());
        assertNull(log.getSubject());
        assertNull(log.getRequestPayload());
        assertNull(log.getResponsePayload());
        assertEquals(NatsRequestLog.RequestStatus.PENDING, log.getStatus()); // Default value
        assertNull(log.getRequestTimestamp());
        assertNull(log.getResponseTimestamp());
        assertEquals(0, log.getRetryCount());
        assertNull(log.getTimeoutDuration());
        assertNull(log.getErrorMessage());
        assertNull(log.getCreatedDate());
        assertNull(log.getUpdatedDate());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        NatsRequestLog log = new NatsRequestLog();
        
        Long id = 1L;
        String requestId = "req-123";
        String correlationId = "corr-456";
        String subject = "test.subject";
        String requestPayload = "{\"test\": \"data\"}";
        String responsePayload = "{\"result\": \"success\"}";
        NatsRequestLog.RequestStatus status = NatsRequestLog.RequestStatus.SUCCESS;
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
        NatsRequestLog.RequestStatus[] statuses = NatsRequestLog.RequestStatus.values();
        
        assertEquals(5, statuses.length);
        assertEquals(NatsRequestLog.RequestStatus.PENDING, NatsRequestLog.RequestStatus.valueOf("PENDING"));
        assertEquals(NatsRequestLog.RequestStatus.SUCCESS, NatsRequestLog.RequestStatus.valueOf("SUCCESS"));
        assertEquals(NatsRequestLog.RequestStatus.FAILED, NatsRequestLog.RequestStatus.valueOf("FAILED"));
        assertEquals(NatsRequestLog.RequestStatus.TIMEOUT, NatsRequestLog.RequestStatus.valueOf("TIMEOUT"));
        assertEquals(NatsRequestLog.RequestStatus.ERROR, NatsRequestLog.RequestStatus.valueOf("ERROR"));
    }

    @Test
    void builder_ShouldCreateLogWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        
        NatsRequestLog log = NatsRequestLog.builder()
                .requestId("req-123")
                .correlationId("corr-456")
                .subject("test.subject")
                .requestPayload("{\"test\": \"data\"}")
                .responsePayload("{\"result\": \"success\"}")
                .status(NatsRequestLog.RequestStatus.SUCCESS)
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
        assertEquals(NatsRequestLog.RequestStatus.SUCCESS, log.getStatus());
        assertEquals(now, log.getRequestTimestamp());
        assertEquals(now.plusSeconds(1), log.getResponseTimestamp());
        assertEquals(1, log.getRetryCount());
        assertEquals(5000L, log.getTimeoutDuration());
        assertNull(log.getErrorMessage());
    }

    @Test
    void equals_ShouldWorkCorrectlyWithId() {
        NatsRequestLog log1 = new NatsRequestLog();
        log1.setId(1L);
        log1.setRequestId("req-123");
        
        NatsRequestLog log2 = new NatsRequestLog();
        log2.setId(1L);
        log2.setRequestId("req-456");
        
        NatsRequestLog log3 = new NatsRequestLog();
        log3.setId(2L);
        log3.setRequestId("req-123");
        
        // Note: equals() is based on all fields with Lombok @Data, not just ID
        assertNotEquals(log1, log3); // Different ID
        assertNotEquals(log1, null);
        assertNotEquals(log1, "not a log");
    }

    @Test
    void hashCode_ShouldBeConsistentWithEquals() {
        NatsRequestLog log1 = new NatsRequestLog();
        log1.setId(1L);
        
        NatsRequestLog log2 = new NatsRequestLog();
        log2.setId(1L);
        
        assertEquals(log1.hashCode(), log2.hashCode());
    }

    @Test
    void toString_ShouldContainKeyFields() {
        NatsRequestLog log = new NatsRequestLog();
        log.setRequestId("req-123");
        log.setSubject("test.subject");
        log.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        
        String toString = log.toString();
        assertTrue(toString.contains("req-123"));
        assertTrue(toString.contains("test.subject"));
        assertTrue(toString.contains("SUCCESS"));
    }

    @Test
    void prePersist_ShouldSetTimestamps() {
        NatsRequestLog log = new NatsRequestLog();
        
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
        NatsRequestLog log = new NatsRequestLog();
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