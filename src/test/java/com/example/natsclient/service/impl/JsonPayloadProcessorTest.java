package com.example.natsclient.service.impl;

import com.example.natsclient.exception.PayloadProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsonPayloadProcessorTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private JsonPayloadProcessor payloadProcessor;

    private final String testJson = "{\"name\":\"test\",\"value\":123}";
    private final TestObject testObject = new TestObject("test", 123);
    private final byte[] testBytes = testJson.getBytes(StandardCharsets.UTF_8);

    @Test
    void serialize_ValidObject_ShouldReturnJsonString() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(testObject)).thenReturn(testJson);

        // Act
        String result = payloadProcessor.serialize(testObject);

        // Assert
        assertEquals(testJson, result);
        verify(objectMapper).writeValueAsString(testObject);
    }

    @Test
    void serialize_ObjectMapperThrowsException_ShouldThrowPayloadProcessingException() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(testObject))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        // Act & Assert
        PayloadProcessingException exception = assertThrows(PayloadProcessingException.class, () -> {
            payloadProcessor.serialize(testObject);
        });

        assertTrue(exception.getMessage().contains("Failed to serialize payload"));
        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @Test
    void serialize_NullObject_ShouldThrowPayloadProcessingException() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(null))
                .thenThrow(new JsonProcessingException("Cannot serialize null") {});

        // Act & Assert
        assertThrows(PayloadProcessingException.class, () -> {
            payloadProcessor.serialize(null);
        });
    }

    @Test
    void deserialize_ValidJsonString_ShouldReturnObject() throws Exception {
        // Arrange
        when(objectMapper.readValue(testJson, TestObject.class)).thenReturn(testObject);

        // Act
        TestObject result = payloadProcessor.deserialize(testJson, TestObject.class);

        // Assert
        assertEquals(testObject.getName(), result.getName());
        assertEquals(testObject.getValue(), result.getValue());
        verify(objectMapper).readValue(testJson, TestObject.class);
    }

    @Test
    void deserialize_ObjectMapperThrowsException_ShouldThrowPayloadProcessingException() throws Exception {
        // Arrange
        when(objectMapper.readValue(testJson, TestObject.class))
                .thenThrow(new JsonProcessingException("Deserialization failed") {});

        // Act & Assert
        PayloadProcessingException exception = assertThrows(PayloadProcessingException.class, () -> {
            payloadProcessor.deserialize(testJson, TestObject.class);
        });

        assertTrue(exception.getMessage().contains("Failed to deserialize payload"));
        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @Test
    void deserialize_InvalidJson_ShouldThrowPayloadProcessingException() throws Exception {
        // Arrange
        String invalidJson = "{invalid json}";
        when(objectMapper.readValue(invalidJson, TestObject.class))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        // Act & Assert
        assertThrows(PayloadProcessingException.class, () -> {
            payloadProcessor.deserialize(invalidJson, TestObject.class);
        });
    }

    @Test
    void deserialize_EmptyString_ShouldThrowPayloadProcessingException() throws Exception {
        // Arrange
        when(objectMapper.readValue("", TestObject.class))
                .thenThrow(new JsonProcessingException("Empty string") {});

        // Act & Assert
        assertThrows(PayloadProcessingException.class, () -> {
            payloadProcessor.deserialize("", TestObject.class);
        });
    }

    @Test
    void toBytes_ValidString_ShouldReturnUTF8Bytes() {
        // Act
        byte[] result = payloadProcessor.toBytes(testJson);

        // Assert
        assertArrayEquals(testBytes, result);
        assertEquals(testJson, new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void toBytes_EmptyString_ShouldReturnEmptyByteArray() {
        // Act
        byte[] result = payloadProcessor.toBytes("");

        // Assert
        assertArrayEquals(new byte[0], result);
    }

    @Test
    void toBytes_NullString_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            payloadProcessor.toBytes(null);
        });
    }

    @Test
    void toBytes_UnicodeString_ShouldHandleCorrectly() {
        // Arrange
        String unicodeString = "Hello 世界! 🌍";
        byte[] expectedBytes = unicodeString.getBytes(StandardCharsets.UTF_8);

        // Act
        byte[] result = payloadProcessor.toBytes(unicodeString);

        // Assert
        assertArrayEquals(expectedBytes, result);
        assertEquals(unicodeString, new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void fromBytes_ValidBytes_ShouldReturnString() {
        // Act
        String result = payloadProcessor.fromBytes(testBytes);

        // Assert
        assertEquals(testJson, result);
    }

    @Test
    void fromBytes_EmptyByteArray_ShouldReturnEmptyString() {
        // Act
        String result = payloadProcessor.fromBytes(new byte[0]);

        // Assert
        assertEquals("", result);
    }

    @Test
    void fromBytes_NullByteArray_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            payloadProcessor.fromBytes(null);
        });
    }

    @Test
    void fromBytes_UnicodeBytes_ShouldHandleCorrectly() {
        // Arrange
        String unicodeString = "Hello 世界! 🌍";
        byte[] unicodeBytes = unicodeString.getBytes(StandardCharsets.UTF_8);

        // Act
        String result = payloadProcessor.fromBytes(unicodeBytes);

        // Assert
        assertEquals(unicodeString, result);
    }

    @Test
    void roundTrip_ToeBytesAndFromBytes_ShouldPreserveData() {
        // Arrange
        String originalString = "Test string with special chars: @#$%^&*()";

        // Act
        byte[] bytes = payloadProcessor.toBytes(originalString);
        String restored = payloadProcessor.fromBytes(bytes);

        // Assert
        assertEquals(originalString, restored);
    }

    @Test
    void serialize_ComplexObject_ShouldWork() throws Exception {
        // Arrange
        Map<String, Object> complexObject = new HashMap<>();
        complexObject.put("string", "value");
        complexObject.put("number", 42);
        complexObject.put("boolean", true);
        complexObject.put("nested", Map.of("key", "nestedValue"));
        
        String expectedJson = "{\"complex\":\"json\"}";
        when(objectMapper.writeValueAsString(complexObject)).thenReturn(expectedJson);

        // Act
        String result = payloadProcessor.serialize(complexObject);

        // Assert
        assertEquals(expectedJson, result);
    }

    @Test
    void deserialize_ToMapClass_ShouldWork() throws Exception {
        // Arrange
        @SuppressWarnings("unchecked")
        Map<String, Object> expectedMap = mock(Map.class);
        when(objectMapper.readValue(testJson, Map.class)).thenReturn(expectedMap);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = payloadProcessor.deserialize(testJson, Map.class);

        // Assert
        assertEquals(expectedMap, result);
    }

    // Test helper class
    private static class TestObject {
        private String name;
        private int value;

        public TestObject() {}

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}