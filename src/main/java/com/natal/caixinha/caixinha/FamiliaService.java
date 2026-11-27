package com.natal.caixinha.caixinha;

import com.natal.caixinha.caixinha.CalculadoraCaixinha.Situacao;
import com.natal.caixinha.lancamento.Lancamento;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.pessoa.PessoaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.math.BigDecimal.ZERO;

/**
 * A família de um link pessoal (o responsável e os dependentes ativos), pronta para a página:
 * situação de cada pagante, valor do PIX, estado do mês, carnê e extrato.
 */
@Service
public class FamiliaService {

    public enum EstadoDoMes { SEM_VALOR, PAGO, PENDENTE, ATRASADO }

    public record Membro(Pessoa pessoa, Situacao situacao) {
    }

    /**
     * @param pagantes   responsável (se paga) e dependentes de peso maior que zero, responsável primeiro
     * @param cotaFamilia valor do mês atual somado entre os pagantes (cota + parcela de acréscimo)
     * @param valorPix   quanto a família precisa depositar para ficar em dia, já com o mês atual
     * @param numeroMes  posição do mês atual na caixinha (1 = primeiro mês)
     * @param extrato    depósitos, créditos e devoluções da família, do mais novo ao mais antigo
     * @param acrescimo  soma dos acréscimos de quem entrou tarde (0 se ninguém)
     */
    public record Familia(Pessoa responsavel, List<Pessoa> dependentes, List<Membro> pagantes,
                          BigDecimal cotaFamilia, BigDecimal valorPix, EstadoDoMes estado, boolean cotaFixada,
                          YearMonth mesAtual, int numeroMes, int totalMeses, List<Carne.Talao> carne,
                          List<Lancamento> extrato, BigDecimal acrescimo) {
    }

    private final CaixinhaRepository caixinhas;
    private final CaixinhaService caixinha;
    private final PessoaRepository pessoas;
    private final PessoaService pessoaService;
    private final LancamentoRepository lancamentos;
    private final Clock clock;

    public FamiliaService(CaixinhaRepository caixinhas, CaixinhaService caixinha, PessoaRepository pessoas,
                          PessoaService pessoaService, LancamentoRepository lancamentos, Clock clock) {
        this.caixinhas = caixinhas;
        this.caixinha = caixinha;
        this.pessoas = pessoas;
        this.pessoaService = pessoaService;
        this.lancamentos = lancamentos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Familia daFamilia(Long responsavelId) {
        Caixinha c = caixinhas.configuracao();
        CaixinhaService.Cota cota = caixinha.cota();
        LocalDate hoje = LocalDate.now(clock);
        Pessoa responsavel = pessoas.findById(responsavelId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa não encontrada: " + responsavelId));
        List<Pessoa> dependentes = pessoaService.dependentesDe(responsavelId);

        List<Pessoa> todos = new ArrayList<>();
        todos.add(responsavel);
        todos.addAll(dependentes);
        List<Lancamento> movimentos = lancamentos.movimentosDe(todos.stream().map(Pessoa::getId).toList());

        List<Membro> pagantes = new ArrayList<>();
        List<Carne.Membro> doCarne = new ArrayList<>();
        BigDecimal cotaFamilia = ZERO;
        BigDecimal valorPix = ZERO;
        BigDecimal acrescimo = ZERO;
        // "sem valor" olha a caixinha inteira, não o mês: um novembro zerado pelo desconto não apaga a cobrança
        BigDecimal totalFamilia = ZERO;
        boolean todosEmDia = true;
        boolean todosPagaramOMes = true;
        for (Pessoa p : todos) {
            if (!p.isPagante() || !p.isAtivo()) {
                continue;
            }
            BigDecimal cotaDaPessoa = CalculadoraCaixinha.cotaDaPessoa(cota.cota(), p.getPeso());
            List<Carne.Movimento> seus = movimentos.stream()
                    .filter(l -> l.getPessoa().getId().equals(p.getId()))
                    .map(l -> new Carne.Movimento(l.getData(), l.valorNoSaldo()))
                    .toList();
            BigDecimal guardado = seus.stream().map(Carne.Movimento::valor).reduce(ZERO, BigDecimal::add);
            List<BigDecimal> valores = CaixinhaService.valoresDaPessoa(c, cota.cota(), p);
            Situacao s = CalculadoraCaixinha.situacao(valores, guardado, hoje, c.getMesInicio(), c.getMesFim());
            pagantes.add(new Membro(p, s));
            doCarne.add(new Carne.Membro(cotaDaPessoa, seus, valores));
            cotaFamilia = cotaFamilia.add(s.cota());
            totalFamilia = totalFamilia.add(valores.stream().reduce(ZERO, BigDecimal::add));
            if (p.getAcrescimo() != null) {
                acrescimo = acrescimo.add(p.getAcrescimo());
            }
            valorPix = valorPix.add(s.valorParaFicarEmDia());
            todosEmDia &= s.emDia();
            todosPagaramOMes &= s.cotaDoMesPaga();
        }
        cotaFamilia = CalculadoraCaixinha.dinheiro(cotaFamilia);

        EstadoDoMes estado;
        if (totalFamilia.signum() <= 0) {
            estado = EstadoDoMes.SEM_VALOR;
        } else if (!todosEmDia) {
            estado = EstadoDoMes.ATRASADO;
        } else if (todosPagaramOMes) {
            estado = EstadoDoMes.PAGO;
        } else {
            estado = EstadoDoMes.PENDENTE;
        }
        if (estado == EstadoDoMes.SEM_VALOR || estado == EstadoDoMes.PAGO) {
            valorPix = ZERO;
        }

        YearMonth mesAtual = CalculadoraCaixinha.mesAtual(hoje, c.getMesInicio(), c.getMesFim());
        int numeroMes = (int) c.getMesInicio().until(mesAtual, java.time.temporal.ChronoUnit.MONTHS) + 1;
        List<Carne.Talao> carne = Carne.montar(totalFamilia.signum() > 0 ? doCarne : List.of(),
                c.getMesInicio(), c.getMesFim());
        List<Lancamento> extrato = new ArrayList<>(movimentos);
        Collections.reverse(extrato);

        return new Familia(responsavel, dependentes, pagantes, cotaFamilia, CalculadoraCaixinha.dinheiro(valorPix),
                estado, cota.fixada(), mesAtual, numeroMes, CalculadoraCaixinha.meses(c.getMesInicio(), c.getMesFim()),
                carne, extrato, CalculadoraCaixinha.dinheiro(acrescimo));
    }

    /**
     * Para cada pagante ativo de uma família com 2 ou mais pagantes: o id do responsável dela.
     * Serve para o painel levar ao "Pagamento da família" em vez do depósito individual.
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> responsavelDasFamiliasComVariosPagantes() {
        java.util.Map<Long, Long> responsavelDe = new java.util.HashMap<>();
        java.util.Map<Long, Integer> pagantesPorFamilia = new java.util.HashMap<>();
        for (Pessoa p : pessoas.findByAtivoTrueOrderByNomeAsc()) {
            if (p.isPagante()) {
                Long responsavel = p.getResponsavel() == null ? p.getId() : p.getResponsavel().getId();
                responsavelDe.put(p.getId(), responsavel);
                pagantesPorFamilia.merge(responsavel, 1, Integer::sum);
            }
        }
        responsavelDe.values().removeIf(r -> pagantesPorFamilia.get(r) < 2);
        return responsavelDe;
    }

    /** Divisão sugerida de um PIX da família em um depósito por pagante. */
    @Transactional(readOnly = true)
    public List<DivisaoFamilia.Parte> sugerirDivisao(Long responsavelId, BigDecimal total) {
        Familia f = daFamilia(responsavelId);
        List<DivisaoFamilia.Membro> membros = f.pagantes().stream()
                .map(m -> new DivisaoFamilia.Membro(m.pessoa().getId(), m.pessoa().getPeso(),
                        m.situacao().valorParaFicarEmDia(), m.pessoa().getId().equals(responsavelId)))
                .toList();
        return DivisaoFamilia.dividir(total, membros);
    }
}
