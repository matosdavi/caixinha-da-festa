package com.natal.caixinha.prestacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Divide a sobra final proporcionalmente ao que cada pessoa guardou (seção 3.7):
 * {@code parte(p) = sobra × guardado(p) ÷ Σ guardado}, em centavos para baixo; os centavos que faltam vão,
 * um a um, para quem guardou mais (empate: menor id), e a soma bate exatamente com a sobra.
 */
public final class Partes {

    public record Guardado(Long pessoaId, BigDecimal valor) {
    }

    private Partes() {
    }

    public static Map<Long, BigDecimal> dividir(BigDecimal sobra, List<Guardado> guardados) {
        Map<Long, BigDecimal> partes = new LinkedHashMap<>();
        List<Guardado> comValor = guardados.stream().filter(g -> g.valor().signum() > 0).toList();
        if (sobra == null || sobra.signum() <= 0 || comValor.isEmpty()) {
            return partes;
        }
        BigDecimal alvo = sobra.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = comValor.stream().map(Guardado::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal distribuido = BigDecimal.ZERO;
        for (Guardado g : comValor) {
            BigDecimal parte = alvo.multiply(g.valor()).divide(total, 2, RoundingMode.DOWN);
            partes.put(g.pessoaId(), parte);
            distribuido = distribuido.add(parte);
        }
        int centavos = alvo.subtract(distribuido).movePointRight(2).intValueExact();
        List<Guardado> ordem = new ArrayList<>(comValor);
        ordem.sort(Comparator.comparing(Guardado::valor).reversed().thenComparing(Guardado::pessoaId));
        for (int i = 0; i < centavos; i++) {
            Long id = ordem.get(i % ordem.size()).pessoaId();
            partes.put(id, partes.get(id).add(new BigDecimal("0.01")));
        }
        return partes;
    }
}
