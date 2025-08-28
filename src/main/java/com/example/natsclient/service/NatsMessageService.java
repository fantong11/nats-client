package com.example.natsclient.service;

import com.example.natsclient.model.PublishResult;
import java.util.concurrent.CompletableFuture;

public interface NatsMessageService {
    CompletableFuture<PublishResult> publishMessage(String requestId, String subject, Object messagePayload);
}