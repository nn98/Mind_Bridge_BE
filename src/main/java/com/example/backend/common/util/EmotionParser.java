package com.example.backend.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class EmotionParser {

	// 기존 텍스트 패턴 (하위 호환성)
	private static final Pattern EMOTION_PATTERN = Pattern.compile("([가-힣]+):\\s*(\\d+(?:\\.\\d+)?)%");

	private final ObjectMapper objectMapper = new ObjectMapper();

	public Map<String, Double> parse(String emotionString) {
		Map<String, Double> emotionMap = new HashMap<>();

		if (emotionString == null || emotionString.trim().isEmpty()) {
			return emotionMap;
		}

		try {
			// 먼저 JSON 형태인지 확인 ('{' 로 시작하면 JSON)
			String trimmed = emotionString.trim();
			if (trimmed.startsWith("{")) {
				return parseJsonFormat(trimmed);
			} else {
				return parseTextFormat(trimmed);
			}
		} catch (Exception e) {
			log.warn("감정 파싱 실패: {}", emotionString, e);
			return emotionMap;
		}
	}

	/**
	 * JSON 형태 파싱: {"기쁨": "10%", "슬픔": "40%", ...}
	 */
	private Map<String, Double> parseJsonFormat(String jsonString) throws Exception {
		Map<String, Double> emotionMap = new HashMap<>();

		// JSON을 Map<String, Object>로 파싱
		Map<String, Object> rawMap = objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String emotion = entry.getKey();
			Object value = entry.getValue();

			double percent = 0.0;
			if (value instanceof Number) {
				percent = ((Number) value).doubleValue();
			} else if (value instanceof String) {
				String valueStr = ((String) value).trim();
				// "10%" → "10" 변환
				if (valueStr.endsWith("%")) {
					valueStr = valueStr.substring(0, valueStr.length() - 1).trim();
				}
				percent = Double.parseDouble(valueStr);
			}

			// 0-100 범위 검증
			if (percent >= 0 && percent <= 100) {
				emotionMap.put(emotion, percent);
			}
		}

		return emotionMap;
	}

	/**
	 * 텍스트 형태 파싱: "기쁨: 10%, 슬픔: 40%, ..." (기존 로직 유지)
	 */
	private Map<String, Double> parseTextFormat(String textString) {
		Map<String, Double> emotionMap = new HashMap<>();

		Matcher matcher = EMOTION_PATTERN.matcher(textString);
		while (matcher.find()) {
			String emotion = matcher.group(1).trim();
			double percent = Double.parseDouble(matcher.group(2));
			if (percent >= 0 && percent <= 100) {
				emotionMap.put(emotion, percent);
			}
		}

		return emotionMap;
	}

	public String format(Map<String, Double> emotions) {
		if (emotions == null || emotions.isEmpty()) {
			return "";
		}
		return emotions.entrySet().stream()
			.map(e -> String.format("%s: %.1f%%", e.getKey(), e.getValue()))
			.reduce((a, b) -> a + ", " + b)
			.orElse("");
	}
}
