package com.example.natsclient.service;

public interface PayloadProcessor {
    
    String serialize(Object payload);
    
    <T> T deserialize(String payload, Class<T> targetClass);
    
    byte[] toBytes(String payload);
    
    String fromBytes(byte[] data);
}