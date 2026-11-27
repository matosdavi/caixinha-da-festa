package com.natal.caixinha.lembrete;

import com.natal.caixinha.comum.Dinheiro;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/** Textos prontos dos lembretes de WhatsApp (seção 5 da spec), com a formatação *negrito* do WhatsApp. */
public final class MensagensLembrete {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final String FAMILIA_E_PIX = "O valor da sua família e o PIX estão no seu link pessoal.";

    /**
     * Mensagem do grupo, uma por mês, no dia 1. Nunca tem nomes.
     *
     * @param mesFechado mês que acabou de fechar; nulo no primeiro mês da caixinha
     * @param mesNovo    mês que começou; nulo no mês seguinte ao último
     * @param proximaEtapa etapa do cronograma no meio da frase ("procurar o lugar"); nula se não houver
     * @param descontoNoMes desconto de novembro por cota inteira, só no mês em que vale (nulo nos outros)
     */
    public record Grupo(YearMonth mesFechado, YearMonth mesNovo, BigDecimal arrecadado, BigDecimal meta,
                        int percentual, int emDia, int pagantes, BigDecimal cota, String proximaEtapa,
                        YearMonth mesProximaEtapa, BigDecimal descontoNoMes) {

        public Grupo(YearMonth mesFechado, YearMonth mesNovo, BigDecimal arrecadado, BigDecimal meta,
                     int percentual, int emDia, int pagantes, BigDecimal cota) {
            this(mesFechado, mesNovo, arrecadado, meta, percentual, emDia, pagantes, cota, null, null, null);
        }

        public Grupo(YearMonth mesFechado, YearMonth mesNovo, BigDecimal arrecadado, BigDecimal meta,
                     int percentual, int emDia, int pagantes, BigDecimal cota, String proximaEtapa,
                     YearMonth mesProximaEtapa) {
            this(mesFechado, mesNovo, arrecadado, meta, percentual, emDia, pagantes, cota, proximaEtapa,
                    mesProximaEtapa, null);
        }
    }

    /**
     * Mensagem para o responsável de uma família que ainda não pagou o mês.
     *
     * @param maiorAtraso maior número de meses de atraso entre os pagantes da família
     */
    public record Individual(String nomeResponsavel, YearMonth mes, BigDecimal cotaFamilia,
                             BigDecimal valorParaFicarEmDia, int maiorAtraso, String link) {
    }

    private MensagensLembrete() {
    }

    public static String grupo(Grupo g) {
        StringBuilder texto = new StringBuilder("*Caixinha da festa* 🎉");
        boolean temCota = g.cota() != null && g.cota().signum() > 0;
        if (g.mesFechado() != null) {
            texto.append("\nFechamos *").append(mes(g.mesFechado())).append("*: ")
                    .append(Dinheiro.formatar(g.arrecadado())).append(" de ").append(Dinheiro.formatar(g.meta()))
                    .append(" (").append(g.percentual()).append("%).")
                    .append("\n*").append(g.emDia()).append(" de ").append(g.pagantes())
                    .append("* pagantes estão em dia 💪");
        }
        if (g.mesNovo() == null) {
            texto.append("\nÚltimo depósito feito. Agora é festa! 🥳");
        } else if (g.mesFechado() == null) {
            texto.append("\nA caixinha começou! *").append(maiuscula(mes(g.mesNovo()))).append("* é o primeiro mês");
            texto.append(temCota ? ": " + frasesDaCota(g.cota()) : ".");
        } else {
            texto.append("\n*").append(maiuscula(mes(g.mesNovo()))).append("* começou");
            boolean comDesconto = temCota && g.descontoNoMes() != null && g.descontoNoMes().signum() > 0;
            texto.append(comDesconto ? ": " + frasesDaCotaComDesconto(g.cota(), g.descontoNoMes())
                    : temCota ? ": " + frasesDaCota(g.cota()) : ".");
        }
        if (g.proximaEtapa() != null && g.mesProximaEtapa() != null) {
            texto.append("\n📅 Próxima etapa: *").append(g.proximaEtapa()).append("* (")
                    .append(mes(g.mesProximaEtapa())).append(")");
        }
        return texto.toString();
    }

    public static String individual(Individual i) {
        String primeiroNome = i.nomeResponsavel().trim().split("\\s+")[0];
        StringBuilder texto = new StringBuilder("Oi, ").append(primeiroNome)
                .append("! 😊 Lembrete da caixinha da festa: a cota de *").append(mes(i.mes()))
                .append("* da família é *").append(Dinheiro.formatar(i.cotaFamilia())).append("*.")
                .append("\nO PIX já está pronto no seu link: ").append(i.link());
        if (i.maiorAtraso() >= 2) {
            texto.append("\nPara ficar em dia: *").append(Dinheiro.formatar(i.valorParaFicarEmDia()))
                    .append("*. Se precisar combinar algo, é só me chamar.");
        }
        return texto.toString();
    }

    private static String frasesDaCota(BigDecimal cota) {
        return "a cota é " + Dinheiro.formatar(cota) + " por pessoa. " + FAMILIA_E_PIX;
    }

    /** Novembro com o presente do amigo secreto: a cota já descontada (nunca negativa). */
    private static String frasesDaCotaComDesconto(BigDecimal cota, BigDecimal desconto) {
        BigDecimal liquida = cota.subtract(desconto.min(cota));
        return "a cota é " + Dinheiro.formatar(liquida) + " por pessoa, já com " + Dinheiro.formatar(desconto.min(cota))
                + " de desconto (presente do amigo secreto 🎁). " + FAMILIA_E_PIX;
    }

    private static String mes(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
    }

    private static String maiuscula(String texto) {
        return texto.substring(0, 1).toUpperCase(PT_BR) + texto.substring(1);
    }
}
