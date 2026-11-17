package com.natal.caixinha.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LinkAcessoRepository extends JpaRepository<LinkAcesso, String> {

    @Query("select l from LinkAcesso l join fetch l.pessoa where l.token = :token and l.ativo = true")
    Optional<LinkAcesso> buscarAtivo(String token);

    List<LinkAcesso> findByPessoaIdAndAtivoTrue(Long pessoaId);

    @Query("select l from LinkAcesso l where l.ativo = true")
    List<LinkAcesso> todosAtivos();

    void deleteByPessoaId(Long pessoaId);
}
