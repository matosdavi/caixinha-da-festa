package com.natal.caixinha.comum;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DinheiroTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource(delimiter = '|', value = {
            "92         | 92",
            "92,5       | 92.5",
            "92,50      | 92.50",
            "R$ 92,00   | 92.00",
            "1.058,00   | 1058.00",
            "6.000      | 6000",
            "6.000,00   | 6000.00",
            "10.400     | 10400",
            "1.234.567  | 1234567",
            "92.50      | 92.50",
            "0.37       | 0.37",
            "1058.00    | 1058.00",
    })
    void leFormatoBrasileiro(String texto, String esperado) {
        assertEquals(0, new BigDecimal(esperado).compareTo(Dinheiro.ler(texto, "Valor")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"seis mil", "92,5,0", "1.0.0"})
    void textoInvalido(String texto) {
        assertThrows(RegraException.class, () -> Dinheiro.ler(texto, "Valor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void vazioViraNulo(String texto) {
        assertNull(Dinheiro.ler(texto, "Valor"));
    }

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "92        | R$ 92,00",
            "1058      | R$ 1.058,00",
            "0.5       | R$ 0,50",
            "-46       | − R$ 46,00",
    })
    void formataEmReais(String valor, String esperado) {
        assertEquals(esperado, Dinheiro.formatar(new BigDecimal(valor)));
    }
}
