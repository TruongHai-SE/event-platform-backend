package vn.eventplatform.ad.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.ad.domain.CampaignPosition;
import vn.eventplatform.ad.enums.BannerPosition;
import java.util.*;

public interface CampaignPositionRepository extends JpaRepository<CampaignPosition, UUID> {
    List<CampaignPosition> findByCampaignId(UUID campaignId);
    boolean existsByCampaignIdAndPosition(UUID campaignId, BannerPosition position);
}
