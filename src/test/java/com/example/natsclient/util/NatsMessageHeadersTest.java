package com.example.natsclient.util;

import io.nats.client.impl.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NatsMessageHeadersTest {

    @Test
    void generateMessageId_ShouldCreateUniqueIds() {
        String id1 = NatsMessageHeaders.generateMessageId();
        String id2 = NatsMessageHeaders.generateMessageId();
        
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("msg-"));
        assertTrue(id2.startsWith("msg-"));
    }

    @Test
    void generateMessageId_WithPrefix_ShouldUsePrefix() {
        String id = NatsMessageHeaders.generateMessageId("test");
        
        assertNotNull(id);
        assertTrue(id.startsWith("test-"));
    }

    @Test
    void createHeadersWithMessageId_ShouldSetCorrectHeader() {
        String messageId = "test-message-123";
        Headers headers = NatsMessageHeaders.createHeadersWithMessageId(messageId);
        
        assertNotNull(headers);
        assertEquals(messageId, headers.getFirst(NatsMessageHeaders.NATS_MSG_ID));
    }

    @Test
    void createHeadersWithIds_ShouldSetMessageId() {
        String messageId = "msg-123";
        
        Headers headers = NatsMessageHeaders.createHeadersWithIds(messageId);
        
        assertNotNull(headers);
        assertEquals(messageId, headers.getFirst(NatsMessageHeaders.NATS_MSG_ID));
    }

    @Test
    void createComprehensiveHeaders_ShouldIncludeAllFields() {
        String messageId = "msg-123";
        String sourceService = "test-service";
        
        Headers headers = NatsMessageHeaders.createComprehensiveHeaders(messageId, sourceService);
        
        assertNotNull(headers);
        assertEquals(messageId, headers.getFirst(NatsMessageHeaders.NATS_MSG_ID));
        assertEquals(sourceService, headers.getFirst(NatsMessageHeaders.CUSTOM_SOURCE_SERVICE));
        assertNotNull(headers.getFirst(NatsMessageHeaders.NATS_TIMESTAMP));
    }

    @Test
    void setMessageId_ShouldUpdateExistingHeaders() {
        Headers headers = new Headers();
        String messageId = "new-message-id";
        
        NatsMessageHeaders.setMessageId(headers, messageId);
        
        assertEquals(messageId, headers.getFirst(NatsMessageHeaders.NATS_MSG_ID));
    }

    @Test
    void generateMessageId_WithNullPrefix_ShouldUseDefault() {
        String id = NatsMessageHeaders.generateMessageId(null);
        
        assertNotNull(id);
        assertTrue(id.startsWith("msg-"));
    }

    @Test
    void generateMessageId_WithEmptyPrefix_ShouldUseDefault() {
        String id = NatsMessageHeaders.generateMessageId("");
        
        assertNotNull(id);
        assertTrue(id.startsWith("msg-"));
    }
}