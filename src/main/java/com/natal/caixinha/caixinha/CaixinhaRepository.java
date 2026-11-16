package com.natal.caixinha.caixinha;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaixinhaRepository extends JpaRepository<Caixinha, Long> {

    default Caixinha configuracao() {
        return findById(Caixinha.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("Configuração da caixinha não encontrada (migração V2)"));
    }
}
