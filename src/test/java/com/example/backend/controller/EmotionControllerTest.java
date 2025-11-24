package com.example.backend.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.dto.emotion.EmotionRequest;
import com.example.backend.service.EmotionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmotionController 테스트")
class EmotionControllerTest {
    
    @Mock
    EmotionService emotionService;
    
    @InjectMocks
    EmotionController emotionController;
    
    MockMvc mockMvc;
    ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(emotionController)
                .setControllerAdvice() // 전역 예외 처리 쓰고 있으면 여기 넣으면 됨
                .build();
    }
    
    @Test
    @DisplayName("analyzeEmotion - 유효한 요청이면 감정 맵을 200 OK로 반환한다")
    void analyzeEmotion_success() throws Exception {
        EmotionRequest request = EmotionRequest.builder()
                .email("user@example.com")
                .text("오늘 기분이 좋다가도 불안합니다.")
                .build();
        
        Map<String, Integer> emotions = Map.of(
                "happiness", 40,
                "sadness", 20
        );
        
        given(emotionService.analyzeText(request.getEmail(), request.getText()))
                .willReturn(emotions);
        
        mockMvc.perform(post("/api/emotion/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.happiness").value(40))
                .andExpect(jsonPath("$.sadness").value(20));
        
        then(emotionService).should()
                .analyzeText(request.getEmail(), request.getText());
    }
    
    @Test
    @DisplayName("analyzeEmotion - 잘못된 요청이면 400 Bad Request를 반환하고 서비스는 호출되지 않는다")
    void analyzeEmotion_invalidRequest_badRequest() throws Exception {
        EmotionRequest invalidRequest = EmotionRequest.builder()
                .email("")   // @NotBlank 가정
                .text("")    // @NotBlank 가정
                .build();
        
        mockMvc.perform(post("/api/emotion/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        
        then(emotionService).shouldHaveNoInteractions();
    }
}
