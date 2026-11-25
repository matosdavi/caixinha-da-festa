package com.natal.caixinha.pessoa;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.comum.RegraException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/** Hoje = 15/03/2027; a lista fechou em 31/12/2026. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioDeTeste.class)
@Transactional
class AcrescimoTest {

    @Autowired
    PessoaService pessoas;
    @Autowired
    PessoaRepository repositorio;

    Pessoa novaPessoa() {
        return pessoas.criar(new DadosPessoa("Nova", "", BigDecimal.ONE, null, LocalDate.of(2027, 3, 1), true, null));
    }

    @Test
    void registraODesdeNoMesDeHojeEMantemAoMudarOValor() {
        Pessoa p = novaPessoa();
        assertTrue(p.entrouTarde(LocalDate.of(2026, 12, 31)));
        assertNull(p.getAcrescimo(), "nulo = ainda não confirmado com o anfitrião");

        pessoas.definirAcrescimo(p.getId(), new BigDecimal("300"));
        Pessoa salva = repositorio.findById(p.getId()).orElseThrow();
        assertEquals(new BigDecimal("300.00"), salva.getAcrescimo());
        assertEquals(YearMonth.of(2027, 3), salva.getAcrescimoDesde());

        pessoas.definirAcrescimo(p.getId(), new BigDecimal("400"));
        assertEquals(YearMonth.of(2027, 3), repositorio.findById(p.getId()).orElseThrow().getAcrescimoDesde());
    }

    @Test
    void zeroConfirmaSemAcrescimoELimpaODesde() {
        Pessoa p = novaPessoa();
        pessoas.definirAcrescimo(p.getId(), new BigDecimal("300"));
        pessoas.definirAcrescimo(p.getId(), BigDecimal.ZERO);
        Pessoa salva = repositorio.findById(p.getId()).orElseThrow();
        assertEquals(new BigDecimal("0.00"), salva.getAcrescimo());
        assertNull(salva.getAcrescimoDesde());
    }

    @Test
    void soParaQuemEntrouDepoisDoFechamento() {
        Pessoa doSeed = repositorio.findAll().stream().filter(x -> x.getNome().equals("Adulto 2")).findFirst().orElseThrow();
        assertFalse(doSeed.entrouTarde(LocalDate.of(2026, 12, 31)));
        RegraException e = assertThrows(RegraException.class,
                () -> pessoas.definirAcrescimo(doSeed.getId(), new BigDecimal("100")));
        assertEquals("O acréscimo é só para quem entrou depois do fechamento da lista.", e.getMessage());
    }

    @Test
    void valorObrigatorioENaoNegativo() {
        Pessoa p = novaPessoa();
        String msg = "Informe o acréscimo: R$ 0 se o anfitrião não cobrar nada.";
        assertEquals(msg, assertThrows(RegraException.class, () -> pessoas.definirAcrescimo(p.getId(), null)).getMessage());
        assertEquals(msg, assertThrows(RegraException.class,
                () -> pessoas.definirAcrescimo(p.getId(), new BigDecimal("-1"))).getMessage());
    }

    @Test
    void soParaQuemPagaEstaAtivo() {
        Pessoa bebe = pessoas.criar(new DadosPessoa("Bebê Novo", "", BigDecimal.ZERO, null,
                LocalDate.of(2027, 3, 1), true, null));
        RegraException e = assertThrows(RegraException.class,
                () -> pessoas.definirAcrescimo(bebe.getId(), new BigDecimal("150")));
        assertEquals("O acréscimo é só para quem paga cota e está ativo na caixinha.", e.getMessage());
        assertNull(repositorio.findById(bebe.getId()).orElseThrow().getAcrescimo());

        Pessoa saiu = pessoas.criar(new DadosPessoa("Saiu", "", BigDecimal.ONE, null,
                LocalDate.of(2027, 3, 1), false, null));
        assertThrows(RegraException.class, () -> pessoas.definirAcrescimo(saiu.getId(), new BigDecimal("150")));
    }

    @Test
    void valorComMaisDeDuasCasasOuGiganteERecusado() {
        Pessoa p = novaPessoa();
        assertEquals("Acréscimo deve ter no máximo 2 casas decimais.", assertThrows(RegraException.class,
                () -> pessoas.definirAcrescimo(p.getId(), new BigDecimal("100.005"))).getMessage());
        assertThrows(RegraException.class,
                () -> pessoas.definirAcrescimo(p.getId(), new BigDecimal("1000000000000")));
        assertNull(repositorio.findById(p.getId()).orElseThrow().getAcrescimo());
    }
}
