package vn.eventplatform.ticket.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.dto.TicketQrPayloadDto;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class TicketQrPayloadServiceImpl implements TicketQrPayloadService {

    private final SecretKey secretKey;

    public TicketQrPayloadServiceImpl(
            @Value("${security.ticket.qr-secret:${security.token.secret-key}}") String secret
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            this.secretKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
    }

    @Override
    public String generatePayload(TicketQrPayloadDto payload) {
        if (payload == null || payload.getTicketId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dữ liệu vé để sinh mã QR không hợp lệ");
        }

        Date issuedAt = payload.getIssuedAt() != null ? Date.from(payload.getIssuedAt()) : new Date();

        return Jwts.builder()
                .subject(payload.getTicketId().toString())
                .claim("eid", payload.getEventId() != null ? payload.getEventId().toString() : null)
                .claim("sid", payload.getShowTimeId() != null ? payload.getShowTimeId().toString() : null)
                .claim("ttid", payload.getTicketTypeId() != null ? payload.getTicketTypeId().toString() : null)
                .claim("ttn", payload.getTicketTypeName())
                .claim("oid", payload.getOrderId() != null ? payload.getOrderId().toString() : null)
                .claim("seat", payload.getSeatNumber())
                .issuedAt(issuedAt)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public TicketQrPayloadDto verifyAndParsePayload(String qrPayload) {
        if (qrPayload == null || qrPayload.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã QR không được để trống");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(qrPayload)
                    .getPayload();

            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mã QR không hợp lệ: thiếu định danh vé");
            }

            return TicketQrPayloadDto.builder()
                    .ticketId(UUID.fromString(sub))
                    .eventId(parseUuid(claims.get("eid", String.class)))
                    .showTimeId(parseUuid(claims.get("sid", String.class)))
                    .ticketTypeId(parseUuid(claims.get("ttid", String.class)))
                    .ticketTypeName(claims.get("ttn", String.class))
                    .orderId(parseUuid(claims.get("oid", String.class)))
                    .seatNumber(claims.get("seat", String.class))
                    .issuedAt(claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null)
                    .build();
        } catch (JwtException e) {
            log.warn("Mã QR không hợp lệ hoặc chữ ký sai: {}", e.getMessage());
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã QR vé không hợp lệ hoặc đã bị can thiệp");
        } catch (IllegalArgumentException e) {
            log.warn("Lỗi định dạng UUID trong mã QR: {}", e.getMessage());
            throw new AppException(ErrorCode.BAD_REQUEST, "Dữ liệu mã QR có định dạng không hợp lệ");
        }
    }

    private UUID parseUuid(String str) {
        return (str != null && !str.isBlank()) ? UUID.fromString(str) : null;
    }
}
