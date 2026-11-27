package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioAjustavel;
import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.prestacao.EscolhaSobraRepository;
import com.natal.caixinha.prestacao.OpcaoSobra;
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

/** Festa 20/12/2027; Adulto 2 guardou 1012, Adulto 3 506; gasto 1000 → sobra 518. */
@SpringBootTest(properties = {BancoDeTeste.URL, "app.admin.senha=senha-de-teste-123"})
@AutoConfigureMockMvc
@Import(RelogioAjustavel.class)
@Transactional
class ContasWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    RelogioAjustavel.Ajustavel relogio;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    CaixinhaService caixinha;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    LinkService links;
    @Autowired
    EscolhaSobraRepository escolhas;

    @BeforeEach
    void preparar() {
        relogio.para(LocalDate.of(2027, 3, 15));
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
        caixinha.fixarCota();
        caixinha.definirDataFesta(LocalDate.of(2027, 12, 20));
        relogio.para(LocalDate.of(2027, 11, 30));
        for (String[] d : new String[][]{{"Adulto 2", "1012"}, {"Adulto 3", "506"}}) {
            lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, pessoa(d[0]).getId(),
                    new BigDecimal(d[1]), LocalDate.of(2027, 11, 30), null, null, null));
        }
        lancamentos.registrar(new DadosLancamento(TipoLancamento.GASTO, null, new BigDecimal("1000"),
                LocalDate.of(2027, 11, 30), "Mercado", Categoria.COMIDA, null));
    }

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    String link(String nome) {
        return "/c/" + links.garantirLink(pessoa(nome)).getToken();
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void antesDaFestaATelaExplica() throws Exception {
        mvc.perform(get("/painel/contas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A prestação de contas abre depois da festa")))
                .andExpect(content().string(containsString("href=\"/painel/contas\"")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void depoisDaFestaMostraASobraELancaDepoisDoPrazo() throws Exception {
        relogio.para(LocalDate.of(2027, 12, 22));
        mvc.perform(get("/painel/contas"))
                .andExpect(content().string(containsString("Sobra final")))
                .andExpect(content().string(containsString("R$ 518,00")))
                .andExpect(content().string(containsString("R$ 345,34")));
        relogio.para(LocalDate.of(2027, 12, 28));
        Long a2 = pessoa("Adulto 2").getId();
        mvc.perform(post("/painel/contas/" + a2 + "/lancar").with(csrf()))
                .andExpect(redirectedUrl("/painel/contas"))
                .andExpect(flash().attribute("sucesso", "Lançado: R$ 345,34 de Adulto 2 (Crédito para 2028)."));
        mvc.perform(post("/painel/contas/" + a2 + "/lancar").with(csrf()))
                .andExpect(flash().attribute("erro", "A parte de Adulto 2 já foi lançada."));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void organizadorMudaAEscolha() throws Exception {
        relogio.para(LocalDate.of(2027, 12, 22));
        Long a3 = pessoa("Adulto 3").getId();
        mvc.perform(post("/painel/contas/" + a3 + "/escolha").param("opcao", "DEVOLVER").with(csrf()))
                .andExpect(redirectedUrl("/painel/contas"));
        assertEquals(OpcaoSobra.DEVOLVER, escolhas.findById(a3).orElseThrow().getOpcao());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void csvDosCreditos() throws Exception {
        relogio.para(LocalDate.of(2027, 12, 28));
        mvc.perform(get("/painel/contas/creditos.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("creditos-2028.csv")))
                .andExpect(content().string(containsString("Adulto 2;345,34")));
    }

    @Test
    void paginaPessoalMostraAParteEEscolhe() throws Exception {
        relogio.para(LocalDate.of(2027, 12, 22));
        String link = link("Adulto 2");
        mvc.perform(get(link))
                .andExpect(content().string(containsString("A parte da sua família: R$ 345,34")))
                .andExpect(content().string(containsString("Quero receber de volta")))
                .andExpect(content().string(containsString("Deixar para o ano que vem")));
        mvc.perform(post(link + "/sobra").param("opcao", "DEVOLVER").with(csrf()))
                .andExpect(redirectedUrl(link))
                .andExpect(flash().attribute("sucesso", "Escolha registrada: receber de volta."));
        assertEquals(OpcaoSobra.DEVOLVER, escolhas.findById(pessoa("Adulto 2").getId()).orElseThrow().getOpcao());
    }

    @Test
    void paginaPessoalAntesDaFestaNaoMostraAPrestacao() throws Exception {
        mvc.perform(get(link("Adulto 2"))).andExpect(content().string(not(containsString("A parte da sua família"))));
    }

    @Test
    void escolhaComLinkInvalidoDa404() throws Exception {
        relogio.para(LocalDate.of(2027, 12, 22));
        mvc.perform(post("/c/link-que-nao-existe/sobra").param("opcao", "DEVOLVER").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void painelSemLoginVaiParaEntrar() throws Exception {
        mvc.perform(get("/painel/contas")).andExpect(redirectedUrl("/entrar"));
    }
}
