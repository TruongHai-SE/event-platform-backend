package vn.eventplatform.ticket.service;

import vn.eventplatform.ticket.dto.TicketQrPayloadDto;

public interface TicketQrPayloadService {

    String generatePayload(TicketQrPayloadDto payload);

    TicketQrPayloadDto verifyAndParsePayload(String qrPayload);
}
