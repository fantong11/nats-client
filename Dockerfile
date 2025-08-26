# Multi-stage build for Java Spring Boot application
FROM openjdk:17-jdk-slim AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY pom.xml .
COPY apache-maven-3.9.6 ./apache-maven-3.9.6

# Copy source code
COPY src ./src

# Set Maven home and add to PATH
ENV MAVEN_HOME=/app/apache-maven-3.9.6
ENV PATH=$MAVEN_HOME/bin:$PATH

# Build the application
RUN mvn clean package -Dmaven.test.skip=true

# Runtime stage
FROM openjdk:17-jdk-slim

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder /app/target/nats-client-0.0.1-SNAPSHOT.jar app.jar

# Create non-root user for security
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]