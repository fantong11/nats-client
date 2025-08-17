package com.example.natsclient.service.impl;

import com.example.natsclient.exception.PayloadProcessingException;
import com.example.natsclient.service.PayloadProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class JsonPayloadProcessor implements PayloadProcessor {
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    public JsonPayloadProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new PayloadProcessingException("Failed to serialize payload", e);
        }
    }
    
    @Override
    public <T> T deserialize(String payload, Class<T> targetClass) {
        try {
            return objectMapper.readValue(payload, targetClass);
        } catch (Exception e) {
            throw new PayloadProcessingException("Failed to deserialize payload", e);
        }
    }
    
    @Override
    public byte[] toBytes(String payload) {
        return payload.getBytes(StandardCharsets.UTF_8);
    }
    
    @Override
    public String fromBytes(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}