package com.example.natsclient.service.listener;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.NatsListenerService;
import com.example.natsclient.service.RequestResponseCorrelationService;
import com.example.natsclient.service.contract.ResponseListenerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Implementation of ResponseListenerManager that manages NATS response listeners.
 * Follows Single Responsibility Principle by focusing only on listener management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NatsResponseListenerManager implements ResponseListenerManager {
    
    private final NatsListenerService natsListenerService;
    private final RequestResponseCorrelationService correlationService;
    
    @Override
    public void ensureListenerActive(String responseSubject, String responseIdField) {
        try {
            if (!isCompatibleListenerActive(responseSubject, responseIdField)) {
                startResponseListener(responseSubject, responseIdField);
            } else {
                log.debug("Compatible response listener already active for subject '{}' with ID field '{}'", 
                         responseSubject, responseIdField);
            }
        } catch (Exception e) {
            log.error("Error ensuring response listener for subject '{}'", responseSubject, e);
        }
    }
    
    /**
     * Check if there's a compatible listener active for the given subject and ID field.
     * A compatible listener must have the same subject AND the same responseIdField.
     */
    private boolean isCompatibleListenerActive(String responseSubject, String responseIdField) {
        // Use getListenerStatus to check if there's a compatible listener
        try {
            return natsListenerService.getListenerStatus()
                .thenApply(statuses -> statuses.stream()
                    .anyMatch(status -> 
                        status.subject().equals(responseSubject) && 
                        status.idFieldName().equals(responseIdField)))
                .get(); // Block to get the result synchronously
        } catch (Exception e) {
            log.warn("Error checking listener compatibility for subject '{}', assuming no compatible listener exists", responseSubject, e);
            return false;
        }
    }
    
    @Override
    public boolean isListenerActive(String subject) {
        return natsListenerService.isListenerActive(subject);
    }
    
    private void startResponseListener(String responseSubject, String responseIdField) {
        log.info("Starting automatic response listener for subject '{}' with ID field '{}'", 
                responseSubject, responseIdField);
        
        Consumer<ListenerResult.MessageReceived> responseHandler = createResponseHandler(responseIdField);
        
        natsListenerService.startListener(responseSubject, responseIdField, responseHandler)
            .whenComplete((listenerId, throwable) -> handleListenerStartResult(
                responseSubject, listenerId, throwable));
    }
    
    private Consumer<ListenerResult.MessageReceived> createResponseHandler(String responseIdField) {
        return messageResult -> {
            log.debug("Response received - Subject: '{}', Sequence: {}", 
                     messageResult.subject(), messageResult.sequence());
            
            boolean correlated = correlationService.processResponse(messageResult, responseIdField);
            
            if (correlated) {
                log.info("Successfully correlated response from subject '{}'", messageResult.subject());
            } else {
                log.warn("Failed to correlate response from subject '{}'", messageResult.subject());
            }
        };
    }
    
    private void handleListenerStartResult(String responseSubject, String listenerId, Throwable throwable) {
        if (throwable != null) {
            log.error("Failed to start automatic response listener for subject '{}'", 
                     responseSubject, throwable);
        } else {
            log.info("Successfully started automatic response listener '{}' for subject '{}'",
                    listenerId, responseSubject);
        }
    }
}