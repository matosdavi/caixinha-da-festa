package com.natal.caixinha.pix;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;

/**
 * PIX copia e cola estático no padrão BR Code (EMV) do Banco Central, gerado localmente (seção 4.1 da spec).
 * Cada campo é: id (2) + tamanho (2 dígitos) + valor.
 */
public final class BrCode {

    public static final int MAX_NOME = 25;
    public static final int MAX_CIDADE = 15;

    /** Quem recebe o PIX: a chave e o nome/cidade como o organizador cadastrou. */
    public record Recebedor(String chave, String nome, String cidade) {
    }

    private BrCode() {
    }

    /** Identificador curto do PIX de uma família. */
    public static String txid(long responsavelId) {
        return "CX2027" + responsavelId;
    }

    /** Texto do copia e cola. Valor zero (ou nulo) deixa o valor em aberto: o campo 54 é omitido. */
    public static String gerar(Recebedor r, BigDecimal valor, String txid) {
        StringBuilder pix = new StringBuilder()
                .append(campo("00", "01"))
                .append(campo("26", campo("00", "br.gov.bcb.pix") + campo("01", r.chave())))
                .append(campo("52", "0000"))
                .append(campo("53", "986"));
        if (valor != null && valor.signum() > 0) {
            pix.append(campo("54", valor.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
        pix.append(campo("58", "BR"))
                .append(campo("59", limpar(r.nome(), MAX_NOME)))
                .append(campo("60", limpar(r.cidade(), MAX_CIDADE)))
                .append(campo("62", campo("05", txid)))
                .append("6304");
        return pix.append(Crc16.ccittFalse(pix.toString())).toString();
    }

    /** Sem acentos, maiúsculas, só A-Z, 0-9 e espaço, cortado no máximo. */
    public static String limpar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpo = semAcento.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "")
                .replaceAll(" +", " ").trim();
        return limpo.length() > maximo ? limpo.substring(0, maximo).trim() : limpo;
    }

    private static String campo(String id, String valor) {
        return id + String.format("%02d", valor.length()) + valor;
    }
}
