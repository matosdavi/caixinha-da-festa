package com.natal.caixinha.orcamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface ItemOrcamentoRepository extends JpaRepository<ItemOrcamento, Long> {

    List<ItemOrcamento> findAllByOrderByCategoriaAscIdAsc();

    @Query("select coalesce(sum(i.valorPrevisto), 0) from ItemOrcamento i")
    BigDecimal somaPrevista();
}
