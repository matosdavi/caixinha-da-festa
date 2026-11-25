package com.natal.caixinha.pessoa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PessoaRepository extends JpaRepository<Pessoa, Long> {

    List<Pessoa> findByAtivoTrueOrderByNomeAsc();

    @Query("select p from Pessoa p left join fetch p.responsavel order by p.ativo desc, lower(p.nome)")
    List<Pessoa> listarParaPainel();

    List<Pessoa> findByResponsavelIdOrderByNomeAsc(Long responsavelId);

    boolean existsByResponsavelId(Long responsavelId);

    /** Consulta direta na tabela, para o pacote pessoa não depender de lancamento. */
    @Query(value = "select count(*) > 0 from lancamento where pessoa_id = :id", nativeQuery = true)
    boolean temLancamentos(Long id);

    /** [data_fechamento_lista, mes_inicio, mes_fim] da caixinha; consulta direta para o pacote pessoa não depender de caixinha. */
    @Query(value = "select data_fechamento_lista, mes_inicio, mes_fim from caixinha order by id limit 1", nativeQuery = true)
    List<Object[]> datasDaCaixinha();
}
