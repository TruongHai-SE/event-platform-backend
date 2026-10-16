package vn.eventplatform.ad.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.ad.domain.AdPositionPrice;
import vn.eventplatform.ad.enums.BannerPosition;
import java.util.*;

public interface AdPositionPriceRepository extends JpaRepository<AdPositionPrice, UUID> {
    Optional<AdPositionPrice> findByPosition(BannerPosition position);
    List<AdPositionPrice> findByActiveTrueOrderByPositionAsc();
}
