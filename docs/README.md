# NATS Client Documentation

This documentation provides comprehensive coverage of the NATS Client application, organized into three main sections:

## 📖 Documentation Sections

### 1. [API Documentation](API_DOCUMENTATION.md)
Complete REST API reference including:
- **Endpoints**: All available REST endpoints with request/response examples
- **Request Formats**: JSON schemas and validation requirements
- **Response Formats**: Success and error response structures
- **Status Codes**: HTTP status codes and their meanings
- **Usage Examples**: cURL commands and integration examples
- **Error Handling**: Common error scenarios and troubleshooting

### 2. [Architecture Documentation](ARCHITECTURE.md)
Technical architecture and design patterns:
- **Core Architecture**: High-level system design and component interaction
- **Distributed System Design**: Multi-pod coordination and failover mechanisms
- **Data Flow**: Message publishing, response correlation, and recovery flows
- **Database Design**: Entity relationships and transaction patterns
- **Configuration Management**: Environment-specific configurations
- **Monitoring & Observability**: Metrics, logging, and health checks
- **Security Considerations**: Current security measures and future enhancements
- **Scalability Architecture**: Horizontal and vertical scaling strategies

### 2.1 [Architecture Diagrams](ARCHITECTURE_DIAGRAMS.md)
Visual architecture representations using Mermaid diagrams:
- **System Architecture**: Overall system components and relationships
- **Application Internal Architecture**: Detailed internal component structure
- **Message Flow Architecture**: Request-response sequence diagrams
- **Distributed Coordination**: Multi-pod coordination patterns
- **Database Design**: Entity relationship diagrams
- **Event-Driven Architecture**: Event flow and observer patterns
- **Configuration Management**: Configuration hierarchy and sources
- **Deployment Architecture**: Development vs. production deployments

### 3. [Project Structure Documentation](PROJECT_STRUCTURE.md)
Detailed codebase organization and patterns:
- **Package Organization**: Complete directory structure and responsibilities
- **Service Layer Architecture**: Business logic organization and patterns
- **Design Patterns**: Implementation of Strategy, Observer, Factory patterns
- **Test Structure**: Unit and integration test organization
- **Configuration Files**: Application properties and environment setup
- **Build Configuration**: Maven setup and deployment configuration
- **Extension Points**: How to add new features and customizations

## 🚀 Quick Start

1. **API Usage**: Start with [API Documentation](API_DOCUMENTATION.md) for immediate integration
2. **Architecture Understanding**: Review [Architecture Documentation](ARCHITECTURE.md) for system design
3. **Development**: Reference [Project Structure Documentation](PROJECT_STRUCTURE.md) for code organization

## 🔧 Technology Stack

- **Framework**: Spring Boot 3.5.0
- **Message Broker**: NATS JetStream
- **Database**: Oracle
- **Containerization**: Docker + Kubernetes
- **Documentation**: OpenAPI 3.0 (Swagger)
- **Testing**: JUnit 5 + Mockito
- **Build Tool**: Maven 3.9.6

## 🏗️ Key Features

- **Distributed Message Publishing**: High-availability NATS message publishing
- **Response Correlation**: Automatic request-response correlation
- **Listener Recovery**: Distributed listener recovery across pod restarts
- **Request Tracking**: Complete request lifecycle tracking
- **Health Monitoring**: Comprehensive health checks and metrics
- **Kubernetes Ready**: Production-ready Kubernetes deployment

## 📊 Monitoring

- **Health Endpoint**: `/api/nats/health`
- **Statistics**: `/api/nats/statistics`
- **Listener Status**: `/api/nats/listeners/status`
- **Metrics Collection**: Prometheus-compatible metrics
- **Structured Logging**: JSON-formatted logs for aggregation

## 🔒 Security

- Input validation on all endpoints
- Secure error handling
- Database query parameterization
- Network security for internal communication
- Audit trail for all operations

## 🚢 Deployment

The application is designed for Kubernetes deployment with:
- Multiple replica support
- Distributed coordination
- Automatic failover
- Rolling updates
- Health-based load balancing

---

**Version**: 1.0.0  
**Last Updated**: 2025-09-01  
**Maintained By**: NATS Client Development Team