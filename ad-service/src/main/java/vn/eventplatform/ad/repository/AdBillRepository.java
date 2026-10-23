package vn.eventplatform.ad.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.ad.domain.AdBill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdBillRepository extends JpaRepository<AdBill, UUID> {
    Optional<AdBill> findByCampaignId(UUID campaignId);

    List<AdBill> findAllByOrderByCreatedAtDesc();

    List<AdBill> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);
}
