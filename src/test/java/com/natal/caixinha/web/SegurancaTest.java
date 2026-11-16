package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        BancoDeTeste.URL,
        "app.admin.usuario=organizador",
        "app.admin.senha=senha-de-teste-123"
})
@AutoConfigureMockMvc
class SegurancaTest {

    @Autowired
    MockMvc mvc;

    @Test
    void painelExigeLogin() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entrar"));
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entrar"));
    }

    @Test
    void paginasPublicas() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/entrar"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mvc.perform(get("/css/estilo.css")).andExpect(status().isOk());
    }

    @Test
    void loginComSenhaCertaAbreOPainel() throws Exception {
        mvc.perform(formLogin("/entrar").user("organizador").password("senha-de-teste-123"))
                .andExpect(authenticated().withRoles("ORGANIZADOR"))
                .andExpect(redirectedUrl("/painel"));
    }

    @Test
    void loginComSenhaErradaVoltaComErro() throws Exception {
        mvc.perform(formLogin("/entrar").user("organizador").password("errada"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/entrar?erro"));
    }

    @Test
    void loginSemTokenCsrfERecusado() throws Exception {
        mvc.perform(post("/entrar").param("username", "organizador").param("password", "senha-de-teste-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void painelLogadoMostraACaixinha() throws Exception {
        var sessao = mvc.perform(formLogin("/entrar").user("organizador").password("senha-de-teste-123"))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/painel").session((org.springframework.mock.web.MockHttpSession) sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Caixinha da Festa 2027")))
                .andExpect(content().string(containsString("Data em votação")));
    }

    @Test
    void sairEncerraASessao() throws Exception {
        mvc.perform(post("/sair").with(csrf()))
                .andExpect(redirectedUrl("/entrar?saiu"));
    }
}
