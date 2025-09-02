package com.example.natsclient.service.handler;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.util.JsonIdExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Simplified unit tests for MessageProcessor focusing on core functionality.
 */
@ExtendWith(MockitoExtension.class)
class SimpleMessageProcessorTest {
    
    @Mock
    private JsonIdExtractor jsonIdExtractor;
    
    @Mock
    private Consumer<ListenerResult.MessageReceived> messageHandler;
    
    private MessageProcessor messageProcessor;
    
    @BeforeEach
    void setUp() {
        messageProcessor = new MessageProcessor(jsonIdExtractor);
    }
    
    @Test
    void messageProcessingException_ShouldHaveCorrectMessage() {
        // Given
        String errorMessage = "Test error message";
        RuntimeException cause = new RuntimeException("Root cause");
        
        // When
        MessageProcessor.MessageProcessingException exception = 
            new MessageProcessor.MessageProcessingException(errorMessage, cause);
        
        // Then
        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
    
    @Test
    void messageProcessingException_WithNullCause_ShouldWork() {
        // Given
        String errorMessage = "Test error message";
        
        // When
        MessageProcessor.MessageProcessingException exception = 
            new MessageProcessor.MessageProcessingException(errorMessage, null);
        
        // Then
        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
    }
    
    @Test
    void messageProcessor_ShouldBeCreatedWithJsonIdExtractor() {
        // When
        MessageProcessor processor = new MessageProcessor(jsonIdExtractor);
        
        // Then
        assertNotNull(processor);
    }
}