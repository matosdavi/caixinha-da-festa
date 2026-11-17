package com.natal.caixinha.orcamento;

/** Categorias de orçamento e de gasto (as mesmas do CHECK no banco). */
public enum Categoria {
    ALUGUEL("Aluguel"),
    LIMPEZA("Limpeza"),
    COMIDA("Comida"),
    BEBIDA("Bebida"),
    GELO_CARVAO_DESCARTAVEIS("Gelo, carvão e descartáveis"),
    DECORACAO("Decoração"),
    TRANSPORTE("Transporte"),
    OUTROS("Outros");

    private final String rotulo;

    Categoria(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
