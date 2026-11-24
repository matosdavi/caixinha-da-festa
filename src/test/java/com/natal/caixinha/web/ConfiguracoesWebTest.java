package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
class ConfiguracoesWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CaixinhaRepository caixinhas;

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/painel/configuracoes")).andExpect(redirectedUrl("/entrar"));
        mvc.perform(post("/painel/configuracoes").with(csrf())).andExpect(redirectedUrl("/entrar"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void semChaveNaoMostraPixDeTeste() throws Exception {
        mvc.perform(get("/painel/configuracoes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chave PIX")))
                .andExpect(content().string(not(containsString("br.gov.bcb.pix"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void salvaEMostraPixDeTeste() throws Exception {
        mvc.perform(post("/painel/configuracoes").with(csrf())
                        .param("tipo", "CPF").param("chave", "529.982.247-25")
                        .param("nome", "Conceição Araújo").param("cidade", "São Paulo"))
                .andExpect(redirectedUrl("/painel/configuracoes"))
                .andExpect(flash().attribute("sucesso", "Configurações salvas."));

        var c = caixinhas.configuracao();
        assertEquals("52998224725", c.getChavePix());
        assertEquals("Conceição Araújo", c.getNomeRecebedor());
        assertEquals("São Paulo", c.getCidadeRecebedor());

        mvc.perform(get("/painel/configuracoes"))
                .andExpect(content().string(containsString("011152998224725")))
                .andExpect(content().string(containsString("54041.00")))
                .andExpect(content().string(containsString("CONCEICAO ARAUJO")))
                .andExpect(content().string(containsString("aria-label=\"QR Code do PIX\"")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void cpfInvalido() throws Exception {
        mvc.perform(post("/painel/configuracoes").with(csrf())
                        .param("tipo", "CPF").param("chave", "529.982.247-24")
                        .param("nome", "Ana").param("cidade", "Santos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CPF inválido")))
                .andExpect(content().string(containsString("value=\"529.982.247-24\"")));
        assertNull(caixinhas.configuracao().getChavePix());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void exigeNomeECidade() throws Exception {
        mvc.perform(post("/painel/configuracoes").with(csrf())
                        .param("tipo", "EMAIL").param("chave", "a@b.com").param("nome", " ").param("cidade", "Santos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe o nome de quem recebe")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void menuTemConfiguracoes() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(content().string(containsString("href=\"/painel/configuracoes\"")));
    }
}
