package com.natal.caixinha;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Relógio que o teste pode mudar de dia (às 10h de Brasília). Começa em 15/03/2027, como o {@link RelogioDeTeste}. */
@TestConfiguration
public class RelogioAjustavel {

    @Bean
    @Primary
    public Ajustavel relogioAjustavel() {
        return new Ajustavel();
    }

    public static class Ajustavel extends Clock {

        private static final ZoneId FUSO = ZoneId.of(CaixinhaApplication.FUSO);
        private Instant agora = RelogioDeTeste.AGORA.toInstant();

        public void para(LocalDate dia) {
            agora = dia.atTime(10, 0).atZone(FUSO).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora, zone);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
