package com.natal.caixinha.lancamento;

/**
 * O valor do lançamento é sempre positivo: o tipo diz se o dinheiro entra ou sai da conta,
 * e se o lançamento é de uma pessoa ou da caixinha.
 */
public enum TipoLancamento {
    DEPOSITO("Depósito", true, true),
    RENDIMENTO("Rendimento", false, true),
    DOACAO("Doação", false, true),
    GASTO("Gasto", false, false),
    DEVOLUCAO("Devolução", true, false),
    CREDITO_ANO_ANTERIOR("Crédito do ano anterior", true, true);

    private final String rotulo;
    private final boolean dePessoa;
    private final boolean entrada;

    TipoLancamento(String rotulo, boolean dePessoa, boolean entrada) {
        this.rotulo = rotulo;
        this.dePessoa = dePessoa;
        this.entrada = entrada;
    }

    public String getRotulo() { return rotulo; }

    /** DEPOSITO, DEVOLUCAO e CREDITO_ANO_ANTERIOR exigem pessoa; os outros não podem ter. */
    public boolean isDePessoa() { return dePessoa; }

    /** Entra na conta (soma no saldo) ou sai (subtrai). */
    public boolean isEntrada() { return entrada; }
}
