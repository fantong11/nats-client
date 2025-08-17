@echo off
echo Testing NATS Client Setup
echo =========================

echo.
echo 1. Checking NATS server status...
curl -s http://localhost:18222/varz | findstr "server_name"

echo.
echo 2. Starting Spring Boot application with local profile...
echo Run this command in another terminal:
echo mvn spring-boot:run -Dspring-boot.run.profiles=local
echo.

echo 3. Test endpoints (after app starts):
echo.
echo Health check:
echo curl http://localhost:8080/api/nats/health
echo.
echo Echo test:
echo curl -X POST http://localhost:8080/api/nats/test/echo -H "Content-Type: application/json" -d "{\"message\": \"Hello NATS!\"}"
echo.
echo Generic request:
echo curl -X POST http://localhost:8080/api/nats/request -H "Content-Type: application/json" -d "{\"subject\": \"demo.test\", \"payload\": {\"data\": \"test\"}}"

pause