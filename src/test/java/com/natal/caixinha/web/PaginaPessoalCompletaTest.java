package com.natal.caixinha.web;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.RelogioDeTeste;
import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.OrcamentoService;
import com.natal.caixinha.orcamento.OrcamentoService.DadosItem;
import com.natal.caixinha.pessoa.DadosPessoa;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.pessoa.PessoaService;
import com.natal.caixinha.pix.ChavePix;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Página pessoal completa da família do seed ("Adulto 1", "Adolescente", "Bebê"), em 15/03/2027, sem login. */
@SpringBootTest(properties = {BancoDeTeste.URL, "app.admin.senha=senha-de-teste-123"})
@AutoConfigureMockMvc
@Import(RelogioDeTeste.class)
@Transactional
class PaginaPessoalCompletaTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    PessoaService pessoaService;
    @Autowired
    LinkService links;
    @Autowired
    OrcamentoService orcamento;
    @Autowired
    LancamentoService lancamentos;
    @Autowired
    CaixinhaService caixinha;

    Pessoa pessoa(String nome) {
        return pessoas.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow();
    }

    String link(String nome) {
        return "/c/" + links.garantirLink(pessoa(nome)).getToken();
    }

    void orcamentoDaSpec() {
        orcamento.salvar(null, new DadosItem(Categoria.ALUGUEL, "Chácara", new BigDecimal("6000"), null));
        orcamento.salvar(null, new DadosItem(Categoria.COMIDA, "Ceia", new BigDecimal("4400"), null));
    }

    void pix() {
        caixinha.salvarPix(ChavePix.Tipo.ALEATORIA, "123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", "Brasília");
    }

    void depositar(String nome, String valor, LocalDate data) {
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, pessoa(nome).getId(),
                new BigDecimal(valor), data, null, null, null));
    }

    void pagarMeses(int meses) {
        int[] dias = {8, 11, 9};
        for (int m = 1; m <= meses; m++) {
            depositar("Adulto 1", "92", LocalDate.of(2027, m, dias[m - 1]));
            depositar("Adolescente", "46", LocalDate.of(2027, m, dias[m - 1]));
        }
    }

    String pagina(String caminho) throws Exception {
        return mvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void mostraAProximaEtapaQuandoHaDataDaFesta() throws Exception {
        orcamentoDaSpec();
        assertFalse(pagina(link("Adulto 1")).contains("Próxima etapa"), "sem data, só etapas já feitas");
        caixinha.definirDataFesta(LocalDate.of(2027, 12, 20));
        assertTrue(pagina(link("Adulto 1")).contains("Próxima etapa: procurar o lugar, em maio."));
    }

    @Test
    void pendente() throws Exception {
        orcamentoDaSpec();
        pix();
        pagarMeses(2);
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("Oi, Adulto 1!"), html);
        assertTrue(html.contains("Esta página é sua, de Adolescente e de Bebê (não paga)."));
        assertTrue(html.contains("Mês 3 de 11"));
        assertTrue(html.contains("Cota de março"));
        assertTrue(html.contains("R$ 138,00"));
        assertTrue(html.contains("Ainda não recebemos."));
        assertTrue(html.contains("É R$ 92,00 de Adulto 1 e R$ 46,00 de Adolescente (meia cota)."));
        assertTrue(html.contains("Copiar PIX de R$ 138,00"));
        assertTrue(html.contains("5406138.00"));
        assertTrue(html.contains("O PIX vai para <strong>FULANO DE TAL</strong>"));
        assertTrue(html.contains("Pagar pelo QR Code"));
        assertTrue(html.contains("aria-label=\"QR Code do PIX\""));
        assertTrue(html.contains("Pix Copia e Cola"));
        assertTrue(html.contains("(valor estimado)"));
    }

    @Test
    void pendenteQuandoSoParteDaFamiliaPagou() throws Exception {
        orcamentoDaSpec();
        pix();
        pagarMeses(2);
        depositar("Adulto 1", "92", LocalDate.of(2027, 3, 9));
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("Já recebemos uma parte."), html);
        assertTrue(html.contains("Falta R$ 46,00 de Adolescente (meia cota)."));
        assertTrue(html.contains("Copiar PIX de R$ 46,00"));
        assertFalse(html.contains("Ainda não recebemos."));
    }

    @Test
    void atrasadoSemTomDeCobranca() throws Exception {
        orcamentoDaSpec();
        pix();
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("Para ficar em dia: R$ 414,00"), html);
        assertTrue(html.contains("Copiar PIX de R$ 414,00"));
        String minusculo = html.toLowerCase();
        assertFalse(minusculo.contains("atras"));
        assertFalse(minusculo.contains("devendo"));
        assertFalse(minusculo.contains("cobran"));
    }

    @Test
    void pago() throws Exception {
        orcamentoDaSpec();
        pix();
        pagarMeses(3);
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("Cota de março paga"), html);
        assertFalse(html.contains("Copiar PIX"));
        assertFalse(html.contains("br.gov.bcb.pix"));
    }

    @Test
    void semChavePix() throws Exception {
        orcamentoDaSpec();
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("O PIX aparece aqui assim que o organizador cadastrar a chave."), html);
        assertFalse(html.contains("br.gov.bcb.pix"));
        assertFalse(html.contains("Copiar PIX"));
    }

    @Test
    void semOrcamento() throws Exception {
        pix();
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("Valor ainda não definido"), html);
        assertFalse(html.contains("Copiar PIX"));
        assertFalse(html.contains("class=\"carimbo\""));
    }

    @Test
    void carneComCarimbo() throws Exception {
        orcamentoDaSpec();
        pix();
        pagarMeses(2);
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("aria-label=\"janeiro: pago em 08/01\""), html);
        assertTrue(html.contains("aria-label=\"fevereiro: pago em 11/02\""));
        assertTrue(html.contains("aria-label=\"março: em aberto\""));
        assertTrue(html.contains("novembro"));
        assertTrue(html.contains("R$ 138<"));
    }

    @Test
    void carneParcial() throws Exception {
        orcamentoDaSpec();
        depositar("Adulto 1", "92", LocalDate.of(2027, 1, 8));
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("aria-label=\"janeiro: faltam R$ 46\""), html);
    }

    @Test
    void familiaToda() throws Exception {
        orcamentoDaSpec();
        pagarMeses(2);
        String html = pagina(link("Adulto 1"));
        assertTrue(html.contains("R$ 276,00 guardados"), html);
        assertTrue(html.contains("2% da meta de R$ 11.440,00."));
        assertTrue(html.contains("2 de 12 pagantes estão em dia."));
        assertTrue(html.contains("Data da festa: em votação"));
        assertTrue(html.contains("Pôr lembrete na agenda do celular"));
        assertTrue(html.contains("Todo dia 1º, às 9h, o celular avisa a hora de depositar."));
        assertTrue(html.contains("Alguma dúvida? Fale com quem organiza a caixinha no WhatsApp."));
    }

    @Test
    void naoMostraOutraFamilia() throws Exception {
        orcamentoDaSpec();
        caixinha.fixarCota();
        pix();
        Pessoa bruno = pessoaService.criar(new DadosPessoa("Bruno", "", BigDecimal.ONE, null,
                LocalDate.of(2026, 11, 20), true, null));
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEPOSITO, bruno.getId(), new BigDecimal("77"),
                LocalDate.of(2027, 1, 5), null, null, null));
        depositar("Adulto 1", "92", LocalDate.of(2027, 1, 8));
        String html = pagina(link("Adulto 1"));
        assertFalse(html.contains("Bruno"));
        assertFalse(html.contains("77,00"));
        assertTrue(html.contains("R$ 92,00"));
    }

    @Test
    void semRecursoExterno() throws Exception {
        orcamentoDaSpec();
        pix();
        String html = pagina(link("Adulto 1"))
                .replace("http://www.w3.org/2000/svg", "")
                .replace("http://www.thymeleaf.org", "");
        assertFalse(html.contains("http://"), html);
        assertFalse(html.contains("https://"), html);
        assertFalse(html.contains("fonts.googleapis"));
    }

    @Test
    void icsDaFamilia() throws Exception {
        orcamentoDaSpec();
        caixinha.fixarCota();
        String ics = mvc.perform(get(link("Adulto 1") + "/lembrete.ics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=utf-8"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", containsString("caixinha-da-festa.ics")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(ics.contains("COUNT=11"), ics);
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20270101"));
        assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:Caixinha da festa — depositar R$ 138\\,00"));
        assertTrue(ics.replace("\r\n ", "").contains("DESCRIPTION:http://localhost" + link("Adulto 1")));
    }

    @Test
    void icsComCotaEstimada() throws Exception {
        orcamentoDaSpec();
        String ics = mvc.perform(get(link("Adulto 1") + "/lembrete.ics"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:Caixinha da festa — depositar a cota do mês"), ics);
    }

    @Test
    void icsLinkInvalido() throws Exception {
        mvc.perform(get("/c/nao-existe/lembrete.ics"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void mostraOAcrescimoDeQuemEntrouTarde() throws Exception {
        orcamentoDaSpec();
        Pessoa nova = pessoaService.criar(new com.natal.caixinha.pessoa.DadosPessoa("Nova", "", BigDecimal.ONE, null,
                LocalDate.of(2027, 3, 1), true, null));
        pessoaService.definirAcrescimo(nova.getId(), new BigDecimal("300"));
        assertTrue(pagina(link("Nova")).contains("Inclui R$ 300,00 de acréscimo do lugar, dividido até novembro."));
        assertFalse(pagina(link("Adulto 2")).contains("acréscimo do lugar"));
    }
}
