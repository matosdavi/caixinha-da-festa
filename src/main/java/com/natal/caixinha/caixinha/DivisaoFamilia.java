package com.natal.caixinha.caixinha;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Sugere como dividir um PIX da família em um depósito por pessoa:
 * primeiro o que cada um precisa para ficar em dia; se chegou menos, proporcional ao que cada um deve;
 * se chegou mais, a sobra vai pelo peso. Os centavos que sobram vão para o responsável.
 */
public final class DivisaoFamilia {

    /** Um pagante da família, na ordem em que aparece na tela. */
    public record Membro(Long pessoaId, BigDecimal peso, BigDecimal paraFicarEmDia, boolean responsavel) {
    }

    public record Parte(Long pessoaId, BigDecimal valor) {
    }

    private DivisaoFamilia() {
    }

    public static List<Parte> dividir(BigDecimal total, List<Membro> membros) {
        if (membros.isEmpty()) {
            return List.of();
        }
        BigDecimal devido = membros.stream().map(Membro::paraFicarEmDia).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal somaPesos = membros.stream().map(Membro::peso).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> valores = new ArrayList<>();
        for (Membro m : membros) {
            BigDecimal parte;
            if (devido.signum() > 0 && total.compareTo(devido) <= 0) {
                parte = proporcional(total, m.paraFicarEmDia(), devido);
            } else {
                parte = m.paraFicarEmDia().add(proporcional(total.subtract(devido), m.peso(), somaPesos));
            }
            valores.add(parte.setScale(2, RoundingMode.DOWN));
        }

        BigDecimal resto = total.subtract(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        int quemRecebeOResto = 0;
        for (int i = 0; i < membros.size(); i++) {
            if (membros.get(i).responsavel()) {
                quemRecebeOResto = i;
                break;
            }
        }
        valores.set(quemRecebeOResto, valores.get(quemRecebeOResto).add(resto).setScale(2, RoundingMode.UNNECESSARY));

        List<Parte> partes = new ArrayList<>();
        for (int i = 0; i < membros.size(); i++) {
            partes.add(new Parte(membros.get(i).pessoaId(), valores.get(i)));
        }
        return partes;
    }

    /** valor × parte ÷ todo, arredondado para baixo em centavos. */
    private static BigDecimal proporcional(BigDecimal valor, BigDecimal parte, BigDecimal todo) {
        if (todo.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(parte).divide(todo, MathContext.DECIMAL128).setScale(2, RoundingMode.DOWN);
    }
}
