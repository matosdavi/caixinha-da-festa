package com.natal.caixinha.prestacao;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioAjustavel;
import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.prestacao.PrestacaoService.Linha;
import com.natal.caixinha.prestacao.PrestacaoService.Visao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Festa em 20/12/2027, prazo da escolha 27/12. Guardaram: Adulto 2 = 1012, Adulto 3 = 506; gasto 1000 → sobra 518. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioAjustavel.class)
@Transactional
class PrestacaoServiceTest {

    @Autowired
    PrestacaoService prestacao;
    @Autowired
    RelogioAjustavel.Ajustavel relogio;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    CaixinhaService caixinha;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    LancamentoRepository lancamentoRepository;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    EscolhaSobraRepository escolhas;

    @BeforeEach
    void preparar() {
        relogio.para(LocalDate.of(2027, 3, 15));
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
        caixinha.fixarCota();
        caixinha.definirDataFesta(LocalDate.of(2027, 12, 20));
        relogio.para(LocalDate.of(2027, 11, 30));
        depositar("Adulto 2", "1012");
        depositar("Adulto 3", "506");
        lancamentos.registrar(new DadosLancamento(TipoLancamento.GASTO, null, new BigDecimal("1000"),
                LocalDate.of(2027, 11, 30), "Mercado", Categoria.COMIDA, null));
    }

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    void depositar(String nome, String valor) {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, pessoa(nome).getId(), new BigDecimal(valor),
                LocalDate.of(2027, 11, 30), null, null, null));
    }

    Linha linha(Visao v, String nome) {
        return v.linhas().stream().filter(l -> l.pessoa().getNome().equals(nome)).findFirst().orElseThrow();
    }

    static void valor(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + ", veio " + real);
    }

    @Test
    void antesDaFestaNaoEstaDisponivel() {
        assertFalse(prestacao.visao().disponivel());
        RegraException e = assertThrows(RegraException.class,
                () -> prestacao.escolherPelaFamilia(pessoa("Adulto 2").getId(), OpcaoSobra.DEVOLVER));
        assertEquals("A prestação de contas abre depois da festa.", e.getMessage());
    }

    @Test
    void depoisDaFestaMostraASobraEAsPartes() {
        relogio.para(LocalDate.of(2027, 12, 22));
        Visao v = prestacao.visao();
        assertTrue(v.disponivel());
        assertEquals(LocalDate.of(2027, 12, 27), v.prazo());
        assertFalse(v.prazoPassou());
        valor("1518", v.entradas());
        valor("1000", v.gastos());
        valor("1000", v.gastosPorCategoria().get(Categoria.COMIDA));
        valor("518", v.sobra());
        valor("345.34", linha(v, "Adulto 2").parte());
        valor("172.66", linha(v, "Adulto 3").parte());
        assertNull(linha(v, "Adulto 2").escolha());
        assertEquals(OpcaoSobra.CREDITO, linha(v, "Adulto 2").efetiva(), "quem não escolhe fica com crédito");
    }

    @Test
    void escolhaDaFamiliaValeParaTodosDela() {
        relogio.para(LocalDate.of(2027, 12, 22));
        Long a1 = pessoa("Adulto 1").getId();
        prestacao.escolherPelaFamilia(a1, OpcaoSobra.DEVOLVER);
        assertEquals(OpcaoSobra.DEVOLVER, escolhas.findById(a1).orElseThrow().getOpcao());
        assertEquals(OpcaoSobra.DEVOLVER, escolhas.findById(pessoa("Adolescente").getId()).orElseThrow().getOpcao());
        prestacao.escolherPelaFamilia(a1, OpcaoSobra.CREDITO);
        assertEquals(OpcaoSobra.CREDITO, prestacao.daFamilia(a1).escolha());
    }

    @Test
    void depoisDoPrazoAFamiliaNaoMudaMais() {
        relogio.para(LocalDate.of(2027, 12, 28));
        RegraException e = assertThrows(RegraException.class,
                () -> prestacao.escolherPelaFamilia(pessoa("Adulto 2").getId(), OpcaoSobra.DEVOLVER));
        assertEquals("A escolha ia até 27/12/2027.", e.getMessage());
        assertFalse(prestacao.daFamilia(pessoa("Adulto 2").getId()).podeEscolher());
    }

    @Test
    void lancarSoDepoisDoPrazo() {
        relogio.para(LocalDate.of(2027, 12, 22));
        RegraException e = assertThrows(RegraException.class, () -> prestacao.lancar(pessoa("Adulto 2").getId()));
        assertEquals("Lance as partes depois do prazo da escolha (27/12/2027).", e.getMessage());
    }

    @Test
    void lancarTudoFechaOSaldoEmZeroSemMudarAsPartes() {
        relogio.para(LocalDate.of(2027, 12, 28));
        Long a2 = pessoa("Adulto 2").getId();
        Long a3 = pessoa("Adulto 3").getId();
        prestacao.definirEscolha(a3, OpcaoSobra.DEVOLVER);

        prestacao.lancar(a2);
        Visao meio = prestacao.visao();
        assertTrue(linha(meio, "Adulto 2").lancado());
        valor("345.34", linha(meio, "Adulto 2").parte());
        valor("172.66", linha(meio, "Adulto 3").parte());
        valor("518", meio.sobra());
        assertThrows(RegraException.class, () -> prestacao.lancar(a2));

        prestacao.lancar(a3);
        valor("0", prestacao.visao().saldo());
        var descricoes = lancamentoRepository.findAll().stream()
                .filter(l -> l.getTipo() == TipoLancamento.DEVOLUCAO).map(l -> l.getDescricao()).toList();
        assertTrue(descricoes.contains("Crédito para 2028"), descricoes.toString());
        assertTrue(descricoes.contains("Sobra da festa"), descricoes.toString());
    }

    @Test
    void csvSoComOsCreditos() {
        relogio.para(LocalDate.of(2027, 12, 28));
        prestacao.definirEscolha(pessoa("Adulto 3").getId(), OpcaoSobra.DEVOLVER);
        assertEquals("Nome;Valor\nAdulto 2;345,34\n", prestacao.csvCreditos());
    }

    @Autowired
    com.natal.caixinha.caixinha.FamiliaService familias;
    @Autowired
    com.natal.caixinha.lembrete.LembreteService lembretes;

    @Test
    void depoisDeLancarAFamiliaContinuaEmDiaSemCobranca() {
        relogio.para(LocalDate.of(2027, 12, 28));
        Long a2 = pessoa("Adulto 2").getId();
        prestacao.lancar(a2);
        var f = familias.daFamilia(a2);
        assertEquals(com.natal.caixinha.caixinha.FamiliaService.EstadoDoMes.PAGO, f.estado());
        valor("0", f.valorPix());
    }

    @Test
    void parteMaiorQueOGuardadoTambemSeLanca() {
        relogio.para(LocalDate.of(2027, 11, 30));
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DOACAO, null, new BigDecimal("1500"),
                LocalDate.of(2027, 11, 30), "Doação da vovó", null, null));
        relogio.para(LocalDate.of(2027, 12, 28));
        Visao v = prestacao.visao();
        assertTrue(linha(v, "Adulto 2").parte().compareTo(new BigDecimal("1012")) > 0, "parte passa do guardado");
        prestacao.lancar(pessoa("Adulto 2").getId());
        prestacao.lancar(pessoa("Adulto 3").getId());
        valor("0", prestacao.visao().saldo());
    }

    @Test
    void devolucaoManualParecidaNaoContaComoDaSobra() {
        relogio.para(LocalDate.of(2027, 11, 30));
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEVOLUCAO, pessoa("Adulto 3").getId(),
                new BigDecimal("6"), LocalDate.of(2027, 11, 30), "Crédito para a Maria (ajuste)", null, null));
        relogio.para(LocalDate.of(2027, 12, 22));
        Visao v = prestacao.visao();
        valor("6", v.devolucoes());
        valor("512", v.sobra());
        assertFalse(linha(v, "Adulto 3").lancado());
    }
}
