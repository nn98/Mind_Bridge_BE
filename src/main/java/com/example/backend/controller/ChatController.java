package com.example.backend.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.common.error.NotFoundException;
import com.example.backend.dto.chat.ChatMessageDto;
import com.example.backend.dto.chat.ChatMessageRequest;
import com.example.backend.dto.chat.ChatSessionDto;
import com.example.backend.dto.chat.SessionRequest;
import com.example.backend.dto.common.ApiResponse;
import com.example.backend.entity.ChatMessageEntity;
import com.example.backend.entity.ChatSessionEntity;
import com.example.backend.security.SecurityUtil;
import com.example.backend.service.ChatService;
import com.example.backend.service.DailyMetricsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private static final String MESSAGE_SESSION_NOT_FOUND = "세션을 찾을 수 없습니다.";
    
    private final DailyMetricsService dailyMetricsService;
    private final SecurityUtil securityUtil;
    private final ChatService chatService;
    
    @PostMapping("/session/save")
    public ResponseEntity<ChatSessionEntity> receiveAnalysis(@RequestBody SessionRequest sessionRequest) {
        log.info("FastAPI 분석 결과 수신 - sessionId: {}", sessionRequest.getSessionId());
        
        ChatSessionEntity saved = chatService.saveSession(sessionRequest);
        dailyMetricsService.increaseChatCount();
        
        return ResponseEntity.ok(saved);
    }
    
    @PostMapping("/message/save")
    public ResponseEntity<ChatMessageEntity> saveMessage(@RequestBody ChatMessageRequest request) {
        log.info("FastAPI 메시지 저장 - sessionId: {}", request.getSessionId());
        
        ChatMessageEntity saved = chatService.saveMessage(request);
        return ResponseEntity.ok(saved);
    }
    
    @GetMapping("/analysis/search")
    public ResponseEntity<List<ChatSessionEntity>> getCounsellings(
            @RequestParam String email,
            @RequestParam String name
    ) {
        List<ChatSessionEntity> result = chatService.getSessionsByEmailAndName(email, name);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatSessionDto>> getChatSessions(Authentication authentication) {
        String email = securityUtil.requirePrincipalEmail(authentication);
        List<ChatSessionDto> sessions = chatService.getChatSessionsByUserEmail(email);
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(sessions);
    }
    
    @GetMapping("/messages/{sessionId}")
    @PreAuthorize("@chatAuth.canAccessSession(#sessionId, authentication.name) or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getMessages(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        List<ChatMessageDto> result =
                chatService.getMessagesBySessionId(sessionId, authentication.getName());
        return ok(result, "메시지를 성공적으로 조회했습니다.");
    }
    
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("@chatAuth.canAccessSession(#sessionId, authentication.name) or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChatSessionDto>> getChatSession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        ChatSessionDto session = chatService.getSessionById(sessionId)
                .orElseThrow(() -> new NotFoundException(MESSAGE_SESSION_NOT_FOUND));
        return ok(session, "세션을 성공적으로 조회했습니다.");
    }
    
    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("@chatAuth.canDeleteSession(#sessionId, authentication.name) or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteSession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        chatService.deleteSession(sessionId);
        return ok("세션이 삭제되었습니다.");
    }
    
    private <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }
    
    private ResponseEntity<ApiResponse<String>> ok(String message) {
        return ResponseEntity.ok(ApiResponse.success(message));
    }
}
