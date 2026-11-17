package com.natal.caixinha.link;

import com.natal.caixinha.pessoa.Pessoa;
import jakarta.persistence.*;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Link pessoal de um responsável: quem tem o link vê a própria situação, sem senha.
 * Um link ativo por responsável; "gerar outro link" desativa o antigo.
 */
@Entity
@Table(name = "link_acesso")
public class LinkAcesso {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** 128 bits aleatórios em base64url (22 caracteres): impossível de adivinhar. */
    @Id
    @Column(length = 32)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected LinkAcesso() {
        // exigido pelo JPA
    }

    LinkAcesso(Pessoa pessoa, OffsetDateTime agora) {
        this.token = novoToken();
        this.pessoa = pessoa;
        this.criadoEm = agora;
    }

    static String novoToken() {
        byte[] bytes = new byte[16];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String getToken() { return token; }
    public Pessoa getPessoa() { return pessoa; }
    public boolean isAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    void desativar() {
        this.ativo = false;
    }
}
