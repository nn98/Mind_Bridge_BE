package com.example.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.backend.dto.chat.ChatMessageDto;
import com.example.backend.dto.chat.ChatMessageRequest;
import com.example.backend.dto.chat.ChatMessageType;
import com.example.backend.dto.chat.ChatSessionDto;
import com.example.backend.dto.chat.SessionRequest;
import com.example.backend.entity.ChatMessageEntity;
import com.example.backend.entity.ChatSessionEntity;
import com.example.backend.security.ChatAuth;
import com.example.backend.security.SecurityUtil;
import com.example.backend.service.ChatService;
import com.example.backend.service.DailyMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ChatController.class,
        excludeFilters = {
                @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        com.example.backend.security.JwtAuthenticationFilter.class,
                        com.example.backend.security.SecurityConfig.class,
                        com.example.backend.security.CustomUserDetailsService.class
                })
        }
)
@AutoConfigureMockMvc(addFilters = true)
@Import({TestSecurityConfig.class})
@EnableMethodSecurity(prePostEnabled = true)
@DisplayName("ChatController 테스트")
class ChatControllerTest {
    
    private static final String BASE_URL = "/api/chat";
    private static final String SESSION_ID = "test-session-id";
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_NAME = "테스트 사용자";
    private static final String USER_MESSAGE = "안녕하세요";
    
    @Autowired
    MockMvc mvc;
    
    @Autowired
    ObjectMapper objectMapper;
    
    @MockitoBean
    DailyMetricsService dailyMetricsService;
    
    @MockitoBean
    SecurityUtil securityUtil;
    
    @MockitoBean
    ChatService chatService;
    
    @MockitoBean(name = "chatAuth")
    ChatAuth chatAuth;
    
    @Test
    @DisplayName("POST /api/chat/session/save → 200 + 세션 엔티티")
    void receiveAnalysis_success() throws Exception {
        SessionRequest request = SessionRequest.builder()
                .sessionId(SESSION_ID)
                .userEmail(USER_EMAIL)
                .userName(USER_NAME)
                .summary("테스트 상담")
                .build();
        
        ChatSessionEntity saved = ChatSessionEntity.builder()
                .sessionId(SESSION_ID)
                .userEmail(USER_EMAIL)
                .userName(USER_NAME)
                .summary("테스트 상담")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        given(chatService.saveSession(any(SessionRequest.class))).willReturn(saved);
        
        String json = objectMapper.writeValueAsString(request);
        
        String body = mvc.perform(post(BASE_URL + "/session/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        ChatSessionEntity response = objectMapper.readValue(body, ChatSessionEntity.class);
        
        assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.getUserEmail()).isEqualTo(USER_EMAIL);
        
        then(chatService).should().saveSession(any(SessionRequest.class));
        then(dailyMetricsService).should().increaseChatCount();
    }
    
    @Test
    @DisplayName("POST /api/chat/message/save → 200 + 메시지 엔티티")
    void saveMessage_success() throws Exception {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .sessionId(SESSION_ID)
                .messageType(ChatMessageType.USER)
                .messageContent(USER_MESSAGE)
                .userEmail(USER_EMAIL)
                .chatStyle("default")
                .build();
        
        ChatMessageEntity saved = ChatMessageEntity.builder()
                .messageId(1L)
                .sessionId(SESSION_ID)
                .messageType(ChatMessageType.USER)
                .messageContent(USER_MESSAGE)
                .userEmail(USER_EMAIL)
                .chatStyle("default")
                .createdAt(LocalDateTime.now())
                .build();
        
        given(chatService.saveMessage(any(ChatMessageRequest.class))).willReturn(saved);
        
        String json = objectMapper.writeValueAsString(request);
        
        String body = mvc.perform(post(BASE_URL + "/message/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        ChatMessageEntity response = objectMapper.readValue(body, ChatMessageEntity.class);
        
        assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.getMessageContent()).isEqualTo(USER_MESSAGE);
        
        then(chatService).should().saveMessage(any(ChatMessageRequest.class));
    }
    
    @Test
    @DisplayName("GET /api/chat/analysis/search → 이메일+이름으로 세션 조회")
    void getCounsellings_success() throws Exception {
        ChatSessionEntity session = ChatSessionEntity.builder()
                .sessionId(SESSION_ID)
                .userEmail(USER_EMAIL)
                .userName(USER_NAME)
                .summary("테스트 상담")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        given(chatService.getSessionsByEmailAndName(USER_EMAIL, USER_NAME))
                .willReturn(List.of(session));
        
        String body = mvc.perform(get(BASE_URL + "/analysis/search")
                        .param("email", USER_EMAIL)
                        .param("name", USER_NAME))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        ChatSessionEntity[] response =
                objectMapper.readValue(body, ChatSessionEntity[].class);
        
        assertThat(response).hasSize(1);
        assertThat(response[0].getSessionId()).isEqualTo(SESSION_ID);
        
        then(chatService).should().getSessionsByEmailAndName(USER_EMAIL, USER_NAME);
    }
    
    @Test
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    @DisplayName("GET /api/chat/sessions → 인증 사용자 세션 목록 조회")
    void getChatSessions_authenticated_success() throws Exception {
        ChatSessionDto dto = new ChatSessionDto(
                SESSION_ID,
                USER_EMAIL,
                USER_NAME,
                "테스트 상담",
                Map.of("joy", 0.3),
                "중간",
                "가족 지지",
                "우울 증상",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        
        given(securityUtil.requirePrincipalEmail(any())).willReturn(USER_EMAIL);
        given(chatService.getChatSessionsByUserEmail(USER_EMAIL))
                .willReturn(List.of(dto));
        
        String body = mvc.perform(get(BASE_URL + "/sessions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        ChatSessionDto[] response =
                objectMapper.readValue(body, ChatSessionDto[].class);
        
        assertThat(response).hasSize(1);
        assertThat(response[0].sessionId()).isEqualTo(SESSION_ID);
        assertThat(response[0].userEmail()).isEqualTo(USER_EMAIL);
        
        then(securityUtil).should().requirePrincipalEmail(any());
        then(chatService).should().getChatSessionsByUserEmail(USER_EMAIL);
    }
    
    @Test
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    @DisplayName("GET /api/chat/messages/{sessionId} → ApiResponse<List<ChatMessageDto>>")
    void getMessages_success() throws Exception {
        ChatMessageDto dto = new ChatMessageDto(
                1L,
                SESSION_ID,
                ChatMessageType.USER,
                USER_MESSAGE,
                null,
                USER_EMAIL,
                "default",
                LocalDateTime.now()
        );
        
        given(chatAuth.canAccessSession(SESSION_ID, USER_EMAIL)).willReturn(true);
        given(chatService.getMessagesBySessionId(SESSION_ID, USER_EMAIL))
                .willReturn(List.of(dto));
        
        mvc.perform(get(BASE_URL + "/messages/{sessionId}", SESSION_ID)
                        .with(user(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.data[0].userEmail").value(USER_EMAIL));
        
        then(chatAuth).should().canAccessSession(SESSION_ID, USER_EMAIL);
        then(chatService).should().getMessagesBySessionId(SESSION_ID, USER_EMAIL);
    }
    
    @Test
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    @DisplayName("GET /api/chat/sessions/{sessionId} → ApiResponse<ChatSessionDto>")
    void getChatSession_success() throws Exception {
        ChatSessionDto dto = new ChatSessionDto(
                SESSION_ID,
                USER_EMAIL,
                USER_NAME,
                "테스트 상담",
                Map.of("joy", 0.3),
                "중간",
                "가족 지지",
                "우울 증상",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        
        given(chatAuth.canAccessSession(SESSION_ID, USER_EMAIL)).willReturn(true);
        given(chatService.getSessionById(SESSION_ID))
                .willReturn(Optional.of(dto));
        
        mvc.perform(get(BASE_URL + "/sessions/{sessionId}", SESSION_ID)
                        .with(user(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.data.userEmail").value(USER_EMAIL));
        
        then(chatAuth).should().canAccessSession(SESSION_ID, USER_EMAIL);
        then(chatService).should().getSessionById(SESSION_ID);
    }
    
    @Test
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    @DisplayName("DELETE /api/chat/sessions/{sessionId} → 세션 삭제 성공")
    void deleteSession_success() throws Exception {
        given(chatAuth.canDeleteSession(SESSION_ID, USER_EMAIL)).willReturn(true);
        
        mvc.perform(delete(BASE_URL + "/sessions/{sessionId}", SESSION_ID)
                        .with(user(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("세션이 삭제되었습니다."));
        
        then(chatAuth).should().canDeleteSession(SESSION_ID, USER_EMAIL);
        then(chatService).should().deleteSession(SESSION_ID);
    }
}
