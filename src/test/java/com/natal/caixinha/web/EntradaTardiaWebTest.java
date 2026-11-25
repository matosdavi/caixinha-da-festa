package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
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
@WithMockUser(roles = "ORGANIZADOR")
class EntradaTardiaWebTest {

    static final String AVISO = "Confirme com o anfitrião a lotação da casa";

    @Autowired
    MockMvc mvc;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    PessoaService pessoaService;
    @Autowired
    PessoaRepository pessoas;

    Pessoa nova;

    @BeforeEach
    void preparar() {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"), null));
        nova = pessoaService.criar(new DadosPessoa("Pessoa Nova", "", BigDecimal.ONE, null,
                LocalDate.of(2027, 3, 1), true, null));
    }

    void pagarOLugar() {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.GASTO, null, new BigDecimal("6000"),
                LocalDate.of(2027, 3, 10), "Airbnb", Categoria.ALUGUEL, null));
    }

    @Test
    void antesDePagarOLugarNaoHaAviso() throws Exception {
        mvc.perform(get("/painel")).andExpect(content().string(not(containsString(AVISO))));
    }

    @Test
    void depoisDePagarOLugarAvisaQuemEntrouTarde() throws Exception {
        pagarOLugar();
        mvc.perform(get("/painel"))
                .andExpect(content().string(containsString(AVISO)))
                .andExpect(content().string(containsString("Pessoa Nova")));
    }

    @Test
    void registrarZeroTiraDoAviso() throws Exception {
        pagarOLugar();
        mvc.perform(post("/painel/pessoas/" + nova.getId() + "/acrescimo").param("acrescimo", "0")
                        .param("voltar", "inicio").with(csrf()))
                .andExpect(redirectedUrl("/painel"))
                .andExpect(flash().attribute("sucesso", "Acréscimo de Pessoa Nova salvo: R$ 0,00."));
        mvc.perform(get("/painel")).andExpect(content().string(not(containsString(AVISO))));
    }

    @Test
    void valorInvalidoOuPessoaDoSeedDaoErro() throws Exception {
        mvc.perform(post("/painel/pessoas/" + nova.getId() + "/acrescimo").param("acrescimo", "abc")
                        .with(csrf()))
                .andExpect(redirectedUrl("/painel/pessoas/" + nova.getId() + "/editar"))
                .andExpect(flash().attributeExists("erro"));
        Long seed = pessoas.findAll().stream().filter(p -> p.getNome().equals("Adulto 2")).findFirst()
                .orElseThrow().getId();
        mvc.perform(post("/painel/pessoas/" + seed + "/acrescimo").param("acrescimo", "100").with(csrf()))
                .andExpect(flash().attribute("erro", "O acréscimo é só para quem entrou depois do fechamento da lista."));
        assertNull(pessoas.findById(nova.getId()).orElseThrow().getAcrescimo());
    }

    @Test
    void formularioDaPessoaMostraOCampoSoParaQuemEntrouTarde() throws Exception {
        mvc.perform(get("/painel/pessoas/" + nova.getId() + "/editar"))
                .andExpect(content().string(containsString("Acréscimo cobrado pelo anfitrião")));
        Long seed = pessoas.findAll().stream().filter(p -> p.getNome().equals("Adulto 2")).findFirst()
                .orElseThrow().getId();
        mvc.perform(get("/painel/pessoas/" + seed + "/editar"))
                .andExpect(content().string(not(containsString("Acréscimo cobrado pelo anfitrião"))));
    }

    @Test
    void formularioNaoMostraOCampoParaQuemNaoPaga() throws Exception {
        Pessoa bebe = pessoaService.criar(new DadosPessoa("Bebê Novo", "", BigDecimal.ZERO, null,
                LocalDate.of(2027, 3, 1), true, null));
        mvc.perform(get("/painel/pessoas/" + bebe.getId() + "/editar"))
                .andExpect(content().string(not(containsString("Acréscimo cobrado pelo anfitrião"))));
    }
}
