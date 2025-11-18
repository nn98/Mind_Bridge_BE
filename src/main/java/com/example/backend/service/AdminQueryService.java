package com.example.backend.service;

import static com.example.backend.common.constant.PostConstants.Visibility.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.backend.common.error.NotFoundException;
import com.example.backend.dto.admin.AdminPostDetail;
import com.example.backend.dto.admin.AdminPostRow;
import com.example.backend.dto.admin.AdminPostSearchRequest;
import com.example.backend.dto.admin.AdminStats;
import com.example.backend.dto.admin.AdminUserDetail;
import com.example.backend.dto.admin.AdminUserRow;
import com.example.backend.dto.admin.AdminUserSearchRequest;
import com.example.backend.dto.admin.DailyMetricPoint;
import com.example.backend.dto.admin.UserDistribution;
import com.example.backend.dto.admin.WeeklyMetricPoint;
import com.example.backend.dto.user.Profile;
import com.example.backend.entity.DailyMetricsEntity;
import com.example.backend.entity.PostEntity;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.DailyMetricsRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueryService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final DailyMetricsRepository dailyMetricsRepository;

    public AdminStats getAdminStats() {
        long totalUsers = userRepository.count();
        long totalPosts = postRepository.count();

        LocalDate today = LocalDate.now();
        DailyMetricPoint todayMetrics = loadTodayMetrics(today);
        WeeklyMetricPoint recentWeekMetrics = loadRecentWeekMetrics(today);

        List<Profile> users = loadAllProfiles();

        return AdminStats.builder()
                .totalUsers(totalUsers)
                .totalPosts(totalPosts)
                .todayChats(todayMetrics.getChatCount())
                .todayVisits(todayMetrics.getVisitCount())
                .weekChats(recentWeekMetrics.getChatCount())
                .weekVisits(recentWeekMetrics.getVisitCount())
                .users(users)
                .build();
    }

    public Page<AdminUserRow> findUsers(AdminUserSearchRequest request, Pageable pageable) {
        Specification<UserEntity> spec = buildUserSpecification(request);
        Page<UserEntity> page = userRepository.findAll(spec, pageable);
        return page.map(this::toUserRow);
    }

    public AdminUserDetail getUserDetail(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toUserDetail(user);
    }

    public Page<AdminPostRow> findPosts(AdminPostSearchRequest request, Pageable pageable) {
        Specification<PostEntity> spec = buildPostSpecification(request);
        Page<PostEntity> page = postRepository.findAll(spec, pageable);
        return page.map(this::toPostRow);
    }

    public AdminPostDetail getPostDetail(Long id) {
        PostEntity post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        return toPostDetail(post);
    }

    @Transactional
    public void updatePostVisibility(Long id, String visibility) {
        PostEntity post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        post.setVisibility(visibility);
        postRepository.save(post);
    }

    @Transactional
    public void deletePost(Long id) {
        postRepository.deleteById(id);
    }

    public DailyMetricPoint getTodayMetrics() {
        LocalDate today = LocalDate.now();
        return loadTodayMetrics(today);
    }

    public List<DailyMetricPoint> getDailyRange(LocalDate start, LocalDate end) {
        return dailyMetricsRepository.findAllByStatDateBetween(start, end).stream()
                .map(this::toDailyMetricPoint)
                .toList();
    }

    public List<WeeklyMetricPoint> getWeeklyMetrics(int weeks) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusWeeks(weeks - 1L).with(DayOfWeek.MONDAY);

        WeekFields weekFields = WeekFields.of(Locale.KOREA);
        List<DailyMetricsEntity> metrics =
                dailyMetricsRepository.findAllByStatDateBetween(start, end);

        Map<String, List<DailyMetricsEntity>> groupedByWeek =
                groupByWeek(metrics, weekFields);

        List<WeeklyMetricPoint> points = groupedByWeek.values().stream()
                .map(list -> toWeeklyMetricPoint(list, weekFields))
                .toList();

        return sortWeeklyMetrics(points);
    }

    public UserDistribution getUserDistribution() {
        java.util.List<UserRepository.GenderCount> genderRows =
                userRepository.countByGenderGroup();
        java.util.Map<String, Long> gender = genderRows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> row.getGender() == null ? "UNKNOWN" : row.getGender(),
                        UserRepository.GenderCount::getCnt
                ));

        java.util.List<UserRepository.AgeBucketCount> ageRows =
                userRepository.countByAgeBucketGroup();
        java.util.Map<String, Long> ageBuckets = ageRows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserRepository.AgeBucketCount::getBucket,
                        UserRepository.AgeBucketCount::getCnt
                ));

        return UserDistribution.builder()
                .genderCounts(gender)
                .ageBuckets(ageBuckets)
                .build();
    }

    private AdminUserRow toUserRow(UserEntity u) {
        return AdminUserRow.builder()
                .id(u.getUserId())
                .nickname(u.getNickname())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .role(u.getRole())
                .gender(u.getGender())
                .age(u.getAge())
                .createdAt(toIso(u.getCreatedAt()))
                .build();
    }

    private AdminUserDetail toUserDetail(UserEntity u) {
        return AdminUserDetail.builder()
                .id(u.getUserId())
                .nickname(u.getNickname())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .role(u.getRole())
                .gender(u.getGender())
                .age(u.getAge())
                .createdAt(toIso(u.getCreatedAt()))
                .updatedAt(toIso(u.getUpdatedAt()))
                .build();
    }

    private AdminPostRow toPostRow(PostEntity p) {
        UserEntity author = userRepository.findById(p.getUserId())
                .orElse(null);

        return AdminPostRow.builder()
                .id(p.getPostId())
                .title(p.getTitle())
                .userNickname(author != null ? author.getNickname() : "탈퇴한 사용자")
                .userEmail(author != null ? author.getEmail() : "deleted@user.com")
                .visibility(p.getVisibility())
                .createdAt(p.getCreatedAt().toString())
                .likeCount(p.getLikeCount())
                .build();
    }

    private AdminPostDetail toPostDetail(PostEntity p) {
        UserEntity author = userRepository.findById(p.getUserId())
                .orElse(null);

        return AdminPostDetail.builder()
                .id(p.getPostId())
                .title(p.getTitle())
                .content(p.getContent())
                .userNickname(author != null ? author.getNickname() : "탈퇴한 사용자")
                .userEmail(author != null ? author.getEmail() : "deleted@user.com")
                .visibility(p.getVisibility())
                .createdAt(p.getCreatedAt().toString())
                .updatedAt(p.getUpdatedAt().toString())
                .extra(buildAdminExtra(p, author))
                .build();
    }

    private Map<String, Object> buildAdminExtra(PostEntity post, UserEntity author) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("status", post.getStatus());
        extra.put("viewCount", post.getViewCount());
        extra.put("authorUserId", post.getUserId());
        if (author != null) {
            extra.put("authorRole", author.getRole());
            extra.put("authorProvider", author.getProvider());
        }
        return extra;
    }

    private static String toIso(java.time.LocalDateTime t) {
        return t == null ? null : t.toString();
    }

    private static long safe(Long v) {
        return v == null ? 0L : v;
    }

    private DailyMetricPoint loadTodayMetrics(LocalDate date) {
        DailyMetricsEntity entity = dailyMetricsRepository.findById(date).orElse(null);

        long chats = entity != null ? safe((long) entity.getChatCount()) : 0L;
        long visits = entity != null ? safe((long) entity.getLoginCount()) : 0L;

        return DailyMetricPoint.builder()
                .date(date)
                .chatCount(chats)
                .visitCount(visits)
                .build();
    }

    private WeeklyMetricPoint loadRecentWeekMetrics(LocalDate today) {
        LocalDate start = today.minusDays(6);
        List<DailyMetricsEntity> metrics =
                dailyMetricsRepository.findAllByStatDateBetween(start, today);

        long chats = metrics.stream()
                .mapToLong(e -> safe((long) e.getChatCount()))
                .sum();
        long visits = metrics.stream()
                .mapToLong(e -> safe((long) e.getLoginCount()))
                .sum();

        return WeeklyMetricPoint.builder()
                .year(today.getYear())
                .week(0)
                .chatCount(chats)
                .visitCount(visits)
                .start(start)
                .end(today)
                .build();
    }

    private List<Profile> loadAllProfiles() {
        return userRepository.findAll().stream()
                .map(this::toProfile)
                .toList();
    }

    private Profile toProfile(UserEntity user) {
        return Profile.builder()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .gender(user.getGender())
                .age(user.getAge())
                .build();
    }

    private Specification<UserEntity> buildUserSpecification(AdminUserSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getQ())) {
                String keyword = request.getQ();
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nickname")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(root.get("phoneNumber"), "%" + keyword + "%")
                ));
            }

            if (StringUtils.hasText(request.getRole())) {
                predicates.add(cb.equal(root.get("role"), request.getRole()));
            }
            if (StringUtils.hasText(request.getGender())) {
                predicates.add(cb.equal(root.get("gender"), request.getGender()));
            }
            if (request.getAgeFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("age"), request.getAgeFrom()));
            }
            if (request.getAgeTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("age"), request.getAgeTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<PostEntity> buildPostSpecification(AdminPostSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getQ())) {
                String pattern = "%" + request.getQ().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("content")), pattern),
                        cb.like(cb.lower(root.get("author").get("nickname")), pattern),
                        cb.like(cb.lower(root.get("author").get("email")), pattern)
                ));
            }

            if (hasVisibilityFilter(request)) {
                boolean isPublic = PUBLIC.equalsIgnoreCase(request.getVisibility());
                predicates.add(cb.equal(root.get("isPublic"), isPublic));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean hasVisibilityFilter(AdminPostSearchRequest request) {
        return StringUtils.hasText(request.getVisibility())
                && !"all".equalsIgnoreCase(request.getVisibility());
    }

    private DailyMetricPoint toDailyMetricPoint(DailyMetricsEntity entity) {
        return DailyMetricPoint.builder()
                .date(entity.getStatDate())
                .chatCount(safe((long) entity.getChatCount()))
                .visitCount(safe((long) entity.getLoginCount()))
                .build();
    }

    private Map<String, List<DailyMetricsEntity>> groupByWeek(List<DailyMetricsEntity> metrics,
                                                              WeekFields weekFields) {
        return metrics.stream()
                .collect(Collectors.groupingBy(e -> buildWeekKey(e, weekFields)));
    }

    private String buildWeekKey(DailyMetricsEntity entity, WeekFields weekFields) {
        LocalDate date = entity.getStatDate();
        int year = date.get(weekFields.weekBasedYear());
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return year + "-" + week;
    }

    private WeeklyMetricPoint toWeeklyMetricPoint(List<DailyMetricsEntity> metrics,
                                                  WeekFields weekFields) {
        long chats = metrics.stream()
                .mapToLong(e -> safe((long) e.getChatCount()))
                .sum();
        long visits = metrics.stream()
                .mapToLong(e -> safe((long) e.getLoginCount()))
                .sum();

        LocalDate any = metrics.get(0).getStatDate();
        int year = any.get(weekFields.weekBasedYear());
        int week = any.get(weekFields.weekOfWeekBasedYear());

        LocalDate weekStart = any.with(weekFields.weekOfWeekBasedYear(), week)
                .with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        return WeeklyMetricPoint.builder()
                .year(year)
                .week(week)
                .chatCount(chats)
                .visitCount(visits)
                .start(weekStart)
                .end(weekEnd)
                .build();
    }

    private List<WeeklyMetricPoint> sortWeeklyMetrics(List<WeeklyMetricPoint> points) {
        return points.stream()
                .sorted((a, b) -> {
                    int yearCompare = Integer.compare(a.getYear(), b.getYear());
                    if (yearCompare != 0) {
                        return yearCompare;
                    }
                    return Integer.compare(a.getWeek(), b.getWeek());
                })
                .toList();
    }

}
