package com.natal.caixinha.web;

import com.natal.caixinha.comum.Dinheiro;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Formatação para as telas, usada nos templates como {@code ${@fmt.reais(valor)}}. */
@Component("fmt")
public class Formato {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM");

    /** R$ 1.058,00 */
    public String reais(BigDecimal valor) {
        return Dinheiro.formatar(valor);
    }

    /** 1.058,00 (sem "R$") */
    public String numero(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(BR));
        return formato.format(valor);
    }

    /** Valor para preencher um campo de formulário: 92,00 → "92", 92,50 → "92,50". */
    public String campo(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        BigDecimal limpo = valor.stripTrailingZeros();
        return limpo.scale() <= 0 ? limpo.toBigInteger().toString() : valor.setScale(2).toPlainString().replace('.', ',');
    }

    /** 11,5 */
    public String peso(BigDecimal valor) {
        return valor == null ? "" : valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** "março" */
    public String mes(YearMonth mes) {
        return mes == null ? "" : mes.getMonth().getDisplayName(TextStyle.FULL, BR);
    }

    /** "março de 2027" */
    public String mesAno(YearMonth mes) {
        return mes == null ? "" : mes(mes) + " de " + mes.getYear();
    }

    public String data(LocalDate data) {
        return data == null ? "" : data.format(DATA);
    }

    public String dataCurta(LocalDate data) {
        return data == null ? "" : data.format(DATA_CURTA);
    }
}
