package com.natal.caixinha.lancamento;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.orcamento.Categoria;
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

import static com.natal.caixinha.lancamento.TipoLancamento.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioDeTeste.class)
@Transactional
class LancamentoServiceTest {

    static final LocalDate HOJE = LocalDate.of(2027, 3, 15);

    @Autowired
    LancamentoService servico;
    @Autowired
    PessoaRepository pessoas;

    Pessoa adulto1;

    @BeforeEach
    void seed() {
        adulto1 = pessoas.findAll().stream().filter(p -> p.getNome().equals("Adulto 1")).findFirst().orElseThrow();
    }

    DadosLancamento dados(TipoLancamento tipo, Long pessoaId, String valor) {
        return new DadosLancamento(tipo, pessoaId, valor == null ? null : new BigDecimal(valor), HOJE, null, null, null);
    }

    void assertRegra(String trecho, DadosLancamento d) {
        RegraException e = assertThrows(RegraException.class, () -> servico.registrar(d));
        assertTrue(e.getMessage().contains(trecho), e.getMessage());
    }

    @Test
    void depositoComAdiantamentoEmDezembro() {
        Lancamento l = servico.registrar(new DadosLancamento(DEPOSITO, adulto1.getId(), new BigDecimal("92"),
                LocalDate.of(2026, 12, 20), "  PIX   adiantado ", null, null));
        assertEquals(new BigDecimal("92.00"), l.getValor());
        assertEquals("PIX adiantado", l.getDescricao());
        assertNotNull(l.getCriadoEm());
        assertEquals(new BigDecimal("92.00"), servico.guardadoDe(adulto1.getId()));
    }

    @Test
    void guardadoSomaCreditoEDescontaDevolucao() {
        servico.registrar(dados(DEPOSITO, adulto1.getId(), "184"));
        servico.registrar(dados(CREDITO_ANO_ANTERIOR, adulto1.getId(), "30"));
        servico.registrar(dados(DEVOLUCAO, adulto1.getId(), "14"));
        assertEquals(new BigDecimal("200.00"), servico.guardadoDe(adulto1.getId()));
    }

    @Test
    void lancamentoDaCaixinhaIgnoraPessoa() {
        Lancamento l = servico.registrar(dados(RENDIMENTO, adulto1.getId(), "3.21"));
        assertNull(l.getPessoa());
        assertNull(l.getCategoria());
    }

    @Test
    void gastoExigeCategoriaEGuardaComprovante() {
        assertRegra("categoria", dados(GASTO, null, "500"));
        Lancamento l = servico.registrar(new DadosLancamento(GASTO, null, new BigDecimal("500"), HOJE,
                "Sinal da chácara", Categoria.ALUGUEL, "https://exemplo.com/recibo.pdf"));
        assertEquals(Categoria.ALUGUEL, l.getCategoria());
        assertEquals("https://exemplo.com/recibo.pdf", l.getComprovanteUrl());
    }

    @Test
    void validacoes() {
        assertRegra("tipo", dados(null, adulto1.getId(), "92"));
        assertRegra("pessoa", dados(DEPOSITO, null, "92"));
        assertRegra("maior que zero", dados(DEPOSITO, adulto1.getId(), "0"));
        assertRegra("maior que zero", dados(DEPOSITO, adulto1.getId(), null));
        assertRegra("2 casas", dados(DEPOSITO, adulto1.getId(), "92.001"));
        assertRegra("sobrou um zero", dados(DEPOSITO, adulto1.getId(), "920000"));
        assertRegra("futuro", new DadosLancamento(DEPOSITO, adulto1.getId(), BigDecimal.TEN, HOJE.plusDays(1), null, null, null));
        assertRegra("data", new DadosLancamento(DEPOSITO, adulto1.getId(), BigDecimal.TEN, null, null, null, null));
        assertRegra("https://", new DadosLancamento(GASTO, null, BigDecimal.TEN, HOJE, null, Categoria.OUTROS,
                "javascript:alert(1)"));
        assertRegra("no máximo 200", new DadosLancamento(DOACAO, null, BigDecimal.TEN, HOJE, "x".repeat(201), null, null));
    }

    @Test
    void devolucaoNaoPassaDoGuardado() {
        servico.registrar(dados(DEPOSITO, adulto1.getId(), "92"));
        assertRegra("maior do que Adulto 1 tem guardado", dados(DEVOLUCAO, adulto1.getId(), "92.01"));
        servico.registrar(dados(DEVOLUCAO, adulto1.getId(), "92"));
        assertEquals(new BigDecimal("0.00"), servico.guardadoDe(adulto1.getId()));
    }

    @Test
    void excluirLancamento() {
        Lancamento l = servico.registrar(dados(DEPOSITO, adulto1.getId(), "92"));
        servico.excluir(l.getId());
        assertEquals(new BigDecimal("0.00"), servico.guardadoDe(adulto1.getId()));
        assertThrows(RegraException.class, () -> servico.excluir(l.getId()));
    }
}
