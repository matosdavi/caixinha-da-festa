package com.natal.caixinha.caixinha;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Configuração da caixinha. Existe uma única linha (id = 1). */
@Entity
@Table(name = "caixinha")
public class Caixinha {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "mes_inicio", nullable = false, length = 7)
    private YearMonth mesInicio;

    @Column(name = "mes_fim", nullable = false, length = 7)
    private YearMonth mesFim;

    @Column(name = "data_festa")
    private LocalDate dataFesta;

    @Column(name = "margem_imprevistos", nullable = false, precision = 5, scale = 4)
    private BigDecimal margemImprevistos;

    @Column(name = "cota_base", precision = 12, scale = 2)
    private BigDecimal cotaBase;

    @Column(name = "data_fechamento_lista", nullable = false)
    private LocalDate dataFechamentoLista;

    @Column(name = "chave_pix", length = 77)
    private String chavePix;

    @Column(name = "nome_recebedor", length = 25)
    private String nomeRecebedor;

    @Column(name = "cidade_recebedor", length = 15)
    private String cidadeRecebedor;

    @Column(name = "desconto_novembro_por_peso", precision = 12, scale = 2)
    private BigDecimal descontoNovembroPorPeso;

    protected Caixinha() {
        // exigido pelo JPA
    }

    /** Fixa a cota base. Depois disso ela não muda mais (seção 3.1). */
    void fixarCota(BigDecimal cota) {
        if (cotaBase != null) {
            throw new IllegalStateException("A cota já foi fixada.");
        }
        this.cotaBase = cota;
    }

    public boolean isCotaFixada() { return cotaBase != null; }

    /** Data da festa; nula volta para "em votação". A validação fica no CaixinhaService. */
    void definirDataFesta(LocalDate data) {
        this.dataFesta = data;
    }

    /** Chave já normalizada; nome e cidade como o organizador digitou (a limpeza para o PIX é no BrCode). */
    void configurarPix(String chave, String nome, String cidade) {
        this.chavePix = chave;
        this.nomeRecebedor = nome;
        this.cidadeRecebedor = cidade;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public YearMonth getMesInicio() { return mesInicio; }
    public YearMonth getMesFim() { return mesFim; }
    public LocalDate getDataFesta() { return dataFesta; }
    public BigDecimal getMargemImprevistos() { return margemImprevistos; }
    public BigDecimal getCotaBase() { return cotaBase; }
    public LocalDate getDataFechamentoLista() { return dataFechamentoLista; }
    public String getChavePix() { return chavePix; }
    public String getNomeRecebedor() { return nomeRecebedor; }
    public String getCidadeRecebedor() { return cidadeRecebedor; }
    public BigDecimal getDescontoNovembroPorPeso() { return descontoNovembroPorPeso; }
}
