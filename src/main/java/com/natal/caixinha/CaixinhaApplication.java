package com.natal.caixinha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CaixinhaApplication {

    /** Todo cálculo de data usa o horário de Brasília (o servidor do Render roda em UTC). */
    public static final String FUSO = "America/Sao_Paulo";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(FUSO));
        SpringApplication.run(CaixinhaApplication.class, args);
    }
}
