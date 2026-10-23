package vn.eventplatform.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.eventplatform.common.exception.AppException;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeGeneratorServiceTest {

    private QrCodeGeneratorService qrCodeGeneratorService;

    @BeforeEach
    void setUp() {
        qrCodeGeneratorService = new QrCodeGeneratorServiceImpl();
    }

    @Test
    @DisplayName("1. Sinh mảng byte ảnh PNG hợp lệ từ chuỗi QR payload")
    void testGenerateQrPng_Success() throws Exception {
        String payload = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abcdef";
        byte[] pngBytes = qrCodeGeneratorService.generateQrPng(payload, 300, 300);

        assertThat(pngBytes).isNotNull();
        assertThat(pngBytes.length).isGreaterThan(0);

        // Kiểm tra magic number của định dạng file PNG: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        byte[] pngMagic = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < pngMagic.length; i++) {
            assertThat(pngBytes[i]).isEqualTo(pngMagic[i]);
        }

        // Đọc ảnh và kiểm tra độ rộng x chiều cao
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(300);
    }

    @Test
    @DisplayName("2. Sinh chuỗi Data URI Base64 chuẩn")
    void testGenerateQrBase64_Success() {
        String payload = "TEST-PAYLOAD";
        String base64Uri = qrCodeGeneratorService.generateQrBase64(payload, 250, 250);

        assertThat(base64Uri).isNotNull();
        assertThat(base64Uri).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("3. Ném ngoại lệ khi nội dung payload null hoặc rỗng")
    void testGenerateQrPng_EmptyOrNull() {
        assertThatThrownBy(() -> qrCodeGeneratorService.generateQrPng("", 300, 300))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Nội dung mã QR không được để trống");

        assertThatThrownBy(() -> qrCodeGeneratorService.generateQrPng(null, 300, 300))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Nội dung mã QR không được để trống");
    }
}
