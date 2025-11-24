package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;

import com.example.backend.entity.DailyMetricsEntity;
import com.example.backend.repository.DailyMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyMetricsService 테스트")
class DailyMetricsServiceTest {
    
    @Mock
    DailyMetricsRepository dailyMetricsRepository;
    
    @InjectMocks
    DailyMetricsService dailyMetricsService;
    
    @Test
    @DisplayName("increaseUserCount - 기존 행이 있으면 카운터만 증가시키고 새로 insert 하지 않는다")
    void increaseUserCount_existingRow_onlyIncrement() {
        LocalDate today = LocalDate.now();
        given(dailyMetricsRepository.incrementDailyUsers(today)).willReturn(1);
        
        assertThatCode(() -> dailyMetricsService.increaseUserCount())
                .doesNotThrowAnyException();
        
        then(dailyMetricsRepository).should().incrementDailyUsers(today);
        then(dailyMetricsRepository).should(never()).save(any(DailyMetricsEntity.class));
    }
    
    @Test
    @DisplayName("increaseUserCount - 오늘 행이 없으면 (UPDATE 결과 0) 새 DailyMetricsEntity를 저장한다")
    void increaseUserCount_noRow_createsNewMetrics() {
        LocalDate today = LocalDate.now();
        given(dailyMetricsRepository.incrementDailyUsers(today)).willReturn(0);
        
        dailyMetricsService.increaseUserCount();
        
        then(dailyMetricsRepository).should().incrementDailyUsers(today);
        then(dailyMetricsRepository).should().save(argThat(entity ->
                entity.getStatDate().equals(today)
                        && entity.getLoginCount() == 1
                        && entity.getChatCount() == 0
        ));
    }
    
    @Test
    @DisplayName("increaseChatCount - 기존 행이 있으면 카운터만 증가시키고 새로 insert 하지 않는다")
    void increaseChatCount_existingRow_onlyIncrement() {
        LocalDate today = LocalDate.now();
        given(dailyMetricsRepository.incrementDailyChats(today)).willReturn(1);
        
        assertThatCode(() -> dailyMetricsService.increaseChatCount())
                .doesNotThrowAnyException();
        
        then(dailyMetricsRepository).should().incrementDailyChats(today);
        then(dailyMetricsRepository).should(never()).save(any(DailyMetricsEntity.class));
    }
    
    @Test
    @DisplayName("increaseChatCount - 오늘 행이 없으면 (UPDATE 결과 0) 새 DailyMetricsEntity를 저장한다")
    void increaseChatCount_noRow_createsNewMetrics() {
        LocalDate today = LocalDate.now();
        given(dailyMetricsRepository.incrementDailyChats(today)).willReturn(0);
        
        dailyMetricsService.increaseChatCount();
        
        then(dailyMetricsRepository).should().incrementDailyChats(today);
        then(dailyMetricsRepository).should().save(argThat(entity ->
                entity.getStatDate().equals(today)
                        && entity.getLoginCount() == 0
                        && entity.getChatCount() == 1
        ));
    }
}
