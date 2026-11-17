package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Telas de lançamentos, orçamento e início, em 15/03/2027. */
@SpringBootTest(properties = {BancoDeTeste.URL, "app.admin.senha=senha-de-teste-123"})
@AutoConfigureMockMvc
@Import(RelogioDeTeste.class)
@Transactional
class PainelFinanceiroWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    LancamentoRepository lancamentos;
    @Autowired
    CaixinhaRepository caixinhas;

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    void item(String categoria, String descricao, String valor) throws Exception {
        mvc.perform(post("/painel/orcamento").with(csrf())
                        .param("categoria", categoria).param("descricao", descricao).param("valorPrevisto", valor))
                .andExpect(redirectedUrl("/painel/orcamento"));
    }

    @Test
    void telasExigemLogin() throws Exception {
        mvc.perform(get("/painel/lancamentos")).andExpect(redirectedUrl("/entrar"));
        mvc.perform(get("/painel/orcamento")).andExpect(redirectedUrl("/entrar"));
        mvc.perform(post("/painel/lancamentos").with(csrf())).andExpect(redirectedUrl("/entrar"));
        mvc.perform(post("/painel/orcamento/fixar-cota").with(csrf())).andExpect(redirectedUrl("/entrar"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void orcamentoCalculaACotaEFixa() throws Exception {
        item("ALUGUEL", "Chácara", "6.000,00");
        item("COMIDA", "Ceia", "4400");

        mvc.perform(get("/painel/orcamento"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cota estimada")))
                .andExpect(content().string(containsString("R$ 10.400,00")))
                .andExpect(content().string(containsString("R$ 11.440,00")))
                .andExpect(content().string(containsString("R$ 90,43")))
                .andExpect(content().string(containsString("Fixar cota em R$ 92,00")))
                .andExpect(content().string(containsString("R$ 1.058,00")))
                .andExpect(content().string(containsString("R$ 11.638,00")));

        mvc.perform(post("/painel/orcamento/fixar-cota").with(csrf()))
                .andExpect(flash().attribute("sucesso", containsString("Cota fixada em R$ 92,00")));
        assertEquals(new BigDecimal("92.00"), caixinhas.configuracao().getCotaBase());

        mvc.perform(get("/painel/orcamento"))
                .andExpect(content().string(containsString("Cota fixada")))
                .andExpect(content().string(not(containsString("Fixar cota em"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void itemInvalidoVoltaComErro() throws Exception {
        mvc.perform(post("/painel/orcamento").with(csrf())
                        .param("categoria", "ALUGUEL").param("descricao", "Chácara").param("valorPrevisto", "seis mil"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Valor previsto inválido")))
                .andExpect(content().string(containsString("value=\"Ch&aacute;cara\"")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void depositoPeloFormularioAparecenoExtratoENoInicio() throws Exception {
        item("ALUGUEL", "Chácara", "10400");
        Long adulto1 = pessoa("Adulto 1").getId();

        mvc.perform(get("/painel/lancamentos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2027-03-15\"")))
                .andExpect(content().string(containsString("data-sugestao=\"276\"")))
                .andExpect(content().string(containsString("Adulto 1 · falta R$ 276,00")));

        mvc.perform(post("/painel/lancamentos").with(csrf())
                        .param("tipo", "DEPOSITO").param("pessoaId", adulto1.toString())
                        .param("valor", "184,00").param("data", "2027-03-10"))
                .andExpect(redirectedUrl("/painel/lancamentos"))
                .andExpect(flash().attribute("sucesso", "Depósito de Adulto 1 (R$ 184,00) lançado."));

        mvc.perform(get("/painel/lancamentos"))
                .andExpect(content().string(containsString("+ R$ 184,00")))
                .andExpect(content().string(containsString("Adulto 1 · falta R$ 92,00")));

        mvc.perform(get("/painel"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("R$ 184,00")))
                .andExpect(content().string(containsString("1 de 12")))
                .andExpect(content().string(containsString("março: falta R$ 92,00")))
                .andExpect(content().string(containsString("2 meses de atraso")))
                .andExpect(content().string(containsString("/painel/lancamentos?pessoa=")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void lancamentoInvalidoVoltaComOsDados() throws Exception {
        mvc.perform(post("/painel/lancamentos").with(csrf())
                        .param("tipo", "GASTO").param("valor", "500").param("data", "2027-03-01")
                        .param("descricao", "Sinal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escolha a categoria do gasto.")))
                .andExpect(content().string(containsString("value=\"Sinal\"")));
        assertEquals(0, lancamentos.count());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void excluirLancamento() throws Exception {
        mvc.perform(post("/painel/lancamentos").with(csrf())
                .param("tipo", "DOACAO").param("valor", "50").param("data", "2027-03-01"));
        Long id = lancamentos.findAll().get(0).getId();
        mvc.perform(post("/painel/lancamentos/" + id + "/excluir").with(csrf()))
                .andExpect(flash().attribute("sucesso", "Lançamento excluído."));
        assertEquals(0, lancamentos.count());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void inicioSemOrcamentoAvisa() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(content().string(containsString("Ainda não há orçamento")))
                .andExpect(content().string(containsString("12 de 12")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void pessoaComLancamentoNaoPodeSerExcluida() throws Exception {
        Long adulto2 = pessoa("Adulto 2").getId();
        mvc.perform(post("/painel/lancamentos").with(csrf())
                .param("tipo", "DEPOSITO").param("pessoaId", adulto2.toString())
                .param("valor", "92").param("data", "2027-03-01"));
        mvc.perform(post("/painel/pessoas/" + adulto2 + "/excluir").with(csrf()))
                .andExpect(flash().attribute("erro", containsString("lançamentos")));
        assertTrue(pessoas.existsById(adulto2));
    }
}
