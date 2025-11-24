package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    
    @Test
    @DisplayName("analyzeText - OpenAI 응답 구조가 잘못되면 예외를 던진다")
    void analyzeText_invalidOpenAiResponse_throws() {
        String email = "user@example.com";
        String text = "텍스트";
        
        ReflectionTestUtils.setField(emotionService, "apiKey", "test-key");
        ReflectionTestUtils.setField(emotionService, "apiUrl", "https://api.test.com");
        
        ResponseEntity<Map> response = new ResponseEntity<>(Map.of(), HttpStatus.OK);
        
        given(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(Map.class)
        )).willReturn(response);
        
        assertThatThrownBy(() -> emotionService.analyzeText(email, text))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("모델 응답 파싱 실패");
        
        then(emotionParser).shouldHaveNoInteractions();
        then(emotionRepository).shouldHaveNoInteractions();
    }
    
    @Test
    @DisplayName("analyzeText - 파서가 실패하면 그 예외를 그대로 전파한다")
    void analyzeText_parserFails_propagatesException() {
        String email = "user@example.com";
        String text = "텍스트";
        String content = "invalid json";
        
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
        
        given(emotionParser.parse(content))
                .willThrow(new IllegalArgumentException("invalid emotion json content"));
        
        assertThatThrownBy(() -> emotionService.analyzeText(email, text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid emotion json content");
        
        then(emotionParser).should().parse(content);
        then(emotionRepository).shouldHaveNoInteractions();
    }
}
