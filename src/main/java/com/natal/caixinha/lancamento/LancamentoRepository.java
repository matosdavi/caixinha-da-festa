package com.natal.caixinha.lancamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    @Query("select l from Lancamento l left join fetch l.pessoa order by l.data desc, l.id desc")
    List<Lancamento> listarRecentes();

    @Query("select l from Lancamento l left join fetch l.pessoa where l.pessoa.id = :pessoaId order by l.data desc, l.id desc")
    List<Lancamento> daPessoa(Long pessoaId);

    /** Totais por tipo: cada linha é [TipoLancamento, BigDecimal]. */
    @Query("select l.tipo, sum(l.valor) from Lancamento l group by l.tipo")
    List<Object[]> totaisPorTipo();

    /** Totais por pessoa e tipo: cada linha é [Long pessoaId, TipoLancamento, BigDecimal]. */
    @Query("select l.pessoa.id, l.tipo, sum(l.valor) from Lancamento l where l.pessoa is not null group by l.pessoa.id, l.tipo")
    List<Object[]> totaisPorPessoa();

    /** Lançamentos que mexem no guardado (depósito, crédito, devolução) dessas pessoas, do mais antigo ao mais novo. */
    @Query("select l from Lancamento l join fetch l.pessoa where l.pessoa.id in :ids and l.tipo in ("
            + "com.natal.caixinha.lancamento.TipoLancamento.DEPOSITO, "
            + "com.natal.caixinha.lancamento.TipoLancamento.CREDITO_ANO_ANTERIOR, "
            + "com.natal.caixinha.lancamento.TipoLancamento.DEVOLUCAO) order by l.data asc, l.id asc")
    List<Lancamento> movimentosDe(java.util.Collection<Long> ids);

    @Query("select coalesce(sum(l.valor), 0) from Lancamento l where l.pessoa.id = :pessoaId and l.tipo = :tipo")
    BigDecimal somaDaPessoa(Long pessoaId, TipoLancamento tipo);
}
