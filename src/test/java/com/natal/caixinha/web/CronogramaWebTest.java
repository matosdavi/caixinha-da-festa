package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
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
class CronogramaWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    CaixinhaRepository caixinhas;
    @Autowired
    com.natal.caixinha.lancamento.LancamentoService lancamentos;

    void orcamento(String real) {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Casa", new BigDecimal("6000"),
                real == null ? null : new BigDecimal(real)));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void semDataMostraEmVotacao() throws Exception {
        mvc.perform(get("/painel/cronograma"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Data em votação")))
                .andExpect(content().string(containsString("Fechar a lista e fixar a cota")))
                .andExpect(content().string(containsString("href=\"/painel/cronograma\"")))
                .andExpect(content().string(not(containsString("Procurar o lugar"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void salvarADataMostraAsEtapasEAVerificacao() throws Exception {
        orcamento(null);
        mvc.perform(post("/painel/cronograma").param("dataFesta", "2027-12-20").with(csrf()))
                .andExpect(redirectedUrl("/painel/cronograma"))
                .andExpect(flash().attribute("sucesso", "Data da festa salva."));
        assertEquals(LocalDate.of(2027, 12, 20), caixinhas.configuracao().getDataFesta());
        mvc.perform(get("/painel/cronograma"))
                .andExpect(content().string(containsString("Procurar o lugar")))
                .andExpect(content().string(containsString("maio de 2027")))
                .andExpect(content().string(containsString("Dá para pagar")))
                .andExpect(content().string(containsString("value=\"2027-12-20\"")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void alertaQuandoNaoDaParaPagarOLugar() throws Exception {
        orcamento("6500");
        mvc.perform(post("/painel/cronograma").param("dataFesta", "2027-12-20").with(csrf()));
        mvc.perform(get("/painel/cronograma"))
                .andExpect(content().string(containsString("faltam R$ 152,00")))
                .andExpect(content().string(containsString("pague uma parte agora")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void depoisDeLancarOAluguelMostraLugarPago() throws Exception {
        orcamento(null);
        lancamentos.registrar(new com.natal.caixinha.lancamento.DadosLancamento(
                com.natal.caixinha.lancamento.TipoLancamento.GASTO, null, new BigDecimal("6000"),
                LocalDate.of(2027, 3, 10), "Airbnb", Categoria.ALUGUEL, null));
        mvc.perform(post("/painel/cronograma").param("dataFesta", "2027-12-20").with(csrf()));
        mvc.perform(get("/painel/cronograma"))
                .andExpect(content().string(containsString("✅ Lugar pago.")))
                .andExpect(content().string(not(containsString("faltam"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void dataForaDoPrazoOuInvalidaMostraErro() throws Exception {
        mvc.perform(post("/painel/cronograma").param("dataFesta", "2027-11-30").with(csrf()))
                .andExpect(redirectedUrl("/painel/cronograma"))
                .andExpect(flash().attribute("erro",
                        "A festa precisa ser depois do último depósito, em dezembro de 2027 ou em janeiro de 2028."));
        mvc.perform(post("/painel/cronograma").param("dataFesta", "20/12").with(csrf()))
                .andExpect(flash().attribute("erro", "Data inválida."));
        assertNull(caixinhas.configuracao().getDataFesta());
    }

    @Test
    @WithMockUser(roles = "ORGANIZADOR")
    void dataVaziaVoltaParaEmVotacao() throws Exception {
        mvc.perform(post("/painel/cronograma").param("dataFesta", "2027-12-20").with(csrf()));
        mvc.perform(post("/painel/cronograma").param("dataFesta", "").with(csrf()))
                .andExpect(flash().attribute("sucesso", "Data da festa apagada: em votação."));
        assertNull(caixinhas.configuracao().getDataFesta());
    }

    @Test
    void semLoginVaiParaEntrar() throws Exception {
        mvc.perform(get("/painel/cronograma")).andExpect(redirectedUrl("/entrar"));
    }
}
