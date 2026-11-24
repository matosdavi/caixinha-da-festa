package com.natal.caixinha.agenda;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Arquivo de agenda (.ics) com o lembrete mensal do depósito (seção 4.2 da spec):
 * um evento de dia inteiro no dia 1, repetido todo mês, com alarme às 9h.
 */
public final class LembreteIcs {

    private static final DateTimeFormatter DIA = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final int LIMITE = 75;

    /**
     * @param inicio primeiro mês do lembrete (o maior entre o início da caixinha e o mês de entrada)
     * @param meses  quantos meses (até o último mês da caixinha)
     * @param link   link pessoal completo, vai na descrição
     */
    public record Dados(long pessoaId, int ano, YearMonth inicio, int meses, String resumo, String link,
                        Instant agora) {
    }

    private LembreteIcs() {
    }

    public static String gerar(Dados d) {
        LocalDate primeiroDia = d.inicio().atDay(1);
        StringBuilder ics = new StringBuilder();
        linha(ics, "BEGIN:VCALENDAR");
        linha(ics, "VERSION:2.0");
        linha(ics, "PRODID:-//Caixinha da Festa//PT-BR");
        linha(ics, "CALSCALE:GREGORIAN");
        linha(ics, "BEGIN:VEVENT");
        // UID estável: importar de novo atualiza o evento em vez de duplicar
        linha(ics, "UID:caixinha-" + d.ano() + "-pessoa-" + d.pessoaId() + "@caixinha-da-festa");
        linha(ics, "DTSTAMP:" + CARIMBO.format(d.agora()));
        linha(ics, "DTSTART;VALUE=DATE:" + primeiroDia.format(DIA));
        linha(ics, "DTEND;VALUE=DATE:" + primeiroDia.plusDays(1).format(DIA));
        linha(ics, "RRULE:FREQ=MONTHLY;COUNT=" + d.meses());
        linha(ics, "SUMMARY:" + escapar(d.resumo()));
        linha(ics, "DESCRIPTION:" + escapar(d.link()));
        linha(ics, "BEGIN:VALARM");
        linha(ics, "ACTION:DISPLAY");
        linha(ics, "DESCRIPTION:" + escapar(d.resumo()));
        linha(ics, "TRIGGER;RELATED=START:PT9H");
        linha(ics, "END:VALARM");
        linha(ics, "END:VEVENT");
        linha(ics, "END:VCALENDAR");
        return ics.toString();
    }

    static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    /** Escreve a linha com CRLF, dobrando em 75 bytes sem partir um caractere UTF-8. */
    private static void linha(StringBuilder ics, String conteudo) {
        int bytesNaLinha = 0;
        int limite = LIMITE;
        for (int i = 0; i < conteudo.length(); ) {
            int ponto = conteudo.codePointAt(i);
            String caractere = new String(Character.toChars(ponto));
            int tamanho = caractere.getBytes(StandardCharsets.UTF_8).length;
            if (bytesNaLinha + tamanho > limite) {
                ics.append("\r\n ");
                bytesNaLinha = 0;
                limite = LIMITE - 1; // o espaço da continuação conta
            }
            ics.append(caractere);
            bytesNaLinha += tamanho;
            i += Character.charCount(ponto);
        }
        ics.append("\r\n");
    }
}
