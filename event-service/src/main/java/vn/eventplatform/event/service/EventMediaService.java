package vn.eventplatform.event.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventMediaService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${cloudinary.root-folder:mss301-event-platform}")
    private String rootFolder;

    @Value("${storage.local-dir:./uploads}")
    private String localDir;

    public record MediaUploadResult(String url, String publicId) {
    }

    public MediaUploadResult uploadEventImage(UUID eventId, MultipartFile file, String subfolder, String prefix) {
        String ext = validateAndDetectImageExtension(file);
        String sanitizedRoot = sanitizeRootFolder(rootFolder);
        String targetFolder = sanitizedRoot + "/events/" + eventId + "/" + subfolder;
        String fileLeafId = prefix + "_" + Instant.now().toEpochMilli();

        if (isCloudinaryConfigured()) {
            return uploadToCloudinary(file, targetFolder, fileLeafId, ext);
        }
        return saveToLocalFallback(file, targetFolder, fileLeafId, ext);
    }

    public void deleteMediaAssetSafely(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            return;
        }
        String sanitizedRoot = sanitizeRootFolder(rootFolder);
        if (!publicId.startsWith(sanitizedRoot + "/") || publicId.contains("..")) {
            log.warn("Blocked deletion of asset outside project root folder: publicId={}, rootFolder={}",
                    publicId, sanitizedRoot);
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Không được phép xóa tài nguyên nằm ngoài thư mục dự án (" + sanitizedRoot + ")");
        }

        if (isCloudinaryConfigured()) {
            deleteFromCloudinary(publicId);
        } else {
            deleteFromLocalFallback(publicId);
        }
    }

    public String validateExternalImageUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "URL hình ảnh không được để trống");
        }
        String trimmed = rawUrl.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "URL hình ảnh không đúng định dạng");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "URL hình ảnh bắt buộc phải sử dụng giao thức HTTPS");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "URL hình ảnh thiếu tên miền hợp lệ");
        }
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost)
                || lowerHost.endsWith(".local")
                || lowerHost.endsWith(".internal")
                || "169.254.169.254".equals(lowerHost)
                || "127.0.0.1".equals(lowerHost)
                || "::1".equals(lowerHost)
                || "[::1]".equals(lowerHost)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "URL hình ảnh không được trỏ tới địa chỉ nội bộ (SSRF blocked)");
        }

        if (looksLikeIpLiteral(lowerHost)) {
            try {
                InetAddress addr = InetAddress.getByName(host);
                if (addr.isAnyLocalAddress()
                        || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || isCgnatOrMetadataAddress(addr.getAddress())) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "URL hình ảnh không được trỏ tới dải IP nội bộ (SSRF blocked)");
                }
            } catch (AppException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Địa chỉ IP trong URL hình ảnh không hợp lệ");
            }
        }
        return trimmed;
    }

    public String validateAndDetectImageExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File hình ảnh tải lên không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Kích thước hình ảnh vượt quá giới hạn tối đa 5MB");
        }

        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.read(header);
        } catch (IOException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể đọc nội dung file tải lên");
        }

        if (bytesRead < 4) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File hình ảnh không hợp lệ hoặc bị hỏng");
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytesRead >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return "png";
        }
        // WebP: RIFF....WEBP
        if (bytesRead >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }

        throw new AppException(ErrorCode.BAD_REQUEST,
                "Định dạng file không được hỗ trợ (chỉ chấp nhận JPEG, PNG, WebP chuẩn Magic Bytes)");
    }

    private boolean isCloudinaryConfigured() {
        return StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret);
    }

    private MediaUploadResult uploadToCloudinary(MultipartFile file, String targetFolder, String fileLeafId, String ext) {
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            Map<String, String> signParams = new TreeMap<>();
            signParams.put("folder", targetFolder);
            signParams.put("overwrite", "true");
            signParams.put("public_id", fileLeafId);
            signParams.put("timestamp", timestamp);

            String signature = signCloudinaryParams(signParams, apiSecret);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return fileLeafId + "." + ext;
                }
            };
            body.add("file", fileResource);
            body.add("api_key", apiKey);
            body.add("timestamp", timestamp);
            body.add("folder", targetFolder);
            body.add("public_id", fileLeafId);
            body.add("overwrite", "true");
            body.add("signature", signature);

            String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
            String responseStr = restClient.post()
                    .uri(uploadUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(responseStr);
            String secureUrl = node.path("secure_url").asText();
            String returnedPublicId = node.path("public_id").asText();
            if (!StringUtils.hasText( returnedPublicId )) {
                returnedPublicId = targetFolder + "/" + fileLeafId;
            }
            return new MediaUploadResult(secureUrl, returnedPublicId);
        } catch (Exception ex) {
            log.error("Cloudinary upload failed for folder={}: {}", targetFolder, ex.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Tải ảnh lên Cloudinary thất bại: " + ex.getMessage());
        }
    }

    private void deleteFromCloudinary(String publicId) {
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            Map<String, String> signParams = new TreeMap<>();
            signParams.put("public_id", publicId);
            signParams.put("timestamp", timestamp);
            String signature = signCloudinaryParams(signParams, apiSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("public_id", publicId);
            body.add("api_key", apiKey);
            body.add("timestamp", timestamp);
            body.add("signature", signature);

            String destroyUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy";
            restClient.post()
                    .uri(destroyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to delete old asset on Cloudinary (publicId={}): {}", publicId, ex.getMessage());
        }
    }

    private MediaUploadResult saveToLocalFallback(MultipartFile file, String targetFolder, String fileLeafId, String ext) {
        String fullPublicId = targetFolder + "/" + fileLeafId;
        try {
            Path folderPath = Paths.get(localDir, targetFolder.split("/")).normalize();
            Files.createDirectories(folderPath);
            Path targetFile = folderPath.resolve(fileLeafId + "." + ext);
            Files.write(targetFile, file.getBytes());
            String url = "/uploads/" + fullPublicId + "." + ext;
            return new MediaUploadResult(url, fullPublicId);
        } catch (IOException ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lưu file hình ảnh cục bộ thất bại");
        }
    }

    private void deleteFromLocalFallback(String publicId) {
        for (String ext : new String[]{"jpg", "png", "webp"}) {
            try {
                Path candidate = Paths.get(localDir, (publicId + "." + ext).split("/")).normalize();
                Files.deleteIfExists(candidate);
            } catch (IOException ignored) {
            }
        }
    }

    private String sanitizeRootFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            return "mss301-event-platform";
        }
        return folder.trim().replaceAll("^/+|/+$", "");
    }

    private boolean looksLikeIpLiteral(String host) {
        return host.matches("^[0-9.]+$") || host.contains(":");
    }

    private boolean isCgnatOrMetadataAddress(byte[] addr) {
        if (addr.length == 4) {
            int b0 = addr[0] & 0xFF;
            int b1 = addr[1] & 0xFF;
            // 100.64.0.0/10 (Carrier-grade NAT) or 0.0.0.0/8
            return b0 == 0 || (b0 == 100 && b1 >= 64 && b1 <= 127);
        }
        return false;
    }

    private String signCloudinaryParams(Map<String, String> params, String secret) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        sb.append(secret);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
