package com.natal.caixinha.cronograma;

import com.natal.caixinha.caixinha.Caixinha;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.caixinha.CaixinhaService;
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
     */
    public record Visao(LocalDate dataFesta, List<Cronograma.Etapa> etapas,
                        Optional<Cronograma.Verificacao> verificacao, boolean semAluguel) {
    }

    private final CaixinhaRepository caixinhas;
    private final CaixinhaService caixinha;
    private final ItemOrcamentoRepository itens;
    private final Clock clock;

    public CronogramaService(CaixinhaRepository caixinhas, CaixinhaService caixinha, ItemOrcamentoRepository itens,
                             Clock clock) {
        this.caixinhas = caixinhas;
        this.caixinha = caixinha;
        this.itens = itens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Visao visao() {
        Caixinha c = caixinhas.configuracao();
        List<Cronograma.Etapa> etapas = etapas(c);
        BigDecimal aluguel = aluguel();
        Optional<Cronograma.Verificacao> verificacao = etapas.stream().filter(Cronograma.Etapa::reserva).findFirst()
                .filter(e -> aluguel.signum() > 0)
                .map(e -> {
                    CaixinhaService.Resumo r = caixinha.resumo();
                    BigDecimal extras = r.rendimentos().add(r.doacoes()).subtract(r.gastos());
                    return Cronograma.verificar(c.getMesInicio(), e.mes(), r.cota().arrecadacaoMensal(), extras,
                            aluguel);
                });
        return new Visao(c.getDataFesta(), etapas, verificacao, aluguel.signum() == 0);
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
