package vn.eventplatform.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.user.dto.AssignRolesRequest;
import vn.eventplatform.user.dto.CreateUserProfileInternalRequest;
import vn.eventplatform.user.dto.UpdateProfileRequest;
import vn.eventplatform.user.repository.UserProfileRepository;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Value("${security.token.secret-key}")
    private String tokenSecret;

    @Value("${security.internal.secret}")
    private String internalSecret;

    private String generateTestJwt(UUID userId, List<String> roles) {
        byte[] keyBytes = tokenSecret.getBytes(StandardCharsets.UTF_8);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();
    }

    @Test
    @DisplayName("E2E Test: User Service RBAC & Profile Management Lifecycle")
    void testUserLifecycleAndSecurity() throws Exception {
        UUID testUserId = UUID.randomUUID();

        // 1. Gọi API internal tạo profile CÓ Secret hợp lệ -> 200 OK
        CreateUserProfileInternalRequest createReq = CreateUserProfileInternalRequest.builder()
                .id(testUserId)
                .email("user_" + testUserId + "@eventplatform.vn")
                .username("user_" + testUserId.toString().substring(0, 8))
                .fullName("Nguyễn Khách Hàng")
                .phoneNumber("0988112233")
                .build();

        mockMvc.perform(post("/internal/v1/users")
                        .header("X-Internal-Secret", internalSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Khách Hàng"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ATTENDEE"));

        // 2. Khách hàng đã đăng nhập gọi /api/v1/users/me -> 200 OK
        String userToken = generateTestJwt(testUserId, List.of("ROLE_ATTENDEE"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Khách Hàng"));

        // 3. Cập nhật hồ sơ cá nhân thành công
        UpdateProfileRequest updateReq = UpdateProfileRequest.builder()
                .fullName("Nguyễn Khách Hàng (Updated)")
                .phoneNumber("0999888777")
                .bio("Tôi là người yêu thích âm nhạc và lễ hội")
                .address("Hồ Chí Minh, Việt Nam")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Khách Hàng (Updated)"))
                .andExpect(jsonPath("$.data.address").value("Hồ Chí Minh, Việt Nam"));

        // 4. Admin truy cập /api/v1/roles -> 200 OK
        UUID adminId = UUID.randomUUID();
        String adminToken = generateTestJwt(adminId, List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());

        // 5. Admin truy cập /api/v1/permissions -> 200 OK
        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray());

        // 6. Admin tìm kiếm người dùng có phân trang (GET /api/v1/users) -> 200 OK
        mockMvc.perform(get("/api/v1/users?keyword=Khách&page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").isNumber());

        // 7. Admin xem chi tiết người dùng theo ID (GET /api/v1/users/{id}) -> 200 OK
        mockMvc.perform(get("/api/v1/users/" + testUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Khách Hàng (Updated)"));

        // 8. Admin gán vai trò mới cho người dùng (PUT /api/v1/users/{id}/roles) -> 200 OK
        AssignRolesRequest assignReq = AssignRolesRequest.builder()
                .roleNames(List.of("ROLE_ORGANIZER"))
                .build();

        mockMvc.perform(put("/api/v1/users/" + testUserId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.roles").isArray());

        // 9. Admin xem chi tiết vai trò theo ID (GET /api/v1/roles/1) -> 200 OK
        mockMvc.perform(get("/api/v1/roles/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1));

        // 10. Service nội bộ lấy thông tin người dùng (GET /internal/v1/users/{id}) -> 200 OK
        mockMvc.perform(get("/internal/v1/users/" + testUserId)
                        .header("X-Internal-Secret", internalSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(testUserId.toString()));
    }

    @Test
    @DisplayName("Unhappy Case: RBAC Authorization Failures")
    void testRbacAuthorizationFailures() throws Exception {
        UUID testUserId = UUID.randomUUID();
        String attendeeToken = generateTestJwt(testUserId, List.of("ROLE_ATTENDEE"));

        // 1. Không có Token -> 403 Forbidden
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());

        // 2. Token bị chỉnh sửa giả mạo (tampered) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.fake_tampered_payload.signature"))
                .andExpect(status().isForbidden());

        // 3. User thường cố truy cập /api/v1/roles (Admin only) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isForbidden());

        // 4. User thường cố truy cập /api/v1/permissions (Admin only) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isForbidden());

        // 5. User thường cố tìm kiếm danh sách người dùng /api/v1/users (Admin only) -> 403 Forbidden
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isForbidden());

        // 6. User thường cố phân quyền /api/v1/users/{id}/roles (Admin only) -> 403 Forbidden
        AssignRolesRequest assignReq = AssignRolesRequest.builder()
                .roleNames(List.of("ROLE_ADMIN"))
                .build();

        mockMvc.perform(put("/api/v1/users/" + testUserId + "/roles")
                        .header("Authorization", "Bearer " + attendeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unhappy Case: Internal Endpoint Security")
    void testInternalEndpointSecurity() throws Exception {
        CreateUserProfileInternalRequest createReq = CreateUserProfileInternalRequest.builder()
                .id(UUID.randomUUID())
                .email("fake@internal.com")
                .username("fake_internal")
                .fullName("Fake Internal")
                .build();

        // 1. Gọi internal endpoint KHÔNG CÓ Header secret -> 403 Forbidden
        mockMvc.perform(post("/internal/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        // 2. Gọi internal endpoint với Header secret SAI -> 403 Forbidden
        mockMvc.perform(post("/internal/v1/users")
                        .header("X-Internal-Secret", "wrong_secret_key_12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
    }

    @Test
    @DisplayName("Unhappy Case: Resource Not Found Scenarios")
    void testResourceNotFoundScenarios() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminToken = generateTestJwt(adminId, List.of("ROLE_ADMIN"));
        UUID nonExistentUserId = UUID.randomUUID();

        // 1. Admin tra cứu người dùng không tồn tại -> 404 Not Found (USER_NOT_FOUND)
        mockMvc.perform(get("/api/v1/users/" + nonExistentUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));

        // 2. Admin gán vai trò không tồn tại -> 404 Not Found (ROLE_NOT_FOUND)
        AssignRolesRequest invalidRoleReq = AssignRolesRequest.builder()
                .roleNames(List.of("ROLE_SUPER_POWER_NON_EXISTENT"))
                .build();

        mockMvc.perform(put("/api/v1/users/" + nonExistentUserId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRoleReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unhappy Case: Validation Failure on Profile Update")
    void testValidationFailureOnUpdate() throws Exception {
        UUID testUserId = UUID.randomUUID();
        String userToken = generateTestJwt(testUserId, List.of("ROLE_ATTENDEE"));

        // Update profile với họ và tên để trống -> 400 Bad Request (VALIDATION_FAILED)
        UpdateProfileRequest invalidReq = UpdateProfileRequest.builder()
                .fullName("") // Blank fullName
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));
    }
}
