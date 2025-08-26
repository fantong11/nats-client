package com.example.natsclient.util;

import io.nats.client.api.PublishAck;
import org.springframework.stereotype.Component;

/**
 * Utility class for NATS message operations.
 */
@Component
public class NatsMessageUtils {
    
    /**
     * Utility method to extract key information from PublishAck for logging.
     */
    public String formatPublishAck(PublishAck ack) {
        if (ack == null) {
            return "null";
        }
        
        return String.format("PublishAck{stream='%s', sequence=%d, duplicate=%s}", 
                           ack.getStream(), ack.getSeqno(), ack.isDuplicate());
    }
}