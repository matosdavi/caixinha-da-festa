package com.natal.caixinha.caixinha;

import com.natal.caixinha.caixinha.DivisaoFamilia.Membro;
import com.natal.caixinha.caixinha.DivisaoFamilia.Parte;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DivisaoFamiliaTest {

    static Membro ana(String falta) {
        return new Membro(1L, BigDecimal.ONE, new BigDecimal(falta), true);
    }

    static Membro theo(String falta) {
        return new Membro(2L, new BigDecimal("0.50"), new BigDecimal(falta), false);
    }

    static void partes(List<Parte> partes, String total, String... esperadas) {
        assertEquals(esperadas.length, partes.size());
        BigDecimal soma = BigDecimal.ZERO;
        for (int i = 0; i < esperadas.length; i++) {
            assertEquals(new BigDecimal(esperadas[i]).setScale(2), partes.get(i).valor(), "parte " + i);
            assertEquals(2, partes.get(i).valor().scale());
            soma = soma.add(partes.get(i).valor());
        }
        assertEquals(0, new BigDecimal(total).compareTo(soma), "a soma das partes é o total");
    }

    @Test
    void totalExato() {
        partes(DivisaoFamilia.dividir(new BigDecimal("138"), List.of(ana("92"), theo("46"))), "138", "92", "46");
    }

    @Test
    void parcialProporcionalAoQueCadaUmDeve() {
        partes(DivisaoFamilia.dividir(new BigDecimal("100"), List.of(ana("92"), theo("46"))),
                "100", "66.67", "33.33");
    }

    @Test
    void adiantamentoDivideASobraPeloPeso() {
        partes(DivisaoFamilia.dividir(new BigDecimal("276"), List.of(ana("92"), theo("46"))), "276", "184", "92");
    }

    @Test
    void tudoEmDiaDividePeloPeso() {
        partes(DivisaoFamilia.dividir(new BigDecimal("138"), List.of(ana("0"), theo("0"))), "138", "92", "46");
    }

    @Test
    void responsavelForaDaListaRestoVaiParaOPrimeiro() {
        List<Membro> netos = List.of(
                new Membro(3L, BigDecimal.ONE, BigDecimal.ZERO, false),
                new Membro(4L, BigDecimal.ONE, BigDecimal.ZERO, false));
        List<Parte> p = DivisaoFamilia.dividir(new BigDecimal("100.01"), netos);
        partes(p, "100.01", "50.01", "50.00");
        assertEquals(3L, p.get(0).pessoaId());
    }

    @Test
    void restoVaiParaOResponsavelMesmoQueNaoSejaOPrimeiro() {
        List<Parte> p = DivisaoFamilia.dividir(new BigDecimal("100"), List.of(theo("46"), ana("92")));
        partes(p, "100", "33.33", "66.67");
        assertEquals(2L, p.get(0).pessoaId());
    }
}
