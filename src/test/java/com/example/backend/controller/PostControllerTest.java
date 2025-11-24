package com.example.backend.controller;

import static com.example.backend.common.constant.PostConstants.Visibility.PRIVATE;
import static com.example.backend.common.constant.PostConstants.Visibility.PUBLIC;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.backend.common.error.NotFoundException;
import com.example.backend.dto.post.CreateRequest;
import com.example.backend.dto.post.Detail;
import com.example.backend.dto.post.Summary;
import com.example.backend.dto.post.UpdateRequest;
import com.example.backend.security.PostAuth;
import com.example.backend.security.SecurityUtil;
import com.example.backend.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
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
        controllers = PostController.class,
        excludeFilters = {
                @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        com.example.backend.security.JwtAuthenticationFilter.class,
                        com.example.backend.security.SecurityConfig.class,
                        com.example.backend.security.CustomUserDetailsService.class
                })
        }
)
@AutoConfigureMockMvc(addFilters = true)
@Import(TestSecurityConfig.class)
@EnableMethodSecurity(prePostEnabled = true)
@DisplayName("PostController 테스트")
class PostControllerTest {
    
    private static final String BASE_URL = "/api/posts";
    private static final String USER_EMAIL = "user@example.com";
    
    @Autowired
    MockMvc mockMvc;
    
    @Autowired
    ObjectMapper objectMapper;
    
    @MockitoBean
    PostService postService;
    
    @MockitoBean
    SecurityUtil securityUtil;
    
    @MockitoBean(name = "postAuth")
    PostAuth postAuth;
    
    @Test
    @DisplayName("인증 없이 보호된 API 접근 시 401")
    void unauthorized_401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/my"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @DisplayName("GET /api/posts → 200 + ApiResponse<List<Detail>>")
    @WithMockUser(roles = "USER")
    void getAllPosts_ok() throws Exception {
        Detail detail = new Detail();
        detail.setId(1L);
        detail.setTitle("제목");
        detail.setContent("내용");
        detail.setUserEmail(USER_EMAIL);
        detail.setCreatedAt(LocalDateTime.now());
        
        given(postService.getAllPosts()).willReturn(List.of(detail));
        
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].title").value("제목"));
        
        then(postService).should().getAllPosts();
    }
    
    @Test
    @DisplayName("GET /api/posts/public → 200 + ApiResponse<List<Summary>>")
    @WithMockUser(roles = "USER")
    void getPublicPosts_ok() throws Exception {
        Summary summary = new Summary();
        summary.setId(1L);
        summary.setContentPreview("내용 미리보기");
        summary.setVisibility(PUBLIC);
        summary.setCreatedAt(LocalDateTime.now());
        
        given(postService.getPublicPosts()).willReturn(List.of(summary));
        
        mockMvc.perform(get(BASE_URL + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].visibility").value(PUBLIC));
        
        then(postService).should().getPublicPosts();
    }
    
    @Test
    @DisplayName("GET /api/posts/recent?limit=5 → 200 + ApiResponse<List<Summary>>")
    @WithMockUser(roles = "USER")
    void getRecentPosts_ok() throws Exception {
        Summary summary = new Summary();
        summary.setId(1L);
        summary.setContentPreview("최근 내용");
        summary.setCreatedAt(LocalDateTime.now());
        
        given(postService.getRecentPosts(5)).willReturn(List.of(summary));
        
        mockMvc.perform(get(BASE_URL + "/recent").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(1L));
        
        then(postService).should().getRecentPosts(5);
    }
    
    @Test
    @DisplayName("GET /api/posts/user/{email} → 200 + ApiResponse<List<Detail>>")
    @WithMockUser(roles = "USER")
    void getPostsByUser_ok() throws Exception {
        Detail detail = new Detail();
        detail.setId(1L);
        detail.setUserEmail("target@example.com");
        detail.setTitle("타겟 글");
        
        given(postService.getPostsByUser("target@example.com"))
                .willReturn(List.of(detail));
        
        mockMvc.perform(get(BASE_URL + "/user/{email}", "target@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userEmail").value("target@example.com"));
        
        then(postService).should().getPostsByUser("target@example.com");
    }
    
    @Test
    @DisplayName("GET /api/posts/my → 200 + 인증 사용자 글 목록")
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    void getMyPosts_ok() throws Exception {
        Detail detail = new Detail();
        detail.setId(1L);
        detail.setUserEmail(USER_EMAIL);
        
        given(securityUtil.requirePrincipalEmail(any())).willReturn(USER_EMAIL);
        given(postService.getPostsByUser(USER_EMAIL)).willReturn(List.of(detail));
        
        mockMvc.perform(get(BASE_URL + "/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userEmail").value(USER_EMAIL));
        
        then(securityUtil).should().requirePrincipalEmail(any());
        then(postService).should().getPostsByUser(USER_EMAIL);
    }
    
    @Test
    @DisplayName("GET /api/posts/{id} (존재) → 200 + ApiResponse<Detail>")
    @WithMockUser(roles = "USER")
    void getPost_found_200() throws Exception {
        Detail detail = new Detail();
        detail.setId(1L);
        detail.setTitle("제목");
        
        given(postService.getPostDetail(1L)).willReturn(Optional.of(detail));
        
        mockMvc.perform(get(BASE_URL + "/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.title").value("제목"));
        
        then(postService).should().getPostDetail(1L);
    }
    
    @Test
    @DisplayName("GET /api/posts/{id} (미존재) → 404 + ApiResponse.error")
    @WithMockUser(roles = "USER")
    void getPost_notFound_404() throws Exception {
        given(postService.getPostDetail(999L)).willReturn(Optional.empty());
        
        mockMvc.perform(get(BASE_URL + "/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        
        then(postService).should().getPostDetail(999L);
    }
    
    @Test
    @DisplayName("POST /api/posts → 201 + ApiResponse<Detail>")
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    void createPost_created_201() throws Exception {
        CreateRequest request = CreateRequest.builder()
                .title("새 글")
                .content("내용")
                .visibility(PUBLIC)
                .build();
        
        Detail created = new Detail();
        created.setId(10L);
        created.setTitle("새 글");
        created.setUserEmail(USER_EMAIL);
        
        given(securityUtil.requirePrincipalEmail(any())).willReturn(USER_EMAIL);
        given(postService.createPost(any(CreateRequest.class), eq(USER_EMAIL)))
                .willReturn(created);
        
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.userEmail").value(USER_EMAIL));
        
        then(securityUtil).should().requirePrincipalEmail(any());
        then(postService).should().createPost(any(CreateRequest.class), eq(USER_EMAIL));
    }
    
    @Test
    @DisplayName("PUT /api/posts/{id} → 200 + ApiResponse<Detail>")
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    void updatePost_ok() throws Exception {
        UpdateRequest request = UpdateRequest.builder()
                .title("수정 제목")
                .content("수정 내용")
                .visibility(PRIVATE)
                .build();
        
        Detail updated = new Detail();
        updated.setId(1L);
        updated.setTitle("수정 제목");
        updated.setUserEmail(USER_EMAIL);
        
        given(securityUtil.requirePrincipalEmail(any())).willReturn(USER_EMAIL);
        given(postAuth.canModify(1L, USER_EMAIL)).willReturn(true);
        given(postService.updatePost(eq(1L), any(UpdateRequest.class), eq(USER_EMAIL)))
                .willReturn(updated);
        
        mockMvc.perform(put(BASE_URL + "/{id}", 1L)
                        .with(user(USER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.title").value("수정 제목"));
        
        then(postAuth).should().canModify(1L, USER_EMAIL);
        then(postService).should().updatePost(eq(1L), any(UpdateRequest.class), eq(USER_EMAIL));
    }
    
    @Test
    @DisplayName("DELETE /api/posts/{id} (ADMIN) → 200 + ApiResponse<String>")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void deletePost_admin_ok() throws Exception {
        given(securityUtil.requirePrincipalEmail(any()))
                .willReturn("admin@example.com");
        
        mockMvc.perform(delete(BASE_URL + "/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("게시글이 성공적으로 삭제되었습니다."));
        
        then(securityUtil).should().requirePrincipalEmail(any());
        then(postService).should().deletePost(5L, "admin@example.com");
    }
    
    @Test
    @DisplayName("DELETE /api/posts/{id} (ADMIN 아님) → 403")
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    void deletePost_forbidden_403() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/{id}", 5L))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @DisplayName("DELETE /api/posts/{id} (미존재) → 404 ProblemDetail")
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void deletePost_notFound_404() throws Exception {
        given(securityUtil.requirePrincipalEmail(any()))
                .willReturn("admin@example.com");
        
        willThrow(new NotFoundException("게시글을 찾을 수 없습니다.", "POST_NOT_FOUND", "postId"))
                .given(postService).deletePost(7L, "admin@example.com");
        
        mockMvc.perform(delete(BASE_URL + "/{id}", 7L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        
        then(securityUtil).should().requirePrincipalEmail(any());
        then(postService).should().deletePost(7L, "admin@example.com");
    }
    
    @Test
    @DisplayName("GET /api/posts/stats → 200 + ApiResponse<Map>")
    @WithMockUser(username = USER_EMAIL, roles = "USER")
    void getPostStats_ok() throws Exception {
        given(securityUtil.requirePrincipalEmail(any())).willReturn(USER_EMAIL);
        given(postService.getPostCountByVisibility(USER_EMAIL, PUBLIC)).willReturn(3L);
        given(postService.getPostCountByVisibility(USER_EMAIL, PRIVATE)).willReturn(2L);
        
        mockMvc.perform(get(BASE_URL + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicCount").value(3))
                .andExpect(jsonPath("$.data.privateCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(5));
        
        then(securityUtil).should().requirePrincipalEmail(any());
        then(postService).should().getPostCountByVisibility(USER_EMAIL, PUBLIC);
        then(postService).should().getPostCountByVisibility(USER_EMAIL, PRIVATE);
    }
}
