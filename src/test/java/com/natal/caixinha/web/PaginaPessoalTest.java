package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.pessoa.DadosPessoa;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A página pessoal é pública: nenhum destes testes faz login. */
@SpringBootTest(properties = {BancoDeTeste.URL, "app.admin.senha=senha-de-teste-123"})
@AutoConfigureMockMvc
@Transactional
class PaginaPessoalTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PessoaService pessoas;
    @Autowired
    LinkService links;

    Pessoa criar(String nome, BigDecimal peso, Long responsavelId) {
        return pessoas.criar(new DadosPessoa(nome, "", peso, responsavelId, LocalDate.of(2026, 11, 20), true, null));
    }

    @Test
    void responsavelComDependenteAbreSemLogin() throws Exception {
        Pessoa mae = criar("Carla Souza", BigDecimal.ONE, null);
        criar("Lia", new BigDecimal("0.5"), mae.getId());
        String token = links.tokensAtivos().get(mae.getId());

        mvc.perform(get("/c/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(content().string(containsString("Olá, Carla Souza!")))
                .andExpect(content().string(containsString("Você + 1 dependente: Lia")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex, nofollow\">")));
    }

    @Test
    void responsavelSemDependentes() throws Exception {
        Pessoa ana = criar("Ana", BigDecimal.ONE, null);
        mvc.perform(get("/c/" + links.tokensAtivos().get(ana.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Esta é a sua página na caixinha.")))
                .andExpect(content().string(not(containsString("dependente"))));
    }

    @Test
    void linkInexistenteOuSubstituidoDa404() throws Exception {
        Pessoa ana = criar("Ana", BigDecimal.ONE, null);
        String antigo = links.tokensAtivos().get(ana.getId());
        links.gerarOutro(ana.getId());

        mvc.perform(get("/c/" + antigo))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Link não encontrado")))
                .andExpect(content().string(not(containsString("Ana"))));
        mvc.perform(get("/c/nao-existe")).andExpect(status().isNotFound());
        mvc.perform(get("/c/" + "x".repeat(200))).andExpect(status().isNotFound());
    }
}
