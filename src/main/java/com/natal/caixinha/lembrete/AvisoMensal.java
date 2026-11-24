package com.natal.caixinha.lembrete;

import com.natal.caixinha.pessoa.Pessoa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.YearMonth;

/** Registro de que o organizador tocou em "Enviar no WhatsApp" num lembrete daquele mês. */
@Entity
@Table(name = "aviso_mensal")
public class AvisoMensal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private YearMonth mes;

    /** Nula nos avisos para o grupo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoAviso tipo;

    @Column(name = "avisado_em", nullable = false)
    private OffsetDateTime avisadoEm;

    protected AvisoMensal() {
    }

    public AvisoMensal(YearMonth mes, Pessoa pessoa, TipoAviso tipo, OffsetDateTime avisadoEm) {
        this.mes = mes;
        this.pessoa = pessoa;
        this.tipo = tipo;
        this.avisadoEm = avisadoEm;
    }

    public Long getId() { return id; }
    public YearMonth getMes() { return mes; }
    public Pessoa getPessoa() { return pessoa; }
    public TipoAviso getTipo() { return tipo; }
    public OffsetDateTime getAvisadoEm() { return avisadoEm; }
}
