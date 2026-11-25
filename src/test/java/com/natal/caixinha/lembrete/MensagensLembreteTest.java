package com.natal.caixinha.lembrete;

import com.natal.caixinha.lembrete.MensagensLembrete.Grupo;
import com.natal.caixinha.lembrete.MensagensLembrete.Individual;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class MensagensLembreteTest {

    static final YearMonth JAN = YearMonth.of(2027, 1);
    static final YearMonth FEV = YearMonth.of(2027, 2);
    static final YearMonth NOV = YearMonth.of(2027, 11);
    static final String LINK = "https://x/c/abc";

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    @Test
    void grupoNoMeioFechaOMesEAnunciaONovo() {
        assertEquals("*Caixinha da festa* 🎉\n"
                        + "Fechamos *janeiro*: R$ 1.012,00 de R$ 11.440,00 (9%).\n"
                        + "*10 de 11* pagantes estão em dia 💪\n"
                        + "*Fevereiro* começou: a cota é R$ 92,00 por pessoa. "
                        + "O valor da sua família e o PIX estão no seu link pessoal.",
                MensagensLembrete.grupo(new Grupo(JAN, FEV, r("1012.00"), r("11440.00"), 9, 10, 11, r("92.00"))));
    }

    @Test
    void grupoNoPrimeiroMesSoAnunciaOComeco() {
        assertEquals("*Caixinha da festa* 🎉\n"
                        + "A caixinha começou! *Janeiro* é o primeiro mês: a cota é R$ 92,00 por pessoa. "
                        + "O valor da sua família e o PIX estão no seu link pessoal.",
                MensagensLembrete.grupo(new Grupo(null, JAN, r("0.00"), r("11440.00"), 0, 11, 11, r("92.00"))));
    }

    @Test
    void grupoDepoisDoUltimoMesSoFecha() {
        assertEquals("*Caixinha da festa* 🎉\n"
                        + "Fechamos *novembro*: R$ 11.500,00 de R$ 11.440,00 (100%).\n"
                        + "*11 de 11* pagantes estão em dia 💪\n"
                        + "Último depósito feito. Agora é festa! 🥳",
                MensagensLembrete.grupo(new Grupo(NOV, null, r("11500.00"), r("11440.00"), 100, 11, 11, r("92.00"))));
    }

    @Test
    void semOrcamentoNaoFalaDaCota() {
        assertTrue(MensagensLembrete.grupo(new Grupo(JAN, FEV, r("0.00"), r("0.00"), 0, 11, 11, r("0.00")))
                .endsWith("\n*Fevereiro* começou."));
        assertTrue(MensagensLembrete.grupo(new Grupo(null, JAN, r("0.00"), r("0.00"), 0, 11, 11, r("0.00")))
                .endsWith("\nA caixinha começou! *Janeiro* é o primeiro mês."));
    }

    @Test
    void individualComCotaELink() {
        assertEquals("Oi, Ana! 😊 Lembrete da caixinha da festa: a cota de *março* da família é *R$ 138,00*.\n"
                        + "O PIX já está pronto no seu link: " + LINK,
                MensagensLembrete.individual(new Individual("Ana Souza", YearMonth.of(2027, 3), r("138.00"),
                        r("138.00"), 0, LINK)));
    }

    @Test
    void individualComDoisMesesDeAtrasoTrazOValorParaFicarEmDia() {
        String texto = MensagensLembrete.individual(new Individual("Ana", YearMonth.of(2027, 3), r("138.00"),
                r("414.00"), 2, LINK));
        assertTrue(texto.endsWith(LINK + "\nPara ficar em dia: *R$ 414,00*. Se precisar combinar algo, é só me chamar."),
                texto);
    }

    @Test
    void individualComUmMesDeAtrasoEhOLembreteNormal() {
        String texto = MensagensLembrete.individual(new Individual("Ana", YearMonth.of(2027, 3), r("138.00"),
                r("276.00"), 1, LINK));
        assertFalse(texto.contains("Para ficar em dia"), texto);
    }

    @Test
    void grupoTerminaComAProximaEtapa() {
        String texto = MensagensLembrete.grupo(new Grupo(JAN, FEV, r("1012.00"), r("11440.00"), 9, 10, 11,
                r("92.00"), "procurar o lugar", YearMonth.of(2027, 5)));
        assertTrue(texto.endsWith("no seu link pessoal.\n📅 Próxima etapa: *procurar o lugar* (maio)"), texto);
    }

}
