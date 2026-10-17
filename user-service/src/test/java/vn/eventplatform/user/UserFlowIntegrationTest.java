package vn.eventplatform.user;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.user.domain.ApplicationStatus;
import vn.eventplatform.user.domain.OrganizationType;
import vn.eventplatform.user.dto.AssignRolesRequest;
import vn.eventplatform.user.dto.CreateOrganizerApplicationRequest;
import vn.eventplatform.user.dto.CreateUserProfileInternalRequest;
import vn.eventplatform.user.dto.ReviewOrganizerApplicationRequest;
import vn.eventplatform.user.dto.UpdateProfileRequest;
import vn.eventplatform.user.repository.UserProfileRepository;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
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

    @Autowired
    private vn.eventplatform.user.repository.RoleRepository roleRepository;

    @Autowired
    private vn.eventplatform.user.repository.PermissionRepository permissionRepository;

    @Value("${security.token.secret-key}")
    private String tokenSecret;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @org.junit.jupiter.api.BeforeEach
    void seedRolesIfMissing() {
        if (roleRepository.count() == 0) {
            vn.eventplatform.user.domain.Permission pView = permissionRepository.save(
                    vn.eventplatform.user.domain.Permission.builder().name("event:view").description("Xem sự kiện").build()
            );
            roleRepository.save(vn.eventplatform.user.domain.Role.builder()
                    .name("ROLE_ADMIN").description("Quản trị viên").permissions(java.util.Set.of(pView)).build());
            roleRepository.save(vn.eventplatform.user.domain.Role.builder()
                    .name("ROLE_ORGANIZER").description("Ban tổ chức").permissions(java.util.Set.of(pView)).build());
            roleRepository.save(vn.eventplatform.user.domain.Role.builder()
                    .name("ROLE_ATTENDEE").description("Người tham dự").permissions(java.util.Set.of(pView)).build());
            roleRepository.save(vn.eventplatform.user.domain.Role.builder()
                    .name("ROLE_STAFF").description("Nhân viên soát vé").permissions(java.util.Set.of(pView)).build());
        }
    }

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
                .phoneNumber("+84999888777")
                .bio("Tôi là người yêu thích âm nhạc và lễ hội")
                .address("Hồ Chí Minh, Việt Nam")
                .avatarUrl("https://cdn.eventplatform.vn/avatars/u1.png")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Khách Hàng (Updated)"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0999888777"))
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
    @DisplayName("E2E Test: Organizer Application Full Lifecycle & Anti-Sabotage Validation")
    void testOrganizerApplicationLifecycleAndAntiSabotage() throws Exception {
        UUID applicantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        // Tạo hồ sơ cho Applicant
        CreateUserProfileInternalRequest createApplicant = CreateUserProfileInternalRequest.builder()
                .id(applicantId)
                .email("applicant_" + applicantId + "@eventplatform.vn")
                .username("app_" + applicantId.toString().substring(0, 8))
                .fullName("Trần Văn Tổ Chức")
                .phoneNumber("0912345678")
                .build();
        mockMvc.perform(post("/internal/v1/users")
                        .header("X-Internal-Secret", internalSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createApplicant)))
                .andExpect(status().isOk());

        String applicantToken = generateTestJwt(applicantId, List.of("ROLE_ATTENDEE"));
        String adminToken = generateTestJwt(adminId, List.of("ROLE_ADMIN"));

        // 1. Unhappy: Xem đơn khi chưa từng nộp -> 404 NOT_FOUND
        mockMvc.perform(get("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));

        // 2. Anti-Sabotage: Nộp đơn nhưng bỏ trống tên tổ chức (organizationName) -> 400 VALIDATION_FAILED
        CreateOrganizerApplicationRequest missingOrgNameReq = CreateOrganizerApplicationRequest.builder()
                .organizationName("   ")
                .organizationType(OrganizationType.INDIVIDUAL)
                .representativeName("Trần Văn Tổ Chức")
                .contactEmail("contact@festival.vn")
                .contactPhone("0912345678")
                .bio("Chuyên tổ chức các đêm nhạc Acoustic và sự kiện sinh viên tại TP.HCM")
                .build();

        mockMvc.perform(post("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingOrgNameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 3. Anti-Sabotage: Nộp đơn nhưng bỏ trống hoặc viết quá ngắn phần giới thiệu/kế hoạch sự kiện (bio < 10 ký tự) -> 400 VALIDATION_FAILED
        CreateOrganizerApplicationRequest shortBioReq = CreateOrganizerApplicationRequest.builder()
                .organizationName("Công Ty TNHH Sự Kiện Việt")
                .organizationType(OrganizationType.BUSINESS)
                .representativeName("Trần Văn Tổ Chức")
                .contactEmail("contact@festival.vn")
                .contactPhone("0912345678")
                .bio("Ngắn")
                .build();

        mockMvc.perform(post("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortBioReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 4. Anti-Sabotage: Truyền websiteUrl chứa mã độc XSS (javascript:alert(1)) -> 400 VALIDATION_FAILED
        CreateOrganizerApplicationRequest xssUrlReq = CreateOrganizerApplicationRequest.builder()
                .organizationName("Lễ Hội Âm Nhạc Mùa Hè")
                .organizationType(OrganizationType.INDIVIDUAL)
                .representativeName("Trần Văn Tổ Chức")
                .contactEmail("contact@festival.vn")
                .contactPhone("0912345678")
                .websiteUrl("javascript:alert(document.cookie)")
                .bio("Chuyên tổ chức các đêm nhạc Acoustic và sự kiện sinh viên tại TP.HCM")
                .build();

        mockMvc.perform(post("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssUrlReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 5. Happy: Nộp đơn INDIVIDUAL hợp lệ với đủ 6 mục thông tin -> 200 OK (PENDING)
        CreateOrganizerApplicationRequest validIndReq = CreateOrganizerApplicationRequest.builder()
                .organizationName("Lễ Hội Âm Nhạc Mùa Hè")
                .organizationType(OrganizationType.INDIVIDUAL)
                .representativeName("Trần Văn Tổ Chức")
                .contactEmail("contact@festival.vn")
                .contactPhone("+84912345678")
                .address("Quận 1, TP. Hồ Chí Minh")
                .websiteUrl("https://festival.vn")
                .bio("Chuyên tổ chức các đêm nhạc Acoustic và lễ hội âm nhạc mùa hè quy mô 1000 khán giả")
                .build();

        MvcResult submitResult = mockMvc.perform(post("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validIndReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.contactPhone").value("0912345678"))
                .andReturn();

        JsonNode submitJson = objectMapper.readTree(submitResult.getResponse().getContentAsString());
        UUID appId1 = UUID.fromString(submitJson.path("data").path("id").asText());

        // 6. Anti-Sabotage: Cố tình nộp đơn thứ 2 khi đơn đầu đang PENDING -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validIndReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));

        // 7. Anti-Sabotage: User thường cố gọi API Admin duyệt đơn -> 403 FORBIDDEN
        ReviewOrganizerApplicationRequest approveReq = ReviewOrganizerApplicationRequest.builder()
                .status(ApplicationStatus.APPROVED)
                .reviewNote("Hồ sơ hợp lệ")
                .build();

        mockMvc.perform(patch("/api/v1/users/organizer-applications/" + appId1 + "/review")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isForbidden());

        // 8. Anti-Sabotage: Admin tự duyệt đơn của chính mình (applicantId == adminId) -> 403 FORBIDDEN
        String selfAdminToken = generateTestJwt(applicantId, List.of("ROLE_ADMIN"));
        mockMvc.perform(patch("/api/v1/users/organizer-applications/" + appId1 + "/review")
                        .header("Authorization", "Bearer " + selfAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        // 9. Anti-Sabotage: Admin truyền status = PENDING khi review -> 400 BAD_REQUEST
        ReviewOrganizerApplicationRequest pendingReviewReq = ReviewOrganizerApplicationRequest.builder()
                .status(ApplicationStatus.PENDING)
                .build();
        mockMvc.perform(patch("/api/v1/users/organizer-applications/" + appId1 + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pendingReviewReq)))
                .andExpect(status().isBadRequest());

        // 10. Anti-Sabotage: Admin từ chối (REJECTED) nhưng không ghi lý do (reviewNote trống) -> 400 VALIDATION_FAILED
        ReviewOrganizerApplicationRequest rejectNoNoteReq = ReviewOrganizerApplicationRequest.builder()
                .status(ApplicationStatus.REJECTED)
                .reviewNote("   ")
                .build();
        mockMvc.perform(patch("/api/v1/users/organizer-applications/" + appId1 + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectNoNoteReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 11. Happy: Admin từ chối có ghi lý do -> 200 OK (REJECTED)
        ReviewOrganizerApplicationRequest validRejectReq = ReviewOrganizerApplicationRequest.builder()
                .status(ApplicationStatus.REJECTED)
                .reviewNote("Vui lòng bổ sung đường dẫn Fanpage chính thức của sự kiện")
                .build();
        mockMvc.perform(patch("/api/v1/users/organizer-applications/" + appId1 + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // 12. Happy: Sau khi bị REJECTED, User được phép nộp lại đơn mới (BUSINESS) -> 200 OK
        CreateOrganizerApplicationRequest validBizReq = CreateOrganizerApplicationRequest.builder()
                .organizationName("Công Ty TNHH Sự Kiện Việt")
                .organizationType(OrganizationType.BUSINESS)
                .representativeName("Trần Văn Tổ Chức")
                .contactEmail("biz@festival.vn")
                .contactPhone("0912345678")
                .address("Tầng 5, Tòa nhà Bitexco, Quận 1, TP.HCM")
                .websiteUrl("https://facebook.com/sukienviet.official")
                .bio("Đơn vị truyền thông và tổ chức chuỗi sự kiện văn hóa lễ hội tại Việt Nam")
                .build();

        MvcResult resubmitResult = mockMvc.perform(post("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBizReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        UUID appId2 = UUID.fromString(
                objectMapper.readTree(resubmitResult.getResponse().getContentAsString())
                        .path("data").path("id").asText()
        );

        // 13. Admin lọc danh sách đơn PENDING -> 200 OK
        mockMvc.perform(get("/api/v1/users/organizer-applications?status=PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        // 14. Happy: Admin duyệt APPROVED đơn thứ 2 -> User tự động được nâng quyền lên ROLE_ORGANIZER!
        mockMvc.perform(patch("/api/v1/users/organizer-applications/" + appId2 + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Kiểm tra hồ sơ user đã có ROLE_ORGANIZER
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("ROLE_ORGANIZER")));

        // 15. Anti-Sabotage: Admin cố bấm duyệt lại đơn đã APPROVED -> 409 CONFLICT
        mockMvc.perform(patch("/api/v1/users/organizer-applications/" + appId2 + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));

        // 16. Anti-Sabotage: User đã là ROLE_ORGANIZER cố nộp thêm đơn -> 400 BAD_REQUEST
        mockMvc.perform(post("/api/v1/users/me/organizer-application")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBizReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("Unhappy Case: RBAC Authorization Failures")
    void testRbacAuthorizationFailures() throws Exception {
        UUID testUserId = UUID.randomUUID();
        String attendeeToken = generateTestJwt(testUserId, List.of("ROLE_ATTENDEE"));

        // 1. Không có Token -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        // 2. Token bị chỉnh sửa giả mạo (tampered) -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.fake_tampered_payload.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

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
    @DisplayName("Unhappy & Anti-Sabotage Case: Validation Failure on Profile Update (Blank Name, Bad Phone, XSS Avatar URL)")
    void testValidationFailureOnUpdate() throws Exception {
        UUID testUserId = UUID.randomUUID();
        String userToken = generateTestJwt(testUserId, List.of("ROLE_ATTENDEE"));

        // 1. Update profile với họ và tên để trống -> 400 Bad Request (VALIDATION_FAILED)
        UpdateProfileRequest invalidReq = UpdateProfileRequest.builder()
                .fullName("")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 2. Update profile với avatarUrl chứa javascript: XSS -> 400 Bad Request
        UpdateProfileRequest xssAvatarReq = UpdateProfileRequest.builder()
                .fullName("Valid Name")
                .avatarUrl("javascript:alert(1)")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssAvatarReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 3. Update profile với số điện thoại sai định dạng -> 400 Bad Request
        UpdateProfileRequest badPhoneReq = UpdateProfileRequest.builder()
                .fullName("Valid Name")
                .phoneNumber("123456")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPhoneReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));
    }
}
