package vn.eventplatform.payment.payment.vnpay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.RefundStatus;
import vn.eventplatform.payment.payment.PaymentContext;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;
import vn.eventplatform.payment.payment.dto.RefundContext;
import vn.eventplatform.payment.payment.dto.RefundResult;
import vn.eventplatform.payment.payment.strategy.PaymentStrategy;
import vn.eventplatform.payment.payment.vnpay.config.VnPayProperties;
import vn.eventplatform.payment.payment.vnpay.util.VnPaySecurityUtil;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class VnPayPaymentStrategy implements PaymentStrategy {

    private final VnPayProperties vnPayProperties;

    private static final String VNP_VERSION = "2.1.0";
    private static final String VNP_COMMAND = "pay";
    private static final String VNP_ORDER_TYPE = "other";
    private static final String VNP_CURR_CODE = "VND";
    private static final String VNP_LOCALE = "vn";

    private static final DateTimeFormatter VNP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.VNPAY;
    }

    @Override
    public PaymentInitResult createPaymentUrl(PaymentContext context) {
        log.info("Bắt đầu tạo VNPay payment URL cho orderCode: {}, amount: {}", context.getOrderCode(), context.getAmount());

        long vnpAmount = context.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        Instant now = Instant.now();
        String createDate = VNP_DATE_FORMATTER.format(now);
        // Phiên thanh toán VNPay tối đa 15 phút, hoặc theo thời gian giữ chỗ của đơn hàng nếu ngắn hơn
        Instant maxVnpayExpire = now.plusSeconds(15 * 60);
        Instant expireTime = (context.getExpiresAt() != null && context.getExpiresAt().isBefore(maxVnpayExpire))
                ? context.getExpiresAt()
                : maxVnpayExpire;
        String expireDate = VNP_DATE_FORMATTER.format(expireTime);

        String clientIp = context.getClientIp();
        if (clientIp == null || clientIp.trim().isEmpty()) {
            clientIp = "127.0.0.1";
        }

        String returnUrl = context.getReturnUrl();
        if (returnUrl == null || returnUrl.trim().isEmpty()) {
            returnUrl = vnPayProperties.getReturnUrl();
        }

        String orderInfo = context.getOrderInfo();
        if (orderInfo == null || orderInfo.trim().isEmpty()) {
            orderInfo = "Thanh toan don hang " + context.getOrderCode();
        }

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", VNP_VERSION);
        vnpParams.put("vnp_Command", VNP_COMMAND);
        vnpParams.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", VNP_CURR_CODE);
        vnpParams.put("vnp_TxnRef", context.getOrderCode());
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", VNP_ORDER_TYPE);
        vnpParams.put("vnp_Locale", VNP_LOCALE);
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", clientIp);
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        String secureHash = VnPaySecurityUtil.hashAllFields(vnpParams, vnPayProperties.getHashSecret());
        String queryUrl = VnPaySecurityUtil.buildQueryUrl(vnpParams);
        String paymentUrl = vnPayProperties.getPayUrl() + "?" + queryUrl + "&vnp_SecureHash=" + secureHash;

        log.info("Tạo VNPay payment URL thành công cho orderCode: {}", context.getOrderCode());

        return PaymentInitResult.builder()
                .paymentId(context.getPaymentId())
                .orderId(context.getOrderId())
                .method(PaymentGateway.VNPAY)
                .amount(context.getAmount())
                .paymentUrl(paymentUrl)
                .qrCode(null)
                .build();
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> callbackParams) {
        log.info("Bắt đầu verify callback từ VNPay: {}", callbackParams);

        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, String> entry : callbackParams.entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();
            if (fieldName != null && fieldName.startsWith("vnp_") && fieldValue != null && !fieldValue.trim().isEmpty()) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnpSecureHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        if (vnpSecureHash == null || vnpSecureHash.trim().isEmpty()) {
            log.error("VNPay callback thiếu chữ ký vnp_SecureHash");
            throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu chữ ký số vnp_SecureHash từ VNPay");
        }

        String signValue = VnPaySecurityUtil.hashAllFields(fields, vnPayProperties.getHashSecret());
        if (!signValue.equalsIgnoreCase(vnpSecureHash)) {
            log.error("VNPay callback sai chữ ký: expected={}, actual={}", signValue, vnpSecureHash);
            throw new AppException(ErrorCode.BAD_REQUEST, "Chữ ký callback VNPay không hợp lệ (Invalid Checksum)");
        }

        String vnpResponseCode = callbackParams.get("vnp_ResponseCode");
        String vnpTransactionStatus = callbackParams.get("vnp_TransactionStatus");
        String vnpTxnRef = callbackParams.get("vnp_TxnRef");
        String vnpTransactionNo = callbackParams.get("vnp_TransactionNo");
        String vnpAmountStr = callbackParams.get("vnp_Amount");
        String vnpPayDate = callbackParams.get("vnp_PayDate");

        BigDecimal amount = null;
        if (vnpAmountStr != null && !vnpAmountStr.isEmpty()) {
            amount = new BigDecimal(vnpAmountStr).divide(BigDecimal.valueOf(100));
        }

        Instant payDate = Instant.now();
        if (vnpPayDate != null && vnpPayDate.length() == 14) {
            try {
                payDate = VNP_DATE_FORMATTER.parse(vnpPayDate, Instant::from);
            } catch (Exception e) {
                log.warn("Không parse được vnp_PayDate={}", vnpPayDate, e);
            }
        }

        boolean isSuccess = "00".equals(vnpResponseCode) && "00".equals(vnpTransactionStatus);
        String message = isSuccess ? "Giao dịch VNPay thành công" : "Giao dịch VNPay thất bại hoặc bị hủy (code=" + vnpResponseCode + ")";

        log.info("Verify callback VNPay hoàn tất: orderCode={}, success={}, transactionNo={}", vnpTxnRef, isSuccess, vnpTransactionNo);

        return PaymentCallbackResult.builder()
                .success(isSuccess)
                .method(PaymentGateway.VNPAY)
                .transactionId(vnpTransactionNo)
                .orderCode(vnpTxnRef)
                .amount(amount)
                .responseCode(vnpResponseCode)
                .message(message)
                .payDate(payDate)
                .build();
    }

    @Override
    public RefundResult refund(RefundContext context) {
        log.warn("Chức năng hoàn tiền VNPay tự động qua API đang trong giai đoạn phát triển: refundLogId={}", context.getRefundLogId());
        return RefundResult.builder()
                .success(false)
                .refundLogId(context.getRefundLogId())
                .status(RefundStatus.PROCESSING)
                .message("Hệ thống đã ghi nhận yêu cầu hoàn tiền VNPay. Cần xác nhận thủ công hoặc qua API đối soát")
                .refundedAmount(context.getAmount())
                .refundedAt(null)
                .build();
    }
}
