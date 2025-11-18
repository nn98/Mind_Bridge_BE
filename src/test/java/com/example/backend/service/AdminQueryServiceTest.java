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
		testUser = createUser(1L, "admin@example.com", "관리자", "ADMIN");
		testPost = createPost(1L, 1L, "테스트 게시글", "테스트 내용", "public", 10, 5);
		testMetrics = createMetrics(LocalDate.now(), 100, 50);
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
		assertThat(result.getTodayChats()).isEqualTo(50L);
		assertThat(result.getTodayVisits()).isEqualTo(100L);
	}

	@Test
	@DisplayName("사용자 검색")
	void findUsers() {
		AdminUserSearchRequest request = AdminUserSearchRequest.builder()
				.q("관리자")
				.role("ADMIN")
				.build();
		Page<UserEntity> userPage = new PageImpl<>(List.of(testUser));
		given(userRepository.findAll(any(Specification.class), any(Pageable.class)))
				.willReturn(userPage);

		Page<AdminUserRow> result = adminQueryService.findUsers(request, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		AdminUserRow userRow = result.getContent().get(0);
		assertThat(userRow.getId()).isEqualTo(1L);
		assertThat(userRow.getNickname()).isEqualTo("관리자");
		assertThat(userRow.getEmail()).isEqualTo("admin@example.com");
		assertThat(userRow.getRole()).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("사용자 상세 조회")
	void getUserDetail() {
		given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

		AdminUserDetail result = adminQueryService.getUserDetail(1L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getNickname()).isEqualTo("관리자");
		assertThat(result.getEmail()).isEqualTo("admin@example.com");
		assertThat(result.getRole()).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("게시글 검색")
	void findPosts() {
		AdminPostSearchRequest request = AdminPostSearchRequest.builder()
				.q("테스트")
				.visibility("public")
				.build();
		Page<PostEntity> postPage = new PageImpl<>(List.of(testPost));
		given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
				.willReturn(postPage);
		given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

		Page<AdminPostRow> result = adminQueryService.findPosts(request, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		AdminPostRow postRow = result.getContent().get(0);
		assertThat(postRow.getId()).isEqualTo(1L);
		assertThat(postRow.getTitle()).isEqualTo("테스트 게시글");
		assertThat(postRow.getUserEmail()).isEqualTo("admin@example.com");
		assertThat(postRow.getUserNickname()).isEqualTo("관리자");
		assertThat(postRow.getVisibility()).isEqualTo("public");
		assertThat(postRow.getLikeCount()).isEqualTo(10);
	}

	@Test
	@DisplayName("게시글 상세 조회")
	void getPostDetail() {
		given(postRepository.findById(1L)).willReturn(Optional.of(testPost));
		given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

		AdminPostDetail result = adminQueryService.getPostDetail(1L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getTitle()).isEqualTo("테스트 게시글");
		assertThat(result.getContent()).isEqualTo("테스트 내용");
		assertThat(result.getUserEmail()).isEqualTo("admin@example.com");
		assertThat(result.getUserNickname()).isEqualTo("관리자");
	}

	@Test
	@DisplayName("게시글 공개설정 수정")
	void updatePostVisibility() {
		given(postRepository.findById(1L)).willReturn(Optional.of(testPost));

		adminQueryService.updatePostVisibility(1L, "private");

		assertThat(testPost.getVisibility()).isEqualTo("private");
		verify(postRepository).save(testPost);
	}

	@Test
	@DisplayName("게시글 삭제")
	void deletePost() {
		adminQueryService.deletePost(1L, "테스트 삭제");

		verify(postRepository).deleteById(1L);
	}

	@Test
	@DisplayName("오늘 통계 조회")
	void getTodayMetrics() {
		LocalDate today = LocalDate.now();
		given(dailyMetricsRepository.findById(today)).willReturn(Optional.of(testMetrics));

		DailyMetricPoint result = adminQueryService.getTodayMetrics();

		assertThat(result).isNotNull();
		assertThat(result.getDate()).isEqualTo(today);
		assertThat(result.getChatCount()).isEqualTo(50);
		assertThat(result.getVisitCount()).isEqualTo(100);
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
		DailyMetricPoint point = result.get(0);
		assertThat(point.getChatCount()).isEqualTo(50);
		assertThat(point.getVisitCount()).isEqualTo(100);
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

	private PostEntity createPost(Long postId, Long userId, String title, String content,
								  String visibility, int likeCount, int commentCount) {
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

}
