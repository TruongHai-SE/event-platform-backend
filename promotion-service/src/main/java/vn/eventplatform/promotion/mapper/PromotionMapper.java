package vn.eventplatform.promotion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.dto.promotion.PromotionRequest;
import vn.eventplatform.promotion.dto.promotion.PromotionResponse;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PromotionMapper {
    @Mapping(target = "code", expression = "java(request.code().trim())")
    Promotion toEntity(PromotionRequest request);

    PromotionResponse toResponse(Promotion promotion);

    List<PromotionResponse> toResponseList(List<Promotion> promotions);

    @Mapping(target = "code", expression = "java(request.code().trim())")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizerId", ignore = true)
    @Mapping(target = "usedCount", ignore = true)
    @Mapping(target = "isCompensation", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    void updateEntity(PromotionRequest request, @MappingTarget Promotion promotion);
}
