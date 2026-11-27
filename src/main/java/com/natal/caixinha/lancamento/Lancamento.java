package com.natal.caixinha.lancamento;

import com.natal.caixinha.orcamento.Categoria;
import com.natal.caixinha.pessoa.Pessoa;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Uma movimentação da conta da caixinha. O saldo é sempre a soma destes lançamentos. */
@Entity
@Table(name = "lancamento")
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private TipoLancamento tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    @Column(length = 200)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Categoria categoria;

    @Column(name = "comprovante_url", length = 500)
    private String comprovanteUrl;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected Lancamento() {
        // exigido pelo JPA
    }

    Lancamento(TipoLancamento tipo, Pessoa pessoa, BigDecimal valor, LocalDate data, String descricao,
               Categoria categoria, String comprovanteUrl, OffsetDateTime criadoEm) {
        this.tipo = tipo;
        this.pessoa = pessoa;
        this.valor = valor;
        this.data = data;
        this.descricao = descricao;
        this.categoria = categoria;
        this.comprovanteUrl = comprovanteUrl;
        this.criadoEm = criadoEm;
    }

    /** Valor com sinal: positivo se entra na conta, negativo se sai. */
    public BigDecimal valorNoSaldo() {
        return tipo.isEntrada() ? valor : valor.negate();
    }

    /** Descrição da devolução da sobra (seção 3.7). */
    public static final String SOBRA_DA_FESTA = "Sobra da festa";

    /** Descrição do crédito para a caixinha do ano seguinte, lançado como devolução: "Crédito para 2028". */
    public static String creditoPara(int ano) {
        return "Crédito para " + ano;
    }

    /**
     * Devolução da prestação de contas (sobra ou crédito para o ano seguinte). Não é dinheiro que a pessoa
     * deixou de guardar: fica fora do guardado da situação e da conta das partes.
     */
    public boolean daSobraDaFesta() {
        return tipo == TipoLancamento.DEVOLUCAO && descricao != null
                && (descricao.equals(SOBRA_DA_FESTA) || descricao.matches("Crédito para \\d{4}"));
    }

    public Long getId() { return id; }
    public TipoLancamento getTipo() { return tipo; }
    public Pessoa getPessoa() { return pessoa; }
    public BigDecimal getValor() { return valor; }
    public LocalDate getData() { return data; }
    public String getDescricao() { return descricao; }
    public Categoria getCategoria() { return categoria; }
    public String getComprovanteUrl() { return comprovanteUrl; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
