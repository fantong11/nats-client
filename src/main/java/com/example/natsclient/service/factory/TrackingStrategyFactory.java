package com.example.natsclient.service.factory;

import com.example.natsclient.service.contract.RequestTrackingStrategy;
import com.example.natsclient.service.strategy.PayloadIdTrackingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory for creating tracking strategies.
 * Follows Factory Pattern and Open/Closed Principle - easy to add new strategies.
 */
@Component
@RequiredArgsConstructor
public class TrackingStrategyFactory {
    
    private final PayloadIdTrackingStrategy payloadIdTrackingStrategy;
    
    /**
     * Get the appropriate tracking strategy based on request configuration.
     * Currently returns PayloadIdTrackingStrategy, but can be extended to support
     * different strategies based on request properties.
     * 
     * @return The tracking strategy to use
     */
    public RequestTrackingStrategy getTrackingStrategy() {
        // In the future, this could be extended to select different strategies
        // based on request properties, configuration, or other factors
        return payloadIdTrackingStrategy;
    }
    
    /**
     * Future extension point for strategy selection based on request type.
     * 
     * @param strategyType The type of strategy needed
     * @return The appropriate tracking strategy
     */
    public RequestTrackingStrategy getTrackingStrategy(StrategyType strategyType) {
        if (strategyType == StrategyType.PAYLOAD_ID) {
            return payloadIdTrackingStrategy;
        }
        // Future strategies can be added here:
        // else if (strategyType == StrategyType.CORRELATION_ID) {
        //     return correlationIdTrackingStrategy;
        // }
        // else if (strategyType == StrategyType.CUSTOM) {
        //     return customTrackingStrategy;
        // }
        
        throw new IllegalArgumentException("Unsupported strategy type: " + strategyType);
    }
    
    public enum StrategyType {
        PAYLOAD_ID
        // CORRELATION_ID,
        // CUSTOM
    }
}