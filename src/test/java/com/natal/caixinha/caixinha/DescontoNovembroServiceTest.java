package com.natal.caixinha.caixinha;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioAjustavel;
import com.natal.caixinha.caixinha.DescontoNovembroService.Previa;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
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

import static org.junit.jupiter.api.Assertions.*;

/** Orçamento da spec (10.400, margem 10%) → cota 92, pesos 11,5, arrecadação mensal 1.058. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioAjustavel.class)
@Transactional
class DescontoNovembroServiceTest {

    @Autowired
    DescontoNovembroService descontos;
    @Autowired
    CaixinhaService caixinha;
    @Autowired
    CaixinhaRepository caixinhas;
    @Autowired
    FamiliaService familias;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    RelogioAjustavel.Ajustavel relogio;

    @BeforeEach
    void preparar() {
        relogio.para(LocalDate.of(2027, 3, 15));
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
        caixinha.fixarCota();
        relogio.para(LocalDate.of(2027, 10, 15));
    }

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    static void valor(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + ", veio " + real);
    }

    @Test
    void previaComACaixinhaInteiraPorPagar() {
        Previa p = descontos.previa();
        valor("0", p.guardado());
        valor("11638", p.falta());
        valor("11638", p.arrecadacaoPrevista());
        valor("10400", p.gastosPrevistos());
        valor("1238", p.sobra());
        valor("53", p.descontoPorPeso());
        valor("628.50", p.reserva());
        assertTrue(p.podeConfirmar(), p.motivo());
        assertNull(p.confirmado());
    }

    @Test
    void quemAdiantouNaoTemFalta() {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, pessoa("Adulto 2").getId(),
                new BigDecimal("1100"), LocalDate.of(2027, 10, 1), null, null, null));
        Previa p = descontos.previa();
        valor("1100", p.guardado());
        valor("10626", p.falta()); // 11638 − 1012 (a cota inteira de Adulto 2 já está coberta)
        valor("11726", p.arrecadacaoPrevista());
    }

    @Test
    void confirmarAplicaNoNovembroDeCadaUm() {
        descontos.confirmar();
        valor("53", caixinhas.configuracao().getDescontoNovembroPorPeso());
        var f2 = familias.daFamilia(pessoa("Adulto 2").getId());
        valor("39", f2.carne().get(10).valor());
        valor("92", f2.carne().get(9).valor());
        // Adulto 1 + Adolescente (meia cota): 39 + (46 − 26,50) = 58,50
        valor("58.50", familias.daFamilia(pessoa("Adulto 1").getId()).carne().get(10).valor());
        valor("53", descontos.previa().confirmado());
    }

    @Test
    void desfazerVoltaAoNormal() {
        descontos.confirmar();
        descontos.desfazer();
        assertNull(caixinhas.configuracao().getDescontoNovembroPorPeso());
        valor("92", familias.daFamilia(pessoa("Adulto 2").getId()).carne().get(10).valor());
    }

    @Test
    void soSeConfirmaEmOutubro() {
        relogio.para(LocalDate.of(2027, 9, 15));
        assertEquals("O desconto de novembro se confirma em outubro.",
                assertThrows(RegraException.class, () -> descontos.confirmar()).getMessage());
        relogio.para(LocalDate.of(2027, 11, 5));
        assertEquals("O desconto já está valendo e não muda mais.",
                assertThrows(RegraException.class, () -> descontos.desfazer()).getMessage());
        assertFalse(descontos.previa().podeConfirmar());
        assertNull(caixinhas.configuracao().getDescontoNovembroPorPeso());
    }

    @Test
    void semSobraNaoHaDesconto() {
        orcamento.salvar(null, new DadosItem(Categoria.OUTROS, "Extra", new BigDecimal("2000"), null));
        Previa p = descontos.previa();
        assertTrue(p.sobra().signum() < 0);
        valor("0", p.descontoPorPeso());
        assertFalse(p.podeConfirmar());
        assertEquals("Não há sobra prevista: sem desconto em novembro.", p.motivo());
        assertThrows(RegraException.class, () -> descontos.confirmar());
    }
}
