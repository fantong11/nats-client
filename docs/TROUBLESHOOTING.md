# Troubleshooting Guide

Comprehensive troubleshooting guide for the NATS Client Service, covering common issues, diagnostic procedures, and solutions.

## 📋 Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Application Issues](#application-issues)
- [Database Issues](#database-issues)
- [NATS Connection Issues](#nats-connection-issues)
- [Performance Issues](#performance-issues)
- [Deployment Issues](#deployment-issues)
- [Monitoring & Logging](#monitoring--logging)
- [Emergency Procedures](#emergency-procedures)

## 🔍 Quick Diagnostics

### Health Check Command
```bash
# Quick health check
curl -s http://localhost:8080/api/nats/health | jq '.'

# Expected healthy response:
{
  "status": "UP",
  "components": {
    "nats": {"status": "UP"},
    "database": {"status": "UP"}
  }
}
```

### Service Status Check
```bash
# Check all components
curl -s http://localhost:8080/api/nats/health
curl -s http://localhost:8222/varz  # NATS server
docker ps | grep -E "(nats|oracle)"  # Docker containers
```

### Log Quick Check
```bash
# Application logs
tail -n 50 logs/nats-client.log

# Docker container logs
docker logs nats-client --tail 50
docker logs nats-dev --tail 50
docker logs oracle-dev --tail 50
```

## 🚨 Application Issues

### Issue: Application Won't Start

#### Symptoms
- Application fails to start
- Error messages in logs
- Port binding failures

#### Diagnostic Commands
```bash
# Check if port is in use
netstat -tulpn | grep :8080
lsof -i :8080

# Check Java version
java -version

# Check environment variables
echo $SPRING_PROFILES_ACTIVE
echo $DB_USERNAME
```

#### Common Solutions

**Port Already in Use**
```bash
# Find process using port 8080
netstat -tulpn | grep :8080
# Kill the process
kill -9 <PID>
# Or use different port
export SERVER_PORT=8081
```

**Java Version Issues**
```bash
# Verify Java 17+
java -version
javac -version

# Set JAVA_HOME if needed
export JAVA_HOME=/path/to/java17
```

**Missing Dependencies**
```bash
# Clean and reinstall dependencies
./apache-maven-3.9.6/bin/mvn clean install -U
```

### Issue: Spring Boot Application Context Fails

#### Symptoms
```
Error creating bean with name 'natsConfig'
Unable to connect to database
Configuration property binding failed
```

#### Solutions
```bash
# Check configuration
cat src/main/resources/application.yml

# Validate environment variables
env | grep -E "(DB_|NATS_)"

# Test with minimal profile
./apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=test
```

### Issue: OutOfMemoryError

#### Symptoms
```
java.lang.OutOfMemoryError: Java heap space
java.lang.OutOfMemoryError: Metaspace
```

#### Solutions
```bash
# Increase heap size
export MAVEN_OPTS="-Xmx2g -Xms1g"

# Run with increased memory
./apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g"

# Monitor memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

## 🗄️ Database Issues

### Issue: Database Connection Failed

#### Symptoms
```
java.sql.SQLException: IO Error: The Network Adapter could not establish the connection
org.springframework.jdbc.CannotGetJdbcConnectionException
```

#### Diagnostic Commands
```bash
# Test database connectivity
telnet localhost 1521

# Check Oracle container
docker ps | grep oracle
docker logs oracle-dev

# Test SQL connection
sqlplus system/oracle123@localhost:1521:xe
```

#### Solutions

**Oracle Container Not Running**
```bash
# Start Oracle container
docker start oracle-dev

# Or recreate container
docker run -d --name oracle-dev -p 1521:1521 -e ORACLE_PASSWORD=oracle123 gvenzl/oracle-xe:latest
```

**Wrong Connection String**
```bash
# Correct format
jdbc:oracle:thin:@localhost:1521:xe

# Debug connection string
echo "DB_URL: jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${DB_SID}"
```

**Network Issues**
```bash
# Check network connectivity
ping localhost
telnet localhost 1521

# Check firewall rules
sudo ufw status  # Ubuntu
netsh advfirewall show allprofiles  # Windows
```

### Issue: Schema/Table Not Found

#### Symptoms
```
ORA-00942: table or view does not exist
Table 'NATS_REQUEST_LOG' doesn't exist
```

#### Solutions
```bash
# Connect to Oracle and create schema
sqlplus system/oracle123@localhost:1521:xe
@src/main/resources/schema.sql

# Or use application auto-create
spring.jpa.hibernate.ddl-auto=create-drop  # Development only
```

### Issue: Database Performance Problems

#### Symptoms
- Slow query execution
- Connection timeouts
- High database CPU usage

#### Diagnostic Queries
```sql
-- Check active sessions
SELECT username, status, machine, program, sql_id 
FROM v$session 
WHERE type = 'USER' AND status = 'ACTIVE';

-- Check blocking locks
SELECT blocking_session, sid, serial#, wait_class, event
FROM v$session 
WHERE blocking_session IS NOT NULL;

-- Check table statistics
SELECT table_name, num_rows, last_analyzed 
FROM user_tables 
WHERE table_name = 'NATS_REQUEST_LOG';
```

#### Solutions
```sql
-- Update table statistics
ANALYZE TABLE nats_request_log COMPUTE STATISTICS;

-- Add missing indexes
CREATE INDEX idx_nats_log_status ON nats_request_log(status);
CREATE INDEX idx_nats_log_created ON nats_request_log(created_at);

-- Optimize connection pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

## 🔌 NATS Connection Issues

### Issue: NATS Server Unreachable

#### Symptoms
```
io.nats.client.JNatsException: Connection refused
NATS connection failed
Unable to connect to NATS server
```

#### Diagnostic Commands
```bash
# Check NATS server status
curl -s http://localhost:8222/varz | jq '.connections'

# Test NATS connectivity
telnet localhost 4222

# Check NATS container
docker logs nats-dev
```

#### Solutions

**NATS Server Not Running**
```bash
# Start NATS container
docker start nats-dev

# Or run new NATS server
docker run -d --name nats-dev -p 4222:4222 -p 8222:8222 nats:latest --http_port 8222
```

**Wrong NATS URL**
```bash
# Correct format
nats://localhost:4222

# For Docker networking
nats://nats:4222  # When app runs in Docker

# With authentication
nats://username:password@localhost:4222
```

**Network Configuration**
```bash
# Check NATS port binding
docker port nats-dev

# Check container network
docker network ls
docker inspect bridge
```

### Issue: NATS Authentication Failed

#### Symptoms
```
Authorization Violation
Authentication failed
Invalid credentials
```

#### Solutions
```bash
# Check NATS server config
curl -s http://localhost:8222/varz | jq '.auth_required'

# Configure authentication in application.yml
nats:
  username: your_username
  password: your_password
  # OR
  token: your_token
  # OR
  credentials: /path/to/creds.file
```

### Issue: NATS Request Timeout

#### Symptoms
```
NatsTimeoutException: Request timeout after 30000ms
No response received from NATS
Subject has no responders
```

#### Solutions
```bash
# Check if there are subscribers
nats sub test.subject  # In separate terminal

# Increase timeout
nats:
  request:
    timeout: 60000  # 60 seconds

# Check NATS server info
curl -s http://localhost:8222/varz | jq '.slow_consumers'
```

## ⚡ Performance Issues

### Issue: High Memory Usage

#### Symptoms
- OutOfMemoryError
- Slow application response
- High heap usage

#### Diagnostic Commands
```bash
# Check memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.memory.max

# Generate heap dump
jcmd <pid> GC.run_finalization
jcmd <pid> VM.dump_heap heap.hprof

# Monitor with JVM tools
jconsole  # Connect to local process
```

#### Solutions
```bash
# Increase heap size
export MAVEN_OPTS="-Xmx2g -Xms1g -XX:MetaspaceSize=256m"

# Tune garbage collection
-XX:+UseG1GC -XX:G1HeapRegionSize=16m

# Monitor memory leaks
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/
```

### Issue: High CPU Usage

#### Symptoms
- Application becomes unresponsive
- High CPU utilization
- Slow API responses

#### Diagnostic Commands
```bash
# Check CPU usage
top -p <java-pid>
htop

# Profile CPU usage
jcmd <pid> Thread.print > thread_dump.txt

# Application metrics
curl http://localhost:8080/actuator/metrics/system.cpu.usage
```

#### Solutions
```bash
# Optimize thread pool
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=16

# Database connection pooling
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2

# NATS connection optimization
nats.connection.max-reconnect=10
nats.connection.reconnect-wait=2000
```

### Issue: Slow API Response Times

#### Symptoms
- Response times > 5 seconds
- Client timeouts
- Poor user experience

#### Diagnostic Commands
```bash
# Test API performance
time curl -X POST http://localhost:8080/api/nats/test/echo \
  -H "Content-Type: application/json" \
  -d '{"message":"test"}'

# Load test
ab -n 100 -c 10 http://localhost:8080/api/nats/health

# Check response time metrics
curl http://localhost:8080/api/nats/statistics
```

#### Solutions
```bash
# Optimize database queries
# Add indexes
CREATE INDEX idx_nats_log_request_id ON nats_request_log(request_id);

# Connection pool tuning
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000

# NATS request timeout
nats.request.timeout=10000  # Reduce from 30000
```

## 🐳 Deployment Issues

### Issue: Docker Container Won't Start

#### Symptoms
```
Container exits immediately
Port binding failed
Container health check failing
```

#### Diagnostic Commands
```bash
# Check container status
docker ps -a | grep nats-client

# View container logs
docker logs nats-client

# Inspect container
docker inspect nats-client

# Check resource usage
docker stats
```

#### Solutions
```bash
# Check Dockerfile
cat Dockerfile

# Verify image build
docker build -t nats-client:latest . --no-cache

# Check port conflicts
netstat -tulpn | grep :8080

# Run with different port
docker run -p 8081:8080 nats-client:latest
```

### Issue: Kubernetes Pod CrashLoopBackOff

#### Symptoms
```
pod/nats-client-xxx CrashLoopBackOff
Container keeps restarting
Pod fails health checks
```

#### Diagnostic Commands
```bash
# Check pod status
kubectl get pods -n nats-client

# View pod events
kubectl describe pod <pod-name> -n nats-client

# Check logs
kubectl logs <pod-name> -n nats-client --previous

# Check resource limits
kubectl top pods -n nats-client
```

#### Solutions
```bash
# Increase resource limits
resources:
  limits:
    memory: "1Gi"
    cpu: "500m"
  requests:
    memory: "512Mi"
    cpu: "250m"

# Fix liveness probe
livenessProbe:
  httpGet:
    path: /api/nats/health
    port: 8080
  initialDelaySeconds: 120  # Increase delay
  periodSeconds: 30

# Check configuration
kubectl get configmap nats-client-config -o yaml
kubectl get secret nats-client-secrets -o yaml
```

## 📊 Monitoring & Logging

### Issue: Missing or Incorrect Logs

#### Symptoms
- No log files generated
- Log level too high
- Missing error details

#### Solutions
```yaml
# Configure logging in application.yml
logging:
  level:
    com.example.natsclient: DEBUG
    org.springframework: INFO
    io.nats: DEBUG
  file:
    name: logs/nats-client.log
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### Issue: Metrics Not Available

#### Symptoms
- Actuator endpoints not accessible
- Missing custom metrics
- Monitoring dashboards empty

#### Solutions
```yaml
# Enable actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true

# Add custom metrics
@Component
public class NatsMetricsCollector {
    private final Counter requestCounter;
    
    public NatsMetricsCollector(MeterRegistry meterRegistry) {
        this.requestCounter = Counter.builder("nats.requests.total")
            .description("Total NATS requests")
            .register(meterRegistry);
    }
}
```

## 🚨 Emergency Procedures

### Application Completely Down

#### Immediate Actions
```bash
# 1. Check if containers are running
docker ps | grep -E "(nats|oracle)"

# 2. Restart all services
docker-compose -f docker-compose-with-app.yml restart

# 3. Check logs for errors
docker-compose -f docker-compose-with-app.yml logs --tail 100

# 4. Health check
curl http://localhost:8080/api/nats/health
```

#### Escalation Steps
```bash
# 1. Complete rebuild
docker-compose -f docker-compose-with-app.yml down
docker-compose -f docker-compose-with-app.yml up -d --build

# 2. Database recovery
docker exec -it oracle-dev sqlplus system/oracle123@localhost:1521/xe
# Run recovery scripts

# 3. Clean Docker environment
docker system prune -f
docker volume prune -f
```

### Data Corruption or Loss

#### Immediate Actions
```bash
# 1. Stop application to prevent further damage
docker-compose -f docker-compose-with-app.yml stop nats-client

# 2. Backup current database state
docker exec oracle-dev sh -c "expdp system/oracle123@xe schemas=system directory=backup_dir dumpfile=emergency_backup.dmp"

# 3. Identify corruption scope
sqlplus system/oracle123@localhost:1521:xe
SELECT COUNT(*) FROM nats_request_log WHERE status IS NULL;
```

#### Recovery Procedures
```sql
-- Check data integrity
SELECT 
    status,
    COUNT(*) as count,
    MIN(created_at) as earliest,
    MAX(created_at) as latest
FROM nats_request_log 
GROUP BY status;

-- Restore from backup if needed
impdp system/oracle123@xe schemas=system directory=backup_dir dumpfile=latest_backup.dmp
```

### Security Incident

#### Immediate Response
```bash
# 1. Stop exposed services
docker-compose -f docker-compose-with-app.yml stop

# 2. Check for unauthorized access
grep -i "unauthorized\|403\|401" logs/nats-client.log

# 3. Review recent database changes
SELECT * FROM nats_request_log 
WHERE created_at > SYSDATE - 1
ORDER BY created_at DESC;
```

#### Investigation Commands
```bash
# Check network connections
netstat -tulpn | grep :8080
ss -tulpn | grep :8080

# Review access logs
tail -n 1000 logs/nats-client.log | grep -E "(POST|GET|PUT|DELETE)"

# Check system resources
top -n 1 -b | head -20
df -h
```

## 🔧 Common Solutions Summary

### Quick Fixes Checklist
- [ ] Check service health: `curl http://localhost:8080/api/nats/health`
- [ ] Verify containers running: `docker ps`
- [ ] Check logs: `docker logs nats-client --tail 50`
- [ ] Test database: `sqlplus system/oracle123@localhost:1521:xe`
- [ ] Test NATS: `curl http://localhost:8222/varz`
- [ ] Restart services: `docker-compose restart`
- [ ] Check resource usage: `docker stats`
- [ ] Verify configuration: `env | grep -E "(DB_|NATS_)"`

### Performance Optimization
- Increase heap size: `-Xmx2g`
- Tune connection pools: `maximum-pool-size=20`
- Add database indexes
- Optimize NATS timeout settings
- Enable G1 garbage collector

### Security Hardening
- Enable authentication for NATS
- Use environment variables for secrets
- Implement proper logging
- Regular security updates
- Monitor access patterns

This troubleshooting guide covers the most common issues and provides systematic approaches to diagnose and resolve problems in the NATS Client Service.