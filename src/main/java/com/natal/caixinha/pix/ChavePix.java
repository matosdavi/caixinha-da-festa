package com.natal.caixinha.pix;

import com.natal.caixinha.comum.RegraException;

import java.util.Locale;

/**
 * Valida e normaliza a chave PIX de acordo com o tipo escolhido pelo organizador.
 * O tipo é escolhido (e não adivinhado) porque CPF e celular com DDD têm os dois 11 dígitos.
 */
public final class ChavePix {

    public static final int MAXIMO = 77;

    public enum Tipo {
        CPF("CPF"), CNPJ("CNPJ"), CELULAR("Celular"), EMAIL("E-mail"), ALEATORIA("Chave aleatória");

        private final String rotulo;

        Tipo(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    private ChavePix() {
    }

    public static String normalizar(Tipo tipo, String texto) {
        if (tipo == null) {
            throw new RegraException("Escolha o tipo da chave PIX.");
        }
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty()) {
            throw new RegraException("Informe a chave PIX.");
        }
        return switch (tipo) {
            case CPF -> cpf(limpo);
            case CNPJ -> cnpj(limpo);
            case CELULAR -> celular(limpo);
            case EMAIL -> email(limpo);
            case ALEATORIA -> aleatoria(limpo);
        };
    }

    /**
     * Tipo de uma chave já gravada (normalizada), para mostrar de novo na tela.
     * Depois de normalizada não há ambiguidade: celular começa com +55, CPF tem 11 dígitos e CNPJ 14.
     */
    public static Tipo tipoDe(String normalizada) {
        if (normalizada == null || normalizada.isBlank()) {
            return null;
        }
        if (normalizada.startsWith("+")) {
            return Tipo.CELULAR;
        }
        if (normalizada.contains("@")) {
            return Tipo.EMAIL;
        }
        if (normalizada.matches("\\d{11}")) {
            return Tipo.CPF;
        }
        if (normalizada.matches("\\d{14}")) {
            return Tipo.CNPJ;
        }
        return Tipo.ALEATORIA;
    }

    private static String cpf(String texto) {
        String d = texto.replaceAll("[.\\-\\s]", "");
        if (!d.matches("\\d{11}") || d.chars().distinct().count() == 1
                || digito(d, 9, 10) != d.charAt(9) - '0' || digito(d, 10, 11) != d.charAt(10) - '0') {
            throw new RegraException("CPF inválido: confira os números.");
        }
        return d;
    }

    /** Dígito verificador do CPF: pesos decrescentes a partir de {@code pesoInicial}. */
    private static int digito(String d, int quantos, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < quantos; i++) {
            soma += (d.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static String cnpj(String texto) {
        String d = texto.replaceAll("[./\\-\\s]", "");
        if (!d.matches("\\d{14}") || d.chars().distinct().count() == 1
                || digitoCnpj(d, 12) != d.charAt(12) - '0' || digitoCnpj(d, 13) != d.charAt(13) - '0') {
            throw new RegraException("CNPJ inválido: confira os números.");
        }
        return d;
    }

    private static int digitoCnpj(String d, int quantos) {
        int soma = 0;
        int peso = quantos - 7; // 5 para o 1º dígito, 6 para o 2º; depois volta de 9 a 2
        for (int i = 0; i < quantos; i++) {
            soma += (d.charAt(i) - '0') * peso;
            peso = peso == 2 ? 9 : peso - 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static String celular(String texto) {
        String d = texto.replaceAll("\\D", "");
        if (d.startsWith("55") && (d.length() == 12 || d.length() == 13)) {
            d = d.substring(2);
        }
        if (!d.matches("\\d{10,11}")) {
            throw new RegraException("Celular inválido: informe com DDD, como (11) 99999-8888.");
        }
        return "+55" + d;
    }

    private static String email(String texto) {
        String e = texto.toLowerCase(Locale.ROOT);
        // só ASCII: o BR Code conta o tamanho dos campos em caracteres e os bancos recusam acento
        if (e.length() > MAXIMO || !e.matches("[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}")) {
            throw new RegraException("E-mail inválido: confira se está como fulano@exemplo.com.");
        }
        return e;
    }

    private static String aleatoria(String texto) {
        String a = texto.toLowerCase(Locale.ROOT);
        if (!a.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new RegraException("Chave aleatória inválida: copie do app do banco, com os tracinhos.");
        }
        return a;
    }
}
