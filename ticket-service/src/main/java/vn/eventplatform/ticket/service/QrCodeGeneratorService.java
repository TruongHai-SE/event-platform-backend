package vn.eventplatform.ticket.service;

public interface QrCodeGeneratorService {

    byte[] generateQrPng(String text, int width, int height);

    String generateQrBase64(String text, int width, int height);
}
