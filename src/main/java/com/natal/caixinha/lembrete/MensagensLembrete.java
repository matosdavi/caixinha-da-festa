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
     */
    public record Grupo(YearMonth mesFechado, YearMonth mesNovo, BigDecimal arrecadado, BigDecimal meta,
                        int percentual, int emDia, int pagantes, BigDecimal cota) {
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
            texto.append(temCota ? ": " + frasesDaCota(g.cota()) : ".");
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

    private static String mes(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
    }

    private static String maiuscula(String texto) {
        return texto.substring(0, 1).toUpperCase(PT_BR) + texto.substring(1);
    }
}
