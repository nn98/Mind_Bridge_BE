package com.example.backend.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.backend.common.error.NotFoundException;
import com.example.backend.dto.chat.ChatMessageDto;
import com.example.backend.dto.chat.ChatMessageRequest;
import com.example.backend.dto.chat.ChatMessageType;
import com.example.backend.dto.chat.ChatSessionDto;
import com.example.backend.dto.chat.SessionRequest;
import com.example.backend.entity.ChatMessageEntity;
import com.example.backend.entity.ChatSessionEntity;
import com.example.backend.mapper.ChatMapper;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.ChatSessionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService 테스트")
class ChatServiceTest {

	private static final String SESSION_ID = "test-session-id";
	private static final String USER_EMAIL = "test@example.com";
	private static final String USER_NAME = "테스트 사용자";
	private static final String USER_MESSAGE = "안녕하세요";
	private static final String AI_MESSAGE = "안녕하세요! 무엇을 도와드릴까요?";

	@Mock
	private ChatMessageRepository chatMessageRepository;

	@Mock
	private ChatSessionRepository chatSessionRepository;

	@Mock
	private ChatMapper chatMapper;

	@InjectMocks
	private ChatService chatService;

	// 테스트용 데이터
	private ChatSessionEntity testSession;
	private ChatMessageEntity testUserMessage;
	private ChatMessageEntity testAiMessage;
	private SessionRequest testSessionRequest;
	private ChatMessageRequest testMessageRequest;
	private ChatSessionDto testSessionDto;
	private ChatMessageDto testMessageDto;

	@BeforeEach
	void setUp() {
		LocalDateTime now = LocalDateTime.now();
		testSession = buildSession(now);
		testUserMessage = buildUserMessage(now);
		testAiMessage = buildAiMessage(now);
		testSessionRequest = buildSessionRequest();
		testMessageRequest = buildMessageRequest();
		testSessionDto = buildSessionDto(now);
		testMessageDto = buildMessageDto(now);
	}

	@Test
	@DisplayName("메시지 저장 성공 테스트")
	void saveMessage_Success() {
		when(chatMapper.toEntity(testMessageRequest)).thenReturn(testUserMessage);
		when(chatMessageRepository.save(testUserMessage)).thenReturn(testUserMessage);

		ChatMessageEntity result = chatService.saveMessage(testMessageRequest);

		assertThat(result).isNotNull();
		assertThat(result.getMessageId()).isEqualTo(1L);
		assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
		assertThat(result.getMessageType()).isEqualTo(ChatMessageType.USER);
		assertThat(result.getMessageContent()).isEqualTo(USER_MESSAGE);

		verify(chatMapper).toEntity(testMessageRequest);
		verify(chatMessageRepository).save(testUserMessage);
	}

	@Test
	@DisplayName("세션별 메시지 조회 테스트 - 기존 메서드")
	void getMessagesBySessionId_Success() {
		List<ChatMessageEntity> mockMessages = List.of(testUserMessage, testAiMessage);
		when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(SESSION_ID)).thenReturn(mockMessages);

		List<ChatMessageEntity> result = chatService.getMessagesBySessionId(SESSION_ID);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getMessageType()).isEqualTo(ChatMessageType.USER);
		assertThat(result.get(1).getMessageType()).isEqualTo(ChatMessageType.AI);

		verify(chatMessageRepository).findBySessionIdOrderByCreatedAtAsc(SESSION_ID);
	}

	@Test
	@DisplayName("세션별 메시지 조회 테스트 - 권한 확인")
	void getMessagesBySessionId_withAuth_success() {
		List<ChatMessageEntity> mockMessages = List.of(testUserMessage, testAiMessage);
		List<ChatMessageDto> mockDtos = List.of(testMessageDto);

		when(chatSessionRepository.existsBySessionIdAndUserEmail(SESSION_ID, USER_EMAIL)).thenReturn(true);
		when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(SESSION_ID)).thenReturn(mockMessages);
		when(chatMapper.toMessageDtoList(mockMessages)).thenReturn(mockDtos);

		List<ChatMessageDto> result = chatService.getMessagesBySessionId(SESSION_ID, USER_EMAIL);

		assertThat(result).hasSize(1);

		verify(chatSessionRepository).existsBySessionIdAndUserEmail(SESSION_ID, USER_EMAIL);
		verify(chatMessageRepository).findBySessionIdOrderByCreatedAtAsc(SESSION_ID);
		verify(chatMapper).toMessageDtoList(mockMessages);
	}

	@Test
	@DisplayName("세션 저장 성공 테스트")
	void saveSession_success() {
		when(chatMapper.toEntity(testSessionRequest)).thenReturn(testSession);
		when(chatSessionRepository.save(testSession)).thenReturn(testSession);

		ChatSessionEntity result = chatService.saveSession(testSessionRequest);

		assertThat(result).isNotNull();
		assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
		assertThat(result.getUserEmail()).isEqualTo(USER_EMAIL);
		assertThat(result.getUserName()).isEqualTo(USER_NAME);

		verify(chatMapper).toEntity(testSessionRequest);
		verify(chatSessionRepository).save(testSession);
	}

	@Test
	@DisplayName("분석 결과 저장 테스트")
	void saveAnalysis_success() {
		Map<String, Object> payload = Map.of(
				"session_id", SESSION_ID,
				"user_email", USER_EMAIL,
				"user_name", USER_NAME,
				"summary", "상담 요약",
				"emotions", "{\"joy\": 0.3, \"sadness\": 0.7}",
				"risk_factors", "우울 증상",
				"primary_risk", "중간",
				"protective_factors", "가족 지지"
		);

		when(chatMapper.toAnalysisEntity(payload)).thenReturn(testSession);
		when(chatSessionRepository.save(testSession)).thenReturn(testSession);

		ChatSessionEntity result = chatService.saveAnalysis(payload);

		assertThat(result).isNotNull();
		assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
		assertThat(result.getSummary()).isEqualTo("테스트 상담");

		verify(chatMapper).toAnalysisEntity(payload);
		verify(chatSessionRepository).save(testSession);
	}

	@Test
	@DisplayName("세션 업데이트 성공 테스트")
	void updateSession_success() {
		when(chatSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(testSession));
		when(chatSessionRepository.save(testSession)).thenReturn(testSession);

		ChatSessionEntity result = chatService.updateSession(SESSION_ID, testSessionRequest);

		assertThat(result).isNotNull();
		assertThat(result.getSessionId()).isEqualTo(SESSION_ID);

		verify(chatSessionRepository).findBySessionId(SESSION_ID);
		verify(chatMapper).updateEntity(testSession, testSessionRequest);
		verify(chatSessionRepository).save(testSession);
	}

	@Test
	@DisplayName("세션 업데이트 - 세션 없음 예외 테스트")
	void updateSession_sessionNotFound_throwsException() {
		String missingId = "non-existent-session";
		when(chatSessionRepository.findBySessionId(missingId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.updateSession(missingId, testSessionRequest))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("세션을 찾을 수 없습니다: " + missingId);

		verify(chatSessionRepository).findBySessionId(missingId);
	}

	@Test
	@DisplayName("이메일과 이름으로 세션 조회 테스트")
	void getSessionsByEmailAndName_success() {
		List<ChatSessionEntity> mockSessions = List.of(testSession);
		when(chatSessionRepository
				.findAllByUserEmailAndUserNameOrderBySessionIdDesc(USER_EMAIL, USER_NAME))
				.thenReturn(mockSessions);

		List<ChatSessionEntity> result =
				chatService.getSessionsByEmailAndName(USER_EMAIL, USER_NAME);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSessionId()).isEqualTo(SESSION_ID);

		verify(chatSessionRepository)
				.findAllByUserEmailAndUserNameOrderBySessionIdDesc(USER_EMAIL, USER_NAME);
	}

	@Test
	@DisplayName("세션 ID로 세션 조회 성공 테스트")
	void getSessionById_success() {
		when(chatSessionRepository.findBySessionId(SESSION_ID))
				.thenReturn(Optional.of(testSession));
		when(chatMapper.toDto(testSession)).thenReturn(testSessionDto);

		Optional<ChatSessionDto> result = chatService.getSessionById(SESSION_ID);

		assertThat(result).isPresent();
		assertThat(result.get().sessionId()).isEqualTo(SESSION_ID);

		verify(chatSessionRepository).findBySessionId(SESSION_ID);
		verify(chatMapper).toDto(testSession);
	}

	@Test
	@DisplayName("세션 ID로 세션 조회 성공 테스트 - Entity용")
	void getSessionByIdEntity_success() {
		when(chatSessionRepository.findBySessionId(SESSION_ID))
				.thenReturn(Optional.of(testSession));

		Optional<ChatSessionEntity> result =
				chatService.getSessionByIdEntity(SESSION_ID);

		assertThat(result).isPresent();
		assertThat(result.get().getSessionId()).isEqualTo(SESSION_ID);

		verify(chatSessionRepository).findBySessionId(SESSION_ID);
	}

	@Test
	@DisplayName("세션 ID로 세션 조회 - 없는 세션")
	void getSessionById_notFound_returnsEmpty() {
		String missingId = "non-existent-session";
		when(chatSessionRepository.findBySessionId(missingId))
				.thenReturn(Optional.empty());

		Optional<ChatSessionDto> result = chatService.getSessionById(missingId);

		assertThat(result).isEmpty();

		verify(chatSessionRepository).findBySessionId(missingId);
	}

	@Test
	@DisplayName("사용자 이메일로 세션 조회 테스트")
	void getChatSessionsByUserEmail_success() {
		List<ChatSessionEntity> mockEntities = List.of(testSession);
		List<ChatSessionDto> mockDtos = List.of(testSessionDto);

		when(chatSessionRepository.findByUserEmailOrderByCreatedAtDesc(USER_EMAIL))
				.thenReturn(mockEntities);
		when(chatMapper.toSessionDtoList(mockEntities)).thenReturn(mockDtos);

		List<ChatSessionDto> result =
				chatService.getChatSessionsByUserEmail(USER_EMAIL);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).sessionId()).isEqualTo(SESSION_ID);
		assertThat(result.get(0).userEmail()).isEqualTo(USER_EMAIL);

		verify(chatSessionRepository).findByUserEmailOrderByCreatedAtDesc(USER_EMAIL);
		verify(chatMapper).toSessionDtoList(mockEntities);
	}

	@Test
	@DisplayName("사용자 이메일로 세션 조회 - 빈 결과")
	void getChatSessionsByUserEmail_emptyResult_returnsEmptyList() {
		String unknownEmail = "nonexistent@example.com";

		when(chatSessionRepository.findByUserEmailOrderByCreatedAtDesc(unknownEmail))
				.thenReturn(List.of());
		when(chatMapper.toSessionDtoList(anyList())).thenReturn(List.of());

		List<ChatSessionDto> result =
				chatService.getChatSessionsByUserEmail(unknownEmail);

		assertThat(result).isEmpty();

		verify(chatSessionRepository).findByUserEmailOrderByCreatedAtDesc(unknownEmail);
		verify(chatMapper).toSessionDtoList(anyList());
	}

	@Test
	@DisplayName("세션 삭제 성공 테스트")
	void deleteSession_success() {
		when(chatSessionRepository.findBySessionId(SESSION_ID))
				.thenReturn(Optional.of(testSession));

		chatService.deleteSession(SESSION_ID);

		verify(chatSessionRepository).findBySessionId(SESSION_ID);
		verify(chatMessageRepository).deleteAllBySessionId(SESSION_ID);
		verify(chatSessionRepository).delete(testSession);
	}

	@Test
	@DisplayName("세션 삭제 - 세션 없음 예외")
	void deleteSession_sessionNotFound_throwsException() {
		String missingId = "non-existent-session";
		when(chatSessionRepository.findBySessionId(missingId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.deleteSession(missingId))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("세션을 찾을 수 없습니다: " + missingId);

		verify(chatSessionRepository).findBySessionId(missingId);
	}

	private ChatSessionEntity buildSession(LocalDateTime now) {
		return ChatSessionEntity.builder()
				.sessionId(SESSION_ID)
				.userEmail(USER_EMAIL)
				.userName(USER_NAME)
				.summary("테스트 상담")
				.emotions("{\"joy\": 0.3, \"sadness\": 0.7}")
				.primaryRisk("중간")
				.riskFactors("우울 증상")
				.protectiveFactors("가족 지지")
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	private ChatMessageEntity buildUserMessage(LocalDateTime now) {
		return ChatMessageEntity.builder()
				.messageId(1L)
				.sessionId(SESSION_ID)
				.messageType(ChatMessageType.USER)
				.messageContent(USER_MESSAGE)
				.userEmail(USER_EMAIL)
				.chatStyle("default")
				.createdAt(now.minusMinutes(5))
				.build();
	}

	private ChatMessageEntity buildAiMessage(LocalDateTime now) {
		return ChatMessageEntity.builder()
				.messageId(2L)
				.sessionId(SESSION_ID)
				.messageType(ChatMessageType.AI)
				.messageContent(AI_MESSAGE)
				.emotion("{\"joy\": 0.9}")
				.userEmail(USER_EMAIL)
				.chatStyle("empathetic")
				.createdAt(now)
				.build();
	}

	private SessionRequest buildSessionRequest() {
		return SessionRequest.builder()
				.sessionId(SESSION_ID)
				.userEmail(USER_EMAIL)
				.userName(USER_NAME)
				.summary("테스트 상담")
				.build();
	}

	private ChatMessageRequest buildMessageRequest() {
		return ChatMessageRequest.builder()
				.sessionId(SESSION_ID)
				.messageType(ChatMessageType.USER)
				.messageContent(USER_MESSAGE)
				.userEmail(USER_EMAIL)
				.chatStyle("default")
				.build();
	}

	private ChatSessionDto buildSessionDto(LocalDateTime now) {
		return new ChatSessionDto(
				SESSION_ID,
				USER_EMAIL,
				USER_NAME,
				"테스트 상담",
				Map.of("joy", 0.3, "sadness", 0.7),
				"중간",
				"가족 지지",
				"우울 증상",
				now,
				now
		);
	}

	private ChatMessageDto buildMessageDto(LocalDateTime now) {
		return new ChatMessageDto(
				1L,
				SESSION_ID,
				ChatMessageType.USER,
				USER_MESSAGE,
				null,
				USER_EMAIL,
				"default",
				now
		);
	}

}
