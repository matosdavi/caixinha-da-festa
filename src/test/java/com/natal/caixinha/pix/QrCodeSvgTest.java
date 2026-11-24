package com.natal.caixinha.pix;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeSvgTest {

    @Test
    void svgComMargemEFundoBranco() {
        String svg = QrCodeSvg.svg("teste");
        assertTrue(svg.startsWith("<svg"), svg);
        // versão 1: 21 módulos + margem de 4 de cada lado
        assertTrue(svg.contains("viewBox=\"0 0 29 29\""), svg);
        assertTrue(svg.contains("fill=\"#fff\""));
        assertTrue(svg.contains("aria-label=\"QR Code do PIX\""));
        assertEquals(1, svg.split("<path", -1).length - 1);
    }

    @Test
    void textoMaiorGeraQrMaior() {
        String pix = BrCode.gerar(BrCodeTest.recebedor(), new java.math.BigDecimal("138"), "CX20277");
        assertFalse(QrCodeSvg.svg(pix).contains("viewBox=\"0 0 29 29\""));
    }
}
