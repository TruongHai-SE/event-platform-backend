package vn.eventplatform.ad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.eventplatform.ad.domain.AdCampaign;
import vn.eventplatform.ad.domain.AdPackage;
import vn.eventplatform.ad.domain.Banner;
import vn.eventplatform.ad.dto.Requests;
import vn.eventplatform.ad.enums.BannerPosition;
import vn.eventplatform.ad.enums.CampaignStatus;
import vn.eventplatform.ad.enums.PricingMode;
import vn.eventplatform.ad.repository.AdCampaignRepository;
import vn.eventplatform.ad.repository.AdPackageRepository;
import vn.eventplatform.ad.repository.BannerRepository;
import vn.eventplatform.common.exception.AppException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdServiceTest {
    @Mock private AdPackageRepository packages;
    @Mock private AdCampaignRepository campaigns;
    @Mock private BannerRepository banners;
    @InjectMocks private AdService service;

    private UUID packageId;
    private AdPackage adPackage;

    @BeforeEach
    void setUp() {
        packageId = UUID.randomUUID();
        adPackage = new AdPackage();
        adPackage.setId(packageId);
        adPackage.setName("Premium");
        adPackage.setPrice(new BigDecimal("120.00"));
        adPackage.setDurationDays(7);
        adPackage.setActive(true);
    }

    @Test
    void createsPrepaidCampaignWithPackagePrice() {
        when(packages.findById(packageId)).thenReturn(Optional.of(adPackage));
        when(campaigns.save(any(AdCampaign.class))).thenAnswer(invocation -> {
            AdCampaign campaign = invocation.getArgument(0);
            campaign.setId(UUID.randomUUID());
            return campaign;
        });

        var result = service.register(UUID.randomUUID(), campaignRequest(PricingMode.PREPAID, null));

        assertThat(result.status()).isEqualTo(CampaignStatus.PENDING_APPROVAL);
        assertThat(result.feeAmount()).isEqualByComparingTo("120.00");
        assertThat(result.escrowAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createsPostpaidCampaignWithEscrowAndPublishesSettlement() {
        when(packages.findById(packageId)).thenReturn(Optional.of(adPackage));
        when(campaigns.save(any(AdCampaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(UUID.randomUUID(), campaignRequest(PricingMode.POSTPAID_ESCROW, new BigDecimal("150")));
        ArgumentCaptor<AdCampaign> captor = ArgumentCaptor.forClass(AdCampaign.class);
        verify(campaigns).save(captor.capture());
        AdCampaign campaign = captor.getValue();
        campaign.setId(UUID.randomUUID());
        when(campaigns.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        service.publishSettlement(campaign.getId());

        assertThat(campaign.getFeeAmount()).isEqualByComparingTo("120.00");
        assertThat(campaign.getEscrowAmount()).isEqualByComparingTo("150");
        assertThat(campaign.getSettlementPublished()).isTrue();
    }

    @Test
    void rejectsCampaignWithInvalidTimeRange() {
        when(packages.findById(packageId)).thenReturn(Optional.of(adPackage));
        LocalDateTime now = LocalDateTime.now();
        var request = new Requests.CampaignRequest(packageId, "Bad", PricingMode.PREPAID, now, now, null);

        assertThatThrownBy(() -> service.register(UUID.randomUUID(), request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Thời gian campaign không hợp lệ");
    }

    @Test
    void returnsOnlyApprovedActiveBannersInRepositoryOrder() {
        Banner first = banner(100);
        Banner second = banner(10);
        when(banners.findPublic(any(LocalDateTime.class))).thenReturn(List.of(first, second));

        var result = service.publicBanners();

        assertThat(result).extracting(response -> response.priority()).containsExactly(100, 10);
        verify(banners).findPublic(any(LocalDateTime.class));
    }

    @Test
    void approvesPendingBannerImage() {
        Banner banner = banner(10);
        banner.setImageUrl("https://cdn.example/old.png");
        banner.setPendingImageUrl("https://cdn.example/new.png");
        when(banners.findById(banner.getId())).thenReturn(Optional.of(banner));
        when(banners.save(any(Banner.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.approveImage(banner.getId());

        assertThat(result.imageUrl()).isEqualTo("https://cdn.example/new.png");
        assertThat(result.pendingImageUrl()).isNull();
    }

    @Test
    void rejectsImageApprovalWithoutPendingImage() {
        Banner banner = banner(10);
        when(banners.findById(banner.getId())).thenReturn(Optional.of(banner));

        assertThatThrownBy(() -> service.approveImage(banner.getId()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không có ảnh chờ duyệt");
    }

    private Requests.CampaignRequest campaignRequest(PricingMode mode, BigDecimal escrow) {
        return new Requests.CampaignRequest(packageId, "Gateway campaign", mode,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1), escrow);
    }

    private Banner banner(int priority) {
        AdCampaign campaign = new AdCampaign();
        campaign.setId(UUID.randomUUID());
        campaign.setStatus(CampaignStatus.APPROVED);
        Banner banner = new Banner();
        banner.setId(UUID.randomUUID());
        banner.setCampaign(campaign);
        banner.setImageUrl("https://cdn.example/banner.png");
        banner.setPosition(BannerPosition.HOME_TOP);
        banner.setPriority(priority);
        banner.setStartsAt(LocalDateTime.now().minusHours(1));
        banner.setEndsAt(LocalDateTime.now().plusHours(1));
        banner.setActive(true);
        return banner;
    }
}
