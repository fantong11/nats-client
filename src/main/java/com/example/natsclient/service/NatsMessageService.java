package com.example.natsclient.service;

import java.util.concurrent.CompletableFuture;

public interface NatsMessageService {
    
    CompletableFuture<String> sendRequest(String subject, Object requestPayload, String correlationId);
    
    CompletableFuture<Void> publishMessage(String subject, Object messagePayload);
}