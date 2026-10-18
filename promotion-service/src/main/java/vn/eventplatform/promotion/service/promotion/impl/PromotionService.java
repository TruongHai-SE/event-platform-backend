package vn.eventplatform.promotion.service.promotion.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.dto.promotion.PromotionUsageResponse;
import vn.eventplatform.promotion.dto.promotion.PromotionRequest;
import vn.eventplatform.promotion.dto.promotion.PromotionResponse;
import vn.eventplatform.promotion.enums.DiscountType;
import vn.eventplatform.promotion.mapper.PromotionMapper;
import vn.eventplatform.promotion.repository.PromotionRepository;
import vn.eventplatform.promotion.repository.PromotionUsageRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionService {
    private final PromotionRepository repository;
    private final PromotionUsageRepository usageRepository;
    private final PromotionMapper promotionMapper;

    @Transactional
    public PromotionResponse createForOrganizer(PromotionRequest request) {
        return create(request, currentUserId(), false);
    }

    @Transactional
    public PromotionResponse createForAdmin(PromotionRequest request) {
        return create(request, request.organizerId(), true);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> findAllForOrganizer() {
        return promotionMapper.toResponseList(repository.findByOrganizerId(currentUserId()));
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> findAllForAdmin() {
        return promotionMapper.toResponseList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public PromotionResponse findByIdForOrganizer(UUID id) {
        return promotionMapper.toResponse(findOrganizerPromotion(id));
    }

    /**
     * Returns every recorded use, including usages later released because the order was cancelled or expired.
     */
    @Transactional(readOnly = true)
    public List<PromotionUsageResponse> findUsageHistoryForOrganizer(UUID id) {
        findOrganizerPromotion(id);
        return usageRepository.findByPromotionIdOrderByUsedAtDesc(id).stream()
                .map(PromotionUsageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse findByIdForAdmin(UUID id) {
        return promotionMapper.toResponse(findPromotion(id));
    }

    @Transactional
    public PromotionResponse updateForOrganizer(UUID id, PromotionRequest request) {
        Promotion promotion = findOrganizerPromotion(id);
        update(promotion, request, currentUserId(), false);
        return promotionMapper.toResponse(repository.save(promotion));
    }

    @Transactional
    public PromotionResponse updateForAdmin(UUID id, PromotionRequest request) {
        Promotion promotion = findPromotion(id);
        update(promotion, request, request.organizerId(), true);
        return promotionMapper.toResponse(repository.save(promotion));
    }

    @Transactional
    public void deleteForOrganizer(UUID id) {
        deactivate(findOrganizerPromotion(id));
    }

    @Transactional
    public void deleteForAdmin(UUID id) {
        deactivate(findPromotion(id));
    }

    private PromotionResponse create(PromotionRequest request, UUID ownerId, boolean admin) {
        validate(request, 0, null);
        if (!admin && Boolean.TRUE.equals(request.isCompensation())) {
            throw new AccessDeniedException("Chỉ quản trị viên được tạo voucher bồi hoàn");
        }
        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setOrganizerId(ownerId);
        promotion.setUsedCount(0);
        promotion.setIsActive(request.isActive() == null || request.isActive());
        promotion.setIsCompensation(admin && Boolean.TRUE.equals(request.isCompensation()));
        return promotionMapper.toResponse(repository.save(promotion));
    }

    private void update(Promotion promotion, PromotionRequest request, UUID ownerId, boolean admin) {
        int usedCount = promotion.getUsedCount() == null ? 0 : promotion.getUsedCount();
        validate(request, usedCount, promotion.getId());
        if (!admin && Boolean.TRUE.equals(request.isCompensation())) {
            throw new AccessDeniedException("Chỉ quản trị viên được tạo voucher bồi hoàn");
        }
        promotionMapper.updateEntity(request, promotion);
        promotion.setOrganizerId(ownerId);
        promotion.setEventId(request.eventId());
        if (admin) {
            promotion.setIsCompensation(Boolean.TRUE.equals(request.isCompensation()));
        }
        if (request.isActive() != null) {
            promotion.setIsActive(request.isActive());
        }
    }

    private void deactivate(Promotion promotion) {
        promotion.setIsActive(false);
        repository.save(promotion);
    }

    private Promotion findPromotion(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy voucher"));
    }

    private Promotion findOrganizerPromotion(UUID id) {
        Promotion promotion = findPromotion(id);
        if (!currentUserId().equals(promotion.getOrganizerId())) {
            throw new AccessDeniedException("Bạn không có quyền quản lý voucher này");
        }
        return promotion;
    }

    private void validate(PromotionRequest request, int usedCount, UUID currentId) {
        String code = request.code() == null ? null : request.code().trim();
        if (code == null || code.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã voucher không được để trống");
        }
        if ((currentId == null && repository.existsByCode(code))
                || (currentId != null && repository.existsByCodeAndIdNot(code, currentId))) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã voucher đã tồn tại");
        }
        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher phần trăm không được vượt quá 100%");
        }
        if (request.discountType() == DiscountType.PERCENTAGE && request.maxDiscountAmount() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher phần trăm phải có mức giảm tối đa");
        }
        if (request.validFrom() != null && request.validTo() != null && !request.validTo().isAfter(request.validFrom())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        if (request.totalUsageLimit() != null && request.totalUsageLimit() < usedCount) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Giới hạn sử dụng không được nhỏ hơn lượt đã dùng");
        }
        if (request.maxUsagePerUser() != null && request.totalUsageLimit() != null
                && request.maxUsagePerUser() > request.totalUsageLimit()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Giới hạn mỗi người dùng không được lớn hơn tổng giới hạn");
        }
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AccessDeniedException("Yêu cầu xác thực tài khoản");
        }
        try {
            return UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Định danh người dùng không hợp lệ");
        }
    }
}
