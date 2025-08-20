# NATS Client Service

A production-ready Spring Boot microservice that provides robust NATS messaging capabilities with Oracle database integration, comprehensive error handling, and monitoring features.

## 🚀 Features

- **Enterprise NATS Integration**: High-performance messaging with request-response and publish-subscribe patterns
- **Oracle Database Persistence**: Complete audit trail with request/response logging and statistics
- **Advanced Error Handling**: Comprehensive exception management with intelligent retry mechanisms
- **Security & Authentication**: Multiple NATS authentication methods with TLS support
- **Monitoring & Observability**: Real-time metrics, health checks, and detailed logging
- **Kubernetes Ready**: Production deployment configurations with secrets management
- **High Availability**: Connection pooling, retry logic, and graceful degradation

## 🏗️ Architecture

```
┌─────────────────┐    ┌──────────────┐    ┌─────────────┐
│   REST API      │────│ NATS Client  │────│ NATS Server │
│                 │    │   Service    │    │             │
└─────────────────┘    └──────┬───────┘    └─────────────┘
                              │
                              ▼
                       ┌──────────────┐
                       │ Oracle DB    │
                       │ (Audit Log)  │
                       └──────────────┘
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
curl http://localhost:8080/api/nats/health
```

## 🧪 Testing

### Unit Tests
```bash
./apache-maven-3.9.6/bin/mvn test
```

### Integration Tests
```bash
./apache-maven-3.9.6/bin/mvn test -Dtest="*IntegrationTest"
```

### API Testing
Use the provided `test-api.http` file with your HTTP client, or:
```bash
# Health check
curl http://localhost:8080/api/nats/health

# Echo test
curl -X POST http://localhost:8080/api/nats/test/echo \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello NATS!"}'
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
- `/api/nats/health` - Service health status
- `/api/nats/statistics` - Real-time statistics
- `/actuator/health` - Spring Boot health checks

### Metrics
- Request/response counts
- Error rates and types
- Response times
- Connection status

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

- **0.0.1-SNAPSHOT**: Initial release with basic NATS integration
- Current: Enhanced error handling, monitoring, and Kubernetes support