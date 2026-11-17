package com.natal.caixinha.pessoa;

import java.math.BigDecimal;
import java.time.LocalDate;

/** O que o organizador preenche no formulário de pessoa. */
public record DadosPessoa(
        String nome,
        String telefone,
        BigDecimal peso,
        Long responsavelId,
        LocalDate dataEntrada,
        boolean ativo,
        String observacao) {

    public static DadosPessoa de(Pessoa p) {
        return new DadosPessoa(p.getNome(), p.getTelefone() == null ? "" : Telefones.formatar(p.getTelefone()),
                p.getPeso(), p.getResponsavel() == null ? null : p.getResponsavel().getId(),
                p.getDataEntrada(), p.isAtivo(), p.getObservacao());
    }
}
