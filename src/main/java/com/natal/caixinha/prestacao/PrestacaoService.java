package com.natal.caixinha.prestacao;

import com.natal.caixinha.caixinha.Caixinha;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.Lancamento;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.pessoa.PessoaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prestação de contas (seção 3.7): sobra final, a parte de cada pessoa, a escolha da família e o lançamento das
 * devoluções da sobra (inclusive o crédito para o ano seguinte, lançado como DEVOLUCAO para o saldo fechar em zero).
 * Os lançamentos da sobra ficam fora da conta, para as partes não mudarem enquanto o organizador lança.
 */
@Service
public class PrestacaoService {

    static final String SOBRA = "Sobra da festa";
    static final String CREDITO = "Crédito para ";
    static final int DIAS_PARA_ESCOLHER = 7;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * @param escolha a escolha gravada (nula se ninguém escolheu)
     * @param efetiva o que vale: a escolha, ou CREDITO para quem não escolheu
     * @param lancado a parte já foi lançada como devolução da sobra
     */
    public record Linha(Pessoa pessoa, BigDecimal guardado, BigDecimal parte, OpcaoSobra escolha, OpcaoSobra efetiva,
                        boolean lancado) {
    }

    public record Visao(boolean disponivel, LocalDate dataFesta, LocalDate prazo, boolean prazoPassou,
                        BigDecimal entradas, Map<Categoria, BigDecimal> gastosPorCategoria, BigDecimal gastos,
                        BigDecimal devolucoes, BigDecimal sobra, List<Linha> linhas, BigDecimal saldo) {
    }

    public record DaFamilia(BigDecimal parte, OpcaoSobra escolha, boolean podeEscolher) {
    }

    private final CaixinhaRepository caixinhas;
    private final LancamentoRepository lancamentoRepository;
    private final LancamentoService lancamentos;
    private final PessoaRepository pessoas;
    private final PessoaService pessoaService;
    private final EscolhaSobraRepository escolhas;
    private final Clock clock;

    public PrestacaoService(CaixinhaRepository caixinhas, LancamentoRepository lancamentoRepository,
                            LancamentoService lancamentos, PessoaRepository pessoas, PessoaService pessoaService,
                            EscolhaSobraRepository escolhas, Clock clock) {
        this.caixinhas = caixinhas;
        this.lancamentoRepository = lancamentoRepository;
        this.lancamentos = lancamentos;
        this.pessoas = pessoas;
        this.pessoaService = pessoaService;
        this.escolhas = escolhas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Visao visao() {
        Caixinha c = caixinhas.configuracao();
        LocalDate festa = c.getDataFesta();
        LocalDate prazo = festa == null ? null : festa.plusDays(DIAS_PARA_ESCOLHER);
        List<Lancamento> todos = lancamentoRepository.findAll();

        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        BigDecimal devolucoes = BigDecimal.ZERO;
        BigDecimal saidasDaSobra = BigDecimal.ZERO;
        Map<Categoria, BigDecimal> porCategoria = new EnumMap<>(Categoria.class);
        Map<Long, BigDecimal> guardado = new HashMap<>();
        Set<Long> lancados = new HashSet<>();
        for (Lancamento l : todos) {
            boolean daSobra = daSobra(l);
            switch (l.getTipo()) {
                case DEPOSITO, CREDITO_ANO_ANTERIOR, RENDIMENTO, DOACAO -> entradas = entradas.add(l.getValor());
                case GASTO -> {
                    gastos = gastos.add(l.getValor());
                    porCategoria.merge(l.getCategoria(), l.getValor(), BigDecimal::add);
                }
                case DEVOLUCAO -> {
                    if (daSobra) {
                        saidasDaSobra = saidasDaSobra.add(l.getValor());
                        lancados.add(l.getPessoa().getId());
                    } else {
                        devolucoes = devolucoes.add(l.getValor());
                    }
                }
            }
            if (l.getPessoa() != null && !daSobra && l.getTipo() != TipoLancamento.GASTO) {
                guardado.merge(l.getPessoa().getId(), l.valorNoSaldo(), BigDecimal::add);
            }
        }
        BigDecimal sobra = dinheiro(entradas.subtract(gastos).subtract(devolucoes));

        List<Pessoa> comGuardado = pessoas.findAll().stream()
                .filter(p -> guardado.getOrDefault(p.getId(), BigDecimal.ZERO).signum() > 0)
                .sorted(Comparator.comparing(Pessoa::getNome))
                .toList();
        Map<Long, BigDecimal> partes = Partes.dividir(sobra, comGuardado.stream()
                .map(p -> new Partes.Guardado(p.getId(), guardado.get(p.getId()))).toList());
        Map<Long, OpcaoSobra> escolhidas = new HashMap<>();
        escolhas.findAll().forEach(e -> escolhidas.put(e.getPessoaId(), e.getOpcao()));

        List<Linha> linhas = new ArrayList<>();
        for (Pessoa p : comGuardado) {
            OpcaoSobra escolha = escolhidas.get(p.getId());
            linhas.add(new Linha(p, dinheiro(guardado.get(p.getId())), partes.getOrDefault(p.getId(), zero()),
                    escolha, escolha == null ? OpcaoSobra.CREDITO : escolha, lancados.contains(p.getId())));
        }
        porCategoria.replaceAll((k, v) -> dinheiro(v));
        LocalDate hoje = LocalDate.now(clock);
        return new Visao(disponivel(festa, hoje), festa, prazo, prazo != null && hoje.isAfter(prazo),
                dinheiro(entradas), porCategoria, dinheiro(gastos), dinheiro(devolucoes), sobra, linhas,
                dinheiro(sobra.subtract(saidasDaSobra)));
    }

    @Transactional(readOnly = true)
    public DaFamilia daFamilia(Long responsavelId) {
        Visao v = visao();
        Set<Long> membros = membros(responsavelId);
        BigDecimal parte = v.linhas().stream().filter(l -> membros.contains(l.pessoa().getId()))
                .map(Linha::parte).reduce(zero(), BigDecimal::add);
        OpcaoSobra escolha = escolhas.findById(responsavelId).map(EscolhaSobra::getOpcao).orElse(null);
        return new DaFamilia(parte, escolha, v.disponivel() && !v.prazoPassou() && parte.signum() > 0);
    }

    /** A família escolhe uma vez e vale para o responsável e os dependentes; pode mudar até o prazo. */
    @Transactional
    public void escolherPelaFamilia(Long responsavelId, OpcaoSobra opcao) {
        Visao v = visao();
        if (!v.disponivel()) {
            throw new RegraException("A prestação de contas abre depois da festa.");
        }
        if (v.prazoPassou()) {
            throw new RegraException("A escolha ia até " + v.prazo().format(DATA) + ".");
        }
        for (Long id : membros(responsavelId)) {
            gravar(id, opcao);
        }
    }

    /** O organizador muda a escolha de alguém (por exemplo, quem não participa no ano seguinte → devolver). */
    @Transactional
    public void definirEscolha(Long pessoaId, OpcaoSobra opcao) {
        if (!visao().disponivel()) {
            throw new RegraException("A prestação de contas abre depois da festa.");
        }
        pessoaService.buscar(pessoaId);
        gravar(pessoaId, opcao);
    }

    /** Lança a parte da pessoa como DEVOLUCAO: "Sobra da festa" ou "Crédito para AAAA". */
    @Transactional
    public void lancar(Long pessoaId) {
        Visao v = visao();
        if (!v.disponivel() || !v.prazoPassou()) {
            throw new RegraException("Lance as partes depois do prazo da escolha ("
                    + (v.prazo() == null ? "depois da festa" : v.prazo().format(DATA)) + ").");
        }
        Linha l = v.linhas().stream().filter(x -> x.pessoa().getId().equals(pessoaId)).findFirst()
                .orElseThrow(() -> new RegraException("Não há parte para lançar."));
        if (l.lancado()) {
            throw new RegraException("A parte de " + l.pessoa().getNome() + " já foi lançada.");
        }
        if (l.parte().signum() <= 0) {
            throw new RegraException("Não há parte para lançar.");
        }
        String descricao = l.efetiva() == OpcaoSobra.DEVOLVER ? SOBRA
                : CREDITO + (caixinhas.configuracao().getMesFim().getYear() + 1);
        lancamentos.registrar(new DadosLancamento(TipoLancamento.DEVOLUCAO, pessoaId, l.parte(),
                LocalDate.now(clock), descricao, null, null));
    }

    /** Créditos para a caixinha do ano seguinte: "Nome;Valor", valores com vírgula (abre no Excel em pt-BR). */
    @Transactional(readOnly = true)
    public String csvCreditos() {
        StringBuilder csv = new StringBuilder("Nome;Valor\n");
        for (Linha l : visao().linhas()) {
            if (l.efetiva() == OpcaoSobra.CREDITO && l.parte().signum() > 0) {
                csv.append(l.pessoa().getNome().replace(";", ",")).append(';')
                        .append(l.parte().toPlainString().replace('.', ',')).append('\n');
            }
        }
        return csv.toString();
    }

    private Set<Long> membros(Long responsavelId) {
        Set<Long> ids = new HashSet<>();
        ids.add(responsavelId);
        pessoaService.dependentesDe(responsavelId).forEach(d -> ids.add(d.getId()));
        return ids;
    }

    private void gravar(Long pessoaId, OpcaoSobra opcao) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        escolhas.findById(pessoaId).ifPresentOrElse(e -> e.mudar(opcao, agora),
                () -> escolhas.save(new EscolhaSobra(pessoaId, opcao, agora)));
    }

    private static boolean daSobra(Lancamento l) {
        return l.getTipo() == TipoLancamento.DEVOLUCAO && l.getDescricao() != null
                && (l.getDescricao().startsWith(SOBRA) || l.getDescricao().startsWith(CREDITO));
    }

    private static boolean disponivel(LocalDate festa, LocalDate hoje) {
        return festa != null && hoje.isAfter(festa);
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
