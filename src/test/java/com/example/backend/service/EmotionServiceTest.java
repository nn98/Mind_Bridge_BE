package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.backend.entity.EmotionEntity;
import com.example.backend.repository.EmotionRepository;
import com.example.backend.service.emotion.EmotionParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmotionService 테스트")
class EmotionServiceTest {
    
    @Mock
    EmotionRepository emotionRepository;
    
    @Mock
    RestTemplate restTemplate;
    
    @Mock
    EmotionParser emotionParser;
    
    @InjectMocks
    EmotionService emotionService;
    
    @Test
    @DisplayName("analyzeText - OpenAI 호출, 파싱, 저장까지 정상적으로 수행된다")
    void analyzeText_success() {
        String email = "user@example.com";
        String text = "오늘 기분이 좋다가도 불안합니다.";
        String content = "{\"happiness\": 40}";
        Map<String, Integer> emotions = Map.of("happiness", 40);
        
        ReflectionTestUtils.setField(emotionService, "apiKey", "test-key");
        ReflectionTestUtils.setField(emotionService, "apiUrl", "https://api.test.com");
        
        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", content))
                )
        );
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        given(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(Map.class)
        )).willReturn(response);
        
        EmotionEntity entity = EmotionEntity.builder()
                .userEmail(email)
                .inputText(text)
                .happiness(40)
                .build();
        
        given(emotionParser.parse(content)).willReturn(emotions);
        given(emotionParser.toEntity(email, text, emotions)).willReturn(entity);
        
        Map<String, Integer> result = emotionService.analyzeText(email, text);
        
        assertThat(result).isEqualTo(emotions);
        
        then(restTemplate).should().exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(Map.class)
        );
        then(emotionParser).should().parse(content);
        then(emotionParser).should().toEntity(email, text, emotions);
        then(emotionRepository).should().save(entity);
    }
}
