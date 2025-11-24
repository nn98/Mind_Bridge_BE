package com.example.backend.service;

import com.example.backend.entity.EmotionEntity;
import com.example.backend.repository.EmotionRepository;
import com.example.backend.service.emotion.EmotionParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionService {
    
    private static final String MESSAGE_MODEL_RESPONSE_PARSING_FAILED = "모델 응답 파싱 실패";
    
    private final EmotionRepository emotionRepository;
    private final RestTemplate restTemplate;
    private final EmotionParser emotionParser;
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    @Value("${openai.api.url}")
    private String apiUrl;
    
    @Transactional
    public Map<String, Integer> analyzeText(String email, String text) {
        String prompt = buildPrompt(text);
        
        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "max_tokens", 200
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                Map.class
        );
        
        Map<String, Object> responseBody = response.getBody();
        String content = extractContent(responseBody);
        
        Map<String, Integer> emotions = emotionParser.parse(content);
        EmotionEntity entityToSave = emotionParser.toEntity(email, text, emotions);
        emotionRepository.save(entityToSave);
        
        return emotions;
    }
    
    private String buildPrompt(String text) {
        return String.format(
                "다음 문장을 감정별 비율(%%)로 분석해줘.%n"
                        + "감정 카테고리: happiness, sadness, anger, anxiety, calmness, etc%n"
                        + "문장: \"%s\"%n%n"
                        + "반드시 총합이 100이 되도록 하고,%n"
                        + "JSON만 출력:%n"
                        + "{\"happiness\": 40, \"sadness\": 20, \"anger\": 10, \"anxiety\": 10, \"calmness\": 20, \"etc\": 0}",
                text
        );
    }
    
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalStateException(MESSAGE_MODEL_RESPONSE_PARSING_FAILED);
        }
        
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException(MESSAGE_MODEL_RESPONSE_PARSING_FAILED);
        }
        
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            throw new IllegalStateException(MESSAGE_MODEL_RESPONSE_PARSING_FAILED);
        }
        
        Object messageObj = firstMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException(MESSAGE_MODEL_RESPONSE_PARSING_FAILED);
        }
        
        Object content = messageMap.get("content");
        return Optional.ofNullable(content)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalStateException(MESSAGE_MODEL_RESPONSE_PARSING_FAILED));
    }
}
