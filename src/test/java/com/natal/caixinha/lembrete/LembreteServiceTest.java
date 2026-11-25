package com.natal.caixinha.lembrete;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioAjustavel;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.lembrete.LembreteService.CartaoIndividual;
import com.natal.caixinha.lembrete.LembreteService.LinhaIndividual;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/** Seed: 11 famílias (Adulto 1 tem Adolescente e Bebê); orçamento da spec → cota 92. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioAjustavel.class)
@Transactional
class LembreteServiceTest {

    static final String BASE = "https://caixinha.test";
    static final YearMonth MARCO = YearMonth.of(2027, 3);

    @Autowired
    LembreteService lembretes;
    @Autowired
    RelogioAjustavel.Ajustavel relogio;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    LinkService links;
    @Autowired
    AvisoMensalRepository avisos;
    @Autowired
    com.natal.caixinha.pessoa.PessoaService pessoaService;

    @BeforeEach
    void preparar() {
        relogio.para(LocalDate.of(2027, 3, 15));
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Chácara", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    void depositar(String nome, String valor) {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, pessoa(nome).getId(),
                new BigDecimal(valor), LocalDate.of(2027, 3, 21), null, null, null));
    }

    LinhaIndividual linha(CartaoIndividual c, String nome) {
        return c.linhas().stream().filter(l -> l.nome().equals(nome)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------ grupo

    @Test
    void antesDaCaixinhaNaoHaCartoes() {
        relogio.para(LocalDate.of(2026, 11, 24));
        assertTrue(lembretes.cartaoGrupo(BASE).isEmpty());
        assertTrue(lembretes.cartaoIndividual(BASE).isEmpty());
    }

    @Test
    void noPrimeiroMesOGrupoAnunciaOComeco() {
        relogio.para(LocalDate.of(2027, 1, 1));
        var c = lembretes.cartaoGrupo(BASE).orElseThrow();
        assertEquals(YearMonth.of(2027, 1), c.mes());
        assertTrue(c.texto().contains("A caixinha começou! *Janeiro*"), c.texto());
        assertTrue(c.texto().contains("R$ 92,00"), c.texto());
    }

    @Test
    void noMeioOGrupoFechaOMesAnterior() {
        var c = lembretes.cartaoGrupo(BASE).orElseThrow();
        assertEquals(MARCO, c.mes());
        assertTrue(c.texto().contains("Fechamos *fevereiro*"), c.texto());
        assertTrue(c.texto().contains("*0 de 12* pagantes"), c.texto());
        assertFalse(c.texto().contains("Adulto"), "sem nomes no grupo");
    }

    @Test
    void emDezembroOGrupoFechaNovembroEDepoisNaoHaMais() {
        relogio.para(LocalDate.of(2027, 12, 1));
        assertTrue(lembretes.cartaoGrupo(BASE).orElseThrow().texto().contains("Agora é festa"));
        assertTrue(lembretes.cartaoIndividual(BASE).isEmpty());
        relogio.para(LocalDate.of(2028, 1, 1));
        assertTrue(lembretes.cartaoGrupo(BASE).isEmpty());
    }

    @Test
    void avisarOGrupoEscondeOCartaoSemDuplicar() {
        lembretes.avisarGrupo(MARCO);
        lembretes.avisarGrupo(MARCO);
        assertTrue(lembretes.cartaoGrupo(BASE).isEmpty());
        assertEquals(1, avisos.findByMesAndTipo(MARCO, TipoAviso.GRUPO).size());
    }

    @Test
    void naoAvisaOGrupoDeOutroMes() {
        RegraException e = assertThrows(RegraException.class, () -> lembretes.avisarGrupo(MARCO.plusMonths(1)));
        assertEquals("Este lembrete não está mais disponível.", e.getMessage());
        assertTrue(avisos.findAll().isEmpty());
    }

    // ------------------------------------------------------------ individual

    @Test
    void antesDoDia20NaoHaCartaoIndividual() {
        relogio.para(LocalDate.of(2027, 3, 19));
        assertTrue(lembretes.cartaoIndividual(BASE).isEmpty());
    }

    @Test
    void aPartirDoDia20ListaAsFamiliasQueDevem() {
        relogio.para(LocalDate.of(2027, 3, 22));
        CartaoIndividual c = lembretes.cartaoIndividual(BASE).orElseThrow();
        assertEquals(MARCO, c.mes());
        assertEquals(11, c.linhas().size(), "uma linha por família, só responsáveis");
        LinhaIndividual adulto1 = linha(c, "Adulto 1");
        // cota da família: 92 + 46 (Adolescente); jan e fev em atraso → 3 meses para ficar em dia
        assertEquals(new BigDecimal("414.00"), adulto1.valor());
        assertNull(adulto1.telefone());
        assertFalse(adulto1.avisado());
        String token = links.tokensAtivos().get(pessoa("Adulto 1").getId());
        assertTrue(adulto1.texto().contains(BASE + "/c/" + token), adulto1.texto());
        assertTrue(adulto1.texto().contains("a cota de *março* da família é *R$ 138,00*"), adulto1.texto());
        assertTrue(adulto1.texto().contains("Para ficar em dia: *R$ 414,00*"), adulto1.texto());
    }

    @Test
    void quemPagaSaiDaLista() {
        relogio.para(LocalDate.of(2027, 3, 22));
        depositar("Adulto 2", "276");
        CartaoIndividual c = lembretes.cartaoIndividual(BASE).orElseThrow();
        assertTrue(c.linhas().stream().noneMatch(l -> l.nome().equals("Adulto 2")));
        assertEquals(10, c.linhas().size());
    }

    @Test
    void pagamentoParcialDoMesContinuaNaLista() {
        relogio.para(LocalDate.of(2027, 3, 22));
        depositar("Adulto 2", "234"); // jan e fev pagos, março só com 50
        LinhaIndividual l = linha(lembretes.cartaoIndividual(BASE).orElseThrow(), "Adulto 2");
        assertEquals(new BigDecimal("42.00"), l.valor());
    }

    @Test
    void familiaQueNaoPagaNaoEntraNaLista() {
        relogio.para(LocalDate.of(2027, 3, 22));
        pessoaService.criar(new com.natal.caixinha.pessoa.DadosPessoa("Vovó", "", BigDecimal.ZERO, null,
                LocalDate.of(2026, 11, 16), true, null));
        assertTrue(lembretes.cartaoIndividual(BASE).orElseThrow().linhas().stream()
                .noneMatch(l -> l.nome().equals("Vovó")));
    }

    @Test
    void avisarMarcaEDesfazerVolta() {
        relogio.para(LocalDate.of(2027, 3, 22));
        Long id = pessoa("Adulto 3").getId();
        LinhaIndividual avisada = lembretes.avisarIndividual(MARCO, id, BASE);
        lembretes.avisarIndividual(MARCO, id, BASE);
        assertEquals("Adulto 3", avisada.nome());
        assertTrue(linha(lembretes.cartaoIndividual(BASE).orElseThrow(), "Adulto 3").avisado());
        assertEquals(1, avisos.findByMesAndTipo(MARCO, TipoAviso.INDIVIDUAL).size());

        lembretes.desfazer(MARCO, id);
        assertFalse(linha(lembretes.cartaoIndividual(BASE).orElseThrow(), "Adulto 3").avisado());
    }

    @Test
    void comTodosAvisadosOCartaoSome() {
        relogio.para(LocalDate.of(2027, 3, 22));
        for (LinhaIndividual l : lembretes.cartaoIndividual(BASE).orElseThrow().linhas()) {
            lembretes.avisarIndividual(MARCO, l.pessoaId(), BASE);
        }
        assertTrue(lembretes.cartaoIndividual(BASE).isEmpty());
    }

    @Test
    void naoAvisaQuemNaoEstaNaLista() {
        relogio.para(LocalDate.of(2027, 3, 22));
        Long bebe = pessoa("Bebê").getId();
        assertThrows(RegraException.class, () -> lembretes.avisarIndividual(MARCO, bebe, BASE));
        assertThrows(RegraException.class, () -> lembretes.avisarIndividual(MARCO, 999_999L, BASE));
        relogio.para(LocalDate.of(2027, 3, 15));
        Long adulto = pessoa("Adulto 3").getId();
        assertThrows(RegraException.class, () -> lembretes.avisarIndividual(MARCO, adulto, BASE));
        assertTrue(avisos.findAll().isEmpty());
    }
}
