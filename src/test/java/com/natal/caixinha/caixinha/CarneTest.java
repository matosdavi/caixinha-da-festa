package com.natal.caixinha.caixinha;

import com.natal.caixinha.caixinha.Carne.Estado;
import com.natal.caixinha.caixinha.Carne.Membro;
import com.natal.caixinha.caixinha.Carne.Movimento;
import com.natal.caixinha.caixinha.Carne.Talao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CarneTest {

    static final YearMonth JAN = YearMonth.of(2027, 1);
    static final YearMonth NOV = YearMonth.of(2027, 11);

    static Movimento mov(int ano, int mes, int dia, String valor) {
        return new Movimento(LocalDate.of(ano, mes, dia), new BigDecimal(valor));
    }

    static Membro ana(Movimento... m) {
        return new Membro(new BigDecimal("92"), List.of(m));
    }

    static Membro theo(Movimento... m) {
        return new Membro(new BigDecimal("46"), List.of(m));
    }

    static void valor(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + ", veio " + real);
    }

    @Test
    void doisMesesPagosPelaFamilia() {
        List<Talao> carne = Carne.montar(List.of(
                ana(mov(2027, 1, 8, "92"), mov(2027, 2, 11, "92")),
                theo(mov(2027, 1, 8, "46"), mov(2027, 2, 11, "46"))), JAN, NOV);

        assertEquals(11, carne.size());
        assertEquals(Estado.PAGO, carne.get(0).estado());
        assertEquals(LocalDate.of(2027, 1, 8), carne.get(0).pagoEm());
        assertEquals(Estado.PAGO, carne.get(1).estado());
        assertEquals(LocalDate.of(2027, 2, 11), carne.get(1).pagoEm());
        assertEquals(Estado.ABERTO, carne.get(2).estado());
        valor("138", carne.get(2).falta());
        assertNull(carne.get(2).pagoEm());
        valor("138", carne.get(2).valor());
        assertEquals(YearMonth.of(2027, 3), carne.get(2).mes());
        assertEquals(3, carne.get(2).numero());
    }

    @Test
    void parcialQuandoSoUmPagou() {
        List<Talao> carne = Carne.montar(List.of(ana(mov(2027, 1, 8, "92")), theo()), JAN, NOV);
        assertEquals(Estado.PARCIAL, carne.get(0).estado());
        valor("46", carne.get(0).falta());
        assertNull(carne.get(0).pagoEm());
    }

    @Test
    void carimboEsperaOUltimoDaFamilia() {
        List<Talao> carne = Carne.montar(List.of(
                ana(mov(2027, 1, 5, "92")), theo(mov(2027, 1, 20, "46"))), JAN, NOV);
        assertEquals(LocalDate.of(2027, 1, 20), carne.get(0).pagoEm());
    }

    @Test
    void adiantamentoEmDezembro() {
        List<Talao> carne = Carne.montar(List.of(
                ana(mov(2026, 12, 10, "184")), theo(mov(2026, 12, 10, "92"))), JAN, NOV);
        assertEquals(Estado.PAGO, carne.get(0).estado());
        assertEquals(Estado.PAGO, carne.get(1).estado());
        assertEquals(LocalDate.of(2026, 12, 10), carne.get(1).pagoEm());
        assertEquals(Estado.ABERTO, carne.get(2).estado());
    }

    @Test
    void devolucaoTiraOCarimbo() {
        List<Talao> carne = Carne.montar(List.of(
                ana(mov(2027, 1, 8, "92"), mov(2027, 2, 11, "92"), mov(2027, 3, 1, "-92")),
                theo(mov(2027, 1, 8, "46"), mov(2027, 2, 11, "46"))), JAN, NOV);
        assertEquals(Estado.PAGO, carne.get(0).estado());
        assertEquals(Estado.PARCIAL, carne.get(1).estado());
        valor("92", carne.get(1).falta());
    }

    @Test
    void devolucaoEDepoisNovoDeposito() {
        List<Talao> carne = Carne.montar(List.of(
                ana(mov(2027, 1, 8, "92"), mov(2027, 2, 11, "92"), mov(2027, 3, 1, "-92"), mov(2027, 3, 10, "92")),
                theo(mov(2027, 1, 8, "46"), mov(2027, 2, 11, "46"))), JAN, NOV);
        assertEquals(Estado.PAGO, carne.get(1).estado());
        assertEquals(LocalDate.of(2027, 3, 10), carne.get(1).pagoEm());
    }

    @Test
    void depositoQuebradoCobreParteDoMesSeguinte() {
        // 100 de Ana: paga janeiro (92) e deixa 8 de fevereiro
        List<Talao> carne = Carne.montar(List.of(ana(mov(2027, 1, 8, "100"))), JAN, NOV);
        assertEquals(Estado.PAGO, carne.get(0).estado());
        assertEquals(Estado.PARCIAL, carne.get(1).estado());
        valor("84", carne.get(1).falta());
    }

    @Test
    void semPagantes() {
        List<Talao> carne = Carne.montar(List.of(), JAN, NOV);
        assertEquals(11, carne.size());
        for (Talao t : carne) {
            assertEquals(Estado.ABERTO, t.estado());
            valor("0", t.valor());
            valor("0", t.falta());
        }
    }
}
