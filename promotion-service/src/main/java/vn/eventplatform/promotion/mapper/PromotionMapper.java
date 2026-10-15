package vn.eventplatform.promotion.mapper;




import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.dto.promotion.PromotionRequest;
import vn.eventplatform.promotion.dto.promotion.PromotionResponse;

import java.util.List;

public final class PromotionMapper {

    private PromotionMapper() {
    }

    // Request -> Entity (CREATE)
    public static Promotion toEntity(PromotionRequest request) {
        Promotion promotion = new Promotion();

        updateEntity(request, promotion);

        return promotion;
    }

    // Entity -> Response
    public static PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getEventId(),
                promotion.getOrganizerId(),
                promotion.getCode(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getMinOrderAmount(),
                promotion.getMaxDiscountAmount(),
                promotion.getTotalUsageLimit(),
                promotion.getUsedCount(),
                promotion.getMaxUsagePerUser(),
                promotion.getIsCompensation(),
                promotion.getValidFrom(),
                promotion.getValidTo(),
                promotion.getIsActive(),
                promotion.getCreatedAt()
        );
    }

    // List<Entity> -> List<Response>
    public static List<PromotionResponse> toResponseList(
            List<Promotion> promotions
    ) {
        return promotions.stream()
                .map(PromotionMapper::toResponse)
                .toList();
    }

    // Request -> Existing Entity (UPDATE)
    public static void updateEntity(
            PromotionRequest request,
            Promotion promotion
    ) {
        promotion.setCode(request.code().trim());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        promotion.setMinOrderAmount(request.minOrderAmount());
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        promotion.setTotalUsageLimit(request.totalUsageLimit());
        promotion.setMaxUsagePerUser(request.maxUsagePerUser());
        promotion.setValidFrom(request.validFrom());
        promotion.setValidTo(request.validTo());
    }
}
