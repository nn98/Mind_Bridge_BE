package com.example.backend.dto.emotion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmotionRequest {
    
    @NotBlank
    @Email
    private final String email;
    
    @NotBlank
    private final String text;
}
