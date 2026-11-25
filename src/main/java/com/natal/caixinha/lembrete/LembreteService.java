package com.natal.caixinha.lembrete;

import com.natal.caixinha.caixinha.Caixinha;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.caixinha.FamiliaService;
import com.natal.caixinha.caixinha.FamiliaService.EstadoDoMes;
import com.natal.caixinha.caixinha.FamiliaService.Familia;
import com.natal.caixinha.comum.RegraException;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pessoa.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cartões de lembrete do painel (seção 5 da spec). Nada é enviado: o app só prepara o texto
 * e grava um {@link AvisoMensal} quando o organizador toca em "Enviar no WhatsApp".
 */
@Service
public class LembreteService {

    /** Dia do mês a partir do qual aparece a lista de quem ainda não pagou. */
    static final int DIA_INDIVIDUAL = 20;
    private static final String INDISPONIVEL = "Este lembrete não está mais disponível.";

    public record CartaoGrupo(YearMonth mes, String texto) {
    }

    public record LinhaIndividual(Long pessoaId, String nome, BigDecimal valor, String telefone, String texto,
                                  boolean avisado) {
    }

    public record CartaoIndividual(YearMonth mes, List<LinhaIndividual> linhas) {
    }

    private final CaixinhaRepository caixinhas;
    private final CaixinhaService caixinha;
    private final FamiliaService familias;
    private final PessoaRepository pessoas;
    private final LinkService links;
    private final AvisoMensalRepository avisos;
    private final Clock clock;

    public LembreteService(CaixinhaRepository caixinhas, CaixinhaService caixinha, FamiliaService familias,
                           PessoaRepository pessoas, LinkService links, AvisoMensalRepository avisos, Clock clock) {
        this.caixinhas = caixinhas;
        this.caixinha = caixinha;
        this.familias = familias;
        this.pessoas = pessoas;
        this.links = links;
        this.avisos = avisos;
        this.clock = clock;
    }

    // ------------------------------------------------------------ grupo

    /** Do dia 1 até ser enviado; de janeiro (começo) a dezembro (fecha novembro). */
    @Transactional(readOnly = true)
    public Optional<CartaoGrupo> cartaoGrupo(String urlBase) {
        return mesDoGrupo()
                .filter(mes -> !avisos.existsByMesAndTipoAndPessoaIsNull(mes, TipoAviso.GRUPO))
                .map(mes -> new CartaoGrupo(mes, textoDoGrupo(mes)));
    }

    /** O texto do grupo do mês, recalculado na hora do envio. */
    @Transactional
    public String avisarGrupo(YearMonth mes) {
        if (!mesDoGrupo().map(mes::equals).orElse(false)) {
            throw new RegraException(INDISPONIVEL);
        }
        if (!avisos.existsByMesAndTipoAndPessoaIsNull(mes, TipoAviso.GRUPO)) {
            avisos.save(new AvisoMensal(mes, null, TipoAviso.GRUPO, OffsetDateTime.now(clock)));
        }
        return textoDoGrupo(mes);
    }

    private Optional<YearMonth> mesDoGrupo() {
        Caixinha c = caixinhas.configuracao();
        YearMonth mes = YearMonth.from(LocalDate.now(clock));
        boolean dentro = !mes.isBefore(c.getMesInicio()) && !mes.isAfter(c.getMesFim().plusMonths(1));
        return dentro ? Optional.of(mes) : Optional.empty();
    }

    private String textoDoGrupo(YearMonth mes) {
        Caixinha c = caixinhas.configuracao();
        CaixinhaService.Resumo r = caixinha.resumo();
        YearMonth fechado = mes.equals(c.getMesInicio()) ? null : mes.minusMonths(1);
        YearMonth novo = mes.isAfter(c.getMesFim()) ? null : mes;
        return MensagensLembrete.grupo(new MensagensLembrete.Grupo(fechado, novo, r.arrecadado(), r.cota().meta(),
                r.percentual(), r.emDia(), r.pagantes(), r.cota().cota()));
    }

    // ------------------------------------------------------------ individual

    /** A partir do dia 20, de janeiro a novembro, enquanto alguém da lista não foi avisado. */
    @Transactional
    public Optional<CartaoIndividual> cartaoIndividual(String urlBase) {
        return mesIndividual()
                .map(mes -> new CartaoIndividual(mes, linhas(mes, urlBase)))
                .filter(c -> c.linhas().stream().anyMatch(l -> !l.avisado()));
    }

    @Transactional
    public LinhaIndividual avisarIndividual(YearMonth mes, Long pessoaId, String urlBase) {
        LinhaIndividual linha = linhaDisponivel(mes, pessoaId, urlBase);
        if (!linha.avisado()) {
            avisos.save(new AvisoMensal(mes, pessoas.getReferenceById(pessoaId), TipoAviso.INDIVIDUAL,
                    OffsetDateTime.now(clock)));
        }
        return new LinhaIndividual(linha.pessoaId(), linha.nome(), linha.valor(), linha.telefone(), linha.texto(),
                true);
    }

    @Transactional
    public void desfazer(YearMonth mes, Long pessoaId) {
        linhaDisponivel(mes, pessoaId, "");
        avisos.deleteByMesAndTipoAndPessoaId(mes, TipoAviso.INDIVIDUAL, pessoaId);
    }

    private LinhaIndividual linhaDisponivel(YearMonth mes, Long pessoaId, String urlBase) {
        if (!mesIndividual().map(mes::equals).orElse(false)) {
            throw new RegraException(INDISPONIVEL);
        }
        return linhas(mes, urlBase).stream().filter(l -> l.pessoaId().equals(pessoaId)).findFirst()
                .orElseThrow(() -> new RegraException(INDISPONIVEL));
    }

    private Optional<YearMonth> mesIndividual() {
        Caixinha c = caixinhas.configuracao();
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mes = YearMonth.from(hoje);
        boolean dentro = !mes.isBefore(c.getMesInicio()) && !mes.isAfter(c.getMesFim())
                && hoje.getDayOfMonth() >= DIA_INDIVIDUAL;
        return dentro ? Optional.of(mes) : Optional.empty();
    }

    /** Uma linha por família (responsável ativo) que ainda deve o mês, por ordem de nome. */
    private List<LinhaIndividual> linhas(YearMonth mes, String urlBase) {
        Map<Long, String> tokens = links.tokensAtivos();
        List<LinhaIndividual> linhas = new ArrayList<>();
        for (Pessoa p : pessoas.findByAtivoTrueOrderByNomeAsc()) {
            if (!p.isResponsavel()) {
                continue;
            }
            Familia f = familias.daFamilia(p.getId());
            if (f.estado() != EstadoDoMes.PENDENTE && f.estado() != EstadoDoMes.ATRASADO) {
                continue;
            }
            String token = tokens.get(p.getId());
            if (token == null) {
                token = links.garantirLink(p).getToken();
            }
            int maiorAtraso = f.pagantes().stream().mapToInt(m -> m.situacao().mesesDeAtraso()).max().orElse(0);
            String texto = MensagensLembrete.individual(new MensagensLembrete.Individual(p.getNome(), mes,
                    f.cotaFamilia(), f.valorPix(), maiorAtraso, urlBase + "/c/" + token));
            boolean avisado = avisos.existsByMesAndTipoAndPessoaId(mes, TipoAviso.INDIVIDUAL, p.getId());
            linhas.add(new LinhaIndividual(p.getId(), p.getNome(), f.valorPix(), p.getTelefone(), texto, avisado));
        }
        return linhas;
    }
}
