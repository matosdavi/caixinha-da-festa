package com.natal.caixinha.prestacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Escolha da sobra de uma pessoa (a família escolhe uma vez e vale para todos dela). */
@Entity
@Table(name = "escolha_sobra")
public class EscolhaSobra {

    @Id
    @Column(name = "pessoa_id")
    private Long pessoaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OpcaoSobra opcao;

    @Column(name = "registrada_em", nullable = false)
    private OffsetDateTime registradaEm;

    protected EscolhaSobra() {
    }

    public EscolhaSobra(Long pessoaId, OpcaoSobra opcao, OffsetDateTime registradaEm) {
        this.pessoaId = pessoaId;
        this.opcao = opcao;
        this.registradaEm = registradaEm;
    }

    void mudar(OpcaoSobra opcao, OffsetDateTime quando) {
        this.opcao = opcao;
        this.registradaEm = quando;
    }

    public Long getPessoaId() { return pessoaId; }
    public OpcaoSobra getOpcao() { return opcao; }
    public OffsetDateTime getRegistradaEm() { return registradaEm; }
}
