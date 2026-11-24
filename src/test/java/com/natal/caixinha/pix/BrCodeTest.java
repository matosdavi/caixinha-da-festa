package com.natal.caixinha.pix;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BrCodeTest {

    static BrCode.Recebedor recebedor() {
        return new BrCode.Recebedor("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", "Brasília");
    }

    static void crcConfere(String pix) {
        assertEquals(Crc16.ccittFalse(pix.substring(0, pix.length() - 4)), pix.substring(pix.length() - 4));
    }

    @Test
    void crcDoVetorPadrao() {
        assertEquals("29B1", Crc16.ccittFalse("123456789"));
    }

    @Test
    void crcDoExemploDoBancoCentral() {
        String semCrc = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
                + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304";
        assertEquals("1D3D", Crc16.ccittFalse(semCrc));
    }

    @Test
    void semValorOmiteOCampo54() {
        String pix = BrCode.gerar(recebedor(), BigDecimal.ZERO, "***");
        assertTrue(pix.startsWith("00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
                + "52040000530398658"), pix);
        assertTrue(pix.contains("5802BR5913FULANO DE TAL6008BRASILIA62070503***6304"), pix);
        crcConfere(pix);
    }

    @Test
    void valorComPontoEDuasCasas() {
        String pix = BrCode.gerar(recebedor(), new BigDecimal("138"), BrCode.txid(7));
        assertTrue(pix.contains("53039865406138.005802BR"), pix);
        assertTrue(pix.contains("62110507CX202776304"), pix);
        crcConfere(pix);
    }

    @Test
    void valorGrandeSemSeparadorDeMilhar() {
        assertTrue(BrCode.gerar(recebedor(), new BigDecimal("1234.5"), "***").contains("54071234.50"));
    }

    @Test
    void nomeECidadeLimposECortados() {
        assertEquals("JOAO DA CONCEICAO DE ARAU", BrCode.limpar("João  da Conceição de Araújo Filho", 25));
        assertEquals("SAO JOSE DOS CA", BrCode.limpar("São José dos Campos", 15));
        assertEquals("ANA", BrCode.limpar("  Ana!  ", 25));
        assertEquals("ABC", BrCode.limpar("ABC DEF", 4));
    }

    @Test
    void nomeLongoNoPix() {
        String pix = BrCode.gerar(new BrCode.Recebedor("a@b.com", "João da Conceição de Araújo Filho", "São José dos Campos"),
                BigDecimal.ONE, "***");
        assertTrue(pix.contains("5925JOAO DA CONCEICAO DE ARAU6015SAO JOSE DOS CA"), pix);
        crcConfere(pix);
    }
}
