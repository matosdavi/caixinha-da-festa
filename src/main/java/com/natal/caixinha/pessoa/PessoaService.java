package com.natal.caixinha.pessoa;

import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.link.LinkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Cadastro de pessoas e responsáveis.
 * <ul>
 *   <li>Responsável = pessoa sem responsável: recebe o próprio link e paga pelos dependentes.</li>
 *   <li>Só um nível: um dependente não tem dependentes, e o responsável dele não é dependente.</li>
 * </ul>
 */
@Service
public class PessoaService {

    static final int TAMANHO_NOME = 80;
    static final int TAMANHO_OBSERVACAO = 500;
    private static final Set<BigDecimal> PESOS = Set.of(
            new BigDecimal("1.00"), new BigDecimal("0.50"), new BigDecimal("0.00"));

    private final PessoaRepository pessoas;
    private final LinkService links;
    private final Clock clock;

    public PessoaService(PessoaRepository pessoas, LinkService links, Clock clock) {
        this.pessoas = pessoas;
        this.links = links;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Pessoa> listar() {
        return pessoas.listarParaPainel();
    }

    @Transactional(readOnly = true)
    public Pessoa buscar(Long id) {
        return pessoas.findById(id).orElseThrow(() -> new RegraException("Pessoa não encontrada."));
    }

    @Transactional(readOnly = true)
    public List<Pessoa> dependentesDe(Long responsavelId) {
        return pessoas.findByResponsavelIdOrderByNomeAsc(responsavelId).stream().filter(Pessoa::isAtivo).toList();
    }

    /** Quem pode ser escolhido como responsável de {@code pessoaId} (nulo = pessoa nova). */
    @Transactional(readOnly = true)
    public List<Pessoa> possiveisResponsaveis(Long pessoaId) {
        return pessoas.findByAtivoTrueOrderByNomeAsc().stream()
                .filter(Pessoa::isResponsavel)
                .filter(p -> !p.getId().equals(pessoaId))
                .toList();
    }

    @Transactional
    public Pessoa criar(DadosPessoa dados) {
        Pessoa pessoa = new Pessoa(null, null, null);
        aplicar(pessoa, dados);
        pessoas.save(pessoa);
        if (pessoa.isAtivo() && pessoa.isResponsavel()) {
            links.garantirLink(pessoa);
        }
        return pessoa;
    }

    @Transactional
    public Pessoa atualizar(Long id, DadosPessoa dados) {
        Pessoa pessoa = buscar(id);
        boolean tinhaLinkProprio = pessoa.isAtivo() && pessoa.isResponsavel();
        aplicar(pessoa, dados);
        boolean temLinkProprio = pessoa.isAtivo() && pessoa.isResponsavel();

        if (tinhaLinkProprio && !temLinkProprio) {
            // virou dependente ou saiu da lista: o link antigo para de funcionar
            links.desativarLinks(pessoa.getId());
        } else if (!tinhaLinkProprio && temLinkProprio) {
            links.garantirLink(pessoa);
        }
        return pessoa;
    }

    /**
     * Acréscimo do anfitrião para quem entrou depois do fechamento da lista (seção 3.5 da spec).
     * É dividido nos meses que faltam: a divisão começa no mês em que o valor passa a ser maior que zero
     * (limitado aos meses da caixinha) e não muda quando o valor é corrigido. Zero confirma "sem acréscimo".
     */
    @Transactional
    public void definirAcrescimo(Long pessoaId, BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new RegraException("Informe o acréscimo: R$ 0 se o anfitrião não cobrar nada.");
        }
        // mesmas regras dos outros valores: no máximo 2 casas e o limite do app (cabe no NUMERIC(12,2))
        valor = com.natal.caixinha.comum.Dinheiro.naoNegativo(valor, "Acréscimo");
        Pessoa pessoa = buscar(pessoaId);
        if (!pessoa.isPagante() || !pessoa.isAtivo()) {
            // quem não paga cota fica fora da situação e do carnê: o acréscimo nunca seria cobrado
            throw new RegraException("O acréscimo é só para quem paga cota e está ativo na caixinha.");
        }
        Object[] datas = pessoas.datasDaCaixinha().get(0);
        LocalDate fechamento = datas[0] instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) datas[0];
        java.time.YearMonth inicio = java.time.YearMonth.parse(datas[1].toString().trim());
        java.time.YearMonth fim = java.time.YearMonth.parse(datas[2].toString().trim());
        if (!pessoa.entrouTarde(fechamento)) {
            throw new RegraException("O acréscimo é só para quem entrou depois do fechamento da lista.");
        }
        BigDecimal novo = valor.setScale(2, java.math.RoundingMode.HALF_UP);
        java.time.YearMonth desde = null;
        if (novo.signum() > 0) {
            boolean jaTinha = pessoa.getAcrescimo() != null && pessoa.getAcrescimo().signum() > 0
                    && pessoa.getAcrescimoDesde() != null;
            if (jaTinha) {
                desde = pessoa.getAcrescimoDesde();
            } else {
                java.time.YearMonth hoje = java.time.YearMonth.now(clock);
                desde = hoje.isBefore(inicio) ? inicio : hoje.isAfter(fim) ? fim : hoje;
            }
        }
        pessoa.setAcrescimo(novo, desde);
    }

    /** Excluir só serve para corrigir cadastro. Quem já tem lançamentos sai pela desistência. */
    @Transactional
    public void excluir(Long id) {
        Pessoa pessoa = buscar(id);
        if (pessoas.temLancamentos(id)) {
            throw new RegraException(pessoa.getNome()
                    + " já tem lançamentos e não pode ser excluída. Desative a pessoa (ou registre a desistência).");
        }
        if (pessoas.existsByResponsavelId(id)) {
            throw new RegraException(pessoa.getNome()
                    + " é responsável por outras pessoas. Troque o responsável delas antes de excluir.");
        }
        links.apagarLinks(id);
        pessoas.delete(pessoa);
    }

    private void aplicar(Pessoa pessoa, DadosPessoa dados) {
        String nome = dados.nome() == null ? "" : dados.nome().strip().replaceAll("\\s+", " ");
        if (nome.isEmpty()) {
            throw new RegraException("Informe o nome.");
        }
        if (nome.length() > TAMANHO_NOME) {
            throw new RegraException("O nome pode ter no máximo " + TAMANHO_NOME + " caracteres.");
        }

        BigDecimal peso = dados.peso() == null ? null : PESOS.stream()
                .filter(p -> p.compareTo(dados.peso()) == 0).findFirst().orElse(null);
        if (peso == null) {
            throw new RegraException("O peso deve ser 1 (cota inteira), 0,5 (meia cota) ou 0 (não paga).");
        }

        Pessoa responsavel = null;
        if (dados.responsavelId() != null) {
            if (dados.responsavelId().equals(pessoa.getId())) {
                throw new RegraException("A pessoa não pode ser responsável por ela mesma.");
            }
            responsavel = buscar(dados.responsavelId());
            if (!responsavel.isResponsavel()) {
                throw new RegraException(responsavel.getNome() + " já é dependente de "
                        + responsavel.getResponsavel().getNome() + " e não pode ser responsável.");
            }
            if (!responsavel.isAtivo()) {
                throw new RegraException(responsavel.getNome() + " está inativa e não pode ser responsável.");
            }
            if (pessoa.getId() != null && pessoas.existsByResponsavelId(pessoa.getId())) {
                throw new RegraException(nome + " é responsável por outras pessoas e não pode virar dependente.");
            }
        }

        if (!dados.ativo() && pessoa.getId() != null && !dependentesDe(pessoa.getId()).isEmpty()) {
            throw new RegraException(nome + " é responsável por pessoas ativas. Troque o responsável delas antes.");
        }

        String observacao = dados.observacao() == null ? null : dados.observacao().strip();
        if (observacao != null && observacao.length() > TAMANHO_OBSERVACAO) {
            throw new RegraException("A observação pode ter no máximo " + TAMANHO_OBSERVACAO + " caracteres.");
        }

        pessoa.setNome(nome);
        pessoa.setTelefone(Telefones.normalizar(dados.telefone()));
        pessoa.setPeso(peso);
        pessoa.setResponsavel(responsavel);
        pessoa.setDataEntrada(dados.dataEntrada() != null ? dados.dataEntrada() : LocalDate.now(clock));
        pessoa.setAtivo(dados.ativo());
        pessoa.setObservacao(observacao == null || observacao.isEmpty() ? null : observacao);
    }
}
