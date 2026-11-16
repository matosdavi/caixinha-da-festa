package com.natal.caixinha.caixinha;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O Flyway cria o schema, o Hibernate valida as entidades e o seed está como a especificação pede. */
@SpringBootTest(properties = BancoDeTeste.URL)
@Transactional
class MigracoesTest {

    @Autowired
    CaixinhaRepository caixinhas;
    @Autowired
    PessoaRepository pessoas;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void configuracaoInicialDaCaixinha() {
        Caixinha c = caixinhas.configuracao();
        assertEquals(YearMonth.of(2027, 1), c.getMesInicio());
        assertEquals(YearMonth.of(2027, 11), c.getMesFim());
        assertEquals(LocalDate.of(2026, 12, 31), c.getDataFechamentoLista());
        assertEquals(0, new BigDecimal("0.10").compareTo(c.getMargemImprevistos()));
        assertNull(c.getCotaBase(), "a cota só é fixada no fechamento da lista");
        assertNull(c.getDataFesta(), "a data da festa é definida por enquete");
    }

    @Test
    void trezePessoasComPesosEResponsaveis() {
        List<Pessoa> todas = pessoas.findAll();
        assertEquals(13, todas.size());
        assertEquals(11, todas.stream().filter(p -> p.getPeso().compareTo(BigDecimal.ONE) == 0).count());

        BigDecimal somaPesos = todas.stream().map(Pessoa::getPeso).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("11.5").compareTo(somaPesos));

        List<Pessoa> dependentes = todas.stream().filter(p -> p.getResponsavel() != null).toList();
        assertEquals(2, dependentes.size(), "adolescente e bebê têm responsável");
        dependentes.forEach(d -> assertTrue(d.getPeso().compareTo(BigDecimal.ONE) < 0));
        assertEquals(12, todas.stream().filter(Pessoa::isPagante).count(), "o bebê não é pagante");
    }

    @Test
    void bancoRecusaLancamentosInconsistentes() {
        // depósito sem pessoa
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into lancamento (tipo, valor, data) values ('DEPOSITO', 92.00, DATE '2027-01-05')"));
        // valor negativo
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into lancamento (tipo, valor, data) values ('RENDIMENTO', -1.00, DATE '2027-01-05')"));
        // gasto sem categoria
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into lancamento (tipo, valor, data) values ('GASTO', 10.00, DATE '2027-01-05')"));
        // peso fora de 0 / 0,5 / 1
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into pessoa (nome, peso, data_entrada) values ('X', 0.75, DATE '2027-01-01')"));
    }
}
