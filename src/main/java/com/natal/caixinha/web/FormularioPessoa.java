package com.natal.caixinha.web;

import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.pessoa.DadosPessoa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Campos do formulário como texto: a conversão (e a mensagem de erro) fica sob nosso controle. */
public class FormularioPessoa {

    private String nome = "";
    private String telefone = "";
    private String peso = "1.00";
    private Long responsavelId;
    private String dataEntrada = "";
    private boolean ativo = true;
    private String observacao = "";

    public static FormularioPessoa de(DadosPessoa d) {
        FormularioPessoa f = new FormularioPessoa();
        f.nome = d.nome();
        f.telefone = d.telefone();
        f.peso = d.peso().setScale(2).toPlainString();
        f.responsavelId = d.responsavelId();
        f.dataEntrada = d.dataEntrada() == null ? "" : d.dataEntrada().toString();
        f.ativo = d.ativo();
        f.observacao = d.observacao() == null ? "" : d.observacao();
        return f;
    }

    public DadosPessoa paraDados() {
        BigDecimal pesoNumero;
        try {
            pesoNumero = new BigDecimal(peso.trim().replace(',', '.'));
        } catch (RuntimeException e) {
            throw new RegraException("O peso deve ser 1 (cota inteira), 0,5 (meia cota) ou 0 (não paga).");
        }
        LocalDate data = null;
        if (dataEntrada != null && !dataEntrada.isBlank()) {
            try {
                data = LocalDate.parse(dataEntrada.trim());
            } catch (DateTimeParseException e) {
                throw new RegraException("Data de entrada inválida.");
            }
        }
        return new DadosPessoa(nome, telefone, pesoNumero, responsavelId, data, ativo, observacao);
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getPeso() { return peso; }
    public void setPeso(String peso) { this.peso = peso == null ? "" : peso; }
    public Long getResponsavelId() { return responsavelId; }
    public void setResponsavelId(Long responsavelId) { this.responsavelId = responsavelId; }
    public String getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(String dataEntrada) { this.dataEntrada = dataEntrada; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
