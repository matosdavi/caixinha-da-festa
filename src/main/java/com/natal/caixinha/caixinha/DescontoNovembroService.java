package com.natal.caixinha.caixinha;

import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.orcamento.ItemOrcamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

/**
 * Desconto de novembro, o presente do amigo secreto (seção 3.4 da spec): metade da sobra prevista vira desconto
 * no último depósito, a outra metade fica de reserva. Confirmar, recalcular e desfazer só no penúltimo mês.
 */
@Service
public class DescontoNovembroService {

    /**
     * @param guardado            o que todos já guardaram (inclui quem saiu, já descontadas as devoluções)
     * @param falta               o que os pagantes ativos ainda devem até o último mês, sem desconto
     * @param arrecadacaoPrevista guardado + rendimentos + doações + falta
     * @param gastosPrevistos     itens do orçamento (valor real quando existe)
     * @param reserva             a parte da sobra que fica na caixinha
     * @param confirmado          desconto por peso já gravado (nulo se não há)
     * @param motivo              por que não dá para confirmar agora (nulo se dá)
     * @param podeDesfazer        há desconto confirmado e ainda é outubro (mesmo se a sobra recalculada sumiu)
     */
    public record Previa(BigDecimal guardado, BigDecimal rendimentos, BigDecimal doacoes, BigDecimal falta,
                         BigDecimal arrecadacaoPrevista, BigDecimal gastosPrevistos, BigDecimal sobra,
                         BigDecimal descontoPorPeso, BigDecimal reserva, BigDecimal somaPesos,
                         BigDecimal confirmado, boolean podeConfirmar, String motivo, boolean podeDesfazer) {
    }

    private final CaixinhaRepository caixinhas;
    private final CaixinhaService caixinha;
    private final ItemOrcamentoRepository itens;
    private final Clock clock;

    public DescontoNovembroService(CaixinhaRepository caixinhas, CaixinhaService caixinha,
                                   ItemOrcamentoRepository itens, Clock clock) {
        this.caixinhas = caixinhas;
        this.caixinha = caixinha;
        this.itens = itens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Previa previa() {
        Caixinha c = caixinhas.configuracao();
        CaixinhaService.Resumo r = caixinha.resumo();
        BigDecimal falta = BigDecimal.ZERO;
        for (CaixinhaService.SituacaoPessoa sp : r.situacoes()) {
            var p = sp.pessoa();
            // sem o desconto: é ele que estamos calculando
            BigDecimal total = CalculadoraCaixinha.valoresDosMeses(
                            CalculadoraCaixinha.cotaDaPessoa(r.cota().cota(), p.getPeso()),
                            p.getAcrescimo(), p.getAcrescimoDesde(), c.getMesInicio(), c.getMesFim())
                    .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            falta = falta.add(total.subtract(sp.situacao().guardado()).max(BigDecimal.ZERO));
        }
        falta = CalculadoraCaixinha.dinheiro(falta);
        BigDecimal arrecadacao = r.guardadoTotal().add(r.rendimentos()).add(r.doacoes()).add(falta);
        BigDecimal gastos = CalculadoraCaixinha.dinheiro(itens.findAll().stream()
                .map(i -> i.getValorReal() != null ? i.getValorReal() : i.getValorPrevisto())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal sobra = CalculadoraCaixinha.dinheiro(arrecadacao.subtract(gastos));
        BigDecimal somaPesos = r.cota().somaPesos();
        BigDecimal desconto = CalculadoraCaixinha.descontoPorPeso(sobra, somaPesos);
        BigDecimal reserva = CalculadoraCaixinha.dinheiro(sobra.subtract(desconto.multiply(somaPesos)).max(BigDecimal.ZERO));
        String motivo = motivo(c, sobra);
        return new Previa(r.guardadoTotal(), r.rendimentos(), r.doacoes(), falta,
                CalculadoraCaixinha.dinheiro(arrecadacao), gastos, sobra, desconto, reserva, somaPesos,
                c.getDescontoNovembroPorPeso(), motivo == null, motivo,
                c.getDescontoNovembroPorPeso() != null && foraDoMes(c) == null);
    }

    @Transactional
    public BigDecimal confirmar() {
        Previa p = previa();
        if (!p.podeConfirmar()) {
            throw new RegraException(p.motivo());
        }
        caixinhas.configuracao().definirDescontoNovembro(p.descontoPorPeso());
        return p.descontoPorPeso();
    }

    @Transactional
    public void desfazer() {
        String fora = foraDoMes(caixinhas.configuracao());
        if (fora != null) {
            throw new RegraException(fora);
        }
        caixinhas.configuracao().definirDescontoNovembro(null);
    }

    private String motivo(Caixinha c, BigDecimal sobra) {
        if (!c.isCotaFixada()) {
            return "Fixe a cota antes de calcular o desconto.";
        }
        String fora = foraDoMes(c);
        if (fora != null) {
            return fora;
        }
        return sobra.signum() <= 0 ? "Não há sobra prevista: sem desconto em novembro." : null;
    }

    /** O desconto se decide no penúltimo mês (outubro); antes ainda não, depois já está valendo. */
    private String foraDoMes(Caixinha c) {
        YearMonth hoje = YearMonth.now(clock);
        YearMonth penultimo = c.getMesFim().minusMonths(1);
        if (hoje.isBefore(penultimo)) {
            return "O desconto de novembro se confirma em outubro.";
        }
        if (hoje.isAfter(penultimo)) {
            return "O desconto já está valendo e não muda mais.";
        }
        return null;
    }
}
