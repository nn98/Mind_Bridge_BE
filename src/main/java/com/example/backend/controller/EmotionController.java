package com.example.backend.controller;

import com.example.backend.dto.emotion.EmotionRequest;
import com.example.backend.service.EmotionService;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
public class EmotionController {
    
    private final EmotionService emotionService;
    
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Integer>> analyzeEmotion(
            @Valid @RequestBody EmotionRequest request
    ) {
        Map<String, Integer> result =
                emotionService.analyzeText(request.getEmail(), request.getText());
        return ResponseEntity.ok(result);
    }
}
