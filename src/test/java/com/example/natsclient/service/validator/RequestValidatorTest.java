package com.example.natsclient.service.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RequestValidatorTest {

    @InjectMocks
    private RequestValidator requestValidator;

    private final Object validPayload = new TestPayload("test data");

    @Test
    void validateRequest_ValidInputs_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest("valid.subject", validPayload);
        });
    }

    @Test
    void validateRequest_NullSubject_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateRequest(null, validPayload);
        });

        assertEquals("Subject cannot be null or empty", exception.getMessage());
    }

    @Test
    void validateRequest_EmptySubject_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateRequest("", validPayload);
        });

        assertEquals("Subject cannot be null or empty", exception.getMessage());
    }

    @Test
    void validateRequest_WhitespaceOnlySubject_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateRequest("   ", validPayload);
        });

        assertEquals("Subject cannot be null or empty", exception.getMessage());
    }

    @Test
    void validateRequest_TabAndNewlineSubject_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateRequest("\t\n\r", validPayload);
        });

        assertEquals("Subject cannot be null or empty", exception.getMessage());
    }

    @Test
    void validateRequest_NullPayload_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateRequest("valid.subject", null);
        });

        assertEquals("Payload cannot be null", exception.getMessage());
    }

    @Test
    void validateRequest_ValidSubjectWithSpecialChars_ShouldNotThrowException() {
        // Arrange
        String specialSubject = "test.subject-with_special.chars123";

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest(specialSubject, validPayload);
        });
    }

    @Test
    void validateRequest_ValidSubjectWithDots_ShouldNotThrowException() {
        // Arrange
        String dottedSubject = "api.v1.user.create.request";

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest(dottedSubject, validPayload);
        });
    }

    @Test
    void validateCorrelationId_ValidId_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateCorrelationId("valid-correlation-id-123");
        });
    }

    @Test
    void validateCorrelationId_NullId_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateCorrelationId(null);
        });
    }

    @Test
    void validateCorrelationId_EmptyString_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateCorrelationId("");
        });

        assertEquals("Correlation ID cannot be empty if provided", exception.getMessage());
    }

    @Test
    void validateCorrelationId_WhitespaceOnlyString_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateCorrelationId("   ");
        });

        assertEquals("Correlation ID cannot be empty if provided", exception.getMessage());
    }

    @Test
    void validateCorrelationId_TabAndNewlineString_ShouldThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            requestValidator.validateCorrelationId("\t\n\r");
        });

        assertEquals("Correlation ID cannot be empty if provided", exception.getMessage());
    }

    @Test
    void validateCorrelationId_ValidIdWithSpecialChars_ShouldNotThrowException() {
        // Arrange
        String specialCorrelationId = "corr-123_abc-def.ghi@domain.com";

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateCorrelationId(specialCorrelationId);
        });
    }

    @Test
    void validateCorrelationId_UUIDFormat_ShouldNotThrowException() {
        // Arrange
        String uuidCorrelationId = "550e8400-e29b-41d4-a716-446655440000";

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateCorrelationId(uuidCorrelationId);
        });
    }

    @Test
    void validateRequest_BothValid_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest("test.subject", validPayload);
            requestValidator.validateCorrelationId("correlation-123");
        });
    }

    @Test
    void validateRequest_ValidWithComplexPayload_ShouldNotThrowException() {
        // Arrange
        ComplexPayload complexPayload = new ComplexPayload("name", 42, true);

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest("complex.test", complexPayload);
        });
    }

    @Test
    void validateRequest_MinimalValidSubject_ShouldNotThrowException() {
        // Arrange
        String minimalSubject = "a";

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest(minimalSubject, validPayload);
        });
    }

    @Test
    void validateRequest_LongValidSubject_ShouldNotThrowException() {
        // Arrange
        String longSubject = "a".repeat(1000);

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest(longSubject, validPayload);
        });
    }

    @Test
    void validateCorrelationId_LongValidId_ShouldNotThrowException() {
        // Arrange
        String longCorrelationId = "correlation-" + "a".repeat(1000);

        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateCorrelationId(longCorrelationId);
        });
    }

    @Test
    void validateRequest_PayloadAsString_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest("string.test", "string payload");
        });
    }

    @Test
    void validateRequest_PayloadAsNumber_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest("number.test", 42);
        });
    }

    @Test
    void validateRequest_PayloadAsBoolean_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            requestValidator.validateRequest("boolean.test", true);
        });
    }

    // Test helper classes
    private static class TestPayload {
        private final String data;

        public TestPayload(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }

    private static class ComplexPayload {
        private final String name;
        private final int value;
        private final boolean flag;

        public ComplexPayload(String name, int value, boolean flag) {
            this.name = name;
            this.value = value;
            this.flag = flag;
        }

        public String getName() { return name; }
        public int getValue() { return value; }
        public boolean isFlag() { return flag; }
    }
}