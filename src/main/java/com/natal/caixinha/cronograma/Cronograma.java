package com.natal.caixinha.cronograma;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cronograma da festa (seção 6 da spec), calculado a partir da data da festa. Classe pura, sem Spring.
 * O lugar é pago por inteiro na reserva (decisão do organizador: no Airbnb não há sinal).
 */
public final class Cronograma {

    public enum Estado { FEITO, AGORA, PROXIMA }

    /**
     * @param reserva marca a etapa em que o lugar é pago, onde aparece a verificação
     */
    public record Etapa(String titulo, String detalhe, YearMonth mes, Estado estado, boolean reserva) {

        /** O título no meio de uma frase: "procurar o lugar". */
        public String frase() {
            return titulo.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + titulo.substring(1);
        }
    }

    /** @param dataFesta nula enquanto a data está em votação */
    public record Dados(LocalDate dataFesta, YearMonth mesInicio, YearMonth mesFim, LocalDate fechamentoLista,
                        LocalDate hoje) {
    }

    /**
     * @param previsto quanto a caixinha deve ter até o fim do mês da reserva, se todos pagarem em dia
     * @param falta    quanto falta para pagar o aluguel inteiro; zero quando cobre
     */
    public record Verificacao(YearMonth mes, BigDecimal previsto, BigDecimal aluguel, BigDecimal falta) {

        public boolean cobre() {
            return falta.signum() == 0;
        }
    }

    private Cronograma() {
    }

    public static List<Etapa> etapas(Dados d) {
        YearMonth hoje = YearMonth.from(d.hoje());
        List<Etapa> etapas = new ArrayList<>();
        etapas.add(etapa("Fechar a lista e fixar a cota", "até " + dataCurta(d.fechamentoLista()),
                YearMonth.from(d.fechamentoLista()), hoje, false));
        etapas.add(etapa("Regras combinadas, data da festa e 1º depósito", "enquete para escolher a data",
                d.mesInicio(), hoje, false));
        if (d.dataFesta() == null) {
            return etapas;
        }
        YearMonth festa = YearMonth.from(d.dataFesta());
        int procurar = antecedenciaProcurar(d.dataFesta());
        etapas.add(etapa("Procurar o lugar",
                "3 opções, o que está incluso, taxa de limpeza, lotação (bebê conta?), horários e regras de som",
                festa.minusMonths(procurar), hoje, false));
        etapas.add(etapa("Reservar e pagar o lugar", "ler a política de cancelamento antes de pagar",
                festa.minusMonths(procurar - 1L), hoje, true));
        etapas.add(etapa("Cardápio, tarefas e lista de compras", "calcular o desconto de novembro",
                d.mesFim().minusMonths(1), hoje, false));
        etapas.add(etapa("Último depósito e sorteio do amigo secreto", "o depósito de novembro já vem com o desconto",
                d.mesFim(), hoje, false));
        etapas.add(etapa("Festa! 🎉", "compras de perecíveis; prestação de contas em até 7 dias",
                festa, hoje, false));
        return etapas;
    }

    /** A primeira etapa que ainda não passou (a do mês atual conta). */
    public static Optional<Etapa> proxima(List<Etapa> etapas) {
        return etapas.stream().filter(e -> e.estado() != Estado.FEITO).findFirst();
    }

    /**
     * Meses de antecedência para procurar o lugar: 7 na alta temporada (a partir de 20/12, ou réveillon em
     * janeiro), 6 antes disso. A reserva é um mês depois de começar a procurar.
     */
    public static int antecedenciaProcurar(LocalDate festa) {
        boolean altaTemporada = festa.getMonth() == Month.JANUARY
                || (festa.getMonth() == Month.DECEMBER && festa.getDayOfMonth() >= 20);
        return altaTemporada ? 7 : 6;
    }

    /**
     * @param extras rendimentos + doações − gastos já lançados
     */
    public static Verificacao verificar(YearMonth mesInicio, YearMonth mesReserva, BigDecimal arrecadacaoMensal,
                                        BigDecimal extras, BigDecimal aluguel) {
        long meses = Math.max(0, mesInicio.until(mesReserva, ChronoUnit.MONTHS) + 1);
        BigDecimal previsto = arrecadacaoMensal.multiply(BigDecimal.valueOf(meses)).add(extras)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal aluguel2 = aluguel.setScale(2, RoundingMode.HALF_UP);
        BigDecimal falta = aluguel2.subtract(previsto).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Verificacao(mesReserva, previsto, aluguel2, falta);
    }

    private static Etapa etapa(String titulo, String detalhe, YearMonth mes, YearMonth hoje, boolean reserva) {
        Estado estado = mes.isBefore(hoje) ? Estado.FEITO : mes.equals(hoje) ? Estado.AGORA : Estado.PROXIMA;
        return new Etapa(titulo, detalhe, mes, estado, reserva);
    }

    private static String dataCurta(LocalDate data) {
        return String.format("%02d/%02d/%d", data.getDayOfMonth(), data.getMonthValue(), data.getYear());
    }
}
