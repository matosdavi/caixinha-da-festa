package com.natal.caixinha.caixinha;

import com.natal.caixinha.caixinha.CalculadoraCaixinha.Situacao;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.ItemOrcamentoRepository;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Junta configuração, orçamento, pessoas e lançamentos e passa tudo pela {@link CalculadoraCaixinha}.
 * Nada aqui é guardado: o resumo é recalculado a cada pedido.
 */
@Service
public class CaixinhaService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CaixinhaRepository caixinhas;
    private final ItemOrcamentoRepository itens;
    private final PessoaRepository pessoas;
    private final LancamentoRepository lancamentos;
    private final Clock clock;

    public CaixinhaService(CaixinhaRepository caixinhas, ItemOrcamentoRepository itens, PessoaRepository pessoas,
                           LancamentoRepository lancamentos, Clock clock) {
        this.caixinhas = caixinhas;
        this.itens = itens;
        this.pessoas = pessoas;
        this.lancamentos = lancamentos;
        this.clock = clock;
    }

    /** Meta e cota: estimada até o organizador fixar, fixa depois. */
    public record Cota(BigDecimal somaPrevista, BigDecimal margem, BigDecimal meta, BigDecimal somaPesos,
                       int pagantes, int meses, BigDecimal cotaBruta, BigDecimal cotaEstimada,
                       boolean fixada, BigDecimal cota, BigDecimal arrecadacaoMensal, BigDecimal totalPrevisto) {
    }

    /** Situação de uma pessoa pagante e ativa. */
    public record SituacaoPessoa(Pessoa pessoa, Situacao situacao) {
    }

    public record Resumo(Caixinha caixinha, LocalDate hoje, YearMonth mesAtual, int mesesFechados, Cota cota,
                         boolean listaFechada, BigDecimal saldo, BigDecimal guardadoTotal, BigDecimal rendimentos,
                         BigDecimal doacoes, BigDecimal gastos, BigDecimal devolucoes, BigDecimal arrecadado,
                         int percentual, List<SituacaoPessoa> situacoes, int emDia) {

        public List<SituacaoPessoa> comDestaque() {
            return situacoes.stream()
                    .filter(s -> s.situacao().destaque() != CalculadoraCaixinha.Destaque.NENHUM)
                    .sorted((a, b) -> Integer.compare(b.situacao().mesesDeAtraso(), a.situacao().mesesDeAtraso()))
                    .toList();
        }

        public int pagantes() {
            return situacoes.size();
        }
    }

    @Transactional(readOnly = true)
    public Cota cota() {
        Caixinha c = caixinhas.configuracao();
        List<Pessoa> ativas = pessoas.findByAtivoTrueOrderByNomeAsc();
        return cota(c, ativas);
    }

    private Cota cota(Caixinha c, List<Pessoa> ativas) {
        BigDecimal somaPrevista = itens.somaPrevista().setScale(2);
        BigDecimal somaPesos = ativas.stream().map(Pessoa::getPeso).reduce(BigDecimal.ZERO, BigDecimal::add);
        int pagantes = (int) ativas.stream().filter(Pessoa::isPagante).count();
        int meses = CalculadoraCaixinha.meses(c.getMesInicio(), c.getMesFim());

        BigDecimal meta = CalculadoraCaixinha.meta(somaPrevista, c.getMargemImprevistos());
        BigDecimal bruta = CalculadoraCaixinha.cotaBruta(meta, somaPesos, meses);
        BigDecimal estimada = CalculadoraCaixinha.cotaBase(meta, somaPesos, meses);
        BigDecimal cota = c.isCotaFixada() ? c.getCotaBase().setScale(2) : estimada;
        BigDecimal mensal = CalculadoraCaixinha.arrecadacaoMensal(cota, somaPesos);
        return new Cota(somaPrevista, c.getMargemImprevistos(), meta, somaPesos, pagantes, meses, bruta, estimada,
                c.isCotaFixada(), cota, mensal, mensal.multiply(BigDecimal.valueOf(meses)));
    }

    @Transactional(readOnly = true)
    public Resumo resumo() {
        Caixinha c = caixinhas.configuracao();
        LocalDate hoje = LocalDate.now(clock);
        List<Pessoa> todas = pessoas.listarParaPainel();
        List<Pessoa> ativas = todas.stream().filter(Pessoa::isAtivo).toList();
        Cota cota = cota(c, ativas);

        Map<TipoLancamento, BigDecimal> totais = new EnumMap<>(TipoLancamento.class);
        for (Object[] linha : lancamentos.totaisPorTipo()) {
            totais.put((TipoLancamento) linha[0], (BigDecimal) linha[1]);
        }
        BigDecimal saldo = ZERO;
        for (var t : totais.entrySet()) {
            saldo = t.getKey().isEntrada() ? saldo.add(t.getValue()) : saldo.subtract(t.getValue());
        }

        Map<Long, BigDecimal> guardadoPorPessoa = guardadoPorPessoa();
        BigDecimal guardadoTotal = guardadoPorPessoa.values().stream().reduce(ZERO, BigDecimal::add);
        BigDecimal rendimentos = total(totais, TipoLancamento.RENDIMENTO);
        BigDecimal doacoes = total(totais, TipoLancamento.DOACAO);
        BigDecimal arrecadado = guardadoTotal.add(rendimentos).add(doacoes);
        int percentual = cota.meta().signum() == 0 ? 0
                : arrecadado.multiply(BigDecimal.valueOf(100)).divide(cota.meta(), 0, RoundingMode.DOWN).intValue();

        List<SituacaoPessoa> situacoes = new ArrayList<>();
        int emDia = 0;
        for (Pessoa p : ativas) {
            if (!p.isPagante()) {
                continue;
            }
            Situacao s = CalculadoraCaixinha.situacao(
                    CalculadoraCaixinha.cotaDaPessoa(cota.cota(), p.getPeso()),
                    guardadoPorPessoa.getOrDefault(p.getId(), ZERO), hoje, c.getMesInicio(), c.getMesFim());
            situacoes.add(new SituacaoPessoa(p, s));
            if (s.emDia()) {
                emDia++;
            }
        }

        return new Resumo(c, hoje, CalculadoraCaixinha.mesAtual(hoje, c.getMesInicio(), c.getMesFim()),
                CalculadoraCaixinha.mesesFechados(hoje, c.getMesInicio(), c.getMesFim()), cota,
                hoje.isAfter(c.getDataFechamentoLista()), saldo.setScale(2), guardadoTotal, rendimentos, doacoes,
                total(totais, TipoLancamento.GASTO), total(totais, TipoLancamento.DEVOLUCAO), arrecadado,
                percentual, situacoes, emDia);
    }

    /** Grava a chave PIX (validada pelo tipo), o nome e a cidade de quem recebe. */
    @Transactional
    public void salvarPix(com.natal.caixinha.pix.ChavePix.Tipo tipo, String chave, String nome, String cidade) {
        String normalizada = com.natal.caixinha.pix.ChavePix.normalizar(tipo, chave);
        String n = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        String cid = cidade == null ? "" : cidade.trim().replaceAll("\\s+", " ");
        // o PIX usa só letras e números: um nome feito só de símbolos sairia vazio e o banco recusaria
        if (com.natal.caixinha.pix.BrCode.limpar(n, com.natal.caixinha.pix.BrCode.MAX_NOME).isEmpty()) {
            throw new RegraException("Informe o nome de quem recebe o PIX, como aparece no banco.");
        }
        if (com.natal.caixinha.pix.BrCode.limpar(cid, com.natal.caixinha.pix.BrCode.MAX_CIDADE).isEmpty()) {
            throw new RegraException("Informe a cidade de quem recebe o PIX.");
        }
        caixinhas.configuracao().configurarPix(normalizada,
                n.substring(0, Math.min(n.length(), com.natal.caixinha.pix.BrCode.MAX_NOME)),
                cid.substring(0, Math.min(cid.length(), com.natal.caixinha.pix.BrCode.MAX_CIDADE)));
    }

    /** Quem recebe o PIX; vazio enquanto a chave não foi cadastrada. */
    @Transactional(readOnly = true)
    public java.util.Optional<com.natal.caixinha.pix.BrCode.Recebedor> recebedor() {
        Caixinha c = caixinhas.configuracao();
        if (c.getChavePix() == null || c.getChavePix().isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new com.natal.caixinha.pix.BrCode.Recebedor(
                c.getChavePix(), c.getNomeRecebedor(), c.getCidadeRecebedor()));
    }

    /**
     * Data da festa: depois do último depósito, até o fim do segundo mês seguinte (dezembro ou o réveillon
     * em janeiro). Nula volta para "em votação".
     */
    @Transactional
    public void definirDataFesta(LocalDate data) {
        Caixinha c = caixinhas.configuracao();
        if (data != null) {
            YearMonth mes = YearMonth.from(data);
            if (!mes.isAfter(c.getMesFim()) || mes.isAfter(c.getMesFim().plusMonths(2))) {
                throw new RegraException("A festa precisa ser depois do último depósito, em dezembro de "
                        + c.getMesFim().plusMonths(1).getYear() + " ou em janeiro de "
                        + c.getMesFim().plusMonths(2).getYear() + ".");
            }
        }
        c.definirDataFesta(data);
    }

    /** Fixa a cota estimada de agora como cota base. Só uma vez. */
    @Transactional
    public BigDecimal fixarCota() {
        Caixinha c = caixinhas.configuracao();
        if (c.isCotaFixada()) {
            throw new RegraException("A cota já está fixada em R$ " + c.getCotaBase() + " e não muda mais.");
        }
        Cota cota = cota(c, pessoas.findByAtivoTrueOrderByNomeAsc());
        if (cota.cotaEstimada().signum() <= 0) {
            throw new RegraException("Cadastre o orçamento antes de fixar a cota.");
        }
        c.fixarCota(cota.cotaEstimada());
        return cota.cotaEstimada();
    }

    /** guardado de cada pessoa = depósitos + crédito do ano anterior − devoluções. */
    private Map<Long, BigDecimal> guardadoPorPessoa() {
        Map<Long, BigDecimal> guardado = new HashMap<>();
        for (Object[] linha : lancamentos.totaisPorPessoa()) {
            Long pessoaId = (Long) linha[0];
            TipoLancamento tipo = (TipoLancamento) linha[1];
            BigDecimal valor = (BigDecimal) linha[2];
            BigDecimal comSinal = tipo == TipoLancamento.DEVOLUCAO ? valor.negate() : valor;
            guardado.merge(pessoaId, comSinal, BigDecimal::add);
        }
        guardado.replaceAll((id, v) -> v.setScale(2));
        return guardado;
    }

    private static BigDecimal total(Map<TipoLancamento, BigDecimal> totais, TipoLancamento tipo) {
        return totais.getOrDefault(tipo, ZERO).setScale(2);
    }
}
