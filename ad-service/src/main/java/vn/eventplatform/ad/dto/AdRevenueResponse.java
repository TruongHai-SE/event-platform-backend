package vn.eventplatform.ad.dto;

import java.math.BigDecimal;

public record AdRevenueResponse(
        BigDecimal totalBilledAmount,
        BigDecimal collectedRevenue,
        BigDecimal pendingRevenue,
        long totalBills,
        long paidBills,
        long pendingPaymentBills
) {
}
