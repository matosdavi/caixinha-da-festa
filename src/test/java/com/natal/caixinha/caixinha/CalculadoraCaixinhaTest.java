package com.natal.caixinha.caixinha;

import com.natal.caixinha.caixinha.CalculadoraCaixinha.Destaque;
import com.natal.caixinha.caixinha.CalculadoraCaixinha.Situacao;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.natal.caixinha.caixinha.CalculadoraCaixinha.*;
import static org.junit.jupiter.api.Assertions.*;

class CalculadoraCaixinhaTest {

    static final YearMonth JAN = YearMonth.of(2027, 1);
    static final YearMonth NOV = YearMonth.of(2027, 11);

    static BigDecimal r(String valor) {
        return new BigDecimal(valor);
    }

    /** Compara dinheiro exigindo as 2 casas. */
    static void assertDinheiro(String esperado, BigDecimal valor) {
        assertEquals(r(esperado).setScale(2), valor);
    }

    @Nested
    class MetaECota {

        @Test
        void cenarioRealDaEspecificacao() {
            BigDecimal meta = meta(r("10400"), r("0.10"));
            assertDinheiro("11440", meta);
            assertEquals(11, meses(JAN, NOV));

            BigDecimal somaPesos = r("11.5");
            assertDinheiro("90.43", cotaBruta(meta, somaPesos, 11));
            BigDecimal cota = cotaBase(meta, somaPesos, 11);
            assertDinheiro("92", cota);

            assertDinheiro("46", cotaDaPessoa(cota, r("0.50")));
            assertDinheiro("0", cotaDaPessoa(cota, r("0.00")));
            assertDinheiro("92", cotaDaPessoa(cota, r("1.00")));

            BigDecimal mensal = arrecadacaoMensal(cota, somaPesos);
            assertDinheiro("1058", mensal);
            assertDinheiro("11638", mensal.multiply(BigDecimal.valueOf(11)));
        }

        @ParameterizedTest(name = "somaPesos {0} → bruta {1} → cota {2}")
        @CsvSource({
                "10.5, 99.05, 100",
                "11,   94.55, 96",
                "11.5, 90.43, 92",
        })
        void outrosCasosComAMesmaMeta(String somaPesos, String bruta, String cota) {
            BigDecimal meta = r("11440.00");
            assertDinheiro(bruta, cotaBruta(meta, r(somaPesos), 11));
            assertDinheiro(cota, cotaBase(meta, r(somaPesos), 11));
        }

        @Test
        void valorExatamenteParNaoSobe() {
            // 92 × 11,5 × 11 = 11.638 → cota bruta exatamente 92,00
            BigDecimal meta = r("11638.00");
            assertDinheiro("92.00", cotaBruta(meta, r("11.5"), 11));
            assertDinheiro("92", cotaBase(meta, r("11.5"), 11));
        }

        @Test
        void umCentavoAcimaDoParSobeParaOProximoPar() {
            BigDecimal meta = r("11638.01");
            assertDinheiro("94", cotaBase(meta, r("11.5"), 11));
        }

        @Test
        void imparArredondaParaOParDeCima() {
            // cota bruta exatamente 91 → 92
            assertDinheiro("92", cotaBase(r("1001"), r("1"), 11));
        }

        @Test
        void semOrcamentoOuSemPagantesACotaEZero() {
            assertDinheiro("0", cotaBase(r("0"), r("11.5"), 11));
            assertDinheiro("0", cotaBase(r("11440"), r("0"), 11));
            assertDinheiro("0", cotaBruta(r("11440"), r("0"), 11));
        }

        @Test
        void mesesContaInicioEFim() {
            assertEquals(1, meses(JAN, JAN));
            assertEquals(12, meses(YearMonth.of(2026, 12), NOV));
        }
    }

    @Nested
    class MesesFechados {

        @ParameterizedTest(name = "{0} → {1} fechados")
        @CsvSource({
                "2026-11-17, 0",
                "2026-12-31, 0",
                "2027-01-01, 0",
                "2027-01-31, 0",
                "2027-02-01, 1",
                "2027-11-30, 10",
                "2027-12-01, 11",
                "2028-06-01, 11",
        })
        void contaSoMesesTerminados(LocalDate hoje, int esperado) {
            assertEquals(esperado, mesesFechados(hoje, JAN, NOV));
        }

        @Test
        void mesAtualFicaDentroDoPeriodo() {
            assertEquals(JAN, mesAtual(LocalDate.of(2026, 12, 10), JAN, NOV));
            assertEquals(YearMonth.of(2027, 5), mesAtual(LocalDate.of(2027, 5, 31), JAN, NOV));
            assertEquals(NOV, mesAtual(LocalDate.of(2027, 12, 18), JAN, NOV));
        }
    }

    @Nested
    class SituacaoDaPessoa {

        static final BigDecimal COTA = r("92.00");

        Situacao em(String hoje, String guardado) {
            return situacao(COTA, r(guardado).setScale(2), LocalDate.parse(hoje), JAN, NOV);
        }

        @Test
        void guardadoSomaDepositosECreditoEDescontaDevolucoes() {
            assertDinheiro("150", guardado(r("184"), r("20"), r("54")));
        }

        @Test
        void emDezembroDe2026NinguemEstaAtrasadoEACotaEmJogoEJaneiro() {
            Situacao s = em("2026-12-15", "0");
            assertEquals(JAN, s.mesAtual());
            assertTrue(s.emDia());
            assertFalse(s.cotaDoMesPaga());
            assertDinheiro("92", s.faltaNoMes());
            assertDinheiro("92", s.valorParaFicarEmDia());
            assertEquals(0, s.mesesDeAtraso());
        }

        @Test
        void adiantamentoEmDezembroJaPagaJaneiro() {
            Situacao dezembro = em("2026-12-20", "92");
            assertTrue(dezembro.cotaDoMesPaga());
            assertDinheiro("0", dezembro.valorParaFicarEmDia());

            Situacao janeiro = em("2027-01-10", "92");
            assertTrue(janeiro.emDia());
            assertTrue(janeiro.cotaDoMesPaga());

            // em fevereiro, o adiantamento cobre janeiro e falta a cota de fevereiro
            Situacao fevereiro = em("2027-02-05", "92");
            assertTrue(fevereiro.emDia());
            assertFalse(fevereiro.cotaDoMesPaga());
            assertDinheiro("92", fevereiro.faltaNoMes());
            assertEquals(0, fevereiro.mesesDeAtraso());
        }

        @Test
        void quemNaoPagouJaneiroFicaUmMesAtrasadoEmFevereiro() {
            Situacao s = em("2027-02-01", "0");
            assertFalse(s.emDia());
            assertEquals(1, s.mesesDeAtraso());
            assertDinheiro("92", s.esperadoAteAgora());
            assertDinheiro("184", s.esperadoComMesAtual());
            assertDinheiro("92", s.faltaNoMes());
            assertDinheiro("184", s.valorParaFicarEmDia());
            assertEquals(Destaque.NENHUM, s.destaque());
        }

        @Test
        void pagamentoParcialArredondaOAtrasoParaCima() {
            // em abril: esperado até agora 276 (3 meses); guardou 200 → deve 76 → 1 mês
            Situacao s = em("2027-04-10", "200");
            assertEquals(1, s.mesesDeAtraso());
            assertDinheiro("168", s.valorParaFicarEmDia());
            assertDinheiro("92", s.faltaNoMes());

            // guardou 100 → deve 176 → 2 meses (1,9 sobe para 2)
            assertEquals(2, em("2027-04-10", "100").mesesDeAtraso());
        }

        @Test
        void pagouParteDoMesAtual() {
            // em março: esperado com o mês atual 276; guardou 230 → falta 46 do mês
            Situacao s = em("2027-03-15", "230");
            assertTrue(s.emDia());
            assertFalse(s.cotaDoMesPaga());
            assertDinheiro("46", s.faltaNoMes());
            assertDinheiro("46", s.valorParaFicarEmDia());
        }

        @Test
        void quemPagouAMaisNaoDeveNada() {
            Situacao s = em("2027-03-15", "500");
            assertTrue(s.emDia());
            assertTrue(s.cotaDoMesPaga());
            assertDinheiro("0", s.valorParaFicarEmDia());
            assertDinheiro("0", s.faltaNoMes());
        }

        @Test
        void depoisDoUltimoMesOEsperadoParaNoTotal() {
            Situacao s = em("2027-12-10", "1012");
            assertDinheiro("1012", s.esperadoAteAgora());
            assertDinheiro("1012", s.esperadoComMesAtual());
            assertTrue(s.emDia());
            assertTrue(s.cotaDoMesPaga());
        }

        @Test
        void meiaCotaDoAdolescente() {
            Situacao s = situacao(r("46.00"), r("46.00"), LocalDate.of(2027, 3, 2), JAN, NOV);
            assertEquals(1, s.mesesDeAtraso());
            assertDinheiro("92", s.valorParaFicarEmDia());
        }

        @Test
        void pesoZeroEstaSempreEmDia() {
            Situacao s = situacao(r("0.00"), r("0.00"), LocalDate.of(2027, 9, 2), JAN, NOV);
            assertTrue(s.emDia());
            assertTrue(s.cotaDoMesPaga());
            assertEquals(0, s.mesesDeAtraso());
            assertDinheiro("0", s.valorParaFicarEmDia());
            assertEquals(Destaque.NENHUM, s.destaque());
        }
    }

    @Nested
    class AtrasoEmDegraus {

        @ParameterizedTest(name = "{0} meses em {1} → {2}")
        @CsvSource({
                "0, 2027-05-10, NENHUM",
                "1, 2027-05-10, NENHUM",
                "2, 2027-05-10, AMARELO",
                "3, 2027-05-10, CONVERSAR_NO_PRIVADO",
                "7, 2027-09-30, CONVERSAR_NO_PRIVADO",
                "1, 2027-10-01, QUITAR_ATE_O_FIM",
                "2, 2027-10-15, QUITAR_ATE_O_FIM",
                "4, 2027-11-20, QUITAR_ATE_O_FIM",
                "0, 2027-10-15, NENHUM",
        })
        void degraus(int meses, LocalDate hoje, Destaque esperado) {
            assertEquals(esperado, destaque(meses, hoje, NOV));
        }

        @Test
        void situacaoJaTrazODestaque() {
            // em junho sem pagar nada: 5 meses fechados
            Situacao s = situacao(r("92.00"), r("0.00"), LocalDate.of(2027, 6, 3), JAN, NOV);
            assertEquals(5, s.mesesDeAtraso());
            assertEquals(Destaque.CONVERSAR_NO_PRIVADO, s.destaque());
        }
    }

    @Nested
    class ValorDeCadaMes {

        static final BigDecimal COTA = new BigDecimal("92.00");

        @Test
        void semAcrescimoTodosOsMesesValemACota() {
            List<BigDecimal> v = valoresDosMeses(COTA, null, null, JAN, NOV);
            assertEquals(11, v.size());
            v.forEach(x -> assertDinheiro("92.00", x));
            assertEquals(v, valoresDosMeses(COTA, BigDecimal.ZERO, null, JAN, NOV));
        }

        @Test
        void acrescimoDivididoNosMesesQueFaltam() {
            List<BigDecimal> v = valoresDosMeses(COTA, r("300"), YearMonth.of(2027, 7), JAN, NOV);
            for (int i = 0; i < 6; i++) {
                assertDinheiro("92.00", v.get(i));
            }
            for (int i = 6; i < 11; i++) {
                assertDinheiro("152.00", v.get(i));
            }
        }

        @Test
        void centavosQueSobramVaoParaOUltimoMes() {
            List<BigDecimal> v = valoresDosMeses(COTA, r("100"), YearMonth.of(2027, 9), JAN, NOV);
            assertDinheiro("125.33", v.get(8));
            assertDinheiro("125.33", v.get(9));
            assertDinheiro("125.34", v.get(10));
        }

        @Test
        void acrescimoNoUltimoMesCaiTodoNele() {
            List<BigDecimal> v = valoresDosMeses(COTA, r("100"), NOV, JAN, NOV);
            assertDinheiro("192.00", v.get(10));
            assertDinheiro("92.00", v.get(9));
        }

        @Test
        void situacaoComAcrescimo() {
            List<BigDecimal> v = valoresDosMeses(COTA, r("300"), YearMonth.of(2027, 7), JAN, NOV);
            // 15/08: jan..jul fechados (92×6 + 152 = 704); guardou 92×7 = 644
            Situacao s = situacao(v, r("644.00"), LocalDate.of(2027, 8, 15), JAN, NOV);
            assertFalse(s.emDia());
            assertEquals(1, s.mesesDeAtraso());
            assertDinheiro("212.00", s.valorParaFicarEmDia());
            assertDinheiro("152.00", s.faltaNoMes());
            assertDinheiro("152.00", s.cota());
            assertDinheiro("704.00", s.esperadoAteAgora());
        }

        @Test
        void comValorConstanteDaOMesmoQueAContaAntiga() {
            LocalDate hoje = LocalDate.of(2027, 3, 15);
            assertEquals(situacao(COTA, r("100.00"), hoje, JAN, NOV),
                    situacao(valoresDosMeses(COTA, null, null, JAN, NOV), r("100.00"), hoje, JAN, NOV));
        }
    }

    @Nested
    class DescontoDeNovembro {

        static final BigDecimal COTA = new BigDecimal("92.00");

        @Test
        void metadeDaSobraDivididaPelosPesosEmReaisInteiros() {
            assertDinheiro("53", descontoPorPeso(r("1238.00"), r("11.5")));
            assertDinheiro("49", descontoPorPeso(r("1149.00"), r("11.5")));
            assertDinheiro("0", descontoPorPeso(r("-10.00"), r("11.5")));
            assertDinheiro("0", descontoPorPeso(r("1000.00"), BigDecimal.ZERO));
        }

        @Test
        void descontoSaiDoUltimoMes() {
            List<BigDecimal> v = comDesconto(valoresDosMeses(COTA, null, null, JAN, NOV), COTA, r("50"), BigDecimal.ONE);
            assertDinheiro("42.00", v.get(10));
            assertDinheiro("92.00", v.get(9));
        }

        @Test
        void meiaCotaTemMeioDesconto() {
            BigDecimal meia = new BigDecimal("46.00");
            List<BigDecimal> v = comDesconto(valoresDosMeses(meia, null, null, JAN, NOV), meia, r("50"), r("0.5"));
            assertDinheiro("21.00", v.get(10));
        }

        @Test
        void novembroNuncaFicaNegativo() {
            List<BigDecimal> v = comDesconto(valoresDosMeses(COTA, null, null, JAN, NOV), COTA, r("100"), BigDecimal.ONE);
            assertDinheiro("0.00", v.get(10));
        }

        @Test
        void parcelaDoAcrescimoContinua() {
            List<BigDecimal> com = valoresDosMeses(COTA, r("300"), YearMonth.of(2027, 7), JAN, NOV);
            assertDinheiro("102.00", comDesconto(com, COTA, r("50"), BigDecimal.ONE).get(10));
            assertDinheiro("60.00", comDesconto(com, COTA, r("100"), BigDecimal.ONE).get(10));
        }

        @Test
        void semDescontoAListaNaoMuda() {
            List<BigDecimal> v = valoresDosMeses(COTA, null, null, JAN, NOV);
            assertEquals(v, comDesconto(v, COTA, null, BigDecimal.ONE));
            assertEquals(v, comDesconto(v, COTA, BigDecimal.ZERO, BigDecimal.ONE));
        }
    }
}
