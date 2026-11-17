package com.natal.caixinha.web;

import com.natal.caixinha.comum.Dinheiro;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.lancamento.DadosLancamento;
import com.natal.caixinha.lancamento.TipoLancamento;
import com.natal.caixinha.orcamento.Categoria;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Campos do formulário de lançamento como texto. */
public class FormularioLancamento {

    private TipoLancamento tipo = TipoLancamento.DEPOSITO;
    private Long pessoaId;
    private String valor = "";
    private String data = "";
    private String descricao = "";
    private Categoria categoria;
    private String comprovanteUrl = "";

    public DadosLancamento paraDados() {
        LocalDate dia = null;
        if (data != null && !data.isBlank()) {
            try {
                dia = LocalDate.parse(data.trim());
            } catch (DateTimeParseException e) {
                throw new RegraException("Data inválida.");
            }
        }
        return new DadosLancamento(tipo, pessoaId, Dinheiro.ler(valor, "Valor"), dia, descricao, categoria,
                comprovanteUrl);
    }

    public TipoLancamento getTipo() { return tipo; }
    public void setTipo(TipoLancamento tipo) { this.tipo = tipo; }
    public Long getPessoaId() { return pessoaId; }
    public void setPessoaId(Long pessoaId) { this.pessoaId = pessoaId; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public String getComprovanteUrl() { return comprovanteUrl; }
    public void setComprovanteUrl(String comprovanteUrl) { this.comprovanteUrl = comprovanteUrl; }
}
