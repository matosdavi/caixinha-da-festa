package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioAjustavel;
import com.natal.caixinha.lembrete.AvisoMensalRepository;
import com.natal.caixinha.lembrete.TipoAviso;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import com.natal.caixinha.pessoa.DadosPessoa;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.pessoa.PessoaService;
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
import java.time.YearMonth;

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
class LembretesWebTest {

    static final YearMonth MARCO = YearMonth.of(2027, 3);

    @Autowired
    MockMvc mvc;
    @Autowired
    RelogioAjustavel.Ajustavel relogio;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    PessoaService pessoaService;
    @Autowired
    AvisoMensalRepository avisos;

    @BeforeEach
    void preparar() {
        relogio.para(LocalDate.of(2027, 3, 15));
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Chácara", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void inicioMostraOCartaoDoGrupo() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lembrete de março")))
                .andExpect(content().string(containsString("Fechamos *fevereiro*")))
                .andExpect(content().string(containsString("name=\"mes\" value=\"2027-03\"")))
                .andExpect(content().string(not(containsString("Quem ainda não pagou março"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void enviarOGrupoGravaEAbreOWhatsApp() throws Exception {
        mvc.perform(post("/painel/lembretes/grupo").param("mes", "2027-03").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://wa.me/?text=")))
                .andExpect(header().string("Location", containsString("Fechamos")));
        assertEquals(1, avisos.findByMesAndTipo(MARCO, TipoAviso.GRUPO).size());
        mvc.perform(get("/painel")).andExpect(content().string(not(containsString("Lembrete de março"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void mesErradoNaoGravaEMostraOErro() throws Exception {
        mvc.perform(post("/painel/lembretes/grupo").param("mes", "2027-04").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/painel"))
                .andExpect(flash().attribute("erro", "Este lembrete não está mais disponível."));
        mvc.perform(post("/painel/lembretes/grupo").param("mes", "abril").with(csrf()))
                .andExpect(redirectedUrl("/painel"));
        assertTrue(avisos.findAll().isEmpty());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void individualNoDia22AbreAConversaDaPessoaEDaParaDesfazer() throws Exception {
        relogio.para(LocalDate.of(2027, 3, 22));
        Pessoa adulto2 = pessoa("Adulto 2");
        DadosPessoa d = DadosPessoa.de(adulto2);
        pessoaService.atualizar(adulto2.getId(), new DadosPessoa(d.nome(), "(11) 99999-8888", d.peso(),
                d.responsavelId(), d.dataEntrada(), d.ativo(), d.observacao()));

        mvc.perform(get("/painel"))
                .andExpect(content().string(containsString("Quem ainda não pagou março")));

        mvc.perform(post("/painel/lembretes/individual").param("mes", "2027-03")
                        .param("pessoaId", adulto2.getId().toString()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://wa.me/5511999998888?text=")));
        mvc.perform(get("/painel")).andExpect(content().string(containsString("Avisado ✓")));

        mvc.perform(post("/painel/lembretes/desfazer").param("mes", "2027-03")
                        .param("pessoaId", adulto2.getId().toString()).with(csrf()))
                .andExpect(redirectedUrl("/painel"));
        mvc.perform(get("/painel")).andExpect(content().string(not(containsString("Avisado ✓"))));
    }

    @Test
    void semLoginNaoAvisa() throws Exception {
        mvc.perform(post("/painel/lembretes/grupo").param("mes", "2027-03").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entrar"));
        assertTrue(avisos.findAll().isEmpty());
    }
}
