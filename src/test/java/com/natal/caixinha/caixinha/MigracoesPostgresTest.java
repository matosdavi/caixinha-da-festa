package com.natal.caixinha.caixinha;

import com.natal.caixinha.pessoa.PessoaRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * As mesmas migrações num PostgreSQL de verdade (o H2 dos outros testes só imita o PostgreSQL).
 * Garante que a primeira subida no Neon não vai quebrar.
 */
@SpringBootTest
class MigracoesPostgresTest {

    static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui subir o PostgreSQL embutido", e);
        }
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registro.add("spring.datasource.username", () -> "postgres");
        registro.add("spring.datasource.password", () -> "");
    }

    @AfterAll
    static void parar() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CaixinhaRepository caixinhas;
    @Autowired
    PessoaRepository pessoas;

    @Test
    void flywayAplicaSchemaESeedNoPostgres() {
        assertTrue(jdbc.queryForObject("select version()", String.class).startsWith("PostgreSQL"));
        assertEquals(3, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class));
        assertEquals(13, pessoas.count());
        assertEquals(0, new BigDecimal("11.50").compareTo(
                jdbc.queryForObject("select sum(peso) from pessoa where ativo", BigDecimal.class)));
        assertEquals("Caixinha da Festa 2027", caixinhas.configuracao().getNome());
    }

    @Test
    void restricoesFuncionamNoPostgres() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into lancamento (tipo, valor, data) values ('DEPOSITO', 92.00, DATE '2027-01-05')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into lancamento (tipo, valor, data, categoria) values ('RENDIMENTO', 1.00, DATE '2027-01-05', 'COMIDA')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into caixinha (id, nome, mes_inicio, mes_fim, data_fechamento_lista) "
                        + "values (2, 'Outra', '2027-01', '2027-11', DATE '2026-12-31')"));
    }
}
