package vn.eventplatform.promotion.service.promotion.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.domain.PromotionUsage;
import vn.eventplatform.promotion.dto.promotion.PromotionUsageResponse;
import vn.eventplatform.promotion.enums.PromotionUsageStatus;
import vn.eventplatform.promotion.repository.PromotionRepository;
import vn.eventplatform.promotion.repository.PromotionUsageRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionUsageRepository usageRepository;
    @InjectMocks private PromotionService promotionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUsageHistoryForVoucherOwnedByOrganizer() {
        UUID organizerId = UUID.randomUUID();
        UUID promotionId = UUID.randomUUID();
        authenticateAs(organizerId);
        when(promotionRepository.findById(promotionId)).thenReturn(Optional.of(Promotion.builder().organizerId(organizerId).build()));
        PromotionUsage latest = usage(promotionId, LocalDateTime.of(2026, 10, 17, 10, 30));
        PromotionUsage earlier = usage(promotionId, LocalDateTime.of(2026, 10, 17, 9, 30));
        when(usageRepository.findByPromotionIdOrderByUsedAtDesc(promotionId)).thenReturn(List.of(latest, earlier));

        List<PromotionUsageResponse> result = promotionService.findUsageHistoryForOrganizer(promotionId);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().userId()).isEqualTo(latest.getUserId());
        assertThat(result.getFirst().orderId()).isEqualTo(latest.getOrderId());
        assertThat(result.getFirst().discountAmount()).isEqualByComparingTo("25.00");
        assertThat(result.getFirst().status()).isEqualTo(PromotionUsageStatus.USED);
        verify(usageRepository).findByPromotionIdOrderByUsedAtDesc(promotionId);
    }

    @Test
    void deniesUsageHistoryOfAnotherOrganizersVoucher() {
        UUID promotionId = UUID.randomUUID();
        authenticateAs(UUID.randomUUID());
        when(promotionRepository.findById(promotionId)).thenReturn(Optional.of(Promotion.builder().organizerId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> promotionService.findUsageHistoryForOrganizer(promotionId))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(usageRepository);
    }

    private PromotionUsage usage(UUID promotionId, LocalDateTime usedAt) {
        return PromotionUsage.builder().promotionId(promotionId).userId(UUID.randomUUID()).orderId(UUID.randomUUID())
                .discountAmount(new BigDecimal("25.00")).status(PromotionUsageStatus.USED).usedAt(usedAt).build();
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId.toString(), null));
    }
}
