package com.natal.caixinha.caixinha;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.caixinha.CaixinhaService.Resumo;
import com.natal.caixinha.caixinha.CaixinhaService.SituacaoPessoa;
import com.natal.caixinha.caixinha.CalculadoraCaixinha.Destaque;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/** Cenário do seed (11 adultos, adolescente e bebê) em 15/03/2027. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioDeTeste.class)
@Transactional
class CaixinhaServiceTest {

    @Autowired
    CaixinhaService servico;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    PessoaRepository pessoas;

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    void lancar(TipoLancamento tipo, String nome, String valor, LocalDate data) {
        lancamentos.registrar(new DadosLancamento(tipo, nome == null ? null : pessoa(nome).getId(), r(valor), data,
                null, tipo == TipoLancamento.GASTO ? Categoria.ALUGUEL : null, null));
    }

    SituacaoPessoa de(Resumo resumo, String nome) {
        return resumo.situacoes().stream().filter(s -> s.pessoa().getNome().equals(nome)).findFirst().orElseThrow();
    }

    void orcamentoReal() {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Chácara", r("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", r("4400"), null));
    }

    @Test
    void semOrcamentoACotaEZeroETodosEstaoEmDia() {
        Resumo r = servico.resumo();
        assertEquals(r("0.00"), r.cota().cota());
        assertFalse(r.cota().fixada());
        assertEquals(12, r.pagantes(), "bebê (peso 0) fica de fora");
        assertEquals(12, r.emDia());
    }

    @Test
    void cotaEstimadaDoCenarioReal() {
        orcamentoReal();
        CaixinhaService.Cota c = servico.cota();
        assertEquals(r("10400.00"), c.somaPrevista());
        assertEquals(r("11440.00"), c.meta());
        assertEquals(0, r("11.5").compareTo(c.somaPesos()));
        assertEquals(12, c.pagantes());
        assertEquals(r("90.43"), c.cotaBruta());
        assertEquals(r("92.00"), c.cota());
        assertEquals(r("1058.00"), c.arrecadacaoMensal());
        assertEquals(r("11638.00"), c.totalPrevisto());
    }

    @Test
    void situacaoEmMarcoComAdiantamentoEAtrasos() {
        orcamentoReal();
        // Adulto 1: adiantou janeiro em dezembro e pagou fevereiro
        lancar(TipoLancamento.DEPOSITO, "Adulto 1", "92", LocalDate.of(2026, 12, 20));
        lancar(TipoLancamento.DEPOSITO, "Adulto 1", "92", LocalDate.of(2027, 2, 5));
        // Adolescente: só a meia cota de janeiro
        lancar(TipoLancamento.DEPOSITO, "Adolescente", "46", LocalDate.of(2027, 1, 5));
        lancar(TipoLancamento.RENDIMENTO, null, "1.50", LocalDate.of(2027, 2, 28));
        lancar(TipoLancamento.GASTO, null, "50", LocalDate.of(2027, 3, 1));

        Resumo r = servico.resumo();
        assertEquals(YearMonth.of(2027, 3), r.mesAtual());
        assertEquals(2, r.mesesFechados());

        var a1 = de(r, "Adulto 1").situacao();
        assertTrue(a1.emDia());
        assertFalse(a1.cotaDoMesPaga());
        assertEquals(r("92.00"), a1.valorParaFicarEmDia());

        var a2 = de(r, "Adulto 2").situacao();
        assertEquals(2, a2.mesesDeAtraso());
        assertEquals(Destaque.AMARELO, a2.destaque());
        assertEquals(r("276.00"), a2.valorParaFicarEmDia());

        var teen = de(r, "Adolescente").situacao();
        assertEquals(r("46.00"), teen.cota());
        assertEquals(1, teen.mesesDeAtraso());
        assertEquals(r("92.00"), teen.valorParaFicarEmDia());

        assertEquals(1, r.emDia(), "só Adulto 1 está em dia");
        assertEquals(10, r.comDestaque().size(), "os 10 adultos sem depósito estão 2 meses atrasados");

        assertEquals(r("230.00"), r.guardadoTotal());
        assertEquals(r("231.50"), r.arrecadado());
        assertEquals(r("181.50"), r.saldo());
        assertEquals(r("50.00"), r.gastos());
        assertEquals(2, r.percentual(), "231,50 de 11.440");
    }

    @Test
    void fixarCotaUmaVezSo() {
        assertThrows(RegraException.class, () -> servico.fixarCota(), "sem orçamento não fixa");
        orcamentoReal();
        assertEquals(r("92.00"), servico.fixarCota());

        // depois de fixada, mudar o orçamento não muda a cota
        orcamento.salvar(null, new DadosItem(Categoria.BEBIDA, "Cerveja", r("2000"), null));
        CaixinhaService.Cota c = servico.cota();
        assertTrue(c.fixada());
        assertEquals(r("92.00"), c.cota());
        assertEquals(r("108.00"), c.cotaEstimada());

        RegraException e = assertThrows(RegraException.class, () -> servico.fixarCota());
        assertTrue(e.getMessage().contains("já está fixada"));
    }
}
