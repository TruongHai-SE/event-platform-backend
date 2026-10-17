package vn.eventplatform.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.user.domain.ApplicationStatus;
import vn.eventplatform.user.dto.*;
import vn.eventplatform.user.service.UserProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        UserProfileResponse data = userProfileService.getProfileById(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        UserProfileResponse data = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin thành công", data));
    }

    @PostMapping("/me/organizer-application")
    public ResponseEntity<ApiResponse<OrganizerApplicationResponse>> submitOrganizerApplication(
            Authentication authentication,
            @Valid @RequestBody CreateOrganizerApplicationRequest request
    ) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        OrganizerApplicationResponse data = userProfileService.submitOrganizerApplication(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Nộp đơn đăng ký làm Ban tổ chức thành công", data));
    }

    @GetMapping("/me/organizer-application")
    public ResponseEntity<ApiResponse<OrganizerApplicationResponse>> getMyOrganizerApplication(
            Authentication authentication
    ) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        OrganizerApplicationResponse data = userProfileService.getMyOrganizerApplication(userId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping({"/organizer-applications", "/admin/organizer-applications"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrganizerApplicationResponse>>> getOrganizerApplications(
            @RequestParam(name = "status", required = false) ApplicationStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<OrganizerApplicationResponse> data = userProfileService.getOrganizerApplications(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @RequestMapping(
            value = {
                    "/organizer-applications/{applicationId}/review",
                    "/admin/organizer-applications/{applicationId}/review"
            },
            method = {RequestMethod.PATCH, RequestMethod.PUT}
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrganizerApplicationResponse>> reviewOrganizerApplication(
            Authentication authentication,
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody ReviewOrganizerApplicationRequest request
    ) {
        UUID adminId = UUID.fromString((String) authentication.getPrincipal());
        OrganizerApplicationResponse data = userProfileService.reviewOrganizerApplication(applicationId, adminId, request);
        return ResponseEntity.ok(ApiResponse.ok("Xử lý đơn đăng ký làm Ban tổ chức thành công", data));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> searchUsers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<UserSummaryResponse> data = userProfileService.searchUsers(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(@PathVariable("id") UUID id) {
        UserProfileResponse data = userProfileService.getProfileById(id);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> assignRoles(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignRolesRequest request
    ) {
        UserProfileResponse data = userProfileService.assignRoles(id, request.getRoleNames());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật vai trò thành công", data));
    }
}
