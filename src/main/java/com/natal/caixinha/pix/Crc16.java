package com.natal.caixinha.pix;

import java.nio.charset.StandardCharsets;

/** CRC16-CCITT-FALSE (polinômio 0x1021, valor inicial 0xFFFF), usado no campo 63 do BR Code. */
public final class Crc16 {

    private Crc16() {
    }

    /** 4 dígitos hexadecimais maiúsculos. */
    public static String ccittFalse(String texto) {
        int crc = 0xFFFF;
        for (byte b : texto.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
