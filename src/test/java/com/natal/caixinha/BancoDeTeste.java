package com.natal.caixinha;

/** H2 em memória no mesmo modo do H2 local (compatível com PostgreSQL). */
public final class BancoDeTeste {

    public static final String URL =
            "spring.datasource.url=jdbc:h2:mem:caixinha-teste;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

    private BancoDeTeste() {
    }
}
