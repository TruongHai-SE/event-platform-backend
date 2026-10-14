package vn.eventplatform.ticket.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.eventplatform.ticket.domain.CheckIn;
import vn.eventplatform.ticket.domain.CheckInMethod;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.dto.CheckInResponse;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CheckInMapper {

    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "seatNumber", source = "ticket.seatNumber")
    CheckInResponse toResponse(CheckIn checkIn);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ticket", source = "ticket")
    @Mapping(target = "staffId", source = "staffId")
    @Mapping(target = "method", source = "method")
    @Mapping(target = "checkedInAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    CheckIn toEntity(Ticket ticket, UUID staffId, CheckInMethod method);
}
