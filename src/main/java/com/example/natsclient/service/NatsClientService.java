package com.example.natsclient.service;

import com.example.natsclient.model.PublishResult;
import com.example.natsclient.service.impl.RetryServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class NatsClientService {

    private static final Logger logger = LoggerFactory.getLogger(NatsClientService.class);

    private final NatsMessageService natsMessageService;
    private final RetryServiceImpl retryService;

    @Autowired
    public NatsClientService(NatsMessageService natsMessageService, 
                           RetryServiceImpl retryService) {
        this.natsMessageService = natsMessageService;
        this.retryService = retryService;
    }
    
    public CompletableFuture<PublishResult> publishMessage(String requestId, String subject, Object messagePayload) {
        return natsMessageService.publishMessage(requestId, subject, messagePayload);
    }

    public void retryFailedRequests() {
        retryService.retryFailedRequests();
    }
}