package com.example.natsclient.service.contract;

/**
 * Interface for managing response listeners.
 * Follows Interface Segregation Principle by separating listener management concerns.
 */
public interface ResponseListenerManager {
    
    /**
     * Ensure a response listener is active for the specified configuration.
     * 
     * @param responseSubject The subject to listen on
     * @param responseIdField The field to extract ID from responses
     */
    void ensureListenerActive(String responseSubject, String responseIdField);
    
    /**
     * Check if a listener is already active for the subject.
     * 
     * @param subject The subject to check
     * @return true if listener is active
     */
    boolean isListenerActive(String subject);
}