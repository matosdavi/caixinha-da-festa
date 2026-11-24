package com.natal.caixinha.agenda;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class LembreteIcsTest {

    static final Instant AGORA = Instant.parse("2027-03-15T13:00:00Z");

    static LembreteIcs.Dados dados(YearMonth inicio, int meses, String resumo, String link) {
        return new LembreteIcs.Dados(7, 2027, inicio, meses, resumo, link, AGORA);
    }

    /** Desfaz a dobra de linhas do iCalendar (CRLF + espaço). */
    static String desdobrar(String ics) {
        return ics.replace("\r\n ", "");
    }

    @Test
    void eventoMensalDeDiaInteiroComAlarme() {
        String ics = LembreteIcs.gerar(dados(YearMonth.of(2027, 1), 11,
                "Caixinha da festa — depositar R$ 138,00", "https://x/c/abc"));
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"), ics);
        assertTrue(ics.contains("UID:caixinha-2027-pessoa-7@caixinha-da-festa\r\n"));
        assertTrue(ics.contains("DTSTAMP:20270315T130000Z\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20270101\r\n"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20270102\r\n"));
        assertTrue(ics.contains("RRULE:FREQ=MONTHLY;COUNT=11\r\n"));
        assertTrue(desdobrar(ics).contains("SUMMARY:Caixinha da festa — depositar R$ 138\\,00\r\n"));
        assertTrue(ics.contains("DESCRIPTION:https://x/c/abc\r\n"));
        assertTrue(ics.contains("BEGIN:VALARM\r\nACTION:DISPLAY\r\n"));
        assertTrue(ics.contains("TRIGGER;RELATED=START:PT9H\r\nEND:VALARM\r\n"));
        assertTrue(ics.endsWith("END:VEVENT\r\nEND:VCALENDAR\r\n"));
        assertFalse(ics.replace("\r\n", "").contains("\n"), "toda quebra de linha é CRLF");
    }

    @Test
    void comecaNoMesDeEntrada() {
        String ics = LembreteIcs.gerar(dados(YearMonth.of(2027, 4), 8, "x", "l"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20270401\r\n"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20270402\r\n"));
        assertTrue(ics.contains("COUNT=8\r\n"));
    }

    @Test
    void escapaVirgulaPontoEVirgulaBarraEQuebra() {
        String ics = LembreteIcs.gerar(dados(YearMonth.of(2027, 1), 11, "a,b;c\\d\ne", "l"));
        assertTrue(ics.contains("SUMMARY:a\\,b\\;c\\\\d\\ne\r\n"), ics);
    }

    @Test
    void linhasLongasSaoDobradasEm75Bytes() {
        String link = "https://caixinha-da-festa.onrender.com/c/" + "a".repeat(80);
        String ics = LembreteIcs.gerar(dados(YearMonth.of(2027, 1), 11, "x", link));
        for (String linha : ics.split("\r\n")) {
            assertTrue(linha.getBytes(StandardCharsets.UTF_8).length <= 75, linha);
        }
        assertTrue(desdobrar(ics).contains("DESCRIPTION:" + link + "\r\n"));
    }

    @Test
    void dobraNaoParteCaractereAcentuado() {
        // "—" ocupa 3 bytes: posicionado para cair em cima do limite de 75
        String resumo = "x".repeat(64) + "——— depositar a cota do mês";
        String ics = LembreteIcs.gerar(dados(YearMonth.of(2027, 1), 11, resumo, "l"));
        for (String linha : ics.split("\r\n")) {
            byte[] bytes = linha.getBytes(StandardCharsets.UTF_8);
            assertTrue(bytes.length <= 75, linha);
            assertEquals(linha, new String(bytes, StandardCharsets.UTF_8));
        }
        assertTrue(desdobrar(ics).contains("SUMMARY:" + resumo + "\r\n"));
    }
}
