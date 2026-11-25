package com.natal.caixinha.pessoa;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pessoa")
public class Pessoa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    /** E.164 sem '+', ex.: 5511999998888. */
    @Column(length = 15)
    private String telefone;

    /** 1.00 = cota inteira, 0.50 = meia cota, 0.00 = não paga. */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal peso;

    /** Quem recebe o link e paga pela pessoa (nulo = ela mesma). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Pessoa responsavel;

    @Column(name = "data_entrada", nullable = false)
    private LocalDate dataEntrada;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(length = 500)
    private String observacao;

    /** Acréscimo do anfitrião para quem entrou tarde. Nulo = ainda não confirmado; 0 = sem acréscimo. */
    @Column(precision = 12, scale = 2)
    private BigDecimal acrescimo;

    /** Mês em que a divisão do acréscimo começa (vai até o último mês da caixinha). */
    @Column(name = "acrescimo_desde", length = 7)
    private java.time.YearMonth acrescimoDesde;

    protected Pessoa() {
        // exigido pelo JPA
    }

    public Pessoa(String nome, BigDecimal peso, LocalDate dataEntrada) {
        this.nome = nome;
        this.peso = peso;
        this.dataEntrada = dataEntrada;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getTelefone() { return telefone; }
    public BigDecimal getPeso() { return peso; }
    public Pessoa getResponsavel() { return responsavel; }
    public LocalDate getDataEntrada() { return dataEntrada; }
    public boolean isAtivo() { return ativo; }
    public String getObservacao() { return observacao; }
    public BigDecimal getAcrescimo() { return acrescimo; }
    public java.time.YearMonth getAcrescimoDesde() { return acrescimoDesde; }

    void setAcrescimo(BigDecimal acrescimo, java.time.YearMonth desde) {
        this.acrescimo = acrescimo;
        this.acrescimoDesde = desde;
    }

    /** Entrou depois do fechamento da lista (seção 3.5 da spec). */
    public boolean entrouTarde(LocalDate fechamentoLista) {
        return dataEntrada.isAfter(fechamentoLista);
    }

    void setNome(String nome) { this.nome = nome; }
    void setTelefone(String telefone) { this.telefone = telefone; }
    void setPeso(BigDecimal peso) { this.peso = peso; }
    void setResponsavel(Pessoa responsavel) { this.responsavel = responsavel; }
    void setDataEntrada(LocalDate dataEntrada) { this.dataEntrada = dataEntrada; }
    void setAtivo(boolean ativo) { this.ativo = ativo; }
    void setObservacao(String observacao) { this.observacao = observacao; }

    /** Pagante = pessoa com peso maior que zero. */
    public boolean isPagante() {
        return peso.signum() > 0;
    }

    /** Responsável = quem recebe o próprio link (não depende de ninguém). */
    public boolean isResponsavel() {
        return responsavel == null;
    }
}
