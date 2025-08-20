# Development Setup Guide

Complete guide for setting up the local development environment for the NATS Client Service.

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [IDE Configuration](#ide-configuration)
- [Database Setup](#database-setup)
- [NATS Server Setup](#nats-server-setup)
- [Application Configuration](#application-configuration)
- [Development Workflow](#development-workflow)
- [Code Quality](#code-quality)
- [Debugging](#debugging)
- [Performance Profiling](#performance-profiling)

## 🔧 Prerequisites

### Required Software

#### Java Development Kit
```bash
# Install Java 17 or higher
# Verify installation
java -version
javac -version
```

#### Docker & Docker Compose
```bash
# Install Docker Desktop
# Verify installation
docker --version
docker-compose --version

# Test Docker
docker run hello-world
```

#### Git
```bash
# Verify Git installation
git --version

# Configure Git (first-time setup)
git config --global user.name "Your Name"
git config --global user.email "your.email@company.com"
```

#### Optional Tools
- **IntelliJ IDEA** / **Eclipse** / **VS Code**
- **Postman** / **Insomnia** for API testing
- **Docker Desktop** for container management
- **DBeaver** / **SQL Developer** for database access

### System Requirements
- **RAM**: Minimum 8GB, Recommended 16GB
- **CPU**: 4+ cores recommended
- **Disk**: 10GB free space
- **OS**: Windows 10/11, macOS 10.15+, Ubuntu 18.04+

## 🌟 Environment Setup

### 1. Clone Repository
```bash
# Clone the repository
git clone <repository-url>
cd nats-client

# Verify project structure
ls -la
```

### 2. Environment Variables
Create environment configuration files:

#### `.env` (for local development)
```env
# Database Configuration
DB_HOST=localhost
DB_PORT=1521
DB_SID=xe
DB_USERNAME=system
DB_PASSWORD=oracle123

# NATS Configuration
NATS_URL=nats://localhost:14222
NATS_USERNAME=
NATS_PASSWORD=
NATS_TOKEN=

# Application Configuration
SPRING_PROFILES_ACTIVE=local
LOG_LEVEL=DEBUG

# Development Settings
SPRING_DEVTOOLS_RESTART_ENABLED=true
SPRING_DEVTOOLS_LIVERELOAD_ENABLED=true
```

#### `setenv.bat` (Windows)
```batch
@echo off
set DB_HOST=localhost
set DB_PORT=1521
set DB_SID=xe
set DB_USERNAME=system
set DB_PASSWORD=oracle123
set NATS_URL=nats://localhost:14222
set SPRING_PROFILES_ACTIVE=local
set LOG_LEVEL=DEBUG
echo Environment variables set for local development
```

#### `setenv.sh` (Linux/macOS)
```bash
#!/bin/bash
export DB_HOST=localhost
export DB_PORT=1521
export DB_SID=xe
export DB_USERNAME=system
export DB_PASSWORD=oracle123
export NATS_URL=nats://localhost:14222
export SPRING_PROFILES_ACTIVE=local
export LOG_LEVEL=DEBUG
echo "Environment variables set for local development"
```

### 3. Make Scripts Executable
```bash
# Linux/macOS
chmod +x setenv.sh
chmod +x apache-maven-3.9.6/bin/mvn

# Windows - no action needed
```

## 🎯 IDE Configuration

### IntelliJ IDEA Setup

#### 1. Import Project
```
File → Open → Select nats-client directory → Import as Maven project
```

#### 2. Configure SDK
```
File → Project Structure → Project → Project SDK → Add SDK → Java 17
```

#### 3. Enable Annotation Processing
```
Settings → Build → Compiler → Annotation Processors → Enable annotation processing
```

#### 4. Install Plugins
- **Lombok Plugin**
- **Spring Boot Plugin**
- **Docker Plugin**
- **Database Navigator**

#### 5. Run Configuration
Create a new Spring Boot run configuration:
```
Main Class: com.example.natsclient.NatsClientApplication
VM Options: -Dspring.profiles.active=local
Environment Variables: (load from .env)
```

### VS Code Setup

#### 1. Install Extensions
```json
{
  "recommendations": [
    "redhat.java",
    "vscjava.vscode-java-pack",
    "pivotal.vscode-spring-boot",
    "ms-vscode.vscode-docker",
    "oracle.oracle-developer-tools"
  ]
}
```

#### 2. Configure Settings
Create `.vscode/settings.json`:
```json
{
  "java.home": "/path/to/java17",
  "java.configuration.runtimes": [
    {
      "name": "JavaSE-17",
      "path": "/path/to/java17"
    }
  ],
  "spring-boot.ls.problem.application-properties.enabled": true,
  "java.compile.nullAnalysis.mode": "automatic"
}
```

#### 3. Launch Configuration
Create `.vscode/launch.json`:
```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Launch NatsClientApplication",
      "request": "launch",
      "mainClass": "com.example.natsclient.NatsClientApplication",
      "projectName": "nats-client",
      "args": "--spring.profiles.active=local",
      "env": {
        "DB_HOST": "localhost",
        "NATS_URL": "nats://localhost:14222"
      }
    }
  ]
}
```

## 🗄️ Database Setup

### Option 1: Docker Oracle (Recommended)
```bash
# Start Oracle in Docker
docker run -d \
  --name oracle-dev \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=oracle123 \
  gvenzl/oracle-xe:latest

# Wait for database to be ready
docker logs -f oracle-dev

# Connect to database
docker exec -it oracle-dev sqlplus system/oracle123@localhost:1521/xe
```

### Option 2: Local Oracle Installation
1. Download Oracle Database XE
2. Install with system/oracle123 credentials
3. Configure listener on port 1521

### Database Schema Setup
```bash
# Connect to database
sqlplus system/oracle123@localhost:1521:xe

# Run schema creation script
@src/main/resources/schema.sql

# Verify tables
SELECT table_name FROM user_tables;
```

### Test Database Connection
```sql
-- Test connection
SELECT 1 FROM DUAL;

-- Check NATS request log table
DESC nats_request_log;
SELECT COUNT(*) FROM nats_request_log;
```

## 🚀 NATS Server Setup

### Option 1: Docker NATS (Recommended)
```bash
# Start NATS server with monitoring
docker run -d \
  --name nats-dev \
  -p 4222:4222 \
  -p 8222:8222 \
  -p 6222:6222 \
  nats:latest \
  --http_port 8222 \
  --port 4222

# Verify NATS is running
curl http://localhost:8222/varz
```

### Option 2: Local NATS Installation
```bash
# Download NATS server
curl -L https://github.com/nats-io/nats-server/releases/download/v2.10.7/nats-server-v2.10.7-linux-amd64.zip -o nats-server.zip
unzip nats-server.zip

# Run NATS server
./nats-server --port 4222 --http_port 8222
```

### NATS CLI Setup
```bash
# Install NATS CLI
curl -sf https://binaries.nats.dev/nats-io/natscli/nats@latest | sh

# Add to PATH
export PATH=$PATH:~/.nats

# Test NATS connection
nats server check connection
```

### Test NATS Communication
```bash
# Terminal 1 - Subscribe
nats sub test.subject

# Terminal 2 - Publish
nats pub test.subject "Hello NATS"

# Terminal 3 - Request/Reply
nats reply test.echo "Echo: {{.Data}}"
nats req test.echo "Hello Request"
```

## ⚙️ Application Configuration

### Local Development Profile
Update `src/main/resources/application.yml`:
```yaml
---
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe
    username: system
    password: oracle123
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update

nats:
  url: nats://localhost:14222

logging:
  level:
    com.example.natsclient: DEBUG
    org.springframework: INFO
    org.hibernate.SQL: DEBUG

# Enable development tools
spring:
  devtools:
    restart:
      enabled: true
    livereload:
      enabled: true
```

### Development-Specific Features
```yaml
# Hot reload configuration
spring:
  devtools:
    restart:
      enabled: true
      additional-paths:
        - src/main/java
        - src/main/resources
    livereload:
      enabled: true
      port: 35729

# Relaxed security for development
management:
  endpoints:
    web:
      exposure:
        include: "*"
  security:
    enabled: false
```

## 🔄 Development Workflow

### 1. Daily Setup
```bash
# Source environment variables
source setenv.sh  # Linux/macOS
# or
setenv.bat  # Windows

# Start infrastructure
docker-compose -f docker-compose-with-app.yml up -d nats oracle-db

# Verify services
docker-compose -f docker-compose-with-app.yml ps
curl http://localhost:8222/varz  # NATS
```

### 2. Build and Run
```bash
# Clean and compile
./apache-maven-3.9.6/bin/mvn clean compile

# Run tests
./apache-maven-3.9.6/bin/mvn test

# Start application
./apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Hot Reload Development
```bash
# Enable Spring Boot DevTools hot reload
./apache-maven-3.9.6/bin/mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring.devtools.restart.enabled=true
```

### 4. Code Generation
```bash
# Generate Lombok code (if needed)
./apache-maven-3.9.6/bin/mvn compile

# Generate sources and update IDE
./apache-maven-3.9.6/bin/mvn generate-sources
```

### 5. Database Migration
```bash
# Run database updates
./apache-maven-3.9.6/bin/mvn flyway:migrate

# Reset database (development only)
./apache-maven-3.9.6/bin/mvn flyway:clean flyway:migrate
```

## 🔍 Code Quality

### Code Formatting
```bash
# Format code with Google Java Format
./apache-maven-3.9.6/bin/mvn fmt:format

# Check code formatting
./apache-maven-3.9.6/bin/mvn fmt:check
```

### Static Code Analysis
```bash
# Run SpotBugs
./apache-maven-3.9.6/bin/mvn spotbugs:check

# Run Checkstyle
./apache-maven-3.9.6/bin/mvn checkstyle:check

# Run PMD
./apache-maven-3.9.6/bin/mvn pmd:check
```

### Code Coverage
```bash
# Generate code coverage report
./apache-maven-3.9.6/bin/mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Pre-commit Hooks
Create `.git/hooks/pre-commit`:
```bash
#!/bin/bash
echo "Running pre-commit checks..."

# Format code
./apache-maven-3.9.6/bin/mvn fmt:format

# Run tests
./apache-maven-3.9.6/bin/mvn test

# Check code coverage
./apache-maven-3.9.6/bin/mvn jacoco:check

echo "Pre-commit checks completed successfully"
```

## 🐛 Debugging

### Application Debugging
```bash
# Run with debug mode
./apache-maven-3.9.6/bin/mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

### IDE Debug Configuration
**IntelliJ IDEA:**
```
Run → Edit Configurations → Add Remote JVM Debug
Host: localhost
Port: 5005
```

### Logging Configuration
```yaml
# Enhanced logging for debugging
logging:
  level:
    com.example.natsclient: TRACE
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE
    io.nats: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/nats-client-dev.log
```

### Debug Utilities
```java
// Add to service classes for debugging
@Slf4j
public class DebugUtils {
    
    public static void logRequest(String operation, Object request) {
        log.debug("🔵 {} Request: {}", operation, 
            JsonUtils.toJsonString(request));
    }
    
    public static void logResponse(String operation, Object response, long duration) {
        log.debug("🟢 {} Response ({}ms): {}", operation, duration,
            JsonUtils.toJsonString(response));
    }
    
    public static void logError(String operation, Exception error) {
        log.error("🔴 {} Error: {}", operation, error.getMessage(), error);
    }
}
```

## 📊 Performance Profiling

### JVM Monitoring
```bash
# Monitor JVM with JConsole
jconsole

# Monitor with VisualVM
visualvm

# Generate heap dump
jcmd <pid> GC.run_finalization
jcmd <pid> VM.classloader_stats
```

### Application Metrics
```bash
# Enable Micrometer metrics
curl http://localhost:8080/actuator/metrics

# Specific metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/nats.requests.total
```

### Database Performance
```sql
-- Monitor active sessions
SELECT username, status, machine, program 
FROM v$session 
WHERE type = 'USER';

-- Check SQL execution plans
EXPLAIN PLAN FOR 
SELECT * FROM nats_request_log WHERE status = 'PENDING';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);
```

### NATS Performance
```bash
# NATS server statistics
curl http://localhost:8222/varz | jq

# Connection info
curl http://localhost:8222/connz | jq

# Subscription info
curl http://localhost:8222/subsz | jq
```

## 🧪 Local Testing

### Unit Tests
```bash
# Run all tests
./apache-maven-3.9.6/bin/mvn test

# Run specific test class
./apache-maven-3.9.6/bin/mvn test -Dtest=NatsMessageServiceImplTest

# Run with coverage
./apache-maven-3.9.6/bin/mvn test jacoco:report
```

### Integration Tests
```bash
# Run integration tests
./apache-maven-3.9.6/bin/mvn test -Dtest="*IntegrationTest"

# Run specific integration test
./apache-maven-3.9.6/bin/mvn test -Dtest=NatsIntegrationTest
```

### API Testing
```bash
# Test health endpoint
curl http://localhost:8080/api/nats/health

# Test echo endpoint
curl -X POST http://localhost:8080/api/nats/test/echo \
  -H "Content-Type: application/json" \
  -d '{"message":"Development test"}'

# Load test with Apache Bench
ab -n 1000 -c 10 http://localhost:8080/api/nats/health
```

## 🚀 Productivity Tips

### Useful Aliases
Add to your `.bashrc` or `.zshrc`:
```bash
# Maven shortcuts
alias mvn='./apache-maven-3.9.6/bin/mvn'
alias mrun='./apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local'
alias mtest='./apache-maven-3.9.6/bin/mvn test'
alias mclean='./apache-maven-3.9.6/bin/mvn clean compile'

# Docker shortcuts
alias dps='docker-compose -f docker-compose-with-app.yml ps'
alias dup='docker-compose -f docker-compose-with-app.yml up -d'
alias ddown='docker-compose -f docker-compose-with-app.yml down'
alias dlogs='docker-compose -f docker-compose-with-app.yml logs -f'

# Application shortcuts
alias nats-health='curl http://localhost:8080/api/nats/health'
alias nats-stats='curl http://localhost:8080/api/nats/statistics'
```

### Development Scripts
Create `scripts/dev-setup.sh`:
```bash
#!/bin/bash
echo "🚀 Starting NATS Client development environment..."

# Start infrastructure
docker-compose -f docker-compose-with-app.yml up -d nats oracle-db

# Wait for services to be ready
echo "⏳ Waiting for services to start..."
sleep 30

# Verify services
echo "✅ Checking NATS..."
curl -s http://localhost:8222/varz > /dev/null && echo "NATS is ready" || echo "NATS not ready"

echo "✅ Checking Oracle..."
docker exec oracle-dev sqlplus -S system/oracle123@localhost:1521/xe <<< "SELECT 1 FROM DUAL;" > /dev/null && echo "Oracle is ready" || echo "Oracle not ready"

echo "🎉 Development environment is ready!"
echo "Run: ./apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local"
```

This development guide provides everything needed to set up and maintain a productive local development environment for the NATS Client Service.