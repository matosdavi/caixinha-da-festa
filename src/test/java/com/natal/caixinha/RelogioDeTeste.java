package com.natal.caixinha;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** "Hoje" fixo em 15/03/2027 às 10h de Brasília: janeiro e fevereiro fechados, março em andamento. */
@TestConfiguration
public class RelogioDeTeste {

    public static final ZonedDateTime AGORA =
            ZonedDateTime.of(2027, 3, 15, 10, 0, 0, 0, ZoneId.of(CaixinhaApplication.FUSO));

    @Bean
    @Primary
    public Clock relogioFixo() {
        return Clock.fixed(AGORA.toInstant(), AGORA.getZone());
    }
}
