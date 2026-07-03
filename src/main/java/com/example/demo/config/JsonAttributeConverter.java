package com.example.demo.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Converter
public class JsonAttributeConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize extra attributes to JSON", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not deserialize extra attributes from JSON", e);
        }
    }
}
