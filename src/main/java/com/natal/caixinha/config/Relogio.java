package com.natal.caixinha.config;

import com.natal.caixinha.CaixinhaApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "Hoje" no fuso de Brasília: sem isso, às 22h do dia 31 o servidor (em UTC)
 * já acharia que é o mês seguinte. Nos testes, é trocado por um relógio fixo.
 */
@Configuration
public class Relogio {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(CaixinhaApplication.FUSO));
    }
}
