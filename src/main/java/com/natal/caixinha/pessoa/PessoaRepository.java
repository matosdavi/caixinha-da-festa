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

    /** Lançamentos ainda não têm entidade: a consulta vai direto na tabela. */
    @Query(value = "select count(*) > 0 from lancamento where pessoa_id = :id", nativeQuery = true)
    boolean temLancamentos(Long id);
}
