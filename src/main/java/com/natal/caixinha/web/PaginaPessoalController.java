package com.natal.caixinha.web;

import com.natal.caixinha.agenda.LembreteIcs;
import com.natal.caixinha.caixinha.Caixinha;
import com.natal.caixinha.caixinha.CaixinhaRepository;
import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.caixinha.CalculadoraCaixinha;
import com.natal.caixinha.caixinha.FamiliaService;
import com.natal.caixinha.caixinha.FamiliaService.EstadoDoMes;
import com.natal.caixinha.caixinha.FamiliaService.Familia;
import com.natal.caixinha.caixinha.FamiliaService.Membro;
import com.natal.caixinha.link.LinkAcesso;
import com.natal.caixinha.link.LinkService;
import com.natal.caixinha.pessoa.Pessoa;
import com.natal.caixinha.pix.BrCode;
import com.natal.caixinha.pix.QrCodeSvg;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Página pessoal de cada responsável: /c/{token}. Pública (quem tem o link, vê), sem login.
 * Mostra a família em forma de carnê, o PIX copia e cola da família, o QR Code e o lembrete .ics.
 */
@Controller
public class PaginaPessoalController {

    private static final MediaType CALENDARIO = MediaType.parseMediaType("text/calendar;charset=utf-8");
    private static final BigDecimal MEIA = new BigDecimal("0.5");

    private final LinkService links;
    private final FamiliaService familias;
    private final CaixinhaService caixinha;
    private final CaixinhaRepository caixinhas;
    private final Formato fmt;
    private final Clock clock;
    private final com.natal.caixinha.cronograma.CronogramaService cronograma;

    public PaginaPessoalController(LinkService links, FamiliaService familias, CaixinhaService caixinha,
                                   CaixinhaRepository caixinhas, Formato fmt, Clock clock,
                                   com.natal.caixinha.cronograma.CronogramaService cronograma) {
        this.links = links;
        this.familias = familias;
        this.caixinha = caixinha;
        this.caixinhas = caixinhas;
        this.fmt = fmt;
        this.clock = clock;
        this.cronograma = cronograma;
    }

    @GetMapping("/c/{token}")
    public String pagina(@PathVariable String token, Model model, HttpServletResponse resposta) {
        // página com dados pessoais: nada de cache em navegador nem em proxy
        resposta.setHeader("Cache-Control", "no-store");

        Optional<LinkAcesso> link = links.abrir(token);
        if (link.isEmpty()) {
            // mesma resposta para link inexistente e link substituído: não revela nada
            resposta.setStatus(HttpStatus.NOT_FOUND.value());
            return "pessoal/link-invalido";
        }

        Pessoa responsavel = link.get().getPessoa();
        Familia f = familias.daFamilia(responsavel.getId());
        Optional<BrCode.Recebedor> recebedor = caixinha.recebedor();

        model.addAttribute("caixinha", caixinhas.configuracao());
        model.addAttribute("familia", f);
        model.addAttribute("quem", quem(f.dependentes()));
        model.addAttribute("divisao", divisao(f));
        model.addAttribute("parcial", parcial(f));
        model.addAttribute("resumo", caixinha.resumo());
        model.addAttribute("token", token);
        model.addAttribute("proximaEtapa", cronograma.proximaEtapa().orElse(null));

        boolean temPix = f.valorPix().signum() > 0 && recebedor.isPresent();
        model.addAttribute("semChave", f.valorPix().signum() > 0 && recebedor.isEmpty());
        if (temPix) {
            String pix = BrCode.gerar(recebedor.get(), f.valorPix(), BrCode.txid(responsavel.getId()));
            model.addAttribute("pix", pix);
            model.addAttribute("qr", QrCodeSvg.svg(pix));
            model.addAttribute("recebedor", BrCode.limpar(recebedor.get().nome(), BrCode.MAX_NOME));
        }
        return "pessoal/inicio";
    }

    @GetMapping("/c/{token}/lembrete.ics")
    public ResponseEntity<String> lembrete(@PathVariable String token) {
        Optional<LinkAcesso> link = links.abrir(token);
        if (link.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.parseMediaType("text/plain;charset=utf-8")).body("Link não encontrado.");
        }
        Pessoa responsavel = link.get().getPessoa();
        Familia f = familias.daFamilia(responsavel.getId());
        Caixinha c = caixinhas.configuracao();

        YearMonth entrada = YearMonth.from(responsavel.getDataEntrada());
        YearMonth inicio = entrada.isAfter(c.getMesInicio()) ? entrada : c.getMesInicio();
        int meses = Math.max(1, (int) inicio.until(c.getMesFim(), ChronoUnit.MONTHS) + 1);
        String resumo = f.cotaFixada() && f.cotaFamilia().signum() > 0
                ? "Caixinha da festa — depositar " + fmt.reais(f.cotaFamilia())
                : "Caixinha da festa — depositar a cota do mês";
        String endereco = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/c/" + token;

        String ics = LembreteIcs.gerar(new LembreteIcs.Dados(responsavel.getId(), c.getMesFim().getYear(), inicio,
                meses, resumo, endereco, clock.instant()));
        return ResponseEntity.ok()
                .contentType(CALENDARIO)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"caixinha-da-festa.ics\"")
                .body(ics);
    }

    /** "Esta página é sua." / "Esta página é sua e de Lia." / "Esta página é sua, de A e de B (não paga)." */
    static String quem(List<Pessoa> dependentes) {
        if (dependentes.isEmpty()) {
            return "Esta página é sua.";
        }
        List<String> nomes = new ArrayList<>();
        for (Pessoa d : dependentes) {
            nomes.add("de " + d.getNome() + (d.isPagante() ? "" : " (não paga)"));
        }
        if (nomes.size() == 1) {
            return "Esta página é sua e " + nomes.get(0) + ".";
        }
        return "Esta página é sua, " + String.join(", ", nomes.subList(0, nomes.size() - 1))
                + " e " + nomes.get(nomes.size() - 1) + ".";
    }

    /** Mês pendente em que parte da família já pagou: o PIX é menor que a cota da família. */
    static boolean parcial(Familia f) {
        return f.estado() == EstadoDoMes.PENDENTE && f.valorPix().compareTo(f.cotaFamilia()) < 0;
    }

    /** "É R$ 92,00 de Ana e R$ 46,00 de Theo (meia cota)." com o que falta de cada pagante. */
    String divisao(Familia f) {
        Function<Membro, BigDecimal> quanto = f.estado() == EstadoDoMes.ATRASADO
                ? m -> m.situacao().valorParaFicarEmDia()
                : m -> m.situacao().faltaNoMes();
        List<String> partes = new ArrayList<>();
        for (Membro m : f.pagantes()) {
            BigDecimal v = quanto.apply(m);
            if (v.signum() > 0) {
                boolean meia = m.pessoa().getPeso().compareTo(MEIA) == 0;
                partes.add(fmt.reais(CalculadoraCaixinha.dinheiro(v)) + " de " + m.pessoa().getNome()
                        + (meia ? " (meia cota)" : ""));
            }
        }
        if (partes.isEmpty()) {
            return "";
        }
        String lista = partes.size() == 1 ? partes.get(0)
                : String.join(", ", partes.subList(0, partes.size() - 1)) + " e " + partes.get(partes.size() - 1);
        if (parcial(f)) {
            return "Falta " + lista + ".";
        }
        return partes.size() < 2 ? "" : "É " + lista + ".";
    }
}
