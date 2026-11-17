package com.natal.caixinha.lancamento;

import com.natal.caixinha.orcamento.Categoria;

import java.math.BigDecimal;
import java.time.LocalDate;

/** O que o organizador preencheu para registrar um lançamento. */
public record DadosLancamento(TipoLancamento tipo, Long pessoaId, BigDecimal valor, LocalDate data,
                              String descricao, Categoria categoria, String comprovanteUrl) {
}
