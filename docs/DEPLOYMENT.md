# Deployment Guide

This guide covers deployment strategies for the NATS Client Service across different environments, from local development to production Kubernetes clusters.

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Local Development](#local-development)
- [Docker Deployment](#docker-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Production Considerations](#production-considerations)
- [Monitoring & Observability](#monitoring--observability)
- [Troubleshooting](#troubleshooting)

## 🔧 Prerequisites

### Required Software
- **Docker**: 20.10+ and Docker Compose v2
- **Kubernetes**: 1.25+ (for K8s deployment)
- **kubectl**: Matching your cluster version
- **Java**: 17+ (for local builds)
- **Maven**: 3.6+ (included in project)

### Required Infrastructure
- **NATS Server**: 2.x cluster
- **Oracle Database**: 12c+ with network connectivity
- **Container Registry**: For storing Docker images

## 💻 Local Development

### Quick Start
```bash
# Clone repository
git clone <repository-url>
cd nats-client

# Start dependencies only
docker-compose -f docker-compose-with-app.yml up -d nats oracle-db

# Run application locally
./apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Local Environment Configuration
```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe
    username: system
    password: oracle123
  profiles:
    active: local

nats:
  url: nats://localhost:14222
```

### Local Database Setup
```bash
# Connect to Oracle
sqlplus system/oracle123@localhost:1521:xe

# Run schema
@src/main/resources/schema.sql
```

## 🐳 Docker Deployment

### Building Docker Image
```bash
# Build application
./apache-maven-3.9.6/bin/mvn clean package -DskipTests

# Build Docker image
docker build -t nats-client:latest .

# Verify image
docker images | grep nats-client
```

### Docker Compose Deployment
```bash
# Deploy full stack
docker-compose -f docker-compose-with-app.yml up -d

# Check services
docker-compose -f docker-compose-with-app.yml ps

# View logs
docker-compose -f docker-compose-with-app.yml logs -f nats-client

# Scale application
docker-compose -f docker-compose-with-app.yml up -d --scale nats-client=3
```

### Docker Compose Configuration
```yaml
# docker-compose-with-app.yml
version: '3.8'
services:
  nats-client:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_HOST=oracle-db
      - DB_USERNAME=system
      - DB_PASSWORD=oracle123
      - NATS_URL=nats://nats:4222
    depends_on:
      - nats
      - oracle-db
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/nats/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  nats:
    image: nats:latest
    ports:
      - "4222:4222"
      - "8222:8222"
      - "6222:6222"
    command: ["--http_port", "8222", "--port", "4222", "--cluster_name", "nats-cluster"]
    restart: unless-stopped

  oracle-db:
    image: gvenzl/oracle-xe:latest
    environment:
      - ORACLE_PASSWORD=oracle123
    ports:
      - "1521:1521"
    volumes:
      - oracle_data:/opt/oracle/oradata
    restart: unless-stopped

volumes:
  oracle_data:
```

### Docker Health Checks
```dockerfile
# In Dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/nats/health || exit 1
```

## ☸️ Kubernetes Deployment

### Prerequisites Check
```bash
# Verify cluster connection
kubectl cluster-info

# Check nodes
kubectl get nodes

# Verify required namespaces
kubectl get namespaces
```

### Deploy to Kubernetes
```bash
# Deploy everything
kubectl apply -f k8s-deploy-all.yml

# Check deployment status
kubectl get deployments -n nats-client

# Check pods
kubectl get pods -n nats-client -w

# Check services
kubectl get services -n nats-client
```

### Kubernetes Configuration Breakdown

#### Namespace
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: nats-client
  labels:
    app: nats-client
```

#### ConfigMap
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nats-client-config
  namespace: nats-client
data:
  application.yml: |
    spring:
      datasource:
        url: jdbc:oracle:thin:@oracle-db:1521:xe
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
    nats:
      url: nats://nats-service:4222
```

#### Secret
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: nats-client-secrets
  namespace: nats-client
type: Opaque
data:
  DB_USERNAME: c3lzdGVt  # base64 encoded 'system'
  DB_PASSWORD: b3JhY2xlMTIz  # base64 encoded 'oracle123'
```

#### Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nats-client
  namespace: nats-client
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nats-client
  template:
    spec:
      containers:
      - name: nats-client
        image: nats-client:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: nats-client-secrets
              key: DB_USERNAME
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nats-client-secrets
              key: DB_PASSWORD
        resources:
          limits:
            memory: "512Mi"
            cpu: "500m"
          requests:
            memory: "256Mi"
            cpu: "250m"
        livenessProbe:
          httpGet:
            path: /api/nats/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /api/nats/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

#### Service
```yaml
apiVersion: v1
kind: Service
metadata:
  name: nats-client-service
  namespace: nats-client
spec:
  selector:
    app: nats-client
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: ClusterIP
```

### Ingress Configuration
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: nats-client-ingress
  namespace: nats-client
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
  - hosts:
    - nats-client.yourdomain.com
    secretName: nats-client-tls
  rules:
  - host: nats-client.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nats-client-service
            port:
              number: 80
```

### Scaling and Updates
```bash
# Scale deployment
kubectl scale deployment nats-client --replicas=5 -n nats-client

# Rolling update
kubectl set image deployment/nats-client nats-client=nats-client:v2.0.0 -n nats-client

# Check rollout status
kubectl rollout status deployment/nats-client -n nats-client

# Rollback if needed
kubectl rollout undo deployment/nats-client -n nats-client
```

## 🏭 Production Considerations

### Security Hardening
```yaml
# Security Context
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop:
    - ALL

# Network Policies
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: nats-client-network-policy
spec:
  podSelector:
    matchLabels:
      app: nats-client
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: nats-system
    ports:
    - protocol: TCP
      port: 4222
```

### Resource Management

All containers in this project have proper resource limits configured to prevent "noisy neighbor" issues:

#### Container Resource Allocations
```yaml
# NATS Server
resources:
  requests:
    memory: "128Mi"
    cpu: "100m"
  limits:
    memory: "256Mi"
    cpu: "500m"

# Oracle Database
resources:
  requests:
    memory: "2Gi"
    cpu: "500m"
  limits:
    memory: "4Gi"
    cpu: "2000m"

# NATS Client Application
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"

# Init Containers (busybox)
resources:
  requests:
    memory: "16Mi"
    cpu: "10m"
  limits:
    memory: "32Mi"
    cpu: "50m"
```

#### Resource Quotas
```yaml
# Resource Quotas
apiVersion: v1
kind: ResourceQuota
metadata:
  name: nats-client-quota
spec:
  hard:
    requests.cpu: "3"
    requests.memory: 6Gi
    limits.cpu: "6"
    limits.memory: 12Gi
    pods: "10"

# Horizontal Pod Autoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: nats-client-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: nats-client
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### Environment-Specific Configurations

#### Staging Environment
```bash
# Deploy to staging
kubectl apply -f k8s-deploy-all.yml -n staging

# Environment-specific config
kubectl create configmap nats-client-config-staging \
  --from-literal=DB_HOST=staging-oracle.db \
  --from-literal=NATS_URL=nats://staging-nats:4222 \
  -n staging
```

#### Production Environment
```bash
# Production deployment with blue-green strategy
kubectl apply -f k8s-deploy-all.yml -n production

# Production secrets from external secret manager
kubectl create secret generic nats-client-secrets-prod \
  --from-literal=DB_USERNAME=$(vault kv get -field=username secret/db) \
  --from-literal=DB_PASSWORD=$(vault kv get -field=password secret/db) \
  -n production
```

## 📊 Monitoring & Observability

### Health Checks
```bash
# Application health
kubectl exec -it deployment/nats-client -n nats-client -- \
  curl http://localhost:8080/api/nats/health

# Kubernetes health
kubectl get pods -n nats-client -o wide
kubectl describe pod <pod-name> -n nats-client
```

### Logging Configuration
```yaml
# Fluent Bit DaemonSet for log collection
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluent-bit
spec:
  selector:
    matchLabels:
      name: fluent-bit
  template:
    spec:
      containers:
      - name: fluent-bit
        image: fluent/fluent-bit:latest
        volumeMounts:
        - name: varlog
          mountPath: /var/log
        - name: varlibdockercontainers
          mountPath: /var/lib/docker/containers
          readOnly: true
```

### Metrics Collection
```yaml
# ServiceMonitor for Prometheus
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: nats-client-metrics
spec:
  selector:
    matchLabels:
      app: nats-client
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

## 🔍 Troubleshooting

### Common Deployment Issues

#### Pod Not Starting
```bash
# Check pod status
kubectl get pods -n nats-client

# Describe pod for events
kubectl describe pod <pod-name> -n nats-client

# Check logs
kubectl logs <pod-name> -n nats-client --previous

# Check resource usage
kubectl top pods -n nats-client
```

#### Database Connection Issues
```bash
# Test database connectivity
kubectl exec -it deployment/nats-client -n nats-client -- \
  nc -zv oracle-db 1521

# Check database pod logs
kubectl logs deployment/oracle-db -n nats-client

# Verify credentials
kubectl get secret nats-client-secrets -n nats-client -o yaml
```

#### NATS Connection Problems
```bash
# Test NATS connectivity
kubectl exec -it deployment/nats-client -n nats-client -- \
  nc -zv nats-service 4222

# Check NATS server status
kubectl exec -it deployment/nats -n nats-client -- \
  nats server info

# View NATS logs
kubectl logs deployment/nats -n nats-client
```

### Performance Issues
```bash
# Check resource utilization
kubectl top pods -n nats-client
kubectl top nodes

# Analyze application metrics
kubectl port-forward service/nats-client-service 8080:80 -n nats-client
curl http://localhost:8080/api/nats/statistics

# Check HPA status
kubectl get hpa -n nats-client
kubectl describe hpa nats-client-hpa -n nats-client
```

### Cleanup and Maintenance
```bash
# Clean up failed deployments
kubectl delete pods --field-selector=status.phase=Failed -n nats-client

# Update deployment
kubectl patch deployment nats-client -p '{"spec":{"template":{"metadata":{"annotations":{"kubectl.kubernetes.io/restartedAt":"'$(date +%Y-%m-%dT%H:%M:%S%z)'"}}}}}' -n nats-client

# Backup persistent data
kubectl exec -it deployment/oracle-db -n nats-client -- \
  expdp system/oracle123@xe schemas=system directory=backup_dir dumpfile=backup.dmp

# Complete cleanup
kubectl delete namespace nats-client
```

This deployment guide provides comprehensive instructions for deploying the NATS Client Service across different environments with proper security, monitoring, and troubleshooting procedures.