package com.natal.caixinha.link;

import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Links pessoais: um ativo por responsável. */
@Service
public class LinkService {

    private final LinkAcessoRepository links;
    private final PessoaRepository pessoas;
    private final Clock clock;

    public LinkService(LinkAcessoRepository links, PessoaRepository pessoas, Clock clock) {
        this.links = links;
        this.pessoas = pessoas;
        this.clock = clock;
    }

    /** Abre a página pessoal: só links ativos de responsáveis. */
    @Transactional(readOnly = true)
    public Optional<LinkAcesso> abrir(String token) {
        if (token == null || token.length() > 32) {
            return Optional.empty();
        }
        return links.buscarAtivo(token).filter(l -> l.getPessoa().isResponsavel());
    }

    /** Token do link ativo de cada pessoa (pessoaId → token), para o painel. */
    @Transactional(readOnly = true)
    public Map<Long, String> tokensAtivos() {
        Map<Long, String> mapa = new HashMap<>();
        for (LinkAcesso l : links.todosAtivos()) {
            mapa.put(l.getPessoa().getId(), l.getToken());
        }
        return mapa;
    }

    /** Cria o link do responsável, se ele ainda não tiver um ativo. */
    @Transactional
    public LinkAcesso garantirLink(Pessoa pessoa) {
        exigirResponsavel(pessoa);
        return links.findByPessoaIdAndAtivoTrue(pessoa.getId()).stream().findFirst()
                .orElseGet(() -> links.save(new LinkAcesso(pessoa, OffsetDateTime.now(clock))));
    }

    /** "Gerar outro link": o antigo para de funcionar na hora. */
    @Transactional
    public LinkAcesso gerarOutro(Long pessoaId) {
        Pessoa pessoa = pessoas.findById(pessoaId)
                .orElseThrow(() -> new RegraException("Pessoa não encontrada."));
        exigirResponsavel(pessoa);
        desativarLinks(pessoaId);
        return links.save(new LinkAcesso(pessoa, OffsetDateTime.now(clock)));
    }

    /** Gera o link de todos os responsáveis ativos que ainda não têm. Devolve quantos foram criados. */
    @Transactional
    public int gerarFaltantes() {
        Map<Long, String> existentes = tokensAtivos();
        int criados = 0;
        for (Pessoa p : pessoas.findByAtivoTrueOrderByNomeAsc()) {
            if (p.isResponsavel() && !existentes.containsKey(p.getId())) {
                links.save(new LinkAcesso(p, OffsetDateTime.now(clock)));
                criados++;
            }
        }
        return criados;
    }

    @Transactional
    public void desativarLinks(Long pessoaId) {
        links.findByPessoaIdAndAtivoTrue(pessoaId).forEach(LinkAcesso::desativar);
        links.flush();
    }

    @Transactional
    public void apagarLinks(Long pessoaId) {
        links.deleteByPessoaId(pessoaId);
    }

    private static void exigirResponsavel(Pessoa pessoa) {
        if (!pessoa.isAtivo()) {
            throw new RegraException(pessoa.getNome() + " está inativa: não recebe link.");
        }
        if (!pessoa.isResponsavel()) {
            throw new RegraException(pessoa.getNome() + " é dependente: o link é o do responsável ("
                    + pessoa.getResponsavel().getNome() + ").");
        }
    }
}
