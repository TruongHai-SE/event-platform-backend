package vn.eventplatform.payment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.eventplatform.payment.domain.Payment;
import vn.eventplatform.payment.dto.PaymentInitResponse;
import vn.eventplatform.payment.dto.PaymentResponse;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);

    List<PaymentResponse> toResponseList(List<Payment> payments);

    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "orderId", source = "payment.orderId")
    @Mapping(target = "orderCode", source = "payment.orderCode")
    @Mapping(target = "method", source = "payment.method")
    @Mapping(target = "amount", source = "payment.amount")
    @Mapping(target = "status", source = "payment.status")
    @Mapping(target = "paymentUrl", source = "initResult.paymentUrl")
    @Mapping(target = "qrCode", source = "initResult.qrCode")
    @Mapping(target = "expiresAt", ignore = true)
    PaymentInitResponse toInitResponse(Payment payment, PaymentInitResult initResult);
}
