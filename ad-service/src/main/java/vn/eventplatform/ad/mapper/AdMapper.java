package vn.eventplatform.ad.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.eventplatform.ad.domain.AdBill;
import vn.eventplatform.ad.domain.AdCampaign;
import vn.eventplatform.ad.domain.AdPackage;
import vn.eventplatform.ad.domain.Banner;
import vn.eventplatform.ad.dto.Responses;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdMapper {
    Responses.PackageResponse toPackageResponse(AdPackage adPackage);

    @Mapping(target = "packageId", source = "adPackage.id")
    Responses.CampaignResponse toCampaignResponse(AdCampaign campaign);

    @Mapping(target = "campaignId", source = "campaign.id")
    Responses.BannerResponse toBannerResponse(Banner banner);

    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "campaignName", source = "campaign.name")
    Responses.BillResponse toBillResponse(AdBill bill);
}
