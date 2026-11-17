package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.pessoa.DadosPessoa;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.pessoa.PessoaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@Transactional
class PessoasWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PessoaService pessoas;
    @Autowired
    PessoaRepository repositorio;
    @Autowired
    LinkService links;

    @Test
    void telasDePessoasExigemLogin() throws Exception {
        mvc.perform(get("/painel/pessoas")).andExpect(redirectedUrl("/entrar"));
        mvc.perform(post("/painel/pessoas/links").with(csrf())).andExpect(redirectedUrl("/entrar"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void listaMostraOSeedEOBotaoDeGerarLinks() throws Exception {
        mvc.perform(get("/painel/pessoas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Adulto 11")))
                .andExpect(content().string(containsString("11 responsáveis ainda não têm link.")))
                .andExpect(content().string(containsString("Dependente de Adulto 1")))
                .andExpect(content().string(containsString("soma dos pesos <strong>11,5</strong>")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void gerarLinksMostraLinkCopiavelEWhatsApp() throws Exception {
        mvc.perform(post("/painel/pessoas/links").with(csrf()))
                .andExpect(redirectedUrl("/painel/pessoas"))
                .andExpect(flash().attribute("sucesso", "11 links gerados."));

        Pessoa adulto1 = repositorio.findAll().stream().filter(p -> p.getNome().equals("Adulto 1")).findFirst().orElseThrow();
        String token = links.tokensAtivos().get(adulto1.getId());

        mvc.perform(get("/painel/pessoas"))
                .andExpect(content().string(containsString("data-copiar=\"http://localhost/c/" + token + "\"")))
                .andExpect(content().string(containsString("https://wa.me/?text=Oi%2C%20Adulto%21")))
                .andExpect(content().string(not(containsString("ainda não têm link"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void cadastrarPessoaPeloFormulario() throws Exception {
        mvc.perform(post("/painel/pessoas").with(csrf())
                        .param("nome", "Tia Rosa")
                        .param("telefone", "(21) 98888-7777")
                        .param("peso", "1.00")
                        .param("responsavelId", "")
                        .param("dataEntrada", "2026-11-20")
                        .param("ativo", "true")
                        .param("observacao", ""))
                .andExpect(redirectedUrl("/painel/pessoas"))
                .andExpect(flash().attribute("sucesso", "Tia Rosa foi cadastrada."));

        Pessoa rosa = repositorio.findAll().stream().filter(p -> p.getNome().equals("Tia Rosa")).findFirst().orElseThrow();
        assertEquals("5521988887777", rosa.getTelefone());
        assertNotNull(links.tokensAtivos().get(rosa.getId()), "responsável novo já ganha link");
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void erroDeValidacaoVoltaParaOFormularioComOsDados() throws Exception {
        mvc.perform(post("/painel/pessoas").with(csrf())
                        .param("nome", "Tia Rosa")
                        .param("telefone", "1234")
                        .param("peso", "1.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("painel/pessoa-form"))
                .andExpect(content().string(containsString("Telefone inválido")))
                .andExpect(content().string(containsString("value=\"Tia Rosa\"")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void desmarcarAtivoNoFormularioDesativa() throws Exception {
        Pessoa ana = pessoas.criar(new DadosPessoa("Ana", "", BigDecimal.ONE, null, LocalDate.of(2026, 11, 20), true, null));
        // checkbox desmarcado: o navegador só manda o campo oculto "_ativo"
        mvc.perform(post("/painel/pessoas/" + ana.getId()).with(csrf())
                        .param("nome", "Ana").param("peso", "1.00").param("_ativo", "on"))
                .andExpect(redirectedUrl("/painel/pessoas"));
        assertFalse(repositorio.findById(ana.getId()).orElseThrow().isAtivo());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void gerarOutroLinkEExcluir() throws Exception {
        Pessoa ana = pessoas.criar(new DadosPessoa("Ana", "", BigDecimal.ONE, null, LocalDate.of(2026, 11, 20), true, null));
        String antigo = links.tokensAtivos().get(ana.getId());

        mvc.perform(post("/painel/pessoas/" + ana.getId() + "/novo-link").with(csrf()))
                .andExpect(redirectedUrl("/painel/pessoas#pessoa-" + ana.getId()));
        assertNotEquals(antigo, links.tokensAtivos().get(ana.getId()));

        mvc.perform(post("/painel/pessoas/" + ana.getId() + "/excluir").with(csrf()))
                .andExpect(flash().attribute("sucesso", "Ana foi excluída."));
        assertFalse(repositorio.existsById(ana.getId()));
    }
}
