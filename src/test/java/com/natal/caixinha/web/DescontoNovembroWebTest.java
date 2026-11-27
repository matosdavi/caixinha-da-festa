package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioAjustavel;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.link.LinkService;
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
@Import(RelogioAjustavel.class)
@Transactional
class DescontoNovembroWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    RelogioAjustavel.Ajustavel relogio;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    CaixinhaService caixinha;
    @Autowired
    CaixinhaRepository caixinhas;
    @Autowired
    LinkService links;
    @Autowired
    PessoaRepository pessoas;

    @BeforeEach
    void preparar() {
        relogio.para(LocalDate.of(2027, 3, 15));
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
        caixinha.fixarCota();
        relogio.para(LocalDate.of(2027, 10, 15));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void telaMostraAPrevia() throws Exception {
        mvc.perform(get("/painel/desconto-novembro"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sobra prevista")))
                .andExpect(content().string(containsString("R$ 1.238,00")))
                .andExpect(content().string(containsString("R$ 53,00 por cota inteira")))
                .andExpect(content().string(containsString("meia cota: R$ 26,50")))
                .andExpect(content().string(containsString("Confirmar desconto")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void confirmarEDesfazer() throws Exception {
        mvc.perform(post("/painel/desconto-novembro").with(csrf()))
                .andExpect(redirectedUrl("/painel/desconto-novembro"))
                .andExpect(flash().attribute("sucesso", "Desconto de novembro confirmado: R$ 53,00 por cota inteira."));
        mvc.perform(get("/painel/desconto-novembro")).andExpect(content().string(containsString("Desfazer")));
        mvc.perform(post("/painel/desconto-novembro/desfazer").with(csrf()))
                .andExpect(flash().attribute("sucesso", "Desconto de novembro desfeito."));
        assertNull(caixinhas.configuracao().getDescontoNovembroPorPeso());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void foraDeOutubroMostraOMotivo() throws Exception {
        relogio.para(LocalDate.of(2027, 9, 15));
        mvc.perform(get("/painel/desconto-novembro"))
                .andExpect(content().string(containsString("O desconto de novembro se confirma em outubro.")))
                .andExpect(content().string(not(containsString("Confirmar desconto"))));
        mvc.perform(post("/painel/desconto-novembro").with(csrf()))
                .andExpect(flash().attribute("erro", "O desconto de novembro se confirma em outubro."));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void cronogramaTemOLink() throws Exception {
        caixinha.definirDataFesta(LocalDate.of(2027, 12, 20));
        mvc.perform(get("/painel/cronograma"))
                .andExpect(content().string(containsString("href=\"/painel/desconto-novembro\"")));
    }

    @Test
    void paginaPessoalMostraONovembroComDesconto() throws Exception {
        relogio.para(LocalDate.of(2027, 10, 15));
        mvc.perform(post("/painel/desconto-novembro").with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("org").roles("ORGANIZADOR")));
        var adulto2 = pessoas.findAll().stream().filter(p -> p.getNome().equals("Adulto 2")).findFirst().orElseThrow();
        mvc.perform(get("/c/" + links.garantirLink(adulto2).getToken()))
                .andExpect(content().string(containsString("Novembro com desconto: R$ 39,00 (presente do amigo secreto 🎁)")));
    }

    @Test
    void semLoginVaiParaEntrar() throws Exception {
        mvc.perform(get("/painel/desconto-novembro")).andExpect(redirectedUrl("/entrar"));
    }
}
