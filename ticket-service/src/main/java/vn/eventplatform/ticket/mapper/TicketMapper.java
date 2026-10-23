package vn.eventplatform.ticket.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketResponse;

@Mapper(componentModel = "spring", uses = {CheckInMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TicketMapper {

    @Mapping(target = "checkIn", source = "checkIn")
    @Mapping(target = "qrImageUrl", expression = "java(ticket.getId() != null ? \"/api/v1/tickets/\" + ticket.getId() + \"/qr-code\" : null)")
    TicketResponse toResponse(Ticket ticket);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(vn.eventplatform.ticket.domain.TicketStatus.SOLD)")
    @Mapping(target = "checkIn", ignore = true)
    Ticket toEntity(IssueTicketRequest request);
}
