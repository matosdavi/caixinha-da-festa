package com.natal.caixinha.web;

import com.natal.caixinha.caixinha.CaixinhaService;
import com.natal.caixinha.caixinha.FamiliaService;
import com.natal.caixinha.lembrete.LembreteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Painel do organizador (protegido por login): início com saldo, progresso e atrasos. */
@Controller
public class PainelController {

    private final CaixinhaService caixinha;
    private final FamiliaService familias;
    private final LembreteService lembretes;

    private final com.natal.caixinha.cronograma.CronogramaService cronograma;

    public PainelController(CaixinhaService caixinha, FamiliaService familias, LembreteService lembretes,
                            com.natal.caixinha.cronograma.CronogramaService cronograma) {
        this.caixinha = caixinha;
        this.familias = familias;
        this.lembretes = lembretes;
        this.cronograma = cronograma;
    }

    @GetMapping("/")
    public String raiz() {
        return "redirect:/painel";
    }

    @GetMapping("/entrar")
    public String entrar() {
        return "entrar";
    }

    @GetMapping("/painel")
    public String inicio(Model model) {
        CaixinhaService.Resumo resumo = caixinha.resumo();
        model.addAttribute("resumo", resumo);
        // quem entrou depois do fechamento e ainda não teve o acréscimo confirmado, depois que o lugar foi pago
        model.addAttribute("entradasTardias", !cronograma.lugarPago() ? java.util.List.of()
                : resumo.situacoes().stream().map(CaixinhaService.SituacaoPessoa::pessoa)
                .filter(p -> p.entrouTarde(resumo.caixinha().getDataFechamentoLista()) && p.getAcrescimo() == null)
                .toList());
        model.addAttribute("familiaDe", familias.responsavelDasFamiliasComVariosPagantes());
        model.addAttribute("cartaoGrupo", lembretes.cartaoGrupo(Enderecos.base()).orElse(null));
        model.addAttribute("cartaoIndividual", lembretes.cartaoIndividual(Enderecos.base()).orElse(null));
        return "painel/inicio";
    }
}
