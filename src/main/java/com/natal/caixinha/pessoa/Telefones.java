package com.natal.caixinha.pessoa;

import com.natal.caixinha.comum.RegraException;

/**
 * Telefones em E.164 sem o '+' (ex.: 5511999998888), o formato que o wa.me espera.
 * O organizador pode digitar como quiser: "(11) 99999-8888", "+55 11 99999 8888"...
 */
public final class Telefones {

    private static final String BRASIL = "55";

    private Telefones() {
    }

    /** Só dígitos, com o código do país. Número brasileiro sem o 55 (DDD + número) ganha o 55. Vazio = nulo. */
    public static String normalizar(String digitado) {
        if (digitado == null || digitado.isBlank()) {
            return null;
        }
        String digitos = digitado.replaceAll("[^0-9]", "");
        if (digitos.startsWith("00")) {
            digitos = digitos.substring(2); // 0055... (discagem internacional)
        }
        if (digitos.length() == 10 || digitos.length() == 11) {
            digitos = BRASIL + digitos; // DDD + número
        }
        if (digitos.length() < 12 || digitos.length() > 15) {
            throw new RegraException("Telefone inválido: use DDD + número, por exemplo (11) 99999-8888.");
        }
        return digitos;
    }

    /** Para mostrar: "+55 (11) 99999-8888". Números de fora do Brasil aparecem só com o '+'. */
    public static String formatar(String e164) {
        if (e164 == null) {
            return "";
        }
        if (e164.startsWith(BRASIL) && (e164.length() == 12 || e164.length() == 13)) {
            String ddd = e164.substring(2, 4);
            String numero = e164.substring(4);
            int corte = numero.length() - 4;
            return "+55 (" + ddd + ") " + numero.substring(0, corte) + "-" + numero.substring(corte);
        }
        return "+" + e164;
    }
}
