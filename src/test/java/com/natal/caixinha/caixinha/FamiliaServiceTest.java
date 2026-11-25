package com.natal.caixinha.caixinha;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.caixinha.FamiliaService.EstadoDoMes;
import com.natal.caixinha.caixinha.FamiliaService.Familia;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import com.natal.caixinha.pessoa.DadosPessoa;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.pessoa.PessoaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/** Família do seed: "Adulto 1" (responsável), "Adolescente" (meia cota) e "Bebê" (não paga), em 15/03/2027. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Import(RelogioDeTeste.class)
@Transactional
class FamiliaServiceTest {

    @Autowired
    FamiliaService familias;
    @Autowired
    CaixinhaService caixinha;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    PessoaService pessoaService;

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    /** Orçamento do caso da spec: 10.400 com margem de 10% → cota 92 com o seed (pesos 11,5). */
    void orcamentoDaSpec() {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Chácara", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    void depositar(String nome, String valor, LocalDate data) {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, pessoa(nome).getId(),
                new BigDecimal(valor), data, null, null, null));
    }

    void pagarMeses(int meses) {
        for (int m = 1; m <= meses; m++) {
            depositar("Adulto 1", "92", LocalDate.of(2027, m, 8));
            depositar("Adolescente", "46", LocalDate.of(2027, m, 8));
        }
    }

    static void valor(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + ", veio " + real);
    }

    @Test
    void pendenteEmMarco() {
        orcamentoDaSpec();
        pagarMeses(2);
        Familia f = familias.daFamilia(pessoa("Adulto 1").getId());

        assertEquals(EstadoDoMes.PENDENTE, f.estado());
        valor("138", f.cotaFamilia());
        valor("138", f.valorPix());
        assertEquals(YearMonth.of(2027, 3), f.mesAtual());
        assertEquals(3, f.numeroMes());
        assertEquals(11, f.totalMeses());
        assertFalse(f.cotaFixada());
        assertEquals(2, f.pagantes().size());
        assertEquals("Adulto 1", f.pagantes().get(0).pessoa().getNome());
        assertEquals(2, f.dependentes().size());
        assertEquals(Carne.Estado.PAGO, f.carne().get(0).estado());
        assertEquals(Carne.Estado.PAGO, f.carne().get(1).estado());
        assertEquals(Carne.Estado.ABERTO, f.carne().get(2).estado());
        assertEquals(4, f.extrato().size());
        assertEquals(LocalDate.of(2027, 2, 8), f.extrato().get(0).getData(), "mais novo primeiro");
    }

    @Test
    void atrasado() {
        orcamentoDaSpec();
        Familia f = familias.daFamilia(pessoa("Adulto 1").getId());
        assertEquals(EstadoDoMes.ATRASADO, f.estado());
        valor("414", f.valorPix());
    }

    @Test
    void pago() {
        orcamentoDaSpec();
        pagarMeses(3);
        Familia f = familias.daFamilia(pessoa("Adulto 1").getId());
        assertEquals(EstadoDoMes.PAGO, f.estado());
        valor("0", f.valorPix());
    }

    @Test
    void semOrcamento() {
        Familia f = familias.daFamilia(pessoa("Adulto 1").getId());
        assertEquals(EstadoDoMes.SEM_VALOR, f.estado());
        valor("0", f.valorPix());
        assertTrue(f.carne().stream().allMatch(t -> t.estado() == Carne.Estado.ABERTO));
    }

    @Test
    void extratoNaoTrazOutraFamilia() {
        orcamentoDaSpec();
        depositar("Adulto 2", "92", LocalDate.of(2027, 1, 8));
        depositar("Adulto 1", "92", LocalDate.of(2027, 1, 9));
        Familia f = familias.daFamilia(pessoa("Adulto 1").getId());
        assertEquals(1, f.extrato().size());
        assertEquals("Adulto 1", f.extrato().get(0).getPessoa().getNome());
    }

    @Test
    void sugereADivisaoDaFamilia() {
        orcamentoDaSpec();
        pagarMeses(2);
        var partes = familias.sugerirDivisao(pessoa("Adulto 1").getId(), new BigDecimal("138"));
        assertEquals(2, partes.size());
        valor("92", partes.get(0).valor());
        valor("46", partes.get(1).valor());
    }

    @Test
    void responsavelPeso0() {
        orcamentoDaSpec();
        caixinha.fixarCota();
        Pessoa avo = pessoaService.criar(new DadosPessoa("Vó Rosa", "", BigDecimal.ZERO, null,
                LocalDate.of(2026, 11, 20), true, null));
        pessoaService.criar(new DadosPessoa("Neto A", "", BigDecimal.ONE, avo.getId(), LocalDate.of(2026, 11, 20), true, null));
        pessoaService.criar(new DadosPessoa("Neto B", "", BigDecimal.ONE, avo.getId(), LocalDate.of(2026, 11, 20), true, null));

        Familia f = familias.daFamilia(avo.getId());
        assertEquals(2, f.pagantes().size());
        valor("184", f.cotaFamilia());

        var partes = familias.sugerirDivisao(avo.getId(), new BigDecimal("184"));
        valor("92", partes.get(0).valor());
        valor("92", partes.get(1).valor());
    }

    @Test
    void acrescimoDeQuemEntrouTardeEntraNoValorDoMes() {
        orcamentoDaSpec();
        caixinha.fixarCota(); // em março a cota já está fixada: a pessoa nova não muda a dos outros
        Pessoa nova = pessoaService.criar(new DadosPessoa("Nova", "", BigDecimal.ONE, null,
                LocalDate.of(2027, 3, 1), true, null));
        pessoaService.definirAcrescimo(nova.getId(), new BigDecimal("90"));
        Familia f = familias.daFamilia(nova.getId());
        // 90 dividido de março a novembro (9 meses) = 10 por mês; março vale 92 + 10
        valor("102.00", f.cotaFamilia());
        valor("90.00", f.acrescimo());
        // deve jan e fev (184) + março (102)
        valor("286.00", f.valorPix());
        valor("102.00", f.carne().get(2).valor());
        valor("92.00", f.carne().get(1).valor());
    }
}
