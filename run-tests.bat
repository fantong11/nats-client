@echo off
echo Running NATS Client Unit Tests
echo ===============================

echo.
echo 1. Running unit tests...
call mvn test -Dtest="!*IntegrationTest"

echo.
echo 2. Running integration tests...
call mvn test -Dtest="*IntegrationTest"

echo.
echo 3. Running all tests with coverage...
call mvn clean test jacoco:report

echo.
echo 4. Test results summary:
echo - Unit test results: target/surefire-reports/
echo - Coverage report: target/site/jacoco/index.html

echo.
echo Tests completed!
pause