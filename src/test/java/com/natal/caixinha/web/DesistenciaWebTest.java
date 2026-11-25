package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {BancoDeTeste.URL, "app.admin.senha=senha-de-teste-123"})
@AutoConfigureMockMvc
@Import(RelogioDeTeste.class)
@Transactional
class DesistenciaWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    PessoaRepository pessoas;

    Long id;

    @BeforeEach
    void preparar() {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
        id = pessoas.findAll().stream().filter(p -> p.getNome().equals("Adulto 2")).findFirst().orElseThrow().getId();
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, id, new BigDecimal("500"),
                LocalDate.of(2027, 3, 1), null, null, null));
        lancamentos.registrar(new DadosLancamento(TipoLancamento.GASTO, null, new BigDecimal("6000"),
                LocalDate.of(2027, 3, 10), "Airbnb", Categoria.ALUGUEL, null));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void telaMostraOGuardadoEASugestao() throws Exception {
        mvc.perform(get("/painel/pessoas/" + id + "/desistencia"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Guardou R$ 500,00")))
                .andExpect(content().string(containsString("value=\"382,60\"")))
                .andExpect(content().string(containsString("Outra pessoa vai assumir a vaga")));
        mvc.perform(get("/painel/pessoas/" + id + "/desistencia").param("vaga", "true"))
                .andExpect(content().string(containsString("value=\"500\"")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void confirmarLancaEVoltaParaPessoas() throws Exception {
        mvc.perform(post("/painel/pessoas/" + id + "/desistencia").param("valor", "382,60")
                        .param("data", "2027-03-15").with(csrf()))
                .andExpect(redirectedUrl("/painel/pessoas"))
                .andExpect(flash().attribute("sucesso", "Desistência registrada: devolução de R$ 382,60."));
        assertFalse(pessoas.findById(id).orElseThrow().isAtivo());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void erroVoltaParaATela() throws Exception {
        mvc.perform(post("/painel/pessoas/" + id + "/desistencia").param("valor", "600")
                        .param("data", "2027-03-15").with(csrf()))
                .andExpect(redirectedUrl("/painel/pessoas/" + id + "/desistencia"))
                .andExpect(flash().attribute("erro", "A devolução vai de R$ 0,00 até o que a pessoa guardou (R$ 500,00)."));
        mvc.perform(post("/painel/pessoas/" + id + "/desistencia").param("valor", "10")
                        .param("data", "15/03").with(csrf()))
                .andExpect(flash().attribute("erro", "Data inválida."));
        assertTrue(pessoas.findById(id).orElseThrow().isAtivo());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void listaDePessoasTemOBotao() throws Exception {
        mvc.perform(get("/painel/pessoas"))
                .andExpect(content().string(containsString("/painel/pessoas/" + id + "/desistencia")));
    }

    @Test
    void semLoginVaiParaEntrar() throws Exception {
        mvc.perform(get("/painel/pessoas/" + id + "/desistencia")).andExpect(redirectedUrl("/entrar"));
    }
}
