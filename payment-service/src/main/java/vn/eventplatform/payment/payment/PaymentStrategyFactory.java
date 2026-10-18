package vn.eventplatform.payment.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.payment.strategy.PaymentStrategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PaymentStrategyFactory {

    private final Map<PaymentGateway, PaymentStrategy> strategies = new EnumMap<>(PaymentGateway.class);

    public PaymentStrategyFactory(List<PaymentStrategy> strategyList) {
        for (PaymentStrategy strategy : strategyList) {
            strategies.put(strategy.getGateway(), strategy);
            log.info("PaymentStrategyFactory: Đã đăng ký PaymentStrategy cho gateway [{}]", strategy.getGateway());
        }
    }

    public PaymentStrategy getStrategy(PaymentGateway gateway) {
        PaymentStrategy strategy = strategies.get(gateway);
        if (strategy == null) {
            log.error("PaymentStrategyFactory: Chưa hỗ trợ cổng thanh toán: {}", gateway);
            throw new AppException(ErrorCode.BAD_REQUEST, "Cổng thanh toán chưa được hỗ trợ: " + gateway);
        }
        return strategy;
    }
}
