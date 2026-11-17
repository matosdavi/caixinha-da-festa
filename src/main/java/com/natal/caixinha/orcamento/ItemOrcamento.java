package com.natal.caixinha.orcamento;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Um item do orçamento da festa: quanto se prevê gastar e, depois, quanto se gastou de fato. */
@Entity
@Table(name = "item_orcamento")
public class ItemOrcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Categoria categoria;

    @Column(nullable = false, length = 200)
    private String descricao;

    @Column(name = "valor_previsto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorPrevisto;

    @Column(name = "valor_real", precision = 12, scale = 2)
    private BigDecimal valorReal;

    protected ItemOrcamento() {
        // exigido pelo JPA
    }

    ItemOrcamento(Categoria categoria, String descricao, BigDecimal valorPrevisto, BigDecimal valorReal) {
        alterar(categoria, descricao, valorPrevisto, valorReal);
    }

    final void alterar(Categoria categoria, String descricao, BigDecimal valorPrevisto, BigDecimal valorReal) {
        this.categoria = categoria;
        this.descricao = descricao;
        this.valorPrevisto = valorPrevisto;
        this.valorReal = valorReal;
    }

    public Long getId() { return id; }
    public Categoria getCategoria() { return categoria; }
    public String getDescricao() { return descricao; }
    public BigDecimal getValorPrevisto() { return valorPrevisto; }
    public BigDecimal getValorReal() { return valorReal; }
}
