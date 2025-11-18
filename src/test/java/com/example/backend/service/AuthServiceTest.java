package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.backend.dto.auth.FindIdRequest;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.backend.common.error.UnauthorizedException;
import com.example.backend.dto.auth.LoginRequest;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtUtil;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String DEFAULT_EMAIL = "test@example.com";
	private static final String RAW_PASSWORD = "password123";
	private static final String ENCODED_PASSWORD = "encodedPassword";
	private static final String WRONG_PASSWORD = "wrongPassword";
	private static final String JWT_TOKEN = "mock-jwt-token";
	private static final String DEFAULT_PHONE = "010-1234-5678";
	private static final String DEFAULT_NICKNAME = "테스터";

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private DailyMetricsService dailyMetricsService;

	@InjectMocks
	private AuthService authService;

	@Test
	@DisplayName("유효한 로그인 정보로 인증 성공 시 쿠키 설정, 마지막 로그인 갱신, 방문자 수 증가")
	void loginAndSetCookie_validCredentials_setsCookieAndUpdatesMetrics() {
		LoginRequest request = createLoginRequest(DEFAULT_EMAIL, RAW_PASSWORD);
		UserEntity user = createUser(DEFAULT_EMAIL, ENCODED_PASSWORD, DEFAULT_PHONE);
		HttpServletResponse response = mock(HttpServletResponse.class);

		given(userRepository.findByEmail(DEFAULT_EMAIL)).willReturn(Optional.of(user));
		given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
		given(jwtUtil.generateToken(DEFAULT_EMAIL)).willReturn(JWT_TOKEN);

		authService.loginAndSetCookie(request, response);

		verify(jwtUtil).setJwtCookie(response, JWT_TOKEN);
		verify(userRepository).touchLastLogin(DEFAULT_EMAIL);
		verify(dailyMetricsService).increaseUserCount();
	}

	@Test
	@DisplayName("잘못된 비밀번호로 로그인 시 UnauthorizedException 발생")
	void loginAndSetCookie_wrongPassword_throwsUnauthorizedException() {
		LoginRequest request = createLoginRequest(DEFAULT_EMAIL, WRONG_PASSWORD);
		UserEntity user = createUser(DEFAULT_EMAIL, ENCODED_PASSWORD, DEFAULT_PHONE);
		HttpServletResponse response = mock(HttpServletResponse.class);

		given(userRepository.findByEmail(DEFAULT_EMAIL)).willReturn(Optional.of(user));
		given(passwordEncoder.matches(WRONG_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

		assertThatThrownBy(() -> authService.loginAndSetCookie(request, response))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("이메일 또는 비밀번호가 잘못되었습니다.");
	}

	@Test
	@DisplayName("로그아웃 시 JWT 쿠키를 제거한다")
	void logout_clearsJwtCookie() {
		HttpServletResponse response = mock(HttpServletResponse.class);

		authService.logout(response);

		verify(jwtUtil).clearJwtCookie(response);
	}

	@Test
	@DisplayName("전화번호와 닉네임이 일치하면 마스킹된 이메일을 반환한다")
	void findAndMaskUserEmail_validRequest_returnsMaskedEmail() {
		FindIdRequest request = new FindIdRequest(DEFAULT_PHONE, DEFAULT_NICKNAME);
		UserEntity user = createUser("longemail@example.com", ENCODED_PASSWORD, DEFAULT_PHONE);

		given(userRepository.findByPhoneNumberAndNickname(DEFAULT_PHONE, DEFAULT_NICKNAME))
				.willReturn(Optional.of(user));

		String maskedEmail = authService.findAndMaskUserEmail(request);

		assertThat(maskedEmail).isEqualTo("lo*******@example.com");
	}

	private LoginRequest createLoginRequest(String email, String password) {
		return new LoginRequest(email, password);
	}

	private UserEntity createUser(String email, String encodedPassword, String phoneNumber) {
		return UserEntity.builder()
				.email(email)
				.password(encodedPassword)
				.phoneNumber(phoneNumber)
				.build();
	}

}
