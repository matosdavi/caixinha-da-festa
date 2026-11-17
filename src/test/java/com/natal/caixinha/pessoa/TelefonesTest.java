package com.natal.caixinha.pessoa;

import com.natal.caixinha.comum.RegraException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelefonesTest {

    @Test
    void normalizaFormatosDigitados() {
        assertEquals("5511999998888", Telefones.normalizar("(11) 99999-8888"));
        assertEquals("5511999998888", Telefones.normalizar("+55 11 99999 8888"));
        assertEquals("5511999998888", Telefones.normalizar("005511999998888"));
        assertEquals("551133334444", Telefones.normalizar("11 3333-4444"), "fixo, sem o nono dígito");
        assertEquals("351912345678", Telefones.normalizar("+351 912 345 678"), "fora do Brasil fica como está");
        assertNull(Telefones.normalizar("  "));
        assertNull(Telefones.normalizar(null));
    }

    @Test
    void recusaNumeroIncompleto() {
        assertThrows(RegraException.class, () -> Telefones.normalizar("99999-8888"));
        assertThrows(RegraException.class, () -> Telefones.normalizar("1234567890123456"));
    }

    @Test
    void formataParaMostrar() {
        assertEquals("+55 (11) 99999-8888", Telefones.formatar("5511999998888"));
        assertEquals("+55 (11) 3333-4444", Telefones.formatar("551133334444"));
        assertEquals("+351912345678", Telefones.formatar("351912345678"));
        assertEquals("", Telefones.formatar(null));
    }
}
