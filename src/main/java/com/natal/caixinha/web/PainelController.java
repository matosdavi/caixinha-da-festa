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

    public PainelController(CaixinhaService caixinha, FamiliaService familias, LembreteService lembretes) {
        this.caixinha = caixinha;
        this.familias = familias;
        this.lembretes = lembretes;
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
        model.addAttribute("resumo", caixinha.resumo());
        model.addAttribute("familiaDe", familias.responsavelDasFamiliasComVariosPagantes());
        model.addAttribute("cartaoGrupo", lembretes.cartaoGrupo(Enderecos.base()).orElse(null));
        model.addAttribute("cartaoIndividual", lembretes.cartaoIndividual(Enderecos.base()).orElse(null));
        return "painel/inicio";
    }
}
