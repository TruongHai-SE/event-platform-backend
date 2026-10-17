package vn.eventplatform.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.user.domain.*;
import vn.eventplatform.user.dto.*;
import vn.eventplatform.user.repository.OrganizerApplicationRepository;
import vn.eventplatform.user.repository.RoleRepository;
import vn.eventplatform.user.repository.UserProfileRepository;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final RoleRepository roleRepository;
    private final OrganizerApplicationRepository organizerApplicationRepository;

    @Transactional
    public UserProfileResponse createProfile(CreateUserProfileInternalRequest request) {
        if (userProfileRepository.existsById(request.getId())) {
            return getProfileById(request.getId());
        }

        Role defaultRole = roleRepository.findByName("ROLE_ATTENDEE")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_ATTENDEE")
                        .description("Khách tham gia sự kiện")
                        .build()));

        Set<Role> roles = new HashSet<>();
        roles.add(defaultRole);

        UserProfile profile = UserProfile.builder()
                .id(request.getId())
                .email(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .username(request.getUsername().trim())
                .fullName(request.getFullName().trim())
                .phoneNumber(normalizePhone(request.getPhoneNumber()))
                .avatarUrl(request.getAvatarUrl())
                .roles(roles)
                .build();

        profile = userProfileRepository.save(profile);
        return mapToResponse(profile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(UUID id) {
        UserProfile profile = userProfileRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));
        return mapToResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID id, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));

        profile.setFullName(request.getFullName().trim());
        if (request.getPhoneNumber() != null) {
            String normalizedPhone = normalizePhone(request.getPhoneNumber());
            if (normalizedPhone != null && userProfileRepository.existsByPhoneNumberAndIdNot(normalizedPhone, id)) {
                throw new AppException(ErrorCode.CONFLICT, "Số điện thoại này đã được sử dụng bởi tài khoản khác");
            }
            profile.setPhoneNumber(normalizedPhone);
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio().trim());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress().trim());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl().trim());
        }

        profile = userProfileRepository.save(profile);
        return mapToResponse(profile);
    }

    @Transactional
    public OrganizerApplicationResponse submitOrganizerApplication(UUID userId, CreateOrganizerApplicationRequest request) {
        UserProfile profile = userProfileRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));

        boolean alreadyOrganizer = profile.getRoles() != null && profile.getRoles().stream()
                .anyMatch(r -> "ROLE_ORGANIZER".equals(r.getName()));
        if (alreadyOrganizer) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản của bạn đã có vai trò Ban tổ chức (ROLE_ORGANIZER)");
        }

        if (organizerApplicationRepository.existsByUserProfile_IdAndStatus(userId, ApplicationStatus.PENDING)) {
            throw new AppException(ErrorCode.CONFLICT, "Bạn đang có một đơn đăng ký làm Ban tổ chức ở trạng thái chờ duyệt");
        }

        OrganizationType orgType = request.getOrganizationType() != null
                ? request.getOrganizationType()
                : OrganizationType.INDIVIDUAL;

        OrganizerApplication application = OrganizerApplication.builder()
                .userProfile(profile)
                .organizationName(request.getOrganizationName().trim())
                .organizationType(orgType)
                .representativeName(request.getRepresentativeName().trim())
                .contactEmail(request.getContactEmail().trim().toLowerCase(Locale.ROOT))
                .contactPhone(normalizePhone(request.getContactPhone()))
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .websiteUrl(request.getWebsiteUrl() != null ? request.getWebsiteUrl().trim() : null)
                .bio(request.getBio() != null ? request.getBio().trim() : null)
                .status(ApplicationStatus.PENDING)
                .build();

        application = organizerApplicationRepository.save(application);
        return mapApplicationToResponse(application);
    }

    @Transactional(readOnly = true)
    public OrganizerApplicationResponse getMyOrganizerApplication(UUID userId) {
        List<OrganizerApplication> apps = organizerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if (apps.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Bạn chưa nộp đơn đăng ký làm Ban tổ chức");
        }
        return mapApplicationToResponse(apps.get(0));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizerApplicationResponse> getOrganizerApplications(ApplicationStatus status, Pageable pageable) {
        Page<OrganizerApplication> page = organizerApplicationRepository.findByStatusFilter(status, pageable);
        return PageResponse.from(page.map(this::mapApplicationToResponse));
    }

    @Transactional
    public OrganizerApplicationResponse reviewOrganizerApplication(
            UUID applicationId,
            UUID adminId,
            ReviewOrganizerApplicationRequest request
    ) {
        OrganizerApplication application = organizerApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn đăng ký làm Ban tổ chức"));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new AppException(ErrorCode.CONFLICT, "Đơn đăng ký này đã được duyệt hoặc từ chối trước đó");
        }

        if (application.getUserProfile().getId().equals(adminId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Quản trị viên không được phép tự duyệt đơn của chính mình");
        }

        if (request.getStatus() != ApplicationStatus.APPROVED && request.getStatus() != ApplicationStatus.REJECTED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái duyệt chỉ chấp nhận APPROVED hoặc REJECTED");
        }

        if (request.getStatus() == ApplicationStatus.REJECTED
                && (request.getReviewNote() == null || request.getReviewNote().isBlank())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Vui lòng nhập lý do từ chối đơn đăng ký");
        }

        application.setStatus(request.getStatus());
        application.setReviewedBy(adminId);
        application.setReviewNote(request.getReviewNote() != null ? request.getReviewNote().trim() : null);
        application.setReviewedAt(Instant.now());

        if (request.getStatus() == ApplicationStatus.APPROVED) {
            UserProfile applicantProfile = application.getUserProfile();
            Role organizerRole = roleRepository.findByName("ROLE_ORGANIZER")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("ROLE_ORGANIZER")
                            .description("Ban tổ chức sự kiện và lễ hội")
                            .build()));
            if (applicantProfile.getRoles() == null) {
                applicantProfile.setRoles(new HashSet<>());
            }
            applicantProfile.getRoles().add(organizerRole);
            userProfileRepository.save(applicantProfile);
        }

        application = organizerApplicationRepository.save(application);
        return mapApplicationToResponse(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> searchUsers(String keyword, Pageable pageable) {
        Page<UserProfile> page = userProfileRepository.searchUsers(keyword, pageable);
        return PageResponse.from(page.map(this::mapToSummary));
    }

    @Transactional
    public UserProfileResponse assignRoles(UUID userId, List<String> roleNames) {
        UserProfile profile = userProfileRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));

        Set<Role> newRoles = new HashSet<>();
        for (String roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND, "Vai trò không hợp lệ: " + roleName));
            newRoles.add(role);
        }

        profile.setRoles(newRoles);
        profile = userProfileRepository.save(profile);
        return mapToResponse(profile);
    }

    private static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String cleaned = rawPhone.trim();
        if (cleaned.startsWith("+84")) {
            return "0" + cleaned.substring(3);
        }
        if (cleaned.startsWith("84") && cleaned.length() == 11) {
            return "0" + cleaned.substring(2);
        }
        return cleaned;
    }

    private OrganizerApplicationResponse mapApplicationToResponse(OrganizerApplication a) {
        return OrganizerApplicationResponse.builder()
                .id(a.getId())
                .userId(a.getUserProfile().getId())
                .username(a.getUserProfile().getUsername())
                .userEmail(a.getUserProfile().getEmail())
                .organizationName(a.getOrganizationName())
                .organizationType(a.getOrganizationType())
                .representativeName(a.getRepresentativeName())
                .contactEmail(a.getContactEmail())
                .contactPhone(a.getContactPhone())
                .address(a.getAddress())
                .websiteUrl(a.getWebsiteUrl())
                .bio(a.getBio())
                .status(a.getStatus())
                .reviewedBy(a.getReviewedBy())
                .reviewNote(a.getReviewNote())
                .reviewedAt(a.getReviewedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private UserProfileResponse mapToResponse(UserProfile p) {
        List<String> roleNames = p.getRoles() != null
                ? p.getRoles().stream().map(Role::getName).sorted().toList()
                : Collections.emptyList();

        return UserProfileResponse.builder()
                .id(p.getId())
                .email(p.getEmail())
                .username(p.getUsername())
                .fullName(p.getFullName())
                .phoneNumber(p.getPhoneNumber())
                .avatarUrl(p.getAvatarUrl())
                .bio(p.getBio())
                .address(p.getAddress())
                .roles(roleNames)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private UserSummaryResponse mapToSummary(UserProfile p) {
        List<String> roleNames = p.getRoles() != null
                ? p.getRoles().stream().map(Role::getName).sorted().toList()
                : Collections.emptyList();

        return UserSummaryResponse.builder()
                .id(p.getId())
                .email(p.getEmail())
                .username(p.getUsername())
                .fullName(p.getFullName())
                .phoneNumber(p.getPhoneNumber())
                .avatarUrl(p.getAvatarUrl())
                .roles(roleNames)
                .createdAt(p.getCreatedAt())
                .build();
    }
}
