package com.natal.caixinha.desistencia;

import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.cronograma.CronogramaService;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.lancamento.LancamentoService;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.ItemOrcamentoRepository;
import com.natal.caixinha.pessoa.DadosPessoa;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import com.natal.caixinha.pessoa.PessoaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;

/**
 * Desistência (seção 3.6 da spec, com o "sinal" trocado por "lugar pago": o lugar é pago inteiro na reserva).
 * Sugere quanto devolver e, ao confirmar, lança a DEVOLUCAO e tira a pessoa da caixinha, sem recalcular a
 * cota fixada dos outros.
 */
@Service
public class DesistenciaService {

    private static final Set<Categoria> VARIAVEIS =
            Set.of(Categoria.COMIDA, Categoria.BEBIDA, Categoria.GELO_CARVAO_DESCARTAVEIS);

    /**
     * @param parteVariavel parte da pessoa nos gastos variáveis (comida, bebida, gelo/carvão/descartáveis)
     * @param sugerido      tudo o que guardou (lugar não pago ou outra pessoa assume a vaga) ou a parte variável,
     *                      nunca mais do que guardou
     */
    public record Sugestao(Pessoa pessoa, BigDecimal guardado, boolean lugarPago, boolean assumeVaga,
                           BigDecimal parteVariavel, BigDecimal sugerido) {
    }

    private final PessoaRepository pessoas;
    private final PessoaService pessoaService;
    private final LancamentoRepository lancamentoRepository;
    private final LancamentoService lancamentos;
    private final ItemOrcamentoRepository itens;
    private final CronogramaService cronograma;

    public DesistenciaService(PessoaRepository pessoas, PessoaService pessoaService,
                              LancamentoRepository lancamentoRepository, LancamentoService lancamentos,
                              ItemOrcamentoRepository itens, CronogramaService cronograma) {
        this.pessoas = pessoas;
        this.pessoaService = pessoaService;
        this.lancamentoRepository = lancamentoRepository;
        this.lancamentos = lancamentos;
        this.itens = itens;
        this.cronograma = cronograma;
    }

    @Transactional(readOnly = true)
    public Sugestao sugerir(Long pessoaId, boolean assumeVaga) {
        Pessoa p = pessoaService.buscar(pessoaId);
        BigDecimal guardado = guardado(pessoaId);
        boolean lugarPago = cronograma.lugarPago();
        BigDecimal parte = parteVariavel(p).min(guardado);
        BigDecimal sugerido = !lugarPago || assumeVaga ? guardado : parte;
        return new Sugestao(p, guardado, lugarPago, assumeVaga, parte, sugerido);
    }

    @Transactional
    public void confirmar(Long pessoaId, BigDecimal valor, LocalDate data) {
        Pessoa p = pessoaService.buscar(pessoaId);
        if (!p.isAtivo()) {
            throw new RegraException(p.getNome() + " já está fora da caixinha.");
        }
        BigDecimal guardado = guardado(pessoaId);
        if (valor == null || valor.signum() < 0 || valor.compareTo(guardado) > 0) {
            throw new RegraException("A devolução vai de R$ 0,00 até o que a pessoa guardou ("
                    + Dinheiro.formatar(guardado) + ").");
        }
        // primeiro tira a pessoa: se a regra de responsável impedir, nada é lançado
        DadosPessoa d = DadosPessoa.de(p);
        pessoaService.atualizar(pessoaId, new DadosPessoa(d.nome(), d.telefone(), d.peso(), d.responsavelId(),
                d.dataEntrada(), false, d.observacao()));
        if (valor.signum() > 0) {
            lancamentos.registrar(new DadosLancamento(TipoLancamento.DEVOLUCAO, pessoaId, valor, data,
                    "Desistência", null, null));
        }
    }

    /** depósitos + crédito do ano anterior − devoluções */
    private BigDecimal guardado(Long pessoaId) {
        return lancamentoRepository.somaDaPessoa(pessoaId, TipoLancamento.DEPOSITO)
                .add(lancamentoRepository.somaDaPessoa(pessoaId, TipoLancamento.CREDITO_ANO_ANTERIOR))
                .subtract(lancamentoRepository.somaDaPessoa(pessoaId, TipoLancamento.DEVOLUCAO))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** gastos variáveis do orçamento (real quando existe) × peso ÷ soma dos pesos ativos, em centavos para baixo */
    private BigDecimal parteVariavel(Pessoa p) {
        BigDecimal variaveis = itens.findAll().stream()
                .filter(i -> VARIAVEIS.contains(i.getCategoria()))
                .map(i -> i.getValorReal() != null ? i.getValorReal() : i.getValorPrevisto())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal somaPesos = pessoas.findByAtivoTrueOrderByNomeAsc().stream()
                .map(Pessoa::getPeso).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!p.isAtivo()) {
            somaPesos = somaPesos.add(p.getPeso());
        }
        if (somaPesos.signum() == 0 || variaveis.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return variaveis.multiply(p.getPeso()).divide(somaPesos, 2, RoundingMode.DOWN);
    }
}
