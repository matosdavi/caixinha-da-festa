package com.natal.caixinha.comum;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Leitura e validação de valores em reais digitados no painel. */
public final class Dinheiro {

    /** Nenhum valor desta caixinha chega perto disso: protege contra um zero a mais digitado. */
    public static final BigDecimal MAXIMO = new BigDecimal("100000.00");

    private Dinheiro() {
    }

    /**
     * Lê "92", "92,50", "1.058,00", "1058.00" ou "R$ 92". Vazio vira {@code null}.
     * Com vírgula, o ponto é separador de milhar; sem vírgula, o ponto é decimal.
     */
    public static BigDecimal ler(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.replace("R$", "").replaceAll("\\s", "");
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            throw new RegraException(campo + " inválido: use só números, como 92 ou 92,50.");
        }
    }

    /** Exige valor maior que zero, com no máximo 2 casas, e devolve com 2 casas. */
    public static BigDecimal positivo(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() <= 0) {
            throw new RegraException(campo + " deve ser maior que zero.");
        }
        return validar(valor, campo);
    }

    /** Aceita zero (ou nulo, que fica nulo). */
    public static BigDecimal naoNegativo(BigDecimal valor, String campo) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() < 0) {
            throw new RegraException(campo + " não pode ser negativo.");
        }
        return validar(valor, campo);
    }

    private static BigDecimal validar(BigDecimal valor, String campo) {
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new RegraException(campo + " deve ter no máximo 2 casas decimais.");
        }
        if (valor.compareTo(MAXIMO) > 0) {
            throw new RegraException(campo + " acima de R$ 100.000: confira se não sobrou um zero.");
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }
}
