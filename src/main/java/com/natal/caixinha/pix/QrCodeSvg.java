package com.natal.caixinha.pix;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/** QR Code em SVG, para embutir direto na página (sem rota nem imagem externa). */
public final class QrCodeSvg {

    private QrCodeSvg() {
    }

    /** Sem dica de charset: o PIX é ASCII e, como nos QR dos bancos, sai sem cabeçalho ECI. */
    public static String svg(String texto) {
        BitMatrix m;
        try {
            m = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 4));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Texto grande demais para um QR Code.", e);
        }
        int w = m.getWidth();
        int h = m.getHeight();
        StringBuilder caminho = new StringBuilder();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (m.get(x, y)) {
                    caminho.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + w + " " + h
                + "\" role=\"img\" aria-label=\"QR Code do PIX\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + caminho + "\"/></svg>";
    }
}
