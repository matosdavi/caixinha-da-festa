package com.natal.caixinha.pessoa;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.link.LinkAcesso;
import com.natal.caixinha.link.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = BancoDeTeste.URL)
@Transactional
class PessoaServiceTest {

    static final BigDecimal INTEIRA = new BigDecimal("1");
    static final BigDecimal MEIA = new BigDecimal("0.5");

    @Autowired
    PessoaService servico;
    @Autowired
    LinkService links;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    JdbcTemplate jdbc;

    static DadosPessoa dados(String nome, BigDecimal peso, Long responsavelId) {
        return new DadosPessoa(nome, "", peso, responsavelId, LocalDate.of(2026, 11, 20), true, null);
    }

    @Test
    void responsavelNovoJaGanhaLink() {
        Pessoa ana = servico.criar(new DadosPessoa("  Ana   Maria ", "(11) 99999-8888", INTEIRA, null,
                null, true, " prima "));
        assertEquals("Ana Maria", ana.getNome());
        assertEquals("5511999998888", ana.getTelefone());
        assertEquals("prima", ana.getObservacao());
        assertNotNull(ana.getDataEntrada(), "sem data: hoje");

        String token = links.tokensAtivos().get(ana.getId());
        assertNotNull(token);
        assertEquals(22, token.length(), "128 bits em base64url");
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
        assertTrue(links.abrir(token).isPresent());
    }

    @Test
    void dependenteNaoGanhaLinkProprio() {
        Pessoa pai = servico.criar(dados("Pai", INTEIRA, null));
        Pessoa filha = servico.criar(dados("Filha", MEIA, pai.getId()));
        assertNull(links.tokensAtivos().get(filha.getId()));
        assertEquals(1, servico.dependentesDe(pai.getId()).size());
        assertThrows(RegraException.class, () -> links.gerarOutro(filha.getId()));
    }

    @Test
    void gerarOutroLinkDesativaOAntigo() {
        Pessoa ana = servico.criar(dados("Ana", INTEIRA, null));
        String antigo = links.tokensAtivos().get(ana.getId());

        LinkAcesso novo = links.gerarOutro(ana.getId());

        assertNotEquals(antigo, novo.getToken());
        assertTrue(links.abrir(antigo).isEmpty(), "o link antigo para de funcionar");
        assertTrue(links.abrir(novo.getToken()).isPresent());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from link_acesso where ativo and pessoa_id = ?", Integer.class, ana.getId()));
    }

    @Test
    void virarDependenteDesativaOLinkEVoltarCriaOutro() {
        Pessoa pai = servico.criar(dados("Pai", INTEIRA, null));
        Pessoa filho = servico.criar(dados("Filho", INTEIRA, null));
        String linkDoFilho = links.tokensAtivos().get(filho.getId());

        servico.atualizar(filho.getId(), dados("Filho", MEIA, pai.getId()));
        assertTrue(links.abrir(linkDoFilho).isEmpty());

        servico.atualizar(filho.getId(), dados("Filho", INTEIRA, null));
        String novo = links.tokensAtivos().get(filho.getId());
        assertNotNull(novo);
        assertNotEquals(linkDoFilho, novo);
    }

    @Test
    void soUmNivelDeResponsavel() {
        Pessoa avo = servico.criar(dados("Avó", INTEIRA, null));
        Pessoa mae = servico.criar(dados("Mãe", INTEIRA, null));
        Pessoa neto = servico.criar(dados("Neto", MEIA, mae.getId()));

        // dependente não pode ser responsável
        assertThrows(RegraException.class, () -> servico.criar(dados("Bisneto", MEIA, neto.getId())));
        // responsável (com dependentes) não pode virar dependente
        assertThrows(RegraException.class, () -> servico.atualizar(mae.getId(), dados("Mãe", INTEIRA, avo.getId())));
        // ninguém é responsável por si mesmo
        assertThrows(RegraException.class, () -> servico.atualizar(avo.getId(), dados("Avó", INTEIRA, avo.getId())));
        // dependente não aparece como opção de responsável
        assertTrue(servico.possiveisResponsaveis(null).stream().noneMatch(p -> p.getId().equals(neto.getId())));
    }

    @Test
    void validaNomePesoETelefone() {
        assertThrows(RegraException.class, () -> servico.criar(dados("   ", INTEIRA, null)));
        assertThrows(RegraException.class, () -> servico.criar(dados("X".repeat(81), INTEIRA, null)));
        assertThrows(RegraException.class, () -> servico.criar(dados("Ana", new BigDecimal("0.75"), null)));
        assertThrows(RegraException.class, () -> servico.criar(dados("Ana", null, null)));
        assertThrows(RegraException.class, () -> servico.criar(
                new DadosPessoa("Ana", "1234", INTEIRA, null, null, true, null)));
        assertEquals(0, new BigDecimal("0.50").compareTo(servico.criar(dados("Teen", new BigDecimal("0.500"), null)).getPeso()));
    }

    @Test
    void naoDesativaResponsavelComDependentesAtivos() {
        Pessoa pai = servico.criar(dados("Pai", INTEIRA, null));
        servico.criar(dados("Filha", MEIA, pai.getId()));
        assertThrows(RegraException.class, () -> servico.atualizar(pai.getId(),
                new DadosPessoa("Pai", "", INTEIRA, null, LocalDate.of(2026, 11, 20), false, null)));
    }

    @Test
    void desativarResponsavelDesativaOLink() {
        Pessoa ana = servico.criar(dados("Ana", INTEIRA, null));
        String token = links.tokensAtivos().get(ana.getId());
        servico.atualizar(ana.getId(), new DadosPessoa("Ana", "", INTEIRA, null, LocalDate.of(2026, 11, 20), false, null));
        assertTrue(links.abrir(token).isEmpty());
        assertThrows(RegraException.class, () -> links.gerarOutro(ana.getId()));
    }

    @Test
    void excluirSoSemLancamentosESemDependentes() {
        Pessoa pai = servico.criar(dados("Pai", INTEIRA, null));
        Pessoa filha = servico.criar(dados("Filha", MEIA, pai.getId()));
        assertThrows(RegraException.class, () -> servico.excluir(pai.getId()), "tem dependente");

        Pessoa ana = servico.criar(dados("Ana", INTEIRA, null));
        jdbc.update("insert into lancamento (tipo, pessoa_id, valor, data) values ('DEPOSITO', ?, 92.00, DATE '2026-12-10')",
                ana.getId());
        assertThrows(RegraException.class, () -> servico.excluir(ana.getId()), "tem lançamento");

        servico.excluir(filha.getId());
        servico.excluir(pai.getId());
        assertFalse(pessoas.existsById(pai.getId()));
    }

    @Test
    void gerarLinksQueFaltamParaOsResponsaveisDoSeed() {
        // o seed (V2) cria 11 responsáveis ativos sem link; adolescente e bebê são dependentes
        int criados = links.gerarFaltantes();
        assertEquals(11, criados);
        assertEquals(0, links.gerarFaltantes(), "rodar de novo não duplica");
    }
}
