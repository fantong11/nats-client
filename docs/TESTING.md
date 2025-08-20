# Testing Guide

This document provides comprehensive testing strategies, examples, and best practices for the NATS Client Service.

## 📋 Table of Contents

- [Testing Strategy](#testing-strategy)
- [Unit Testing](#unit-testing)
- [Integration Testing](#integration-testing)
- [API Testing](#api-testing)
- [Performance Testing](#performance-testing)
- [Testing Best Practices](#testing-best-practices)
- [Test Data Management](#test-data-management)
- [CI/CD Testing](#cicd-testing)

## 🎯 Testing Strategy

### Testing Pyramid
```
    ┌─────────────┐
    │   E2E/UI    │ ← Few, Expensive, Slow
    └─────────────┘
  ┌───────────────────┐
  │   Integration     │ ← Some, Moderate Cost
  └───────────────────┘
┌─────────────────────────┐
│     Unit Tests          │ ← Many, Fast, Cheap
└─────────────────────────┘
```

### Test Categories
- **Unit Tests (70%)**: Individual component testing
- **Integration Tests (20%)**: Service interaction testing
- **End-to-End Tests (10%)**: Complete workflow testing

## 🔬 Unit Testing

### Running Unit Tests
```bash
# Run all unit tests
./apache-maven-3.9.6/bin/mvn test

# Run specific test class
./apache-maven-3.9.6/bin/mvn test -Dtest="NatsMessageServiceImplTest"

# Run tests with specific pattern
./apache-maven-3.9.6/bin/mvn test -Dtest="*Test"

# Run tests with coverage report
./apache-maven-3.9.6/bin/mvn test jacoco:report
```

### Unit Test Examples

#### Service Layer Testing
```java
@ExtendWith(MockitoExtension.class)
class NatsMessageServiceImplTest {
    
    @Mock
    private Connection natsConnection;
    
    @InjectMocks
    private NatsMessageServiceImpl natsMessageService;
    
    @Test
    void shouldSendRequestSuccessfully() {
        // Given
        String subject = "test.subject";
        String payload = "{\"test\":\"data\"}";
        
        // When & Then
        assertThatNoException().isThrownBy(() -> 
            natsMessageService.sendRequest(subject, payload)
        );
    }
}
```

#### Controller Testing
```java
@WebMvcTest(NatsController.class)
class NatsControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private NatsOrchestrationService orchestrationService;
    
    @Test
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/nats/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

### Test Coverage Requirements
- **Minimum**: 80% line coverage
- **Target**: 90% line coverage
- **Critical Components**: 95% coverage required

## 🔗 Integration Testing

### Running Integration Tests
```bash
# Run all integration tests
./apache-maven-3.9.6/bin/mvn test -Dtest="*IntegrationTest"

# Run specific integration test
./apache-maven-3.9.6/bin/mvn test -Dtest="NatsIntegrationTest"
```

### Integration Test Setup
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "nats.url=nats://localhost:4222"
})
class NatsIntegrationTest {
    
    @Autowired
    private NatsOrchestrationService orchestrationService;
    
    @Test
    void shouldProcessCompleteWorkflow() {
        // Test complete request-response workflow
    }
}
```

### Database Integration Tests
```java
@DataJpaTest
class NatsRequestLogRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private NatsRequestLogRepository repository;
    
    @Test
    void shouldSaveAndRetrieveRequestLog() {
        // Test database operations
    }
}
```

## 🌐 API Testing

### Using HTTP Client Files
The project includes `test-api.http` for manual API testing:

```http
### Health Check
GET http://localhost:8080/api/nats/health

### Echo Test
POST http://localhost:8080/api/nats/test/echo
Content-Type: application/json

{
  "message": "Hello NATS!",
  "metadata": "test from REST API"
}

### Generic Request
POST http://localhost:8080/api/nats/request
Content-Type: application/json

{
  "subject": "demo.user.create",
  "payload": {
    "username": "john_doe",
    "email": "john@example.com"
  }
}
```

### cURL Examples
```bash
# Health check
curl -X GET http://localhost:8080/api/nats/health

# Echo test
curl -X POST http://localhost:8080/api/nats/test/echo \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello NATS!","metadata":"test"}'

# Send request with timeout
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{"subject":"test.timeout","payload":{"data":"test"}}' \
  --max-time 35

# Get statistics
curl -X GET http://localhost:8080/api/nats/statistics
```

### Automated API Testing with REST Assured
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/nats";
    }
    
    @Test
    void shouldReturnHealthStatus() {
        given()
            .when()
                .get("/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
```

## ⚡ Performance Testing

### Load Testing with JMeter
```xml
<!-- test-plan.jmx -->
<TestPlan>
  <ThreadGroup num_threads="100" ramp_time="10" duration="60">
    <HTTPRequest method="POST" path="/api/nats/request">
      <body>{"subject":"load.test","payload":{"id":"${__counter(TRUE)}"}}</body>
    </HTTPRequest>
  </ThreadGroup>
</TestPlan>
```

### Stress Testing Script
```bash
#!/bin/bash
# stress-test.sh
for i in {1..1000}; do
  curl -X POST http://localhost:8080/api/nats/test/echo \
    -H "Content-Type: application/json" \
    -d "{\"message\":\"stress-test-$i\"}" &
done
wait
```

### Performance Benchmarks
- **Response Time**: < 100ms for 95th percentile
- **Throughput**: > 1000 requests/second
- **Resource Usage**: < 512MB memory under load
- **Connection Pool**: Handle 100 concurrent connections

## 🎯 Testing Best Practices

### Test Naming Convention
```java
// Pattern: should[Expected Behavior]When[State Under Test]
@Test
void shouldReturnErrorWhenNatsConnectionFails() { }

@Test
void shouldRetryRequestWhenTimeoutOccurs() { }

@Test
void shouldLogRequestWhenValidPayloadReceived() { }
```

### Test Organization
```
src/test/java/
├── com/example/natsclient/
│   ├── controller/
│   │   ├── NatsControllerTest.java
│   │   └── NatsControllerIntegrationTest.java
│   ├── service/
│   │   ├── impl/
│   │   │   ├── NatsMessageServiceImplTest.java
│   │   │   └── RequestLogServiceImplTest.java
│   │   └── validator/
│   │       └── RequestValidatorTest.java
│   ├── integration/
│   │   ├── NatsIntegrationTest.java
│   │   └── DatabaseIntegrationTest.java
│   └── performance/
│       └── NatsPerformanceTest.java
```

### Mock Configuration
```java
@TestConfiguration
public class TestConfig {
    
    @Bean
    @Primary
    public Connection mockNatsConnection() {
        return mock(Connection.class);
    }
    
    @Bean
    @Primary
    public DataSource testDataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("schema.sql")
            .build();
    }
}
```

## 🗄️ Test Data Management

### Test Data Setup
```java
@Component
public class TestDataBuilder {
    
    public static NatsRequestLog createTestRequestLog() {
        return NatsRequestLog.builder()
            .requestId(UUID.randomUUID().toString())
            .correlationId("test-correlation")
            .subject("test.subject")
            .payload("{\"test\":\"data\"}")
            .status(RequestStatus.PENDING)
            .createdAt(Instant.now())
            .build();
    }
}
```

### Database Test Data
```sql
-- test-data.sql
INSERT INTO nats_request_log (
    request_id, correlation_id, subject, payload, status, created_at
) VALUES (
    'test-001', 'corr-001', 'test.subject', '{"test":"data"}', 'SUCCESS', CURRENT_TIMESTAMP
);
```

### Environment-Specific Test Configuration
```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

nats:
  url: nats://localhost:4222
  connection:
    timeout: 5000
  request:
    timeout: 10000

logging:
  level:
    com.example.natsclient: INFO
```

## 🚀 CI/CD Testing

### Maven Test Configuration
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
        </includes>
        <excludes>
            <exclude>**/*IntegrationTest.java</exclude>
        </excludes>
    </configuration>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*IntegrationTest.java</include>
        </includes>
    </configuration>
</plugin>
```

### GitHub Actions Workflow
```yaml
name: Test Pipeline
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      nats:
        image: nats:latest
        ports:
          - 4222:4222
      oracle:
        image: gvenzl/oracle-xe:latest
        env:
          ORACLE_PASSWORD: oracle123
        ports:
          - 1521:1521
    
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Run Tests
        run: ./apache-maven-3.9.6/bin/mvn test
      
      - name: Run Integration Tests
        run: ./apache-maven-3.9.6/bin/mvn test -Dtest="*IntegrationTest"
      
      - name: Generate Test Report
        run: ./apache-maven-3.9.6/bin/mvn jacoco:report
```

## 📊 Test Reporting

### Coverage Reports
```bash
# Generate coverage report
./apache-maven-3.9.6/bin/mvn jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Test Metrics to Track
- **Coverage**: Line, branch, method coverage
- **Performance**: Response times, throughput
- **Reliability**: Test success rate, flakiness
- **Quality**: Code coverage trends, test maintainability

### Failure Analysis
```java
@Test
void shouldHandleNatsConnectionFailure() {
    // Given
    when(natsConnection.request(any(), any(), any()))
        .thenThrow(new IOException("Connection failed"));
    
    // When & Then
    assertThatThrownBy(() -> service.sendRequest("subject", "payload"))
        .isInstanceOf(NatsClientException.class)
        .hasMessageContaining("Connection failed");
}
```

## 🔍 Debugging Tests

### Test Debugging Tips
1. **Enable Debug Logging**: Set log level to DEBUG for test packages
2. **Use @DirtiesContext**: For tests affecting Spring context
3. **Isolate Tests**: Ensure tests don't depend on each other
4. **Mock External Dependencies**: Use WireMock for HTTP services
5. **Test Containers**: Use TestContainers for integration tests

### Common Test Issues
- **Timing Issues**: Use appropriate timeouts and retries
- **Resource Cleanup**: Ensure proper cleanup in @AfterEach
- **Port Conflicts**: Use random ports in integration tests
- **Database State**: Reset database state between tests

This testing guide ensures comprehensive coverage and reliable test execution across all components of the NATS Client Service.