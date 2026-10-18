package vn.eventplatform.payment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.eventplatform.payment.domain.RefundLog;
import vn.eventplatform.payment.dto.RefundResponse;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RefundLogMapper {

    @Mapping(target = "paymentId", source = "payment.id")
    RefundResponse toResponse(RefundLog refundLog);

    List<RefundResponse> toResponseList(List<RefundLog> refundLogs);
}
