# NATS Client Service

A production-ready Spring Boot microservice that provides robust NATS messaging capabilities with Oracle database integration, comprehensive error handling, and monitoring features. Built with enterprise-grade patterns including Template Method, Observer, and Factory patterns for enhanced maintainability and scalability.

## 🚀 Features

### Core NATS Integration
- **Dual Service Architecture**: Original `NatsMessageServiceImpl` and Enhanced `EnhancedNatsMessageService` with advanced features
- **JetStream Support**: Reliable message streaming with at-least-once delivery guarantees
- **Hybrid Operations**: NATS Core for request-response, JetStream for publish operations
- **Template Method Pattern**: Specialized processors for requests (`NatsRequestProcessor`) and publishing (`NatsPublishProcessor`)

### Advanced Messaging Features  
- **Request-Response Pattern**: Synchronous communication with timeout handling
- **Publish-Subscribe Pattern**: Asynchronous message broadcasting
- **Message Event System**: Observer pattern with `NatsEventPublisher` for event-driven architecture
- **Smart Routing**: Subject-based message routing and processing

### Enterprise Monitoring & Metrics
- **Micrometer Integration**: Request counters, success rates, error tracking, and response time metrics
- **Custom Metrics Factory**: Centralized metrics creation and management
- **Performance Testing**: Built-in load testing and memory stress testing capabilities
- **Real-time Statistics**: Live request/response statistics and success rates

### Database & Persistence
- **Oracle Database Integration**: Complete audit trail with request/response logging
- **Request Log Service**: Comprehensive tracking of all NATS operations
- **Database Statistics**: Query-based metrics and reporting
- **Transactional Support**: ACID compliance for all database operations

### Security & Authentication
- **Multiple Auth Methods**: Username/password, token-based, and credential file authentication
- **TLS/SSL Support**: Encrypted connections for production environments
- **Input Validation**: Comprehensive request validation with custom validators
- **Correlation ID Tracking**: End-to-end request tracing

### Deployment & Operations
- **Kubernetes Ready**: Complete K8s deployment with ConfigMaps, Secrets, and health probes
- **Docker Support**: Multi-stage builds with optimized container images
- **Health Checks**: Liveness and readiness probes for K8s orchestration
- **Graceful Shutdown**: Proper resource cleanup and connection management

## 🏗️ Architecture

### High-Level Architecture
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST API      │────│ Enhanced NATS    │────│ NATS Server     │
│   Controller    │    │ Message Service  │    │ + JetStream     │
└─────────────────┘    └────────┬─────────┘    └─────────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │    Template Method   │
                    │      Processors      │
                    │ ┌──────────────────┐ │
                    │ │ NatsRequest      │ │
                    │ │ Processor        │ │
                    │ └──────────────────┘ │
                    │ ┌──────────────────┐ │
                    │ │ NatsPublish      │ │
                    │ │ Processor        │ │
                    │ └──────────────────┘ │
                    └────────┬─────────────┘
                             │
                    ┌────────▼─────────────┐
                    │  Monitoring &        │
                    │  Event System        │
                    │ ┌──────────────────┐ │
                    │ │ Metrics Factory  │ │
                    │ │ Event Publisher  │ │
                    │ │ Request Logger   │ │
                    │ └──────────────────┘ │
                    └────────┬─────────────┘
                             │
                             ▼
                    ┌──────────────────────┐
                    │    Oracle Database   │
                    │  (Audit & Metrics)   │
                    └──────────────────────┘
```

### Component Architecture
```
┌─────────────────────────────────────────┐
│               NATS Client               │
├─────────────────┬───────────────────────┤
│  Original       │  Enhanced Service     │
│  Service        │                       │
│                 │  ┌─────────────────┐  │
│  ┌─────────────┐│  │ Template Method │  │
│  │ Basic NATS  ││  │   Processors    │  │
│  │ Operations  ││  │                 │  │
│  └─────────────┘│  │ • Request Proc  │  │
│                 │  │ • Publish Proc  │  │
│                 │  └─────────────────┘  │
├─────────────────┼───────────────────────┤
│     Hybrid NATS Operations              │
│  ┌─────────────┬─────────────────────┐  │
│  │ NATS Core   │    JetStream        │  │
│  │ (Request)   │    (Publish)        │  │
│  └─────────────┴─────────────────────┘  │
└─────────────────────────────────────────┘
```

## 📋 Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.6+ (included in project)
- **NATS Server**: 2.x
- **Oracle Database**: 12c or higher
- **Docker** (for containerized deployment)
- **Kubernetes** (for K8s deployment)

## 🚀 Quick Start

### 1. Clone and Setup
```bash
git clone <repository-url>
cd nats-client
```

### 2. Database Setup
Execute the database schema:
```sql
-- Run the SQL script in src/main/resources/schema.sql
```

### 3. Configuration
Update `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe
    username: your_username
    password: your_password

nats:
  url: nats://localhost:4222
```

### 4. Run Application
```bash
# Using Maven wrapper (recommended)
./apache-maven-3.9.6/bin/mvn spring-boot:run

# Or with profile
./apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 5. Verify Installation
```bash
curl http://localhost:8080/actuator/health
```

## 🧪 Testing

### Unit Tests
```bash
# Run all tests
./apache-maven-3.9.6/bin/mvn test

# Run specific test classes
./apache-maven-3.9.6/bin/mvn test -Dtest="NatsMessageServiceImplTest"
./apache-maven-3.9.6/bin/mvn test -Dtest="EnhancedNatsMessageServiceTest"
```

### Integration Tests
```bash
./apache-maven-3.9.6/bin/mvn test -Dtest="NatsIntegrationTest"
```

### Performance Tests
```bash
./apache-maven-3.9.6/bin/mvn test -Dtest="NatsPerformanceTest"
```

### API Testing
Use the provided `test-api.http` file with your HTTP client, or:
```bash
# Health check
curl http://localhost:8080/actuator/health

# NATS request with correlation ID
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{"subject":"test.api","payload":"Hello NATS!","correlationId":"test-123"}'

# NATS publish
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{"subject":"test.publish","payload":"Broadcast message"}'

# Check request status
curl http://localhost:8080/api/nats/status/test-123

# View statistics
curl http://localhost:8080/api/nats/statistics
```

## 🐳 Deployment

### Docker Compose (Recommended)
```bash
docker-compose -f docker-compose-with-app.yml up -d
```

### Kubernetes
```bash
kubectl apply -f k8s-deploy-all.yml
```

## 📚 Documentation

- [**Testing Guide**](docs/TESTING.md) - Comprehensive testing strategies and examples
- [**Deployment Guide**](docs/DEPLOYMENT.md) - Production deployment instructions
- [**API Documentation**](docs/API.md) - Complete API reference
- [**Development Setup**](docs/DEVELOPMENT.md) - Local development environment setup
- [**Troubleshooting**](docs/TROUBLESHOOTING.md) - Common issues and solutions

## 🔧 Configuration

### Environment Variables
| Variable | Description | Default |
|----------|-------------|---------|
| `DB_USERNAME` | Database username | `your_username` |
| `DB_PASSWORD` | Database password | `your_password` |
| `DB_HOST` | Database host | `localhost` |
| `NATS_URL` | NATS server URL | `nats://localhost:4222` |
| `NATS_USERNAME` | NATS username | - |
| `NATS_PASSWORD` | NATS password | - |
| `NATS_TOKEN` | NATS token | - |

### Profiles
- **default**: Standard configuration
- **local**: Local development with relaxed security
- **kubernetes**: Production K8s deployment

## 📊 Monitoring

### Health Endpoints
- `/actuator/health` - Application health with liveness/readiness probes
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/api/nats/statistics` - Real-time NATS statistics and metrics

### Available Metrics (via Micrometer)
- **Request Metrics**: Total requests, pending requests, success/failure counts
- **Performance Metrics**: Response times, processing latencies, throughput rates
- **Error Metrics**: Timeout requests, validation errors, NATS connection failures  
- **Success Rate**: Real-time calculation of successful operations percentage
- **JetStream Metrics**: Message sequence numbers, stream acknowledgments

### Statistics API Response
```json
{
  "totalRequests": 150,
  "pendingRequests": 2,
  "successfulRequests": 135,
  "failedRequests": 8,
  "timeoutRequests": 5,
  "errorRequests": 2,
  "successRate": 90.0
}
```

### Custom Metrics Dashboard
The application includes built-in performance testing and metrics collection:
- Concurrent request handling validation
- Memory usage monitoring and leak detection
- Large payload processing benchmarks
- Stress testing with configurable load patterns

## 🔒 Security

### Authentication Methods
- Username/Password
- Token-based
- Credential files
- TLS/SSL encryption

### Best Practices
- Credentials stored as environment variables
- TLS encryption for production
- Request validation and sanitization
- Comprehensive audit logging

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Run tests (`./apache-maven-3.9.6/bin/mvn test`)
4. Commit changes (`git commit -m 'Add amazing feature'`)
5. Push to branch (`git push origin feature/amazing-feature`)
6. Create Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Documentation**: Check the `docs/` directory
- **Issues**: Create GitHub issues for bugs
- **Questions**: Use GitHub Discussions

## 🔄 Version History

### **0.0.1-SNAPSHOT** (Current)
#### **Latest Features (2025-08-23)**
- **Template Method Pattern Implementation**: Specialized processors for request/publish operations
- **Enhanced NATS Service**: Advanced service with Micrometer metrics and event publishing
- **Observer Pattern**: `NatsEventPublisher` for event-driven architecture  
- **Factory Pattern**: `MetricsFactory` for centralized metrics management
- **JetStream Integration**: Reliable message streaming with persistence
- **Hybrid Operations**: Dual-mode NATS Core + JetStream operations

#### **Recent Improvements**
- **Performance Testing Suite**: `NatsPerformanceTest` with concurrent load testing
- **Memory Stress Testing**: Leak detection and resource management validation
- **Comprehensive Unit Tests**: 100+ test cases with mock-based testing
- **Enhanced Error Handling**: Custom exceptions with detailed error context
- **Kubernetes Deployment**: Complete K8s configuration with health probes

#### **Architecture Enhancements**  
- **Dual Service Design**: Original service + Enhanced service for backward compatibility
- **Specialized Processors**: `NatsRequestProcessor` and `NatsPublishProcessor`
- **Advanced Monitoring**: Real-time statistics, success rates, and performance metrics
- **Database Integration**: Oracle DB with comprehensive audit logging
- **Correlation ID Tracking**: End-to-end request tracing and monitoring

#### **Previous Versions**
- **v0.0.1-alpha**: Basic NATS integration with Oracle database
- **v0.0.1-beta**: Added Kubernetes support and containerization
- **v0.0.1-rc**: Error handling improvements and monitoring enhancements