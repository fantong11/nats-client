package com.example.natsclient.service.config;

import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConsumerConfigurationFactory.
 * Tests Pull Consumer configuration creation following Single Responsibility Principle.
 */
class ConsumerConfigurationFactoryTest {

    private ConsumerConfigurationFactory configFactory;

    @BeforeEach
    void setUp() {
        configFactory = new ConsumerConfigurationFactory();
    }

    @Test
    void createPullConsumerConfig_WithValidSubject_ShouldCreateCorrectConfiguration() {
        // Given
        String subject = "orders.requests";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        assertNotNull(config);
        assertEquals("pull-consumer-orders-requests", config.getName());
        assertEquals("pull-consumer-orders-requests", config.getDurable());
        assertEquals(DeliverPolicy.New, config.getDeliverPolicy());
        assertEquals(AckPolicy.Explicit, config.getAckPolicy());
        assertEquals(Duration.ofSeconds(30), config.getAckWait());
        assertEquals(3, config.getMaxDeliver());
        assertEquals(1000, config.getMaxAckPending());
    }

    @Test
    void createPullConsumerConfig_WithSubjectContainingDots_ShouldReplaceDots() {
        // Given
        String subject = "users.profile.updates";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);
        
        // Then
        assertNotNull(config);
        assertEquals("pull-consumer-users-profile-updates", config.getName());
        assertEquals("pull-consumer-users-profile-updates", config.getDurable());
    }

    @Test
    void createPullConsumerConfig_WithSimpleSubject_ShouldWorkCorrectly() {
        // Given
        String subject = "notifications";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        assertNotNull(config);
        assertEquals("pull-consumer-notifications", config.getName());
        assertEquals("pull-consumer-notifications", config.getDurable());
        assertEquals(DeliverPolicy.New, config.getDeliverPolicy());
        assertEquals(AckPolicy.Explicit, config.getAckPolicy());
        assertEquals(Duration.ofSeconds(30), config.getAckWait());
        assertEquals(3, config.getMaxDeliver());
        assertEquals(1000, config.getMaxAckPending());
    }

    @Test
    void createPullConsumerConfig_MultipleCallsWithSameSubject_ShouldReturnConsistentResults() {
        // Given
        String subject = "test.subject";

        // When
        ConsumerConfiguration config1 = configFactory.createPullConsumerConfig(subject);
        ConsumerConfiguration config2 = configFactory.createPullConsumerConfig(subject);

        // Then
        assertEquals(config1.getName(), config2.getName());
        assertEquals(config1.getDurable(), config2.getDurable());
        assertEquals(config1.getDeliverPolicy(), config2.getDeliverPolicy());
        assertEquals(config1.getAckPolicy(), config2.getAckPolicy());
        assertEquals(config1.getAckWait(), config2.getAckWait());
        assertEquals(config1.getMaxDeliver(), config2.getMaxDeliver());
        assertEquals(config1.getMaxAckPending(), config2.getMaxAckPending());
    }

    @Test
    void generateDurableConsumerName_WithValidSubject_ShouldGenerateCorrectName() {
        // Given
        String subject = "orders.processing";

        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);

        // Then
        assertEquals("pull-consumer-orders-processing", consumerName);
    }

    @Test
    void generateDurableConsumerName_WithSingleWordSubject_ShouldGenerateCorrectName() {
        // Given
        String subject = "events";

        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);

        // Then
        assertEquals("pull-consumer-events", consumerName);
    }

    @Test
    void generateDurableConsumerName_WithMultipleDotsInSubject_ShouldReplaceAllDots() {
        // Given
        String subject = "company.department.team.project.events";

        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);

        // Then
        assertEquals("pull-consumer-company-department-team-project-events", consumerName);
        assertFalse(consumerName.contains("."));
    }

    @Test
    void generateDurableConsumerName_WithEmptySubject_ShouldHandleGracefully() {
        // Given
        String subject = "";

        // When
        String consumerName = configFactory.generateDurableConsumerName(subject);

        // Then
        assertEquals("pull-consumer-", consumerName);
    }

    @Test
    void createPullConsumerConfig_ConsistencyBetweenNameAndDurable_ShouldMatch() {
        // Given
        String subject = "test.consistency";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);
        String expectedName = configFactory.generateDurableConsumerName(subject);

        // Then
        assertEquals(expectedName, config.getName());
        assertEquals(expectedName, config.getDurable());
        assertEquals(config.getName(), config.getDurable()); // Name and durable should be identical
    }

    @Test
    void createPullConsumerConfig_ShouldHaveCorrectDefaultValues() {
        // Given
        String subject = "test.defaults";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        // Test all expected default values
        assertEquals(DeliverPolicy.New, config.getDeliverPolicy());
        assertEquals(AckPolicy.Explicit, config.getAckPolicy());
        assertEquals(Duration.ofSeconds(30), config.getAckWait());
        assertEquals(3, config.getMaxDeliver());
        assertEquals(1000, config.getMaxAckPending());

        // Ensure durable consumer is properly configured
        assertNotNull(config.getName());
        assertNotNull(config.getDurable());
        assertEquals(config.getName(), config.getDurable());
    }

    @Test
    void createPullConsumerConfig_DifferentSubjects_ShouldHaveDifferentNames() {
        // Given
        String subject1 = "users.created";
        String subject2 = "orders.updated";

        // When
        ConsumerConfiguration config1 = configFactory.createPullConsumerConfig(subject1);
        ConsumerConfiguration config2 = configFactory.createPullConsumerConfig(subject2);

        // Then
        assertNotEquals(config1.getName(), config2.getName());
        assertNotEquals(config1.getDurable(), config2.getDurable());

        // But should have same other properties
        assertEquals(config1.getDeliverPolicy(), config2.getDeliverPolicy());
        assertEquals(config1.getAckPolicy(), config2.getAckPolicy());
        assertEquals(config1.getAckWait(), config2.getAckWait());
        assertEquals(config1.getMaxDeliver(), config2.getMaxDeliver());
        assertEquals(config1.getMaxAckPending(), config2.getMaxAckPending());
    }

    @Test
    void createPullConsumerConfig_WithSpecialCharacters_ShouldHandleCorrectly() {
        // Given
        String subject = "test.subject-with_special.chars";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        assertNotNull(config);
        // 应该只替换点号
        assertEquals("pull-consumer-test-subject-with_special-chars", config.getName());
    }

    @Test
    void createPullConsumerConfig_WithVeryLongSubject_ShouldHandleCorrectly() {
        // Given
        String subject = "very.long.subject.name.with.many.dots.in.it.for.testing.purposes";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        assertNotNull(config);
        String expectedName = "pull-consumer-very-long-subject-name-with-many-dots-in-it-for-testing-purposes";
        assertEquals(expectedName, config.getName());
        assertEquals(expectedName, config.getDurable());
        assertFalse(config.getName().contains("."));
    }

    @Test
    void createPullConsumerConfig_AckPolicyIsExplicit_ShouldBeConfiguredCorrectly() {
        // Given
        String subject = "test.ack.policy";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        // Pull Consumer 必须使用显式 ACK
        assertEquals(AckPolicy.Explicit, config.getAckPolicy());
    }

    @Test
    void createPullConsumerConfig_MaxAckPending_ShouldBeSetCorrectly() {
        // Given
        String subject = "test.max.ack.pending";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        // 验证最大未确认消息数
        assertEquals(1000, config.getMaxAckPending());
        assertTrue(config.getMaxAckPending() > 0);
    }

    @Test
    void generateDurableConsumerName_WithNullSubject_ShouldHandleGracefully() {
        // When & Then
        // 虽然不应该传入 null，但测试健壮性
        assertThrows(NullPointerException.class, () -> {
            configFactory.generateDurableConsumerName(null);
        });
    }

    @Test
    void createPullConsumerConfig_DeliverPolicyNew_ShouldOnlyGetNewMessages() {
        // Given
        String subject = "test.deliver.new";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        // Pull Consumer 应该配置为只接收新消息，不重放历史
        assertEquals(DeliverPolicy.New, config.getDeliverPolicy());
    }

    @Test
    void createPullConsumerConfig_AckWaitTimeout_ShouldBeReasonable() {
        // Given
        String subject = "test.ack.wait";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        // 验证 ACK 等待时间是合理的（30秒）
        assertEquals(Duration.ofSeconds(30), config.getAckWait());
        assertTrue(config.getAckWait().getSeconds() > 0);
        assertTrue(config.getAckWait().getSeconds() <= 60); // 不超过1分钟
    }

    @Test
    void createPullConsumerConfig_MaxDeliver_ShouldPreventInfiniteRetries() {
        // Given
        String subject = "test.max.deliver";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);

        // Then
        // 验证最大投递次数，防止无限重试
        assertEquals(3, config.getMaxDeliver());
        assertTrue(config.getMaxDeliver() > 0);
        assertTrue(config.getMaxDeliver() < 10); // 合理的重试次数
    }

    @Test
    void createPullConsumerConfig_ConsumerNameFormat_ShouldFollowConvention() {
        // Given
        String subject = "orders.processing";

        // When
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);
        String consumerName = config.getName();

        // Then
        // 验证命名约定
        assertTrue(consumerName.startsWith("pull-consumer-"));
        assertTrue(consumerName.contains("orders"));
        assertTrue(consumerName.contains("processing"));
        assertFalse(consumerName.contains("."));
    }

    @Test
    void createPullConsumerConfig_ImmutabilityTest_MultipleCalls() {
        // Given
        String subject = "test.immutability";

        // When
        ConsumerConfiguration config1 = configFactory.createPullConsumerConfig(subject);
        ConsumerConfiguration config2 = configFactory.createPullConsumerConfig(subject);

        // Then
        // 虽然是不同的对象，但配置应该完全相同
        assertEquals(config1.getName(), config2.getName());
        assertEquals(config1.getDurable(), config2.getDurable());
        assertEquals(config1.getDeliverPolicy(), config2.getDeliverPolicy());
        assertEquals(config1.getAckPolicy(), config2.getAckPolicy());
        assertEquals(config1.getAckWait(), config2.getAckWait());
        assertEquals(config1.getMaxDeliver(), config2.getMaxDeliver());
        assertEquals(config1.getMaxAckPending(), config2.getMaxAckPending());
    }
}