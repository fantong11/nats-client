@echo off
echo Starting NATS Client Local Development Environment
echo ===================================================

echo.
echo 1. Starting Docker services...
docker-compose up -d

echo.
echo 2. Waiting for services to be ready...
timeout /t 10

echo.
echo 3. Checking service status...
docker-compose ps

echo.
echo 4. NATS Server status:
curl -s http://localhost:8222/varz | jq .server_name 2>nul || echo "NATS monitoring available at http://localhost:8222"

echo.
echo 5. Starting Spring Boot application...
echo "Use the following command to start the application:"
echo "mvn spring-boot:run -Dspring-boot.run.profiles=local"

echo.
echo Ready to test! Available endpoints:
echo - POST http://localhost:8080/api/nats/test/echo
echo - POST http://localhost:8080/api/nats/request
echo - POST http://localhost:8080/api/nats/publish
echo - GET  http://localhost:8080/api/nats/health

pause