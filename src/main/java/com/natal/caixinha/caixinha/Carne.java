package com.natal.caixinha.caixinha;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static com.natal.caixinha.caixinha.CalculadoraCaixinha.dinheiro;

/**
 * O carnê da família: um talão por mês da caixinha. O talão k está pago quando cada pagante da família
 * guardou pelo menos k cotas dele, a mesma conta da situação (seção 3.2), então o carnê nunca diz
 * "pago" para alguém que deve.
 */
public final class Carne {

    /** Um lançamento que mexe no guardado da pessoa, com sinal (devolução é negativa), em ordem de data e id. */
    public record Movimento(LocalDate data, BigDecimal valor) {
    }

    /** Um pagante da família: a cota mensal dele e os movimentos. */
    public record Membro(BigDecimal cota, List<Movimento> movimentos) {
    }

    public enum Estado { PAGO, PARCIAL, ABERTO }

    /**
     * @param numero posição do mês na caixinha (1 = primeiro mês)
     * @param valor  cota da família no mês
     * @param falta  quanto ainda falta para esse talão ficar pago (0 quando pago)
     * @param pagoEm data do lançamento que completou o talão (só quando pago)
     */
    public record Talao(YearMonth mes, int numero, BigDecimal valor, Estado estado, BigDecimal falta,
                        LocalDate pagoEm) {
    }

    private Carne() {
    }

    public static List<Talao> montar(List<Membro> membros, YearMonth inicio, YearMonth fim) {
        int total = CalculadoraCaixinha.meses(inicio, fim);
        BigDecimal valor = dinheiro(membros.stream().map(Membro::cota).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<BigDecimal> guardados = membros.stream()
                .map(m -> m.movimentos().stream().map(Movimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add))
                .toList();

        List<Talao> taloes = new ArrayList<>();
        for (int k = 1; k <= total; k++) {
            YearMonth mes = inicio.plusMonths(k - 1);
            if (valor.signum() <= 0) {
                taloes.add(new Talao(mes, k, valor, Estado.ABERTO, dinheiro(BigDecimal.ZERO), null));
                continue;
            }
            BigDecimal falta = BigDecimal.ZERO;
            for (int i = 0; i < membros.size(); i++) {
                BigDecimal cota = membros.get(i).cota();
                BigDecimal devendo = cota.multiply(BigDecimal.valueOf(k)).subtract(guardados.get(i)).max(BigDecimal.ZERO);
                falta = falta.add(devendo.min(cota));
            }
            falta = dinheiro(falta);
            Estado estado = falta.signum() == 0 ? Estado.PAGO
                    : falta.compareTo(valor) >= 0 ? Estado.ABERTO : Estado.PARCIAL;
            LocalDate pagoEm = estado == Estado.PAGO ? dataDoCarimbo(membros, k) : null;
            taloes.add(new Talao(mes, k, valor, estado, falta, pagoEm));
        }
        return taloes;
    }

    /** O mais tarde, entre os membros, do último momento em que a soma corrente alcançou k cotas. */
    private static LocalDate dataDoCarimbo(List<Membro> membros, int k) {
        LocalDate data = null;
        for (Membro m : membros) {
            BigDecimal alvo = m.cota().multiply(BigDecimal.valueOf(k));
            BigDecimal soma = BigDecimal.ZERO;
            LocalDate alcancou = null;
            for (Movimento mov : m.movimentos()) {
                BigDecimal antes = soma;
                soma = soma.add(mov.valor());
                if (antes.compareTo(alvo) < 0 && soma.compareTo(alvo) >= 0) {
                    alcancou = mov.data();
                }
            }
            if (alcancou != null && (data == null || alcancou.isAfter(data))) {
                data = alcancou;
            }
        }
        return data;
    }
}
