package com.natal.caixinha.cronograma;

import com.natal.caixinha.cronograma.Cronograma.Dados;
import com.natal.caixinha.cronograma.Cronograma.Estado;
import com.natal.caixinha.cronograma.Cronograma.Etapa;
import com.natal.caixinha.cronograma.Cronograma.Verificacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CronogramaTest {

    static final YearMonth JAN = YearMonth.of(2027, 1);
    static final YearMonth NOV = YearMonth.of(2027, 11);
    static final LocalDate FECHAMENTO = LocalDate.of(2026, 12, 31);
    static final LocalDate HOJE = LocalDate.of(2027, 3, 15);

    static List<Etapa> etapas(LocalDate festa, LocalDate hoje) {
        return Cronograma.etapas(new Dados(festa, JAN, NOV, FECHAMENTO, hoje));
    }

    static Etapa reserva(List<Etapa> etapas) {
        return etapas.stream().filter(Etapa::reserva).findFirst().orElseThrow();
    }

    @Test
    void semDataSoAsDuasPrimeirasEtapas() {
        List<Etapa> e = etapas(null, HOJE);
        assertEquals(2, e.size());
        assertEquals("Fechar a lista e fixar a cota", e.get(0).titulo());
        assertEquals(YearMonth.of(2026, 12), e.get(0).mes());
        assertEquals("Regras combinadas, data da festa e 1º depósito", e.get(1).titulo());
        assertEquals(JAN, e.get(1).mes());
    }

    @Test
    void festaDia20ProcuraSeteMesesAntesEReservaSeis() {
        List<Etapa> e = etapas(LocalDate.of(2027, 12, 20), HOJE);
        assertEquals(7, e.size());
        assertEquals("Procurar o lugar", e.get(2).titulo());
        assertEquals(YearMonth.of(2027, 5), e.get(2).mes());
        assertEquals("Reservar e pagar o lugar", e.get(3).titulo());
        assertEquals(YearMonth.of(2027, 6), e.get(3).mes());
        assertTrue(e.get(3).reserva());
        assertEquals(YearMonth.of(2027, 10), e.get(4).mes());
        assertEquals(NOV, e.get(5).mes());
        assertEquals("Festa! 🎉", e.get(6).titulo());
        assertEquals(YearMonth.of(2027, 12), e.get(6).mes());
    }

    @Test
    void festaDia19ProcuraSeisMesesAntes() {
        List<Etapa> e = etapas(LocalDate.of(2027, 12, 19), HOJE);
        assertEquals(YearMonth.of(2027, 6), e.get(2).mes());
        assertEquals(YearMonth.of(2027, 7), reserva(e).mes());
    }

    @Test
    void reveillonEmJaneiroContaComoAltaTemporada() {
        assertEquals(7, Cronograma.antecedenciaProcurar(LocalDate.of(2028, 1, 1)));
        List<Etapa> e = etapas(LocalDate.of(2028, 1, 1), HOJE);
        assertEquals(YearMonth.of(2027, 6), e.get(2).mes());
        assertEquals(YearMonth.of(2027, 7), reserva(e).mes());
        assertEquals(YearMonth.of(2028, 1), e.get(6).mes());
    }

    @Test
    void estadosPeloMesDeHoje() {
        List<Etapa> e = etapas(LocalDate.of(2027, 12, 20), HOJE);
        assertEquals(Estado.FEITO, e.get(0).estado());
        assertEquals(Estado.FEITO, e.get(1).estado());
        assertEquals(Estado.PROXIMA, e.get(2).estado());
        assertEquals("Procurar o lugar", Cronograma.proxima(e).orElseThrow().titulo());

        List<Etapa> emMaio = etapas(LocalDate.of(2027, 12, 20), LocalDate.of(2027, 5, 10));
        assertEquals(Estado.AGORA, emMaio.get(2).estado());
        assertEquals("Procurar o lugar", Cronograma.proxima(emMaio).orElseThrow().titulo());
    }

    @Test
    void depoisDeTudoNaoHaProximaEtapa() {
        assertTrue(Cronograma.proxima(etapas(LocalDate.of(2027, 12, 20), LocalDate.of(2028, 2, 1))).isEmpty());
        assertTrue(Cronograma.proxima(etapas(null, LocalDate.of(2027, 2, 1))).isEmpty());
    }

    @Test
    void fraseComecaMinuscula() {
        assertEquals("procurar o lugar", etapas(LocalDate.of(2027, 12, 20), HOJE).get(2).frase());
    }

    @Test
    void verificaSeDaParaPagarOAluguelInteiro() {
        Verificacao maio = Cronograma.verificar(JAN, YearMonth.of(2027, 5), new BigDecimal("1058.00"),
                BigDecimal.ZERO, new BigDecimal("6000.00"));
        assertEquals(new BigDecimal("5290.00"), maio.previsto());
        assertEquals(new BigDecimal("710.00"), maio.falta());

        Verificacao junho = Cronograma.verificar(JAN, YearMonth.of(2027, 6), new BigDecimal("1058.00"),
                BigDecimal.ZERO, new BigDecimal("6000.00"));
        assertEquals(new BigDecimal("6348.00"), junho.previsto());
        assertEquals(new BigDecimal("0.00"), junho.falta());
        assertEquals(new BigDecimal("6000.00"), junho.aluguel());
    }

    @Test
    void gastosJaFeitosReduzemOPrevisto() {
        Verificacao v = Cronograma.verificar(JAN, YearMonth.of(2027, 6), new BigDecimal("1058.00"),
                new BigDecimal("-400.00"), new BigDecimal("6000.00"));
        assertEquals(new BigDecimal("5948.00"), v.previsto());
        assertEquals(new BigDecimal("52.00"), v.falta());
    }
}
