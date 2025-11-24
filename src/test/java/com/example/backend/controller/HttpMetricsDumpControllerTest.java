package com.example.backend.controller;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpMetricsDumpControllerTest {
    
    @Test
    @DisplayName("dumpHttpServerRequests - 모든 시리즈를 덤프하고 기본 필드를 채운다")
    void dumpHttpServerRequests_basic() {
        MeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsDumpController controller = new HttpMetricsDumpController(registry);
        
        Timer t1 = Timer.builder("http.server.requests")
                .tag("uri", "/api/foo")
                .tag("method", "GET")
                .tag("status", "200")
                .register(registry);
        t1.record(100, MILLISECONDS);
        t1.record(200, MILLISECONDS);
        
        Map<String, Object> payload =
                controller.dumpHttpServerRequests(null, null, null, false, false);
        
        assertThat(payload.get("timestamp")).isInstanceOf(String.class);
        assertThat(payload.get("seriesCount")).isEqualTo(1);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) payload.get("data");
        assertThat(data).hasSize(1);
        
        Map<String, Object> row = data.get(0);
        assertThat(row.get("name")).isEqualTo("http.server.requests");
        assertThat(row.get("uri")).isEqualTo("/api/foo");
        assertThat(row.get("method")).isEqualTo("GET");
        assertThat(row.get("status")).isEqualTo("200");
        assertThat(row.get("count")).isEqualTo(2L);
        
        Double total = (Double) row.get("totalTimeSeconds");
        assertThat(total).isGreaterThan(0.0);
        Double avg = (Double) row.get("avgSeconds");
        assertThat(avg).isGreaterThan(0.0);
        Double max = (Double) row.get("maxSeconds");
        assertThat(max).isGreaterThan(0.0);
        
        assertThat(row.get("p95Seconds")).isNull();
        assertThat(row.get("p99Seconds")).isNull();
    }
    
    @Test
    @DisplayName("dumpHttpServerRequests - uri/method/status 필터와 includeZero 옵션을 적용한다")
    void dumpHttpServerRequests_filtersAndIncludeZero() {
        MeterRegistry registry = new SimpleMeterRegistry();
        HttpMetricsDumpController controller = new HttpMetricsDumpController(registry);
        
        Timer matched = Timer.builder("http.server.requests")
                .tag("uri", "/api/bar")
                .tag("method", "POST")
                .tag("status", "201")
                .register(registry);
        matched.record(150, MILLISECONDS);
        
        Timer otherUri = Timer.builder("http.server.requests")
                .tag("uri", "/other")
                .tag("method", "POST")
                .tag("status", "201")
                .register(registry);
        otherUri.record(50, MILLISECONDS);
        
        Timer zeroCount = Timer.builder("http.server.requests")
                .tag("uri", "/api/bar")
                .tag("method", "POST")
                .tag("status", "500")
                .register(registry);
        
        Map<String, Object> payload =
                controller.dumpHttpServerRequests("/api/bar", "POST", "201", false, false);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) payload.get("data");
        assertThat(data).hasSize(1);
        
        Map<String, Object> row = data.get(0);
        assertThat(row.get("uri")).isEqualTo("/api/bar");
        assertThat(row.get("method")).isEqualTo("POST");
        assertThat(row.get("status")).isEqualTo("201");
        assertThat(row.get("count")).isEqualTo(1L);
        
        Map<String, Object> payloadIncludeZero =
                controller.dumpHttpServerRequests("/api/bar", "POST", null, true, false);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataIncludeZero =
                (List<Map<String, Object>>) payloadIncludeZero.get("data");
        
        assertThat(dataIncludeZero)
                .extracting(m -> (String) m.get("status"))
                .containsExactlyInAnyOrder("201", "500");
    }
}
