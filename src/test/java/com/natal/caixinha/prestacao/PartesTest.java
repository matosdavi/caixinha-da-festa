package com.natal.caixinha.prestacao;

import com.natal.caixinha.prestacao.Partes.Guardado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PartesTest {

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    static BigDecimal soma(Map<Long, BigDecimal> partes) {
        return partes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void centavoQueSobraVaiParaQuemGuardouMaisNoEmpateOMenorId() {
        Map<Long, BigDecimal> p = Partes.dividir(r("100.00"),
                List.of(new Guardado(3L, r("50")), new Guardado(1L, r("50")), new Guardado(2L, r("50"))));
        assertEquals(r("33.34"), p.get(1L));
        assertEquals(r("33.33"), p.get(2L));
        assertEquals(r("33.33"), p.get(3L));
        assertEquals(0, r("100.00").compareTo(soma(p)));
    }

    @Test
    void proporcionalAoGuardadoESomaExata() {
        Map<Long, BigDecimal> p = Partes.dividir(r("1238.00"),
                List.of(new Guardado(1L, r("1012.00")), new Guardado(2L, r("506.00"))));
        assertEquals(r("825.34"), p.get(1L));
        assertEquals(r("412.66"), p.get(2L));
        assertEquals(0, r("1238.00").compareTo(soma(p)));
    }

    @Test
    void quemNaoGuardouNadaFicaDeFora() {
        Map<Long, BigDecimal> p = Partes.dividir(r("10.00"),
                List.of(new Guardado(1L, r("100")), new Guardado(2L, BigDecimal.ZERO)));
        assertEquals(1, p.size());
        assertEquals(r("10.00"), p.get(1L));
    }

    @Test
    void semSobraNaoHaPartes() {
        assertTrue(Partes.dividir(r("-5.00"), List.of(new Guardado(1L, r("100")))).isEmpty());
        assertTrue(Partes.dividir(r("0.00"), List.of(new Guardado(1L, r("100")))).isEmpty());
    }
}
