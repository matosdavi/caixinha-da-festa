package com.natal.caixinha.cronograma;

import com.natal.caixinha.caixinha.Caixinha;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.lancamento.LancamentoRepository;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.orcamento.ItemOrcamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Junta configuração, orçamento e resumo para montar o {@link Cronograma}. */
@Service
public class CronogramaService {

    /**
     * @param verificacao vazia enquanto não há data da festa
     * @param semAluguel  o orçamento ainda não tem item de aluguel para conferir
     * @param lugarPago   os gastos de aluguel já lançados cobrem o aluguel do orçamento
     */
    public record Visao(LocalDate dataFesta, List<Cronograma.Etapa> etapas,
                        Optional<Cronograma.Verificacao> verificacao, boolean semAluguel, boolean lugarPago) {
    }

    private final CaixinhaRepository caixinhas;
    private final CaixinhaService caixinha;
    private final ItemOrcamentoRepository itens;
    private final LancamentoRepository lancamentos;
    private final Clock clock;

    public CronogramaService(CaixinhaRepository caixinhas, CaixinhaService caixinha, ItemOrcamentoRepository itens,
                             LancamentoRepository lancamentos, Clock clock) {
        this.caixinhas = caixinhas;
        this.caixinha = caixinha;
        this.itens = itens;
        this.lancamentos = lancamentos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Visao visao() {
        Caixinha c = caixinhas.configuracao();
        List<Cronograma.Etapa> etapas = etapas(c);
        BigDecimal aluguel = aluguel();
        BigDecimal aluguelPago = lancamentos.somaPorTipoECategoria(TipoLancamento.GASTO, Categoria.ALUGUEL);
        Optional<Cronograma.Verificacao> verificacao = etapas.stream().filter(Cronograma.Etapa::reserva).findFirst()
                .filter(e -> aluguel.signum() > 0)
                .map(e -> {
                    CaixinhaService.Resumo r = caixinha.resumo();
                    // o que já foi pago do aluguel sai do próprio aluguel, não do previsto: senão contaria duas vezes
                    BigDecimal outrosGastos = r.gastos().subtract(aluguelPago);
                    BigDecimal extras = r.rendimentos().add(r.doacoes()).subtract(outrosGastos);
                    return Cronograma.verificar(c.getMesInicio(), e.mes(), r.cota().arrecadacaoMensal(), extras,
                            aluguel);
                });
        boolean lugarPago = aluguel.signum() > 0 && aluguelPago.compareTo(aluguel) >= 0;
        return new Visao(c.getDataFesta(), etapas, verificacao, aluguel.signum() == 0, lugarPago);
    }

    @Transactional(readOnly = true)
    public Optional<Cronograma.Etapa> proximaEtapa() {
        return Cronograma.proxima(etapas(caixinhas.configuracao()));
    }

    @Transactional
    public void definirDataFesta(LocalDate data) {
        caixinha.definirDataFesta(data);
    }

    private List<Cronograma.Etapa> etapas(Caixinha c) {
        return Cronograma.etapas(new Cronograma.Dados(c.getDataFesta(), c.getMesInicio(), c.getMesFim(),
                c.getDataFechamentoLista(), LocalDate.now(clock)));
    }

    /** O aluguel é pago inteiro na reserva: vale o valor real quando já existe, senão o previsto. */
    private BigDecimal aluguel() {
        return itens.findAll().stream()
                .filter(i -> i.getCategoria() == Categoria.ALUGUEL)
                .map(i -> i.getValorReal() != null ? i.getValorReal() : i.getValorPrevisto())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
