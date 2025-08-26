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