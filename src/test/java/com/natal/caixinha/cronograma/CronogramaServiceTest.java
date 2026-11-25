package com.natal.caixinha.cronograma;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.cronograma.CronogramaService.Visao;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/** Hoje = 15/03/2027 (RelogioDeTeste). Orçamento da spec → cota 92, arrecadação mensal 1.058. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioDeTeste.class)
@Transactional
class CronogramaServiceTest {

    static final LocalDate DIA_20 = LocalDate.of(2027, 12, 20);

    @Autowired
    CronogramaService cronograma;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    CaixinhaRepository caixinhas;
    @Autowired
    com.natal.caixinha.lancamento.LancamentoService lancamentos;

    void aluguel(String previsto, String real) {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal(previsto),
                real == null ? null : new BigDecimal(real)));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    @Test
    void semDataMostraSoAsPrimeirasEtapas() {
        aluguel("6000", null);
        Visao v = cronograma.visao();
        assertNull(v.dataFesta());
        assertEquals(2, v.etapas().size());
        assertTrue(v.verificacao().isEmpty());
    }

    @Test
    void comDataCalculaAsEtapasEVerificaAReserva() {
        aluguel("6000", null);
        cronograma.definirDataFesta(DIA_20);
        assertEquals(DIA_20, caixinhas.configuracao().getDataFesta());
        Visao v = cronograma.visao();
        assertEquals(7, v.etapas().size());
        var ver = v.verificacao().orElseThrow();
        assertEquals(YearMonth.of(2027, 6), ver.mes());
        assertEquals(new BigDecimal("6348.00"), ver.previsto());
        assertTrue(ver.cobre());
        assertFalse(v.semAluguel());
        assertEquals("Procurar o lugar", cronograma.proximaEtapa().orElseThrow().titulo());
    }

    @Test
    void usaOValorRealDoAluguelQuandoExiste() {
        aluguel("6000", "6500");
        cronograma.definirDataFesta(DIA_20);
        assertEquals(new BigDecimal("152.00"), cronograma.visao().verificacao().orElseThrow().falta());
    }

    @Test
    void depoisDePagarOLugarNaoDaAlarmeFalso() {
        aluguel("6000", null);
        cronograma.definirDataFesta(DIA_20);
        lancamentos.registrar(new com.natal.caixinha.lancamento.DadosLancamento(
                com.natal.caixinha.lancamento.TipoLancamento.GASTO, null, new BigDecimal("6000"),
                LocalDate.of(2027, 3, 10), "Airbnb", Categoria.ALUGUEL, null));
        Visao v = cronograma.visao();
        assertTrue(v.lugarPago());
        var ver = v.verificacao().orElseThrow();
        assertTrue(ver.cobre(), "o pagamento do lugar não conta contra ele mesmo: " + ver);
        assertEquals(new BigDecimal("6348.00"), ver.previsto());
    }

    @Test
    void etapaDaFestaNaFraseFicaNatural() {
        cronograma.definirDataFesta(DIA_20);
        var festa = cronograma.visao().etapas().get(6);
        assertEquals("a festa 🎉", festa.frase());
    }

    @Test
    void semAluguelNoOrcamentoAvisa() {
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
        cronograma.definirDataFesta(DIA_20);
        assertTrue(cronograma.visao().semAluguel());
    }

    @Test
    void dataPrecisaSerDepoisDoUltimoDeposito() {
        RegraException antes = assertThrows(RegraException.class,
                () -> cronograma.definirDataFesta(LocalDate.of(2027, 11, 30)));
        assertEquals("A festa precisa ser depois do último depósito, em dezembro de 2027 ou em janeiro de 2028.",
                antes.getMessage());
        assertThrows(RegraException.class, () -> cronograma.definirDataFesta(LocalDate.of(2028, 2, 1)));
        cronograma.definirDataFesta(LocalDate.of(2027, 12, 1));
        cronograma.definirDataFesta(LocalDate.of(2028, 1, 31));
    }

    @Test
    void dataVaziaVoltaParaEmVotacao() {
        cronograma.definirDataFesta(DIA_20);
        cronograma.definirDataFesta(null);
        assertNull(caixinhas.configuracao().getDataFesta());
        assertEquals(2, cronograma.visao().etapas().size());
    }
}
