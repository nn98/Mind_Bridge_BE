package com.example.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.error.ForbiddenException;
import com.example.backend.common.error.NotFoundException;
import com.example.backend.dto.chat.ChatMessageDto;
import com.example.backend.dto.chat.ChatMessageRequest;
import com.example.backend.dto.chat.ChatSessionDto;
import com.example.backend.dto.chat.SessionRequest;
import com.example.backend.entity.ChatMessageEntity;
import com.example.backend.entity.ChatSessionEntity;
import com.example.backend.mapper.ChatMapper;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private static final String MESSAGE_SESSION_NOT_FOUND = "세션을 찾을 수 없습니다: ";
    private static final String MESSAGE_SESSION_UPDATE_FORBIDDEN = "세션 수정 권한이 없습니다.";
    private static final String MESSAGE_SESSION_ACCESS_FORBIDDEN = "세션 접근 권한이 없습니다.";
    
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMapper chatMapper;
    
    @Transactional
    public ChatMessageEntity saveMessage(ChatMessageRequest request) {
        log.debug("메시지 저장 요청 - sessionId: {}, type: {}", request.getSessionId(), request.getMessageType());
        
        ChatMessageEntity entity = chatMapper.toEntity(request);
        ChatMessageEntity saved = chatMessageRepository.save(entity);
        
        log.info("메시지 저장 완료 - messageId: {}, sessionId: {}", saved.getMessageId(), saved.getSessionId());
        return saved;
    }
    
    @Deprecated
    @Transactional(readOnly = true)
    public List<ChatMessageEntity> getMessagesBySessionId(String sessionId) {
        log.debug("메시지 조회 - sessionId: {}", sessionId);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
    
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesBySessionId(String sessionId, String userEmail) {
        log.debug("메시지 조회 - sessionId: {}, userEmail: {}", sessionId, userEmail);
        
        validateSessionAccess(sessionId, userEmail);
        
        List<ChatMessageEntity> entities = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessageDto> messages = chatMapper.toMessageDtoList(entities);
        
        log.info("메시지 {} 건 조회 완료", messages.size());
        return messages;
    }
    
    @Transactional
    public ChatSessionEntity saveSession(SessionRequest request) {
        log.debug("세션 저장 요청 - sessionId: {}, userEmail: {}", request.getSessionId(), request.getUserEmail());
        
        ChatSessionEntity entity = chatMapper.toEntity(request);
        ChatSessionEntity saved = chatSessionRepository.save(entity);
        
        log.info("세션 저장 완료 - sessionId: {}, userEmail: {}", saved.getSessionId(), saved.getUserEmail());
        return saved;
    }
    
    @Deprecated
    @Transactional
    public ChatSessionEntity saveAnalysis(Map<String, Object> payload) {
        log.debug("분석 결과 저장 - payload: {}", payload.keySet());
        
        ChatSessionEntity entity = chatMapper.toAnalysisEntity(payload);
        ChatSessionEntity saved = chatSessionRepository.save(entity);
        
        log.info("분석 결과 저장 완료 - sessionId: {}, userEmail: {}", saved.getSessionId(), saved.getUserEmail());
        return saved;
    }
    
    @Deprecated
    @Transactional
    public ChatSessionEntity updateSession(String sessionId, SessionRequest request) {
        log.debug("세션 업데이트 - sessionId: {}", sessionId);
        
        ChatSessionEntity entity = findSessionOrThrow(sessionId);
        validateSessionOwner(entity, request.getUserEmail());
        
        chatMapper.updateEntity(entity, request);
        ChatSessionEntity updated = chatSessionRepository.save(entity);
        
        log.info("세션 업데이트 완료 - sessionId: {}", updated.getSessionId());
        return updated;
    }
    
    @Deprecated
    @Transactional(readOnly = true)
    public Optional<ChatSessionEntity> getSessionByIdEntity(String sessionId) {
        log.debug("세션 조회 (Entity) - sessionId: {}", sessionId);
        return chatSessionRepository.findBySessionId(sessionId);
    }
    
    @Transactional(readOnly = true)
    public Optional<ChatSessionDto> getSessionById(String sessionId) {
        log.debug("세션 조회 - sessionId: {}", sessionId);
        
        return chatSessionRepository.findBySessionId(sessionId)
                .map(chatMapper::toDto);
    }
    
    @Transactional(readOnly = true)
    public List<ChatSessionEntity> getSessionsByEmailAndName(String userEmail, String userName) {
        log.debug("세션 조회 - userEmail: {}, userName: {}", userEmail, userName);
        
        List<ChatSessionEntity> sessions = chatSessionRepository
                .findAllByUserEmailAndUserNameOrderBySessionIdDesc(userEmail, userName);
        
        log.info("세션 {} 건 조회 완료", sessions.size());
        return sessions;
    }
    
    @Transactional(readOnly = true)
    public List<ChatSessionDto> getChatSessionsByUserEmail(String userEmail) {
        log.debug("사용자 세션 조회 - userEmail: {}", userEmail);
        
        List<ChatSessionEntity> entities = chatSessionRepository
                .findByUserEmailOrderByCreatedAtDesc(userEmail);
        List<ChatSessionDto> sessions = chatMapper.toSessionDtoList(entities);
        
        log.info("사용자 세션 {} 건 조회 완료", sessions.size());
        return sessions;
    }
    
    @Transactional
    public void deleteSession(String sessionId) {
        log.debug("세션 삭제 - sessionId: {}", sessionId);
        
        ChatSessionEntity entity = findSessionOrThrow(sessionId);
        
        chatMessageRepository.deleteAllBySessionId(sessionId);
        chatSessionRepository.delete(entity);
        
        log.info("세션 삭제 완료 - sessionId: {}", sessionId);
    }
    
    private ChatSessionEntity findSessionOrThrow(String sessionId) {
        return chatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException(MESSAGE_SESSION_NOT_FOUND + sessionId));
    }
    
    private void validateSessionAccess(String sessionId, String userEmail) {
        boolean exists = chatSessionRepository.existsBySessionIdAndUserEmail(sessionId, userEmail);
        if (!exists) {
            throw new ForbiddenException(MESSAGE_SESSION_ACCESS_FORBIDDEN);
        }
    }
    
    private void validateSessionOwner(ChatSessionEntity entity, String requestedEmail) {
        if (requestedEmail != null && !entity.getUserEmail().equals(requestedEmail)) {
            throw new ForbiddenException(MESSAGE_SESSION_UPDATE_FORBIDDEN);
        }
    }
}
