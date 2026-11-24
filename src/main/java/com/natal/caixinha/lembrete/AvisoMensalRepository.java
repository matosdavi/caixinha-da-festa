package com.natal.caixinha.lembrete;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;

public interface AvisoMensalRepository extends JpaRepository<AvisoMensal, Long> {

    boolean existsByMesAndTipoAndPessoaIsNull(YearMonth mes, TipoAviso tipo);

    boolean existsByMesAndTipoAndPessoaId(YearMonth mes, TipoAviso tipo, Long pessoaId);

    List<AvisoMensal> findByMesAndTipo(YearMonth mes, TipoAviso tipo);

    void deleteByMesAndTipoAndPessoaId(YearMonth mes, TipoAviso tipo, Long pessoaId);
}
