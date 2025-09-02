package com.example.natsclient.service.config;

import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConsumerConfigurationFactory.
 * Tests the Single Responsibility Principle implementation for configuration creation.
 */
class ConsumerConfigurationFactoryTest {
    
    private ConsumerConfigurationFactory configFactory;
    
    @BeforeEach
    void setUp() {
        configFactory = new ConsumerConfigurationFactory();
    }
    
    @Test
    void createDurableConsumerConfig_WithValidSubject_ShouldCreateCorrectConfiguration() {
        // Given
        String subject = "orders.requests";
        
        // When
        ConsumerConfiguration config = configFactory.createDurableConsumerConfig(subject);
        
        // Then
        assertNotNull(config);
        assertEquals("durable-consumer-orders-requests", config.getName());
        assertEquals("durable-consumer-orders-requests", config.getDurable());
        assertEquals(DeliverPolicy.New, config.getDeliverPolicy());
        assertEquals(Duration.ofSeconds(30), config.getAckWait());
        assertEquals(3, config.getMaxDeliver());
    }
    
    @Test
    void createDurableConsumerConfig_WithSubjectContainingDots_ShouldReplaceDots() {
        // Given
        String subject = "users.profile.updates";
        
        // When
        ConsumerConfiguration config = configFactory.createDurableConsumerConfig(subject);
        
        // Then
        assertNotNull(config);
        assertEquals("durable-consumer-users-profile-updates", config.getName());
        assertEquals("durable-consumer-users-profile-updates", config.getDurable());
    }
    
    @Test
    void createDurableConsumerConfig_WithSimpleSubject_ShouldWorkCorrectly() {
        // Given
        String subject = "notifications";
        
        // When
        ConsumerConfiguration config = configFactory.createDurableConsumerConfig(subject);
        
        // Then
        assertNotNull(config);
        assertEquals("durable-consumer-notifications", config.getName());
        assertEquals("durable-consumer-notifications", config.getDurable());
        assertEquals(DeliverPolicy.New, config.getDeliverPolicy());
        assertEquals(Duration.ofSeconds(30), config.getAckWait());
        assertEquals(3, config.getMaxDeliver());
    }
    
    @Test
    void createDurableConsumerConfig_MultipleCallsWithSameSubject_ShouldReturnConsistentResults() {
        // Given
        String subject = "test.subject";
        
        // When
        ConsumerConfiguration config1 = configFactory.createDurableConsumerConfig(subject);
        ConsumerConfiguration config2 = configFactory.createDurableConsumerConfig(subject);
        
        // Then
        assertEquals(config1.getName(), config2.getName());
        assertEquals(config1.getDurable(), config2.getDurable());
        assertEquals(config1.getDeliverPolicy(), config2.getDeliverPolicy());
        assertEquals(config1.getAckWait(), config2.getAckWait());
        assertEquals(config1.getMaxDeliver(), config2.getMaxDeliver());
    }
    
    @Test
    void generateDurableConsumerName_WithValidSubject_ShouldGenerateCorrectName() {
        // Given
        String subject = "orders.processing";
        
        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);
        
        // Then
        assertEquals("durable-consumer-orders-processing", consumerName);
    }
    
    @Test
    void generateDurableConsumerName_WithSingleWordSubject_ShouldGenerateCorrectName() {
        // Given
        String subject = "events";
        
        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);
        
        // Then
        assertEquals("durable-consumer-events", consumerName);
    }
    
    @Test
    void generateDurableConsumerName_WithMultipleDotsInSubject_ShouldReplaceAllDots() {
        // Given
        String subject = "company.department.team.project.events";
        
        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);
        
        // Then
        assertEquals("durable-consumer-company-department-team-project-events", consumerName);
        assertFalse(consumerName.contains("."));
    }
    
    @Test
    void generateDurableConsumerName_WithEmptySubject_ShouldHandleGracefully() {
        // Given
        String subject = "";
        
        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);
        
        // Then
        assertEquals("durable-consumer-", consumerName);
    }
    
    @Test
    void createDurableConsumerConfig_ConsistencyBetweenNameAndDurable_ShouldMatch() {
        // Given
        String subject = "test.consistency";
        
        // When
        ConsumerConfiguration config = configFactory.createDurableConsumerConfig(subject);
        String expectedName = configFactory.generateDurableConsumerName(subject);
        
        // Then
        assertEquals(expectedName, config.getName());
        assertEquals(expectedName, config.getDurable());
        assertEquals(config.getName(), config.getDurable()); // Name and durable should be identical
    }
    
    @Test
    void createDurableConsumerConfig_ShouldHaveCorrectDefaultValues() {
        // Given
        String subject = "test.defaults";
        
        // When
        ConsumerConfiguration config = configFactory.createDurableConsumerConfig(subject);
        
        // Then
        // Test all expected default values
        assertEquals(DeliverPolicy.New, config.getDeliverPolicy());
        assertEquals(Duration.ofSeconds(30), config.getAckWait());
        assertEquals(3, config.getMaxDeliver());
        
        // Ensure durable consumer is properly configured
        assertNotNull(config.getName());
        assertNotNull(config.getDurable());
        assertEquals(config.getName(), config.getDurable());
    }
    
    @Test
    void createDurableConsumerConfig_DifferentSubjects_ShouldHaveDifferentNames() {
        // Given
        String subject1 = "users.created";
        String subject2 = "orders.updated";
        
        // When
        ConsumerConfiguration config1 = configFactory.createDurableConsumerConfig(subject1);
        ConsumerConfiguration config2 = configFactory.createDurableConsumerConfig(subject2);
        
        // Then
        assertNotEquals(config1.getName(), config2.getName());
        assertNotEquals(config1.getDurable(), config2.getDurable());
        
        // But should have same other properties
        assertEquals(config1.getDeliverPolicy(), config2.getDeliverPolicy());
        assertEquals(config1.getAckWait(), config2.getAckWait());
        assertEquals(config1.getMaxDeliver(), config2.getMaxDeliver());
    }
}