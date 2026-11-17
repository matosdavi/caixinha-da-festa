package com.natal.caixinha.caixinha;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Todas as regras de cálculo da caixinha (seções 3.1 a 3.3 da especificação).
 * Classe pura: sem Spring, sem banco, sem relógio. Quem chama passa "hoje".
 * Dinheiro sempre em BigDecimal com 2 casas.
 */
public final class CalculadoraCaixinha {

    private static final BigDecimal DOIS = BigDecimal.valueOf(2);
    /** Precisão das divisões intermediárias, antes de qualquer arredondamento. */
    private static final MathContext PRECISAO = MathContext.DECIMAL128;

    private CalculadoraCaixinha() {
    }

    // ---------------------------------------------------------------- 3.1 meta e cota

    /** meta = soma dos valores previstos × (1 + margem). */
    public static BigDecimal meta(BigDecimal somaPrevista, BigDecimal margemImprevistos) {
        return dinheiro(somaPrevista.multiply(BigDecimal.ONE.add(margemImprevistos)));
    }

    /** Número de meses de depósito, de mesInicio até mesFim, inclusive. */
    public static int meses(YearMonth mesInicio, YearMonth mesFim) {
        return (int) ChronoUnit.MONTHS.between(mesInicio, mesFim) + 1;
    }

    /** cotaBruta = meta ÷ (somaPesos × meses), com 2 casas só para mostrar. Zero se ninguém paga. */
    public static BigDecimal cotaBruta(BigDecimal meta, BigDecimal somaPesos, int meses) {
        return dinheiro(cotaBrutaExata(meta, somaPesos, meses));
    }

    /**
     * Cota base: a cota bruta arredondada para cima até o próximo inteiro par
     * (assim a meia cota do adolescente também fica inteira). Um valor que já é par não sobe.
     */
    public static BigDecimal cotaBase(BigDecimal meta, BigDecimal somaPesos, int meses) {
        BigDecimal metade = cotaBrutaExata(meta, somaPesos, meses).divide(DOIS, PRECISAO);
        return dinheiro(metade.setScale(0, RoundingMode.CEILING).multiply(DOIS));
    }

    private static BigDecimal cotaBrutaExata(BigDecimal meta, BigDecimal somaPesos, int meses) {
        if (somaPesos.signum() <= 0 || meses <= 0 || meta.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return meta.divide(somaPesos.multiply(BigDecimal.valueOf(meses)), PRECISAO);
    }

    /** cotaDaPessoa = cota base × peso. */
    public static BigDecimal cotaDaPessoa(BigDecimal cotaBase, BigDecimal peso) {
        return dinheiro(cotaBase.multiply(peso));
    }

    /** Arrecadação de um mês com todos em dia = cota base × soma dos pesos. */
    public static BigDecimal arrecadacaoMensal(BigDecimal cotaBase, BigDecimal somaPesos) {
        return dinheiro(cotaBase.multiply(somaPesos));
    }

    // ---------------------------------------------------------------- 3.2 situação

    /** guardado = depósitos + crédito do ano anterior − devoluções. */
    public static BigDecimal guardado(BigDecimal depositos, BigDecimal creditoAnoAnterior, BigDecimal devolucoes) {
        return dinheiro(depositos.add(creditoAnoAnterior).subtract(devolucoes));
    }

    /**
     * Meses da caixinha que já terminaram em "hoje". Janeiro só conta como fechado a partir de 1º de fevereiro.
     * Antes do primeiro mês é 0; depois do último, é o total de meses.
     */
    public static int mesesFechados(LocalDate hoje, YearMonth mesInicio, YearMonth mesFim) {
        long fechados = ChronoUnit.MONTHS.between(mesInicio, YearMonth.from(hoje));
        return (int) Math.max(0, Math.min(fechados, meses(mesInicio, mesFim)));
    }

    /**
     * O "mês atual" da caixinha: antes do início é o primeiro mês (já dá para adiantar),
     * depois do fim é o último.
     */
    public static YearMonth mesAtual(LocalDate hoje, YearMonth mesInicio, YearMonth mesFim) {
        YearMonth mes = YearMonth.from(hoje);
        if (mes.isBefore(mesInicio)) {
            return mesInicio;
        }
        return mes.isAfter(mesFim) ? mesFim : mes;
    }

    /** Situação de uma pessoa em "hoje". */
    public static Situacao situacao(BigDecimal cotaDaPessoa, BigDecimal guardado, LocalDate hoje,
                                    YearMonth mesInicio, YearMonth mesFim) {
        int total = meses(mesInicio, mesFim);
        int fechados = mesesFechados(hoje, mesInicio, mesFim);
        YearMonth mesAtual = mesAtual(hoje, mesInicio, mesFim);
        BigDecimal esperadoAteAgora = dinheiro(cotaDaPessoa.multiply(BigDecimal.valueOf(fechados)));
        BigDecimal esperadoComMesAtual = dinheiro(cotaDaPessoa.multiply(BigDecimal.valueOf(Math.min(fechados + 1, total))));

        if (cotaDaPessoa.signum() <= 0) {
            // peso 0 (ou cota ainda sem orçamento): sempre em dia, nada a pagar
            return new Situacao(mesAtual, dinheiro(cotaDaPessoa), guardado, esperadoAteAgora, esperadoComMesAtual,
                    true, true, dinheiro(BigDecimal.ZERO), 0, dinheiro(BigDecimal.ZERO), Destaque.NENHUM);
        }

        boolean emDia = guardado.compareTo(esperadoAteAgora) >= 0;
        boolean cotaDoMesPaga = guardado.compareTo(esperadoComMesAtual) >= 0;
        BigDecimal paraFicarEmDia = dinheiro(esperadoComMesAtual.subtract(guardado).max(BigDecimal.ZERO));
        // o que falta só da cota deste mês (o atraso de meses anteriores aparece em "para ficar em dia")
        BigDecimal faltaNoMes = dinheiro(paraFicarEmDia.min(cotaDaPessoa));

        BigDecimal devendo = esperadoAteAgora.subtract(guardado);
        int mesesDeAtraso = devendo.signum() > 0
                ? devendo.divide(cotaDaPessoa, 0, RoundingMode.CEILING).intValueExact()
                : 0;

        return new Situacao(mesAtual, dinheiro(cotaDaPessoa), guardado, esperadoAteAgora, esperadoComMesAtual,
                emDia, cotaDoMesPaga, faltaNoMes, mesesDeAtraso, paraFicarEmDia,
                destaque(mesesDeAtraso, hoje, mesFim));
    }

    // ---------------------------------------------------------------- 3.3 atraso em degraus

    /**
     * Destaque no painel: 2 meses = amarelo; 3 ou mais = vermelho "conversar no privado";
     * do penúltimo mês (outubro) em diante, qualquer atraso = vermelho "quitar até novembro".
     */
    public static Destaque destaque(int mesesDeAtraso, LocalDate hoje, YearMonth mesFim) {
        if (mesesDeAtraso <= 0) {
            return Destaque.NENHUM;
        }
        if (!YearMonth.from(hoje).isBefore(mesFim.minusMonths(1))) {
            return Destaque.QUITAR_ATE_O_FIM;
        }
        if (mesesDeAtraso >= 3) {
            return Destaque.CONVERSAR_NO_PRIVADO;
        }
        return mesesDeAtraso == 2 ? Destaque.AMARELO : Destaque.NENHUM;
    }

    /** Arredonda para centavos (HALF_UP), a regra geral de dinheiro da especificação. */
    public static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- tipos

    public enum Destaque {
        NENHUM, AMARELO, CONVERSAR_NO_PRIVADO, QUITAR_ATE_O_FIM
    }

    /**
     * @param mesAtual            mês cuja cota está "em jogo" agora
     * @param emDia               guardado ≥ esperado até agora (meses já fechados)
     * @param cotaDoMesPaga       guardado ≥ esperado contando o mês atual
     * @param faltaNoMes          quanto falta da cota do mês atual (no máximo uma cota)
     * @param valorParaFicarEmDia max(0, esperadoComMesAtual − guardado): é o valor do PIX
     */
    public record Situacao(YearMonth mesAtual, BigDecimal cota, BigDecimal guardado,
                           BigDecimal esperadoAteAgora, BigDecimal esperadoComMesAtual,
                           boolean emDia, boolean cotaDoMesPaga, BigDecimal faltaNoMes,
                           int mesesDeAtraso, BigDecimal valorParaFicarEmDia, Destaque destaque) {
    }
}
