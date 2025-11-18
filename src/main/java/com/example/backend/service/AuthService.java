package com.example.backend.service;

import java.util.Optional;
import java.util.Random;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.error.NotFoundException;
import com.example.backend.common.error.UnauthorizedException;
import com.example.backend.dto.auth.FindIdRequest;
import com.example.backend.dto.auth.LoginRequest;
import com.example.backend.dto.auth.ResetPasswordRequest;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtUtil;
import com.example.backend.service.DailyMetricsService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;
    private final DailyMetricsService dailyMetricsService;

    @Transactional
    public void loginAndSetCookie(LoginRequest request, HttpServletResponse response) {
        UserEntity user = authenticateUser(request);
        issueJwtCookie(response, user.getEmail());
        recordLoginSuccess(user.getEmail());
    }

    public void logout(HttpServletResponse response) {
        jwtUtil.clearJwtCookie(response);
        log.info("로그아웃 처리 완료");
    }

    @Transactional(readOnly = true)
    public String findAndMaskUserEmail(FindIdRequest request) {
        UserEntity user = findUserByPhoneAndNickname(request);
        String email = user.getEmail();
        String maskedEmail = maskEmail(email);
        log.info("아이디 찾기 성공: {}", email);
        return maskedEmail;
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        UserEntity user = findUserByEmail(request.getEmail());
        validatePhoneNumber(user, request.getPhoneNumber());

        String tempPassword = generateTempPassword();
        resetPasswordWithTemp(user, tempPassword);

        return tempPassword;
    }

    @Transactional
    public void updateLastLogin(String email) {
        try {
            userRepository.touchLastLogin(email);
        } catch (Exception e) {
            log.warn("마지막 로그인 시간 업데이트 실패: {}", email, e);
        }
    }

    private void issueJwtCookie(HttpServletResponse response, String email) {
        String token = jwtUtil.generateToken(email);
        jwtUtil.setJwtCookie(response, token);
    }

    private void recordLoginSuccess(String email) {
        updateLastLogin(email);
        dailyMetricsService.increaseUserCount();
        log.info("로그인 성공: {}", email);
    }

    private UserEntity findUserByPhoneAndNickname(FindIdRequest request) {
        Optional<UserEntity> optionalUser = userRepository
                .findByPhoneNumberAndNickname(request.getPhoneNumber(), request.getNickname());

        if (optionalUser.isEmpty()) {
            log.warn("아이디 찾기 실패 - 전화번호: {}, 닉네임: {}",
                    request.getPhoneNumber(), request.getNickname());
            throw new NotFoundException("일치하는 회원 정보를 찾을 수 없습니다.");
        }

        return optionalUser.get();
    }

    private UserEntity findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 이메일로 비밀번호 재설정 시도: {}", email);
                    return new NotFoundException("해당 이메일로 등록된 계정이 없습니다.");
                });
    }

    private UserEntity authenticateUser(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 잘못되었습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 잘못되었습니다.");
        }

        return user;
    }

    private void validatePhoneNumber(UserEntity user, String phoneNumber) {
        if (!user.getPhoneNumber().equals(phoneNumber)) {
            log.warn("이메일과 전화번호가 일치하지 않습니다: {}", user.getEmail());
            throw new NotFoundException("이메일과 전화번호가 일치하지 않습니다.");
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;

        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];

        if (local.length() <= 2) return email;

        String masked = local.substring(0, 2) + "*".repeat(Math.max(1, local.length() - 2));
        return masked + "@" + domain;
    }

    private String generateTempPassword() {
        int length = 12;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    private void resetPasswordWithTemp(UserEntity user, String tempPassword) {
        try {
            sendTempPasswordEmail(user.getEmail(), tempPassword);
            user.setPassword(passwordEncoder.encode(tempPassword));
            userRepository.save(user);
            log.info("비밀번호 재설정 완료: {}", user.getEmail());
        } catch (Exception e) {
            log.error("비밀번호 재설정 실패: {}", e.getMessage());
            throw new RuntimeException("임시 비밀번호 발송에 실패했습니다.", e);
        }
    }

    private void sendTempPasswordEmail(String toEmail, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[마음챗] 임시 비밀번호 안내");
        message.setText(
            "안녕하세요. 마음챗입니다.\n\n" +
                "요청하신 임시 비밀번호는 다음과 같습니다:\n\n" +
                "임시 비밀번호: " + tempPassword + "\n\n" +
                "보안을 위해 로그인 후 반드시 비밀번호를 변경해 주세요.\n\n" +
                "감사합니다."
        );
        mailSender.send(message);
    }
}
