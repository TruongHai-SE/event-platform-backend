package vn.eventplatform.ticket.service;

import vn.eventplatform.ticket.dto.CheckInRequest;
import vn.eventplatform.ticket.dto.CheckInResponse;

import java.util.UUID;

public interface CheckInService {

    CheckInResponse processCheckIn(CheckInRequest request);

    CheckInResponse getCheckInByTicketId(UUID ticketId);
}
