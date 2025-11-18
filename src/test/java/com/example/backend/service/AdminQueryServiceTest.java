package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.example.backend.common.error.NotFoundException;
import com.example.backend.dto.admin.AdminPostDetail;
import com.example.backend.dto.admin.AdminPostRow;
import com.example.backend.dto.admin.AdminPostSearchRequest;
import com.example.backend.dto.admin.AdminStats;
import com.example.backend.dto.admin.AdminUserDetail;
import com.example.backend.dto.admin.AdminUserRow;
import com.example.backend.dto.admin.AdminUserSearchRequest;
import com.example.backend.dto.admin.DailyMetricPoint;
import com.example.backend.entity.DailyMetricsEntity;
import com.example.backend.entity.PostEntity;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.DailyMetricsRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminQueryService 테스트")
class AdminQueryServiceTest {

	private static final Long DEFAULT_USER_ID = 1L;
	private static final String DEFAULT_USER_EMAIL = "admin@example.com";
	private static final String DEFAULT_USER_NICKNAME = "관리자";
	private static final String DEFAULT_USER_ROLE = "ADMIN";

	private static final Long DEFAULT_POST_ID = 1L;
	private static final String DEFAULT_POST_TITLE = "테스트 게시글";
	private static final String DEFAULT_POST_CONTENT = "테스트 내용";
	private static final String DEFAULT_POST_VISIBILITY = "public";
	private static final int DEFAULT_POST_LIKE_COUNT = 10;
	private static final int DEFAULT_POST_COMMENT_COUNT = 5;

	private static final int DEFAULT_LOGIN_COUNT = 100;
	private static final int DEFAULT_CHAT_COUNT = 50;

	@InjectMocks
	private AdminQueryService adminQueryService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PostRepository postRepository;

	@Mock
	private DailyMetricsRepository dailyMetricsRepository;

	private UserEntity testUser;
	private PostEntity testPost;
	private DailyMetricsEntity testMetrics;

	@BeforeEach
	void setUp() {
		testUser = createUser(
				DEFAULT_USER_ID,
				DEFAULT_USER_EMAIL,
				DEFAULT_USER_NICKNAME,
				DEFAULT_USER_ROLE
		);

		testPost = createPost(
				DEFAULT_POST_ID,
				DEFAULT_USER_ID,
				DEFAULT_POST_TITLE,
				DEFAULT_POST_CONTENT,
				DEFAULT_POST_VISIBILITY,
				DEFAULT_POST_LIKE_COUNT,
				DEFAULT_POST_COMMENT_COUNT
		);

		testMetrics = createMetrics(LocalDate.now(), DEFAULT_LOGIN_COUNT, DEFAULT_CHAT_COUNT);
	}

	@Test
	@DisplayName("관리자 통계 조회")
	void getAdminStats() {
		given(userRepository.count()).willReturn(1000L);
		given(postRepository.count()).willReturn(500L);
		given(dailyMetricsRepository.findById(any(LocalDate.class)))
				.willReturn(Optional.of(testMetrics));
		given(dailyMetricsRepository.findAllByStatDateBetween(any(LocalDate.class), any(LocalDate.class)))
				.willReturn(List.of(testMetrics));
		given(userRepository.findAll()).willReturn(List.of(testUser));

		AdminStats result = adminQueryService.getAdminStats();

		assertThat(result.getTotalUsers()).isEqualTo(1000L);
		assertThat(result.getTotalPosts()).isEqualTo(500L);
		assertThat(result.getTodayChats()).isEqualTo(DEFAULT_CHAT_COUNT);
		assertThat(result.getTodayVisits()).isEqualTo(DEFAULT_LOGIN_COUNT);
	}

	@Test
	@DisplayName("관리자 통계 조회 - 최근 1주 데이터가 없으면 weekChats/weekVisits는 0")
	void getAdminStats_noRecentMetrics() {
		given(userRepository.count()).willReturn(1000L);
		given(postRepository.count()).willReturn(500L);
		given(dailyMetricsRepository.findById(any(LocalDate.class)))
				.willReturn(Optional.empty());
		given(dailyMetricsRepository.findAllByStatDateBetween(any(LocalDate.class), any(LocalDate.class)))
				.willReturn(List.of());
		given(userRepository.findAll()).willReturn(List.of(testUser));

		AdminStats result = adminQueryService.getAdminStats();

		assertThat(result.getTodayChats()).isZero();
		assertThat(result.getTodayVisits()).isZero();
		assertThat(result.getWeekChats()).isZero();
		assertThat(result.getWeekVisits()).isZero();
	}

	@Test
	@DisplayName("사용자 검색")
	void findUsers() {
		AdminUserSearchRequest request = AdminUserSearchRequest.builder()
				.q(DEFAULT_USER_NICKNAME)
				.role(DEFAULT_USER_ROLE)
				.build();
		Page<UserEntity> userPage = new PageImpl<>(List.of(testUser));
		given(userRepository.findAll(any(Specification.class), any(Pageable.class)))
				.willReturn(userPage);

		Page<AdminUserRow> result = adminQueryService.findUsers(request, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertAdminUserRowDefault(result.getContent().get(0));
	}

	@Test
	@DisplayName("사용자 상세 조회")
	void getUserDetail() {
		given(userRepository.findById(DEFAULT_USER_ID)).willReturn(Optional.of(testUser));

		AdminUserDetail result = adminQueryService.getUserDetail(DEFAULT_USER_ID);

		assertAdminUserDetailDefault(result);
	}

	@Test
	@DisplayName("게시글 검색")
	void findPosts() {
		AdminPostSearchRequest request = AdminPostSearchRequest.builder()
				.q("테스트")
				.visibility(DEFAULT_POST_VISIBILITY)
				.build();
		Page<PostEntity> postPage = new PageImpl<>(List.of(testPost));

		given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
				.willReturn(postPage);
		given(userRepository.findById(DEFAULT_USER_ID)).willReturn(Optional.of(testUser));

		Page<AdminPostRow> result = adminQueryService.findPosts(request, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertAdminPostRowDefault(result.getContent().get(0));
	}

	@Test
	@DisplayName("게시글 상세 조회")
	void getPostDetail() {
		given(postRepository.findById(DEFAULT_POST_ID)).willReturn(Optional.of(testPost));
		given(userRepository.findById(DEFAULT_USER_ID)).willReturn(Optional.of(testUser));

		AdminPostDetail result = adminQueryService.getPostDetail(DEFAULT_POST_ID);

		assertAdminPostDetailDefault(result);
	}

	@Test
	@DisplayName("게시글 공개설정 수정")
	void updatePostVisibility() {
		given(postRepository.findById(DEFAULT_POST_ID)).willReturn(Optional.of(testPost));

		adminQueryService.updatePostVisibility(DEFAULT_POST_ID, "private");

		assertThat(testPost.getVisibility()).isEqualTo("private");
		verify(postRepository).save(testPost);
	}

	@Test
	@DisplayName("게시글 삭제")
	void deletePost() {
		adminQueryService.deletePost(DEFAULT_POST_ID, "테스트 삭제");

		verify(postRepository).deleteById(DEFAULT_POST_ID);
	}

	@Test
	@DisplayName("오늘 통계 조회")
	void getTodayMetrics() {
		LocalDate today = LocalDate.now();
		given(dailyMetricsRepository.findById(today)).willReturn(Optional.of(testMetrics));

		DailyMetricPoint result = adminQueryService.getTodayMetrics();

		assertDailyMetricPoint(result, today, DEFAULT_CHAT_COUNT, DEFAULT_LOGIN_COUNT);
	}

	@Test
	@DisplayName("날짜 범위 통계 조회")
	void getDailyRange() {
		LocalDate start = LocalDate.now().minusDays(7);
		LocalDate end = LocalDate.now();
		given(dailyMetricsRepository.findAllByStatDateBetween(start, end))
				.willReturn(List.of(testMetrics));

		List<DailyMetricPoint> result = adminQueryService.getDailyRange(start, end);

		assertThat(result).hasSize(1);
		assertDailyMetricPoint(result.get(0), testMetrics.getStatDate(), DEFAULT_CHAT_COUNT, DEFAULT_LOGIN_COUNT);
	}

	@Test
	@DisplayName("존재하지 않는 사용자 조회 시 예외")
	void getUserDetailNotFoundException() {
		given(userRepository.findById(999L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> adminQueryService.getUserDetail(999L))
				.isInstanceOf(NotFoundException.class)
				.hasMessage("User not found");
	}

	private UserEntity createUser(Long id, String email, String nickname, String role) {
		LocalDateTime now = LocalDateTime.now();
		return UserEntity.builder()
				.userId(id)
				.email(email)
				.nickname(nickname)
				.fullName(nickname)
				.role(role)
				.age(30)
				.gender("male")
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	private PostEntity createPost(Long postId,
								  Long userId,
								  String title,
								  String content,
								  String visibility,
								  int likeCount,
								  int commentCount) {
		LocalDateTime now = LocalDateTime.now();
		return PostEntity.builder()
				.postId(postId)
				.title(title)
				.content(content)
				.userId(userId)
				.visibility(visibility)
				.likeCount(likeCount)
				.commentCount(commentCount)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	private DailyMetricsEntity createMetrics(LocalDate date, int loginCount, int chatCount) {
		return DailyMetricsEntity.builder()
				.statDate(date)
				.loginCount(loginCount)
				.chatCount(chatCount)
				.build();
	}

	private void assertAdminUserRowDefault(AdminUserRow row) {
		assertAdminUserRow(
				row,
				DEFAULT_USER_ID,
				DEFAULT_USER_EMAIL,
				DEFAULT_USER_NICKNAME,
				DEFAULT_USER_ROLE
		);
	}

	private void assertAdminUserRow(AdminUserRow row, Long id, String email, String nickname, String role) {
		assertThat(row.getId()).isEqualTo(id);
		assertThat(row.getEmail()).isEqualTo(email);
		assertThat(row.getNickname()).isEqualTo(nickname);
		assertThat(row.getRole()).isEqualTo(role);
	}

	private void assertAdminUserDetailDefault(AdminUserDetail detail) {
		assertAdminUserDetail(
				detail,
				DEFAULT_USER_ID,
				DEFAULT_USER_EMAIL,
				DEFAULT_USER_NICKNAME,
				DEFAULT_USER_ROLE
		);
	}

	private void assertAdminUserDetail(AdminUserDetail detail, Long id, String email, String nickname, String role) {
		assertThat(detail).isNotNull();
		assertThat(detail.getId()).isEqualTo(id);
		assertThat(detail.getEmail()).isEqualTo(email);
		assertThat(detail.getNickname()).isEqualTo(nickname);
		assertThat(detail.getRole()).isEqualTo(role);
	}

	private void assertAdminPostRowDefault(AdminPostRow row) {
		assertAdminPostRow(
				row,
				DEFAULT_POST_ID,
				DEFAULT_POST_TITLE,
				DEFAULT_USER_EMAIL,
				DEFAULT_USER_NICKNAME,
				DEFAULT_POST_VISIBILITY,
				DEFAULT_POST_LIKE_COUNT
		);
	}

	private void assertAdminPostRow(AdminPostRow row, Long id, String title, String userEmail,
									String userNickname, String visibility, int likeCount) {
		assertThat(row.getId()).isEqualTo(id);
		assertThat(row.getTitle()).isEqualTo(title);
		assertThat(row.getUserEmail()).isEqualTo(userEmail);
		assertThat(row.getUserNickname()).isEqualTo(userNickname);
		assertThat(row.getVisibility()).isEqualTo(visibility);
		assertThat(row.getLikeCount()).isEqualTo(likeCount);
	}

	private void assertAdminPostDetailDefault(AdminPostDetail detail) {
		assertAdminPostDetail(
				detail,
				DEFAULT_POST_ID,
				DEFAULT_POST_TITLE,
				DEFAULT_POST_CONTENT,
				DEFAULT_USER_EMAIL,
				DEFAULT_USER_NICKNAME
		);
	}

	private void assertAdminPostDetail(AdminPostDetail detail, Long id, String title, String content,
									   String userEmail, String userNickname) {
		assertThat(detail).isNotNull();
		assertThat(detail.getId()).isEqualTo(id);
		assertThat(detail.getTitle()).isEqualTo(title);
		assertThat(detail.getContent()).isEqualTo(content);
		assertThat(detail.getUserEmail()).isEqualTo(userEmail);
		assertThat(detail.getUserNickname()).isEqualTo(userNickname);
	}

	private void assertDailyMetricPoint(DailyMetricPoint point, LocalDate expectedDate,
										long expectedChats, long expectedVisits) {
		assertThat(point).isNotNull();
		assertThat(point.getDate()).isEqualTo(expectedDate);
		assertThat(point.getChatCount()).isEqualTo(expectedChats);
		assertThat(point.getVisitCount()).isEqualTo(expectedVisits);
	}

}
