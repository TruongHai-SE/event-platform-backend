package vn.eventplatform.payment.payment.vnpay.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class VnPaySecurityUtil {

    private VnPaySecurityUtil() {
    }

    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key và data không được null");
            }
            Mac hmac = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac.init(secretKey);
            byte[] result = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Lỗi khi băm HmacSHA512", ex);
            return "";
        }
    }

    public static String hashAllFields(Map<String, String> fields, String secretKey) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                sb.append(fieldName);
                sb.append("=");
                sb.append(encodeValue(fieldValue));
                if (itr.hasNext()) {
                    sb.append("&");
                }
            }
        }
        return hmacSHA512(secretKey, sb.toString());
    }

    public static String buildQueryUrl(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                query.append(encodeValue(fieldName));
                query.append("=");
                query.append(encodeValue(fieldValue));
                if (itr.hasNext()) {
                    query.append("&");
                }
            }
        }
        return query.toString();
    }

    public static String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());
        } catch (UnsupportedEncodingException e) {
            log.error("Lỗi encode URL parameter", e);
            return value;
        }
    }
}
