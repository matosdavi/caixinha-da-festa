package com.natal.caixinha.lembrete;

import com.natal.caixinha.BancoDeTeste;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = BancoDeTeste.URL)
@Transactional
class AvisoMensalRepositoryTest {

    static final YearMonth MARCO = YearMonth.of(2027, 3);

    @Autowired
    AvisoMensalRepository avisos;
    @Autowired
    PessoaRepository pessoas;

    @Test
    void gravaEConsultaAvisosDoGrupoEIndividuais() {
        Pessoa adulto = pessoas.findAll().stream().filter(p -> p.getNome().equals("Adulto 1")).findFirst().orElseThrow();
        avisos.save(new AvisoMensal(MARCO, null, TipoAviso.GRUPO, OffsetDateTime.now()));
        avisos.save(new AvisoMensal(MARCO, adulto, TipoAviso.INDIVIDUAL, OffsetDateTime.now()));
        avisos.flush();

        assertTrue(avisos.existsByMesAndTipoAndPessoaIsNull(MARCO, TipoAviso.GRUPO));
        assertFalse(avisos.existsByMesAndTipoAndPessoaIsNull(MARCO.plusMonths(1), TipoAviso.GRUPO));
        assertTrue(avisos.existsByMesAndTipoAndPessoaId(MARCO, TipoAviso.INDIVIDUAL, adulto.getId()));
        assertEquals(1, avisos.findByMesAndTipo(MARCO, TipoAviso.INDIVIDUAL).size());

        avisos.deleteByMesAndTipoAndPessoaId(MARCO, TipoAviso.INDIVIDUAL, adulto.getId());
        avisos.flush();
        assertFalse(avisos.existsByMesAndTipoAndPessoaId(MARCO, TipoAviso.INDIVIDUAL, adulto.getId()));
        assertTrue(avisos.existsByMesAndTipoAndPessoaIsNull(MARCO, TipoAviso.GRUPO), "o do grupo continua");
    }
}
