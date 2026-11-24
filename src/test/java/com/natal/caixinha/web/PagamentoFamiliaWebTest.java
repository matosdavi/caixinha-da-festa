package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.lancamento.Lancamento;
import com.natal.caixinha.lancamento.LancamentoRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pagamento da família da "Adulto 1" (com "Adolescente" e "Bebê"), cota 92, em 15/03/2027. */
@SpringBootTest(properties = {BancoDeTeste.URL, "app.admin.senha=senha-de-teste-123"})
@AutoConfigureMockMvc
@Import(RelogioDeTeste.class)
@Transactional
class PagamentoFamiliaWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    LancamentoRepository lancamentos;
    @Autowired
    OrcamentoService orcamento;

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    @BeforeEach
    void orcamentoDaSpec() {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Chácara", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/painel/lancamentos/familia")).andExpect(redirectedUrl("/entrar"));
        mvc.perform(post("/painel/lancamentos/familia").with(csrf())).andExpect(redirectedUrl("/entrar"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void sugereADivisao() throws Exception {
        Long adulto = pessoa("Adulto 1").getId();
        Long adolescente = pessoa("Adolescente").getId();
        mvc.perform(get("/painel/lancamentos/familia").param("responsavel", adulto.toString()).param("total", "138"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pagamento da família")))
                .andExpect(content().string(containsString("name=\"parte_" + adulto + "\"")))
                .andExpect(content().string(containsString("value=\"92\"")))
                .andExpect(content().string(containsString("name=\"parte_" + adolescente + "\"")))
                .andExpect(content().string(containsString("value=\"46\"")))
                .andExpect(content().string(not(containsString("name=\"parte_" + pessoa("Bebê").getId() + "\""))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void semTotalUsaOValorDaFamilia() throws Exception {
        // em 15/03 sem depósitos: 3 meses × 138 = 414
        mvc.perform(get("/painel/lancamentos/familia").param("responsavel", pessoa("Adulto 1").getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"414\"")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void lancaUmDepositoPorPessoa() throws Exception {
        Long adulto = pessoa("Adulto 1").getId();
        Long adolescente = pessoa("Adolescente").getId();
        mvc.perform(post("/painel/lancamentos/familia").with(csrf())
                        .param("responsavelId", adulto.toString()).param("total", "138").param("data", "2027-03-10")
                        .param("parte_" + adulto, "92").param("parte_" + adolescente, "46"))
                .andExpect(redirectedUrl("/painel/lancamentos"))
                .andExpect(flash().attribute("sucesso", "Pagamento da família lançado: 2 depósitos."));

        List<Lancamento> todos = lancamentos.findAll();
        assertEquals(2, todos.size());
        assertTrue(todos.stream().allMatch(l -> l.getTipo() == TipoLancamento.DEPOSITO
                && l.getData().equals(LocalDate.of(2027, 3, 10))
                && "Pagamento da família: PIX de R$ 138,00".equals(l.getDescricao())));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void partesQueNaoFechamOTotal() throws Exception {
        Long adulto = pessoa("Adulto 1").getId();
        Long adolescente = pessoa("Adolescente").getId();
        mvc.perform(post("/painel/lancamentos/familia").with(csrf())
                        .param("responsavelId", adulto.toString()).param("total", "138").param("data", "2027-03-10")
                        .param("parte_" + adulto, "92").param("parte_" + adolescente, "40"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("As partes somam R$ 132,00, mas o PIX foi de R$ 138,00.")))
                .andExpect(content().string(containsString("value=\"40\"")));
        assertEquals(0, lancamentos.count());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void recusaPessoaDeOutraFamilia() throws Exception {
        Long adulto = pessoa("Adulto 1").getId();
        Long outro = pessoa("Adulto 2").getId();
        mvc.perform(post("/painel/lancamentos/familia").with(csrf())
                        .param("responsavelId", adulto.toString()).param("total", "138").param("data", "2027-03-10")
                        .param("parte_" + adulto, "92").param("parte_" + outro, "46"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("não é da família")));
        assertEquals(0, lancamentos.count());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void inicioLevaAoPagamentoDaFamilia() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(content().string(containsString(
                        "/painel/lancamentos/familia?responsavel=" + pessoa("Adulto 1").getId())));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void lancamentosTemOAtalho() throws Exception {
        mvc.perform(get("/painel/lancamentos"))
                .andExpect(content().string(containsString("href=\"/painel/lancamentos/familia\"")));
    }
}
