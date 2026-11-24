package com.natal.caixinha.pix;

import com.natal.caixinha.comum.RegraException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ChavePixTest {

    @ParameterizedTest(name = "{0} \"{1}\" → {2}")
    @CsvSource(delimiter = '|', value = {
            "CPF       | 529.982.247-25                         | 52998224725",
            "CNPJ      | 11.222.333/0001-81                     | 11222333000181",
            "CELULAR   | (11) 99999-8888                        | +5511999998888",
            "CELULAR   | +55 11 99999-8888                      | +5511999998888",
            "CELULAR   | (11) 3333-4444                         | +551133334444",
            "EMAIL     | ' Fulano@Exemplo.com '                 | fulano@exemplo.com",
            "ALEATORIA | 123E4567-E12B-12D1-A456-426655440000   | 123e4567-e12b-12d1-a456-426655440000",
    })
    void normalizaChavesValidas(ChavePix.Tipo tipo, String texto, String esperado) {
        assertEquals(esperado, ChavePix.normalizar(tipo, texto));
    }

    @ParameterizedTest(name = "{0} \"{1}\" → \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "CPF       | 529.982.247-24     | CPF inválido",
            "CPF       | 111.111.111-11     | CPF inválido",
            "CPF       | 1234               | CPF inválido",
            "CNPJ      | 11.222.333/0001-80 | CNPJ inválido",
            "CELULAR   | 9999-8888          | DDD",
            "EMAIL     | fulano.exemplo.com | E-mail inválido",
            "EMAIL     | fulano@exemplo     | E-mail inválido",
            "ALEATORIA | 123e4567           | Chave aleatória inválida",
            "CPF       | ''                 | Informe a chave PIX",
    })
    void recusaChavesInvalidas(ChavePix.Tipo tipo, String texto, String mensagem) {
        RegraException erro = assertThrows(RegraException.class, () -> ChavePix.normalizar(tipo, texto));
        assertTrue(erro.getMessage().contains(mensagem), erro.getMessage());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "52998224725                          | CPF",
            "11222333000181                       | CNPJ",
            "+5511999998888                       | CELULAR",
            "fulano@exemplo.com                   | EMAIL",
            "123e4567-e12b-12d1-a456-426655440000 | ALEATORIA",
    })
    void reconheceOTipoDaChaveJaNormalizada(String chave, ChavePix.Tipo tipo) {
        assertEquals(tipo, ChavePix.tipoDe(chave));
    }
}
