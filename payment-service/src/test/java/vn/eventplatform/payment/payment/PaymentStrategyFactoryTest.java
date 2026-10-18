package vn.eventplatform.payment.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.payment.strategy.PaymentStrategy;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class PaymentStrategyFactoryTest {

    @Test
    @DisplayName("getStrategy returns registered strategy correctly")
    void getStrategy_Found() {
        PaymentStrategy vnpayStrategy = Mockito.mock(PaymentStrategy.class);
        when(vnpayStrategy.getGateway()).thenReturn(PaymentGateway.VNPAY);

        PaymentStrategyFactory factory = new PaymentStrategyFactory(List.of(vnpayStrategy));
        PaymentStrategy actual = factory.getStrategy(PaymentGateway.VNPAY);

        assertThat(actual).isSameAs(vnpayStrategy);
    }

    @Test
    @DisplayName("getStrategy throws AppException when gateway is unsupported")
    void getStrategy_NotFound() {
        PaymentStrategyFactory factory = new PaymentStrategyFactory(Collections.emptyList());

        assertThatThrownBy(() -> factory.getStrategy(PaymentGateway.MOMO))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cổng thanh toán chưa được hỗ trợ");
    }
}
