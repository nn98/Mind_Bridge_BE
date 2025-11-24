package com.example.backend.service.emotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.entity.EmotionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmotionParserTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmotionParser parser = new EmotionParser(objectMapper);
    
    @Test
    @DisplayName("순수 JSON 문자열을 감정 맵으로 파싱한다")
    void parse_pureJson() {
        String content =
                "{\"happiness\":40,\"sadness\":20,\"anger\":10,\"anxiety\":10,\"calmness\":20,\"etc\":0}";
        
        Map<String, Integer> emotions = parser.parse(content);
        
        assertThat(emotions.get("happiness")).isEqualTo(40);
        assertThat(emotions.get("sadness")).isEqualTo(20);
        assertThat(emotions.get("anger")).isEqualTo(10);
        assertThat(emotions.get("anxiety")).isEqualTo(10);
        assertThat(emotions.get("calmness")).isEqualTo(20);
        assertThat(emotions.get("etc")).isEqualTo(0);
    }
    
    @Test
    @DisplayName("앞뒤 텍스트가 섞인 문자열에서도 JSON 부분만 추출해 파싱한다")
    void parse_textWrappedJson() {
        String content =
                "결과는 다음과 같습니다:\n"
                        + "{\"happiness\":30,\"sadness\":30,\"anger\":10,\"anxiety\":10,\"calmness\":20,\"etc\":0}\n"
                        + "감사합니다.";
        
        Map<String, Integer> emotions = parser.parse(content);
        
        assertThat(emotions.get("happiness")).isEqualTo(30);
        assertThat(emotions.get("sadness")).isEqualTo(30);
        assertThat(emotions.get("anger")).isEqualTo(10);
        assertThat(emotions.get("anxiety")).isEqualTo(10);
        assertThat(emotions.get("calmness")).isEqualTo(20);
        assertThat(emotions.get("etc")).isEqualTo(0);
    }
    
    @Test
    @DisplayName("유효한 JSON 구간이 없으면 IllegalArgumentException을 던진다")
    void parse_invalidJson_throwsException() {
        String content = "no json here";
        
        assertThatThrownBy(() -> parser.parse(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid emotion json content");
    }
    
    @Test
    @DisplayName("감정 맵을 기반으로 EmotionEntity를 생성한다")
    void toEntity_buildsEntity() {
        Map<String, Integer> emotions = Map.of(
                "happiness", 25,
                "sadness", 5,
                "etc", 3
        );
        
        EmotionEntity entity = parser.toEntity("user@example.com", "some text", emotions);
        
        assertThat(entity.getUserEmail()).isEqualTo("user@example.com");
        assertThat(entity.getInputText()).isEqualTo("some text");
        assertThat(entity.getHappiness()).isEqualTo(25);
        assertThat(entity.getSadness()).isEqualTo(5);
        assertThat(entity.getEtc()).isEqualTo(3);
    }
}
