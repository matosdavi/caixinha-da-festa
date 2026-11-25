package com.natal.caixinha.desistencia;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.desistencia.DesistenciaService.Sugestao;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.Lancamento;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Seed: soma dos pesos 11,5. Orçamento: aluguel 6000 e comida 4400. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioDeTeste.class)
@Transactional
class DesistenciaServiceTest {

    static final LocalDate HOJE = LocalDate.of(2027, 3, 15);

    @Autowired
    DesistenciaService desistencias;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    LancamentoRepository lancamentoRepository;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    LinkService links;

    @BeforeEach
    void preparar() {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    void depositar(String nome, String valor) {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, pessoa(nome).getId(), new BigDecimal(valor),
                LocalDate.of(2027, 3, 1), null, null, null));
    }

    void pagarOLugar() {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.GASTO, null, new BigDecimal("6000"),
                LocalDate.of(2027, 3, 10), "Airbnb", Categoria.ALUGUEL, null));
    }

    @Test
    void antesDePagarOLugarDevolveTudo() {
        depositar("Adulto 2", "276");
        Sugestao s = desistencias.sugerir(pessoa("Adulto 2").getId(), false);
        assertFalse(s.lugarPago());
        assertEquals(new BigDecimal("276.00"), s.guardado());
        assertEquals(new BigDecimal("276.00"), s.sugerido());
    }

    @Test
    void depoisDePagarOLugarDevolveAParteDosGastosVariaveis() {
        depositar("Adulto 2", "500");
        pagarOLugar();
        Sugestao s = desistencias.sugerir(pessoa("Adulto 2").getId(), false);
        assertTrue(s.lugarPago());
        // 4400 × 1 ÷ 11,5 = 382,608… → 382,60
        assertEquals(new BigDecimal("382.60"), s.parteVariavel());
        assertEquals(new BigDecimal("382.60"), s.sugerido());
    }

    @Test
    void nuncaDevolveMaisDoQueAPessoaGuardou() {
        depositar("Adulto 2", "100");
        pagarOLugar();
        assertEquals(new BigDecimal("100.00"), desistencias.sugerir(pessoa("Adulto 2").getId(), false).sugerido());
    }

    @Test
    void seOutraPessoaAssumirAVagaDevolveTudo() {
        depositar("Adulto 2", "500");
        pagarOLugar();
        assertEquals(new BigDecimal("500.00"), desistencias.sugerir(pessoa("Adulto 2").getId(), true).sugerido());
    }

    @Test
    void confirmarLancaADevolucaoETiraAPessoa() {
        depositar("Adulto 2", "500");
        Long id = pessoa("Adulto 2").getId();
        desistencias.confirmar(id, new BigDecimal("382.60"), HOJE);
        List<Lancamento> devolucoes = lancamentoRepository.daPessoa(id).stream()
                .filter(l -> l.getTipo() == TipoLancamento.DEVOLUCAO).toList();
        assertEquals(1, devolucoes.size());
        assertEquals(0, new BigDecimal("382.60").compareTo(devolucoes.get(0).getValor()));
        assertEquals("Desistência", devolucoes.get(0).getDescricao());
        assertFalse(pessoas.findById(id).orElseThrow().isAtivo());
        assertFalse(links.tokensAtivos().containsKey(id), "o link para de funcionar");
    }

    @Test
    void devolucaoZeroSoTiraAPessoa() {
        Long id = pessoa("Adulto 3").getId();
        desistencias.confirmar(id, BigDecimal.ZERO, HOJE);
        assertFalse(pessoas.findById(id).orElseThrow().isAtivo());
        assertTrue(lancamentoRepository.daPessoa(id).isEmpty());
    }

    @Test
    void devolucaoForaDoLimiteNaoGravaNada() {
        depositar("Adulto 2", "500");
        Long id = pessoa("Adulto 2").getId();
        RegraException e = assertThrows(RegraException.class,
                () -> desistencias.confirmar(id, new BigDecimal("600"), HOJE));
        assertEquals("A devolução vai de R$ 0,00 até o que a pessoa guardou (R$ 500,00).", e.getMessage());
        assertThrows(RegraException.class, () -> desistencias.confirmar(id, new BigDecimal("-1"), HOJE));
        assertTrue(pessoas.findById(id).orElseThrow().isAtivo());
    }

    @Test
    void responsavelComDependentesNaoDesisteSozinho() {
        depositar("Adulto 1", "92");
        Long id = pessoa("Adulto 1").getId();
        assertThrows(RegraException.class, () -> desistencias.confirmar(id, new BigDecimal("92"), HOJE));
        assertTrue(pessoas.findById(id).orElseThrow().isAtivo());
        assertTrue(lancamentoRepository.daPessoa(id).stream().noneMatch(l -> l.getTipo() == TipoLancamento.DEVOLUCAO));
    }

    @Test
    void quemJaSaiuNaoDesisteDeNovo() {
        Long id = pessoa("Adulto 3").getId();
        desistencias.confirmar(id, BigDecimal.ZERO, HOJE);
        RegraException e = assertThrows(RegraException.class, () -> desistencias.confirmar(id, BigDecimal.ZERO, HOJE));
        assertEquals("Adulto 3 já está fora da caixinha.", e.getMessage());
    }
}
