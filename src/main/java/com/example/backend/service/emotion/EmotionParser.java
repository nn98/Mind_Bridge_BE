package com.example.backend.service.emotion;

import com.example.backend.entity.EmotionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmotionParser {
    
    private static final String MESSAGE_INVALID_JSON = "invalid emotion json content";
    
    private final ObjectMapper objectMapper;
    
    public Map<String, Integer> parse(String content) {
        Map<String, Integer> parsed = tryParse(content);
        if (parsed != null) {
            return parsed;
        }
        
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JSON);
        }
        
        String json = content.substring(start, end + 1);
        Map<String, Integer> sliced = tryParse(json);
        if (sliced != null) {
            return sliced;
        }
        
        throw new IllegalArgumentException(MESSAGE_INVALID_JSON);
    }
    
    public EmotionEntity toEntity(String email, String text, Map<String, Integer> emotions) {
        return EmotionEntity.builder()
                .userEmail(email)
                .inputText(text)
                .happiness(emotions.getOrDefault("happiness", 0))
                .sadness(emotions.getOrDefault("sadness", 0))
                .anger(emotions.getOrDefault("anger", 0))
                .anxiety(emotions.getOrDefault("anxiety", 0))
                .calmness(emotions.getOrDefault("calmness", 0))
                .etc(emotions.getOrDefault("etc", 0))
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    private Map<String, Integer> tryParse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
