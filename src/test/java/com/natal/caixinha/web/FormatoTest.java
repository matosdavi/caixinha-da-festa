package com.natal.caixinha.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FormatoTest {

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "138.00   | R$ 138",
            "138.50   | R$ 138,50",
            "1058     | R$ 1.058",
            "0        | R$ 0",
    })
    void reaisCurtoSemCentavosQuandoInteiro(String valor, String esperado) {
        assertEquals(esperado, new Formato().reaisCurto(new BigDecimal(valor)));
    }
}
